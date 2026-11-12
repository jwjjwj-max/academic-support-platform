package cn.edu.academic.support.matching.service;

import cn.edu.academic.support.application.mapper.ApplicationProfileSnapshotMapper;
import cn.edu.academic.support.application.mapper.ParticipationApplicationMapper;
import cn.edu.academic.support.application.model.ApplicationProfileSnapshot;
import cn.edu.academic.support.application.model.ParticipationApplication;
import cn.edu.academic.support.audit.service.AuditService;
import cn.edu.academic.support.audit.mapper.OperationLogMapper;
import cn.edu.academic.support.audit.model.OperationLog;
import cn.edu.academic.support.common.exception.BusinessException;
import cn.edu.academic.support.matching.dto.MatchCandidateResponse;
import cn.edu.academic.support.matching.dto.MatchDecisionRequest;
import cn.edu.academic.support.matching.dto.MatchingPolicy;
import cn.edu.academic.support.matching.mapper.MatchCandidateMapper;
import cn.edu.academic.support.matching.mapper.MatchRoundMapper;
import cn.edu.academic.support.matching.mapper.RejectedMatchPairMapper;
import cn.edu.academic.support.matching.mapper.SupportPairMapper;
import cn.edu.academic.support.matching.model.MatchCandidate;
import cn.edu.academic.support.matching.model.MatchRound;
import cn.edu.academic.support.matching.model.RejectedMatchPair;
import cn.edu.academic.support.matching.model.SupportPair;
import cn.edu.academic.support.period.mapper.ActivityPeriodMapper;
import cn.edu.academic.support.period.model.ActivityPeriod;
import cn.edu.academic.support.security.AuthenticatedUser;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MatchingService {
    private final MatchRoundMapper roundMapper;
    private final MatchCandidateMapper candidateMapper;
    private final RejectedMatchPairMapper rejectedPairMapper;
    private final SupportPairMapper supportPairMapper;
    private final ParticipationApplicationMapper applicationMapper;
    private final ApplicationProfileSnapshotMapper snapshotMapper;
    private final ActivityPeriodMapper periodMapper;
    private final ObjectMapper objectMapper;
    private final AuditService auditService;
    private final MatchingWorkspace workspace;
    private final OperationLogMapper logMapper;

    public record RoundSummary(long id, int roundNo, String roundType, String status, Integer workspaceRevision,
                               Integer draftRevision, LocalDateTime createdAt, LocalDateTime publishedAt) {}
    public record AdjustmentLog(String administrator, String reason, LocalDateTime time,
                                List<Long> before, List<Long> after) {}
    public record RoundParticipant(long id, String name, String studentNo, int grade) {}
    public record ManualOption(long navigatorApplicationId, long climberApplicationId, MatchingPolicy.Kind matchKind,
                               List<String> exceptions) {}
    public record RoundView(RoundSummary round, MatchingPolicy policy, List<Long> participantIds,
                            List<MatchCandidateResponse> candidates, List<AdjustmentLog> adjustments,
                            List<RoundParticipant> participants, Set<Long> extraSlotNavigatorIds) {}
    private record RuleSnapshot(MatchingPolicy policy, List<Long> participantIds) {}

    private static final Map<String, String> SUBJECT_ALIASES = Map.ofEntries(
            Map.entry("高数", "高等数学"), Map.entry("高等数学a", "高等数学"),
            Map.entry("高等数学b", "高等数学"), Map.entry("大学英语", "英语"),
            Map.entry("大英", "英语"), Map.entry("c语言", "程序设计基础"),
            Map.entry("c程序设计", "程序设计基础"), Map.entry("线代", "线性代数"),
            Map.entry("概率论", "概率论与数理统计"), Map.entry("毛概", "毛泽东思想和中国特色社会主义理论体系概论")
    );

    @Transactional
    public List<MatchCandidateResponse> generate(AuthenticatedUser actor, Long periodId,
                                                 HttpServletRequest request) {
        ActivityPeriod period = lockPeriod(periodId);
        if (!"MATCHING".equals(period.getStatus())) {
            throw new BusinessException("PERIOD_NOT_MATCHING", "仅匹配阶段可以运行匹配");
        }
        if (workspace.policy(period).revision() > 0) throw new BusinessException("MATCHING_WORKSPACE_REQUIRED", "请使用匹配工作台生成试算");
        return generateRound(actor, period, "NORMAL", Set.of(), request);
    }

    @Transactional
    public List<MatchCandidateResponse> rematch(AuthenticatedUser actor, Long periodId,
                                                HttpServletRequest request) {
        ActivityPeriod period = lockPeriod(periodId);
        if (!"CONFIRMATION".equals(period.getStatus())) {
            throw new BusinessException("LOCAL_REMATCH_NOT_OPEN", "仅双方确认阶段可以进行局部重新匹配");
        }
        if (!LocalDateTime.now().isBefore(period.getConfirmationDeadline())) {
            throw new BusinessException("CONFIRMATION_DEADLINE_REACHED", "匹配确认截止时间已到，请调整期次时间后再操作");
        }
        MatchRound draft = roundMapper.selectOne(Wrappers.<MatchRound>lambdaQuery()
                .eq(MatchRound::getPeriodId, periodId)
                .eq(MatchRound::getRoundType, "LOCAL_REMATCH")
                .eq(MatchRound::getStatus, "DRAFT").last("LIMIT 1"));
        if (draft != null) throw new BusinessException("LOCAL_REMATCH_DRAFT_EXISTS", "已有局部重匹配草稿，请先处理");
        MatchRound latestLocalRound = roundMapper.selectOne(Wrappers.<MatchRound>lambdaQuery()
                .eq(MatchRound::getPeriodId, periodId)
                .eq(MatchRound::getRoundType, "LOCAL_REMATCH").eq(MatchRound::getStatus, "PUBLISHED")
                .orderByDesc(MatchRound::getRoundNo).last("LIMIT 1"));
        var rejectedQuery = Wrappers.<RejectedMatchPair>lambdaQuery()
                .eq(RejectedMatchPair::getPeriodId, periodId)
                .eq(RejectedMatchPair::getActive, true);
        if (latestLocalRound != null) rejectedQuery.gt(RejectedMatchPair::getCreatedAt, latestLocalRound.getCreatedAt());
        Set<Long> affectedUserIds = rejectedPairMapper.selectList(rejectedQuery).stream()
                .flatMap(pair -> java.util.stream.Stream.of(pair.getNavigatorUserId(), pair.getClimberUserId()))
                .collect(Collectors.toSet());
        if (affectedUserIds.isEmpty()) {
            throw new BusinessException("NO_PENDING_REJECTION", "当前没有需要处理的新拒绝记录");
        }
        Set<Long> affectedApplicationIds = applicationMapper.selectList(Wrappers.<ParticipationApplication>lambdaQuery()
                        .eq(ParticipationApplication::getPeriodId, periodId)
                        .in(ParticipationApplication::getStudentUserId, affectedUserIds))
                .stream().map(ParticipationApplication::getId).collect(Collectors.toSet());
        var settings = workspace.policy(period);
        if (settings.revision() > 0) {
            MatchRound latestPublished = latestPublished(periodId);
            MatchingPolicy policy = latestPublished != null && latestPublished.getPolicyRevision() != null
                    ? ruleSnapshot(latestPublished).policy() : settings.policy();
            MatchRound round = generateStructured(actor, period, "LOCAL_REMATCH",
                    new MatchingWorkspace.PolicyVersion(settings.revision(), policy), affectedApplicationIds, request);
            return listAdmin(periodId, round.getId());
        }
        return generateRound(actor, period, "LOCAL_REMATCH", affectedApplicationIds, request);
    }

    private List<MatchCandidateResponse> generateRound(AuthenticatedUser actor, ActivityPeriod period,
                                                       String roundType, Set<Long> affectedApplicationIds,
                                                       HttpServletRequest request) {
        Long periodId = period.getId();
        List<ParticipationApplication> pool = applicationMapper.selectList(Wrappers.<ParticipationApplication>lambdaQuery()
                .eq(ParticipationApplication::getPeriodId, periodId)
                .eq(ParticipationApplication::getApplicationStatus, "APPROVED")
                .eq(ParticipationApplication::getSelectionStatus, "MATCHING_POOL"));
        Set<Long> unavailable = unavailableApplicationIds(periodId);
        List<ParticipationApplication> navigators = pool.stream()
                .filter(a -> "NAVIGATOR".equals(a.getParticipantRole()) && !unavailable.contains(a.getId())).toList();
        List<ParticipationApplication> climbers = pool.stream()
                .filter(a -> "CLIMBER".equals(a.getParticipantRole()) && !unavailable.contains(a.getId())).toList();
        if (navigators.isEmpty() || climbers.isEmpty()) {
            throw new BusinessException("MATCHING_POOL_INCOMPLETE", "匹配池中需要至少一名领航者和一名攀登者");
        }

        MatchRound round = new MatchRound();
        round.setPeriodId(periodId);
        round.setRoundNo(nextRoundNo(periodId));
        round.setRoundType(roundType);
        round.setStatus("DRAFT");
        round.setWeightSnapshot(json(weights(period)));
        round.setCreatedBy(actor.id());
        roundMapper.insert(round);

        List<ScoredPair> scoredPairs = new ArrayList<>();
        for (ParticipationApplication navigator : navigators) {
            for (ParticipationApplication climber : climbers) {
                if (!affectedApplicationIds.isEmpty()
                        && !affectedApplicationIds.contains(navigator.getId())
                        && !affectedApplicationIds.contains(climber.getId())) continue;
                if (isRejected(periodId, navigator.getStudentUserId(), climber.getStudentUserId())) continue;
                ApplicationProfileSnapshot navigatorProfile = snapshot(navigator.getId());
                ApplicationProfileSnapshot climberProfile = snapshot(climber.getId());
                if (navigatorProfile.getGrade() > climberProfile.getGrade()) continue;
                if (navigator.getCumulativeGpa().compareTo(climber.getCumulativeGpa()) <= 0) continue;
                ScoredPair scored = score(period, navigator, navigatorProfile, climber, climberProfile);
                if (!scored.matchedSubjects().isEmpty()) scoredPairs.add(scored);
            }
        }
        if ("LOCAL_REMATCH".equals(roundType) && scoredPairs.isEmpty()) {
            throw new BusinessException("NO_LOCAL_REMATCH_CANDIDATE", "当前没有满足条件的局部重匹配组合，请人工处理");
        }
        scoredPairs.sort(Comparator.comparing(ScoredPair::totalScore).reversed());
        Set<Long> selectedNavigators = new HashSet<>();
        Set<Long> selectedClimbers = new HashSet<>();
        for (ScoredPair scored : scoredPairs) {
            MatchCandidate candidate = new MatchCandidate();
            candidate.setRoundId(round.getId());
            candidate.setNavigatorApplicationId(scored.navigator().getId());
            candidate.setClimberApplicationId(scored.climber().getId());
            candidate.setTotalScore(scored.totalScore());
            candidate.setScoreDetail(json(scored.detail()));
            candidate.setMatchedSubjects(json(scored.matchedSubjects()));
            boolean selected = !selectedNavigators.contains(scored.navigator().getId())
                    && !selectedClimbers.contains(scored.climber().getId());
            if (selected) {
                selectedNavigators.add(scored.navigator().getId());
                selectedClimbers.add(scored.climber().getId());
            }
            candidate.setAdminConfirmed(selected);
            candidate.setStatus(selected ? "SELECTED" : "GENERATED");
            candidate.setNavigatorDecision("PENDING");
            candidate.setClimberDecision("PENDING");
            candidateMapper.insert(candidate);
        }
        auditService.log(actor, "LOCAL_REMATCH".equals(roundType) ? "MATCHING_LOCAL_REMATCH" : "MATCHING_RUN",
                "MATCH_ROUND", round.getId(), null,
                Map.of("periodId", periodId, "candidateCount", scoredPairs.size()), null, request);
        return listAdmin(periodId, round.getId());
    }

    @Transactional
    public RoundView preview(AuthenticatedUser actor, Long periodId, int workspaceRevision, Long replaceRoundId,
                             Integer replaceRevision, HttpServletRequest request) {
        ActivityPeriod period = lockPeriod(periodId);
        if (!"MATCHING".equals(period.getStatus()) || latestPublished(periodId) != null) {
            throw new BusinessException("MATCHING_PREVIEW_NOT_OPEN", "仅首次发布前的匹配阶段可以生成本期试算");
        }
        var settings = workspace.policy(period);
        MatchingWorkspace.checkRevision(workspaceRevision, settings.revision());
        if (settings.revision() == 0) throw new BusinessException("MATCHING_POLICY_NOT_SAVED", "请先保存本期匹配规则");
        List<MatchRound> drafts = roundMapper.selectList(Wrappers.<MatchRound>lambdaQuery()
                .eq(MatchRound::getPeriodId, periodId).eq(MatchRound::getStatus, "DRAFT")
                .orderByDesc(MatchRound::getRoundNo));
        MatchRound current = drafts.isEmpty() ? null : drafts.getFirst();
        if (!Objects.equals(replaceRoundId, current == null ? null : current.getId())
                || !Objects.equals(replaceRevision, current == null ? null : current.getDraftRevision())) {
            throw new BusinessException("MATCHING_VERSION_CONFLICT", "试算草稿已更新，请刷新并确认替换", HttpStatus.CONFLICT);
        }
        for (MatchRound draft : drafts) supersede(actor, draft, "重新试算替换草稿", request);
        return roundView(generateStructured(actor, period, "NORMAL", settings, Set.of(), request));
    }

    private MatchRound generateStructured(AuthenticatedUser actor, ActivityPeriod period, String roundType,
                                          MatchingWorkspace.PolicyVersion settings, Set<Long> affected,
                                          HttpServletRequest request) {
        long periodId = period.getId();
        var people = workspace.participants(periodId, unavailableApplicationIds(periodId));
        var plan = MatchingPlanner.plan(settings.policy(), people, blockedPairs(periodId, people), affected);
        if (!affected.isEmpty() && plan.candidates().isEmpty()) {
            throw new BusinessException("NO_LOCAL_REMATCH_CANDIDATE", "没有满足当前规则的局部重匹配组合，请复核依据");
        }
        Set<Long> considered = new HashSet<>(affected);
        if (!affected.isEmpty()) plan.candidates().forEach(edge -> {
            considered.add(edge.first().id());
            considered.add(edge.second().id());
        });
        MatchRound round = new MatchRound();
        round.setPeriodId(periodId);
        round.setRoundNo(nextRoundNo(periodId));
        round.setRoundType(roundType);
        round.setStatus("DRAFT");
        round.setPolicyRevision(settings.revision());
        round.setDraftRevision(0);
        round.setWeightSnapshot(json(new RuleSnapshot(settings.policy(), people.stream().map(MatchingPlanner.Participant::id)
                .filter(id -> affected.isEmpty() || considered.contains(id)).toList())));
        round.setCreatedBy(actor.id());
        roundMapper.insert(round);
        for (var edge : plan.candidates()) {
            MatchCandidate candidate = newCandidate(round, edge);
            boolean selected = plan.selected().contains(edge);
            candidate.setAdminConfirmed(selected);
            candidate.setStatus(selected ? "SELECTED" : "GENERATED");
            candidateMapper.insert(candidate);
        }
        auditService.log(actor, "MATCHING_WORKSPACE_PREVIEW", "MATCH_ROUND", round.getId(), null,
                Map.of("periodId", periodId, "candidateCount", plan.candidates().size(), "selectedPairs", plan.selected().size()), null, request);
        return requiredRound(round.getId());
    }

    private MatchCandidate newCandidate(MatchRound round, MatchingPlanner.Candidate edge) {
        MatchCandidate candidate = new MatchCandidate();
        candidate.setRoundId(round.getId());
        candidate.setNavigatorApplicationId(edge.first().id());
        candidate.setClimberApplicationId(edge.second().id());
        candidate.setMatchKind(edge.rule().kind().name());
        candidate.setTotalScore(BigDecimal.ZERO);
        candidate.setScoreDetail(json(edge.basis()));
        candidate.setMatchedSubjects(json(edge.subjects()));
        candidate.setManuallySelected(false);
        candidate.setNavigatorDecision("PENDING");
        candidate.setClimberDecision("PENDING");
        return candidate;
    }

    private Set<MatchingPlanner.Key> blockedPairs(long periodId, List<MatchingPlanner.Participant> people) {
        Set<MatchingPlanner.Key> blocked = workspace.blocked(periodId);
        Map<Long, Long> applicationByUser = people.stream().collect(Collectors.toMap(
                person -> person.application().getStudentUserId(), MatchingPlanner.Participant::id));
        for (RejectedMatchPair pair : rejectedPairMapper.selectList(Wrappers.<RejectedMatchPair>lambdaQuery()
                .eq(RejectedMatchPair::getPeriodId, periodId).eq(RejectedMatchPair::getActive, true))) {
            Long first = applicationByUser.get(pair.getNavigatorUserId()), second = applicationByUser.get(pair.getClimberUserId());
            if (first != null && second != null) blocked.add(MatchingPlanner.Key.of(first, second));
        }
        return blocked;
    }

    @Transactional(readOnly = true)
    public List<RoundSummary> rounds(Long periodId) {
        requiredPeriod(periodId);
        return roundMapper.selectList(Wrappers.<MatchRound>lambdaQuery().eq(MatchRound::getPeriodId, periodId)
                        .orderByDesc(MatchRound::getRoundNo)).stream().map(this::summary).toList();
    }

    @Transactional(readOnly = true)
    public RoundView roundView(Long periodId, Long roundId) {
        MatchRound round = requiredRound(roundId);
        if (!periodId.equals(round.getPeriodId())) throw new BusinessException("MATCH_ROUND_NOT_FOUND", "本期匹配轮次不存在", HttpStatus.NOT_FOUND);
        return roundView(round);
    }

    private RoundView roundView(MatchRound round) {
        RuleSnapshot snapshot = round.getPolicyRevision() == null ? new RuleSnapshot(null, List.of()) : ruleSnapshot(round);
        List<AdjustmentLog> adjustments = logMapper.selectList(Wrappers.<OperationLog>lambdaQuery()
                        .eq(OperationLog::getAction, "MATCHING_MANUAL_ADJUST").eq(OperationLog::getTargetType, "MATCH_ROUND")
                        .eq(OperationLog::getTargetId, round.getId().toString()).orderByDesc(OperationLog::getId))
                .stream().map(log -> new AdjustmentLog(log.getActorLogin(), log.getReason(), log.getCreatedAt(),
                        readIds(log.getBeforeSnapshot()), readIds(log.getAfterSnapshot()))).toList();
        var candidates = adminCandidates(round);
        Set<Long> ids = new HashSet<>(snapshot.participantIds());
        for (var candidate : candidates) {
            ids.add(candidate.navigatorApplicationId());
            ids.add(candidate.climberApplicationId());
        }
        List<RoundParticipant> participants = ids.isEmpty() ? List.of() : snapshotMapper.selectList(
                Wrappers.<ApplicationProfileSnapshot>lambdaQuery().in(ApplicationProfileSnapshot::getApplicationId, ids)).stream()
                .map(profile -> new RoundParticipant(profile.getApplicationId(), profile.getRealName(), profile.getStudentNo(),
                        profile.getGrade())).toList();
        Set<Long> extraSlotIds = "DRAFT".equals(round.getStatus()) && "NORMAL".equals(round.getRoundType())
                ? extraSlotNavigators(round.getPeriodId()) : Set.of();
        return new RoundView(summary(round), snapshot.policy(), snapshot.participantIds(), candidates, adjustments, participants, extraSlotIds);
    }

    private List<Long> readIds(String value) {
        try { return objectMapper.readValue(value, new TypeReference<>() {}); }
        catch (JsonProcessingException exception) { throw new IllegalStateException(exception); }
    }

    private RuleSnapshot ruleSnapshot(MatchRound round) {
        try { return objectMapper.readValue(round.getWeightSnapshot(), RuleSnapshot.class); }
        catch (JsonProcessingException exception) { throw new IllegalStateException(exception); }
    }

    private RoundSummary summary(MatchRound round) {
        return new RoundSummary(round.getId(), round.getRoundNo(), round.getRoundType(), round.getStatus(),
                round.getPolicyRevision(), round.getDraftRevision(), round.getCreatedAt(), round.getPublishedAt());
    }

    private List<MatchCandidateResponse> adminCandidates(MatchRound round) {
        List<MatchCandidate> candidates = candidateMapper.selectList(Wrappers.<MatchCandidate>lambdaQuery()
                .eq(MatchCandidate::getRoundId, round.getId()).orderByDesc(MatchCandidate::getAdminConfirmed)
                .orderByDesc(MatchCandidate::getTotalScore).orderByAsc(MatchCandidate::getId));
        if (candidates.isEmpty()) return List.of();
        Set<Long> ids = candidates.stream().flatMap(candidate -> members(candidate).stream()).collect(Collectors.toSet());
        Map<Long, ParticipationApplication> apps = applicationMapper.selectByIds(ids).stream()
                .collect(Collectors.toMap(ParticipationApplication::getId, Function.identity()));
        Map<Long, ApplicationProfileSnapshot> profiles = snapshotMapper.selectList(Wrappers.<ApplicationProfileSnapshot>lambdaQuery()
                        .in(ApplicationProfileSnapshot::getApplicationId, ids)).stream()
                .collect(Collectors.toMap(ApplicationProfileSnapshot::getApplicationId, Function.identity()));
        Map<Long, SupportPair> pairs = supportPairMapper.selectList(Wrappers.<SupportPair>lambdaQuery()
                        .eq(SupportPair::getPeriodId, round.getPeriodId()).isNotNull(SupportPair::getSourceCandidateId)).stream()
                .collect(Collectors.toMap(SupportPair::getSourceCandidateId, Function.identity(), (first, second) -> first));
        return candidates.stream().map(candidate -> {
            Long a = candidate.getNavigatorApplicationId(), b = candidate.getClimberApplicationId();
            return response(candidate, round, apps.get(a), apps.get(b), profiles.get(a), profiles.get(b), pairs.get(candidate.getId()), false, null);
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<ManualOption> manualOptions(Long roundId, int revision, Long applicationId) {
        MatchRound round = requiredRound(roundId);
        requireEditable(round, requiredPeriod(round.getPeriodId()), revision);
        return manualCandidates(round, applicationId).stream().map(candidate -> new ManualOption(candidate.first().id(),
                candidate.second().id(), candidate.rule().kind(), candidate.basis().exceptions())).toList();
    }

    private List<MatchingPlanner.Candidate> manualCandidates(MatchRound round, Long applicationId) {
        if (!"NORMAL".equals(round.getRoundType())) {
            throw new BusinessException("MATCHING_MANUAL_DRAFT_REQUIRED", "管理员指定搭档仅适用于本期试算草稿");
        }
        RuleSnapshot snapshot = ruleSnapshot(round);
        Set<Long> ids = new HashSet<>(snapshot.participantIds());
        long periodId = round.getPeriodId();
        var people = workspace.participants(periodId, unavailableApplicationIds(periodId)).stream()
                .filter(person -> ids.contains(person.id())).toList();
        var anchor = people.stream().filter(person -> applicationId.equals(person.id())).findFirst()
                .orElseThrow(() -> new BusinessException("MATCHING_MANUAL_STUDENT_UNAVAILABLE", "该学生已不在本轮可安排名单"));
        var blocked = blockedPairs(periodId, people);
        return people.stream().filter(person -> !person.id().equals(applicationId)
                        && !blocked.contains(MatchingPlanner.Key.of(applicationId, person.id())))
                .map(person -> MatchingPlanner.manualCandidate(snapshot.policy(), anchor, person)).filter(Objects::nonNull).toList();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public RoundView adjust(AuthenticatedUser actor, Long roundId, int revision, Long removeId, Long selectId,
                            Long applicationId, Long partnerId, String reason, HttpServletRequest request) {
        return adjust(actor, roundId, revision, removeId, selectId, applicationId, partnerId, false, reason, request);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public RoundView adjust(AuthenticatedUser actor, Long roundId, int revision, Long removeId, Long selectId,
                            Long applicationId, Long partnerId, boolean extraSlot, String reason, HttpServletRequest request) {
        ActivityPeriod period = lockRoundPeriod(roundId);
        MatchRound round = requiredRound(roundId);
        requireEditable(round, period, revision);
        boolean manual = applicationId != null && partnerId != null;
        if ((applicationId == null) != (partnerId == null) || manual && selectId != null
                || !manual && Objects.equals(removeId, selectId) || extraSlot && (!manual || removeId != null)) {
            throw new BusinessException("INVALID_MATCHING_ADJUSTMENT", "请选择拆组或新的组合");
        }
        List<MatchCandidate> before = candidateMapper.selectList(Wrappers.<MatchCandidate>lambdaQuery()
                .eq(MatchCandidate::getRoundId, roundId).eq(MatchCandidate::getAdminConfirmed, true));
        MatchCandidate remove = removeId == null ? null : before.stream().filter(pair -> removeId.equals(pair.getId())).findFirst()
                .orElseThrow(() -> new BusinessException("INVALID_MATCHING_ADJUSTMENT", "待解除的组合已变更，请刷新"));
        MatchCandidate selected = manual ? manualSelection(round, applicationId, partnerId)
                : selectId == null ? null : requiredCandidate(selectId);
        if (selected != null && (!roundId.equals(selected.getRoundId())
                || selected.getId() != null && !"GENERATED".equals(selected.getStatus())
                && !(manual && "MANUAL_ONLY".equals(selected.getStatus()))
                || remove != null && Collections.disjoint(members(remove), members(selected)))) {
            throw new BusinessException("INVALID_MATCHING_ADJUSTMENT", "请选择本轮符合规则的候选组合");
        }
        if (extraSlot && before.stream().anyMatch(pair -> members(pair).contains(selected.getClimberApplicationId()))) {
            throw new BusinessException("MATCHING_EXTRA_SLOT_CLIMBER_OCCUPIED", "额外名额只能用于待安排的攀登者");
        }
        if (selected != null) selected.setNavigatorSlot(extraSlot ? 2 : 1);
        List<MatchCandidate> removed = extraSlot ? List.of() : before.stream().filter(pair -> pair == remove
                || selected != null && !Collections.disjoint(members(pair), members(selected))).toList();
        List<MatchCandidate> after = new ArrayList<>(before);
        after.removeAll(removed);
        if (selected != null) after.add(selected);
        validateSelection(round, after);
        for (MatchCandidate candidate : removed) {
            candidate.setAdminConfirmed(false);
            candidate.setManuallySelected(false);
            candidate.setNavigatorSlot(1);
            candidate.setStatus(map(candidate.getScoreDetail()).get("exceptions") instanceof List<?> exceptions && !exceptions.isEmpty()
                    ? "MANUAL_ONLY" : "GENERATED");
            candidateMapper.updateById(candidate);
        }
        if (selected != null) {
            selected.setAdminConfirmed(true);
            selected.setManuallySelected(true);
            selected.setStatus("SELECTED");
            if (selected.getId() == null) candidateMapper.insert(selected);
            else candidateMapper.updateById(selected);
        }
        round.setDraftRevision(round.getDraftRevision() + 1);
        roundMapper.updateById(round);
        auditService.log(actor, "MATCHING_MANUAL_ADJUST", "MATCH_ROUND", roundId,
                before.stream().map(MatchCandidate::getId).toList(), after.stream().map(MatchCandidate::getId).toList(), reason.strip(), request);
        return roundView(round);
    }

    private MatchCandidate manualSelection(MatchRound round, Long applicationId, Long partnerId) {
        var edge = manualCandidates(round, applicationId).stream().filter(candidate ->
                        partnerId.equals(candidate.first().id()) || partnerId.equals(candidate.second().id())).findFirst()
                .orElseThrow(() -> new BusinessException("MATCHING_MANUAL_PAIR_UNAVAILABLE", "这两名学生不符合本轮可指定的身份、年级或组合限制"));
        MatchCandidate existing = candidateMapper.selectOne(Wrappers.<MatchCandidate>lambdaQuery()
                .eq(MatchCandidate::getRoundId, round.getId()).eq(MatchCandidate::getNavigatorApplicationId, edge.first().id())
                .eq(MatchCandidate::getClimberApplicationId, edge.second().id()));
        return existing == null ? newCandidate(round, edge) : existing;
    }

    private List<Long> members(MatchCandidate candidate) {
        return List.of(candidate.getNavigatorApplicationId(), candidate.getClimberApplicationId());
    }

    private void validateSelection(MatchRound round, List<MatchCandidate> selected) {
        Set<Long> people = new HashSet<>();
        Set<Long> usedExtraSlots = new HashSet<>();
        for (MatchCandidate candidate : selected) {
            List<Long> occupied = members(candidate);
            if (usesExtraSlot(candidate)) {
                Long navigatorId = candidate.getNavigatorApplicationId();
                boolean hasPrimary = selected.stream().anyMatch(pair -> !usesExtraSlot(pair)
                        && !"PEER".equals(pair.getMatchKind()) && navigatorId.equals(pair.getNavigatorApplicationId()));
                if (!"NORMAL".equals(round.getRoundType()) || "PEER".equals(candidate.getMatchKind()) || !hasPrimary
                        || !usedExtraSlots.add(navigatorId) || !extraSlotNavigators(round.getPeriodId()).contains(navigatorId)) {
                    throw new BusinessException("MATCHING_EXTRA_SLOT_UNAVAILABLE", "该领航者没有可用的额外名额，或原帮扶对已变更");
                }
                occupied = List.of(candidate.getClimberApplicationId());
            }
            for (Long id : occupied) {
                if (!people.add(id)) throw new BusinessException("MATCHING_STUDENT_OCCUPIED", "同一学生本期只能进入一组");
            }
        }
        if (round.getPolicyRevision() == null) return;
        Set<MatchingPlanner.Key> blocked = workspace.blocked(round.getPeriodId());
        for (MatchCandidate candidate : selected) {
            if (blocked.contains(MatchingPlanner.Key.of(candidate.getNavigatorApplicationId(), candidate.getClimberApplicationId()))) {
                throw new BusinessException("MATCHING_PAIR_EXCLUDED", "该组合已排除，请重新试算");
            }
        }
        for (MatchingPolicy.Rule rule : ruleSnapshot(round).policy().rules()) {
            long count = selected.stream().filter(pair -> rule.kind().name().equals(pair.getMatchKind())).count();
            if (rule.maximumPairs() != null && count > rule.maximumPairs()) {
                throw new BusinessException("MATCHING_RULE_CAPACITY", "调整会超过该规则配置的组数上限");
            }
        }
    }

    private boolean usesExtraSlot(MatchCandidate candidate) {
        return Integer.valueOf(2).equals(candidate.getNavigatorSlot());
    }

    private Set<Long> extraSlotNavigators(Long periodId) {
        return applicationMapper.selectList(Wrappers.<ParticipationApplication>lambdaQuery()
                        .select(ParticipationApplication::getId).eq(ParticipationApplication::getPeriodId, periodId)
                        .eq(ParticipationApplication::getExtraNavigatorSlot, true)
                        .eq(ParticipationApplication::getParticipantRole, "NAVIGATOR")
                        .eq(ParticipationApplication::getApplicationStatus, "APPROVED")
                        .eq(ParticipationApplication::getSelectionStatus, "MATCHING_POOL")).stream()
                .map(ParticipationApplication::getId).collect(Collectors.toSet());
    }

    private void requireEditable(MatchRound round, ActivityPeriod period, Integer revision) {
        requireRoundOperationStage(round, period);
        if (!"DRAFT".equals(round.getStatus()) || round.getPolicyRevision() == null) {
            throw new BusinessException("MATCHING_DRAFT_NOT_EDITABLE", "仅本期规则试算草稿可调整");
        }
        if (revision == null) throw new BusinessException("MATCHING_REVISION_REQUIRED", "请从工作台提交当前草稿版本");
        MatchingWorkspace.checkRevision(revision, round.getDraftRevision());
        MatchingWorkspace.checkRevision(round.getPolicyRevision(), workspace.policy(period).revision());
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void discard(AuthenticatedUser actor, Long roundId, Integer revision, String reason, HttpServletRequest request) {
        ActivityPeriod period = lockRoundPeriod(roundId);
        MatchRound round = requiredRound(roundId);
        requireRoundOperationStage(round, period);
        if (!"DRAFT".equals(round.getStatus()) || !Objects.equals(revision, round.getDraftRevision())) {
            throw new BusinessException("MATCHING_VERSION_CONFLICT", "草稿已更新，请刷新后再操作", HttpStatus.CONFLICT);
        }
        supersede(actor, round, reason, request);
    }

    private void supersede(AuthenticatedUser actor, MatchRound round, String reason, HttpServletRequest request) {
        round.setStatus("SUPERSEDED");
        roundMapper.updateById(round);
        auditService.log(actor, "MATCHING_DRAFT_SUPERSEDED", "MATCH_ROUND", round.getId(), null, null, reason.strip(), request);
    }

    private MatchRound latestPublished(long periodId) {
        return roundMapper.selectOne(Wrappers.<MatchRound>lambdaQuery().eq(MatchRound::getPeriodId, periodId)
                .eq(MatchRound::getStatus, "PUBLISHED").orderByDesc(MatchRound::getRoundNo).last("LIMIT 1"));
    }

    private int nextRoundNo(long periodId) {
        return 1 + roundMapper.selectObjs(Wrappers.<MatchRound>query()
                        .select("MAX(round_no)").eq("period_id", periodId))
                .stream().filter(Objects::nonNull).mapToInt(value -> ((Number) value).intValue()).findFirst().orElse(0);
    }

    public List<MatchCandidateResponse> listAdmin(Long periodId, Long roundId) {
        Long targetRound = roundId;
        if (targetRound == null) {
            MatchRound latest = roundMapper.selectOne(Wrappers.<MatchRound>lambdaQuery()
                    .eq(MatchRound::getPeriodId, periodId).ne(MatchRound::getStatus, "SUPERSEDED")
                    .orderByDesc(MatchRound::getRoundNo).last("LIMIT 1"));
            if (latest == null) return List.of();
            targetRound = latest.getId();
        }
        MatchRound round = requiredRound(targetRound);
        if (!periodId.equals(round.getPeriodId())) throw new BusinessException("MATCH_ROUND_NOT_FOUND", "本期匹配轮次不存在", HttpStatus.NOT_FOUND);
        return adminCandidates(round);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public MatchCandidateResponse select(AuthenticatedUser actor, Long candidateId, HttpServletRequest request) {
        ActivityPeriod period = lockCandidatePeriod(candidateId);
        MatchCandidate selected = requiredCandidate(candidateId);
        MatchRound round = requiredRound(selected.getRoundId());
        requireRoundOperationStage(round, period);
        if (round.getPolicyRevision() != null || workspace.policy(period).revision() > 0)
            throw new BusinessException("MATCHING_WORKSPACE_REQUIRED", "规则轮次须在工作台填写原因后调整");
        if (!"DRAFT".equals(round.getStatus())) throw new BusinessException("ROUND_ALREADY_PUBLISHED", "已发布轮次不可调整");
        List<MatchCandidate> conflicts = candidateMapper.selectList(Wrappers.<MatchCandidate>lambdaQuery()
                .eq(MatchCandidate::getRoundId, round.getId())
                .and(q -> q.eq(MatchCandidate::getNavigatorApplicationId, selected.getNavigatorApplicationId())
                        .or().eq(MatchCandidate::getClimberApplicationId, selected.getClimberApplicationId())));
        for (MatchCandidate conflict : conflicts) {
            conflict.setAdminConfirmed(conflict.getId().equals(candidateId));
            conflict.setStatus(conflict.getId().equals(candidateId) ? "SELECTED" : "GENERATED");
            candidateMapper.updateById(conflict);
        }
        auditService.log(actor, "MATCHING_ADJUST", "MATCH_CANDIDATE", candidateId, null,
                Map.of("selected", true), null, request);
        return response(requiredCandidate(candidateId), false, null);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public List<MatchCandidateResponse> publish(AuthenticatedUser actor, Long roundId, HttpServletRequest request) {
        return publish(actor, roundId, null, request);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public List<MatchCandidateResponse> publish(AuthenticatedUser actor, Long roundId, Integer revision,
                                               HttpServletRequest request) {
        ActivityPeriod period = lockRoundPeriod(roundId);
        MatchRound round = requiredRound(roundId);
        requireRoundOperationStage(round, period);
        if (!LocalDateTime.now().isBefore(period.getConfirmationDeadline())) {
            throw new BusinessException("CONFIRMATION_DEADLINE_REACHED", "匹配确认截止时间已到，请调整期次时间后再发布");
        }
        if (!"DRAFT".equals(round.getStatus())) throw new BusinessException("ROUND_ALREADY_PUBLISHED", "该轮匹配已经发布");
        if (round.getPolicyRevision() != null) requireEditable(round, period, revision);
        else if (workspace.policy(period).revision() > 0) throw new BusinessException("MATCHING_WORKSPACE_REQUIRED", "请按现行规则重新试算后发布");
        List<MatchCandidate> selected = candidateMapper.selectList(Wrappers.<MatchCandidate>lambdaQuery()
                .eq(MatchCandidate::getRoundId, roundId).eq(MatchCandidate::getAdminConfirmed, true));
        if (selected.isEmpty()) throw new BusinessException("NO_SELECTED_CANDIDATE", "请先确认至少一个候选组合");
        validateSelection(round, selected);
        Set<Long> unavailable = unavailableApplicationIds(round.getPeriodId());
        if (selected.stream().anyMatch(candidate -> members(candidate).stream().anyMatch(unavailable::contains))) {
            throw new BusinessException("MATCHING_STUDENT_OCCUPIED", "学生已在另一轮发布或确认，请重新试算");
        }
        LocalDateTime now = LocalDateTime.now();
        for (MatchCandidate candidate : selected) {
            candidate.setStatus("PUBLISHED");
            candidate.setPublishedAt(now);
            candidateMapper.updateById(candidate);
        }
        round.setStatus("PUBLISHED");
        round.setPublishedAt(now);
        roundMapper.updateById(round);
        auditService.log(actor, "MATCHING_PUBLISH", "MATCH_ROUND", roundId, null,
                Map.of("publishedCandidates", selected.size()), null, request);
        return listAdmin(round.getPeriodId(), roundId);
    }

    public List<MatchCandidateResponse> mine(Long userId) {
        List<ParticipationApplication> applications = applicationMapper.selectList(Wrappers.<ParticipationApplication>lambdaQuery()
                .eq(ParticipationApplication::getStudentUserId, userId));
        if (applications.isEmpty()) return List.of();
        Set<Long> ids = applications.stream().map(ParticipationApplication::getId).collect(Collectors.toSet());
        return candidateMapper.selectList(Wrappers.<MatchCandidate>lambdaQuery()
                        .in(MatchCandidate::getStatus, List.of("PUBLISHED", "CONFIRMED", "REJECTED"))
                        .and(q -> q.in(MatchCandidate::getNavigatorApplicationId, ids)
                                .or().in(MatchCandidate::getClimberApplicationId, ids))
                        .orderByDesc(MatchCandidate::getPublishedAt))
                .stream()
                .filter(candidate -> studentCanSee(requiredPeriod(requiredRound(candidate.getRoundId()).getPeriodId()).getStatus()))
                .map(candidate -> response(candidate, true, userId)).toList();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public MatchCandidateResponse decide(AuthenticatedUser actor, Long candidateId, MatchDecisionRequest decision,
                                         HttpServletRequest request) {
        ActivityPeriod period = lockCandidatePeriod(candidateId);
        MatchCandidate candidate = requiredCandidate(candidateId);
        if (!"CONFIRMATION".equals(period.getStatus())) {
            throw new BusinessException("MATCHING_CONFIRMATION_NOT_OPEN", "仅双方确认阶段可以确认或拒绝匹配");
        }
        if (!LocalDateTime.now().isBefore(period.getConfirmationDeadline())) {
            throw new BusinessException("MATCHING_CONFIRMATION_ENDED", "匹配确认已截止，请联系管理员处理");
        }
        if (!"PUBLISHED".equals(candidate.getStatus())) {
            throw new BusinessException("CANDIDATE_NOT_DECIDABLE", "当前匹配结果不能确认或拒绝");
        }
        ParticipationApplication navigator = applicationMapper.selectById(candidate.getNavigatorApplicationId());
        ParticipationApplication climber = applicationMapper.selectById(candidate.getClimberApplicationId());
        boolean isNavigator = navigator.getStudentUserId().equals(actor.id());
        boolean isClimber = climber.getStudentUserId().equals(actor.id());
        if (!isNavigator && !isClimber) throw new BusinessException("CANDIDATE_NOT_YOURS", "无权处理该匹配结果", HttpStatus.FORBIDDEN);
        if ("REJECTED".equals(decision.decision()) && (decision.reason() == null || decision.reason().isBlank())) {
            throw new BusinessException("REJECTION_REASON_REQUIRED", "拒绝匹配必须填写原因");
        }
        if (isNavigator && !"PENDING".equals(candidate.getNavigatorDecision())
                || isClimber && !"PENDING".equals(candidate.getClimberDecision())) {
            throw new BusinessException("DECISION_ALREADY_MADE", "你已经处理过该匹配结果");
        }
        if (isNavigator) {
            candidate.setNavigatorDecision(decision.decision());
            candidate.setNavigatorDecidedAt(LocalDateTime.now());
        } else {
            candidate.setClimberDecision(decision.decision());
            candidate.setClimberDecidedAt(LocalDateTime.now());
        }
        if ("REJECTED".equals(decision.decision())) {
            candidate.setStatus("REJECTED");
            candidate.setRejectionReason(decision.reason().trim());
            RejectedMatchPair rejected = new RejectedMatchPair();
            rejected.setPeriodId(requiredRound(candidate.getRoundId()).getPeriodId());
            rejected.setNavigatorUserId(navigator.getStudentUserId());
            rejected.setClimberUserId(climber.getStudentUserId());
            rejected.setRejectedBy(actor.id());
            rejected.setReason(decision.reason().trim());
            rejected.setActive(true);
            rejectedPairMapper.insert(rejected);
        } else if ("CONFIRMED".equals(candidate.getNavigatorDecision())
                && "CONFIRMED".equals(candidate.getClimberDecision())) {
            candidate.setStatus("CONFIRMED");
            createSupportPair(candidate, requiredRound(candidate.getRoundId()).getPeriodId());
        }
        candidateMapper.updateById(candidate);
        auditService.log(actor, "MATCHING_DECISION", "MATCH_CANDIDATE", candidateId, null,
                Map.of("decision", decision.decision()), decision.reason(), request);
        return response(candidate, true, actor.id());
    }

    private void createSupportPair(MatchCandidate candidate, Long periodId) {
        if (usesExtraSlot(candidate) && !extraSlotNavigators(periodId).contains(candidate.getNavigatorApplicationId())) {
            throw new BusinessException("MATCHING_EXTRA_SLOT_UNAVAILABLE", "该领航者没有可用的额外名额，请联系管理员");
        }
        SupportPair pair = new SupportPair();
        pair.setPeriodId(periodId);
        pair.setPairNo("PAIR-" + periodId + "-" + candidate.getId());
        pair.setNavigatorApplicationId(candidate.getNavigatorApplicationId());
        pair.setNavigatorSlot(usesExtraSlot(candidate) ? 2 : 1);
        pair.setClimberApplicationId(candidate.getClimberApplicationId());
        pair.setSourceCandidateId(candidate.getId());
        pair.setMatchKind(candidate.getMatchKind());
        pair.setStatus("ACTIVE");
        pair.setConfirmedAt(LocalDateTime.now());
        supportPairMapper.insert(pair);
    }

    private ScoredPair score(ActivityPeriod period, ParticipationApplication navigator,
                             ApplicationProfileSnapshot np, ParticipationApplication climber,
                             ApplicationProfileSnapshot cp) {
        List<String> matched = matchedSubjects(list(navigator.getSubjectsJson()), list(climber.getSubjectsJson()));
        double subject = matched.isEmpty() ? 0 : period.getSubjectWeight();
        double major = np.getMajor().equalsIgnoreCase(cp.getMajor()) ? period.getMajorWeight()
                : relatedMajor(np.getMajor(), cp.getMajor()) ? period.getMajorWeight() * .6 : 0;
        int gradeGap = cp.getGrade() - np.getGrade();
        double grade = period.getGradeWeight() * (gradeGap == 0 ? 1 : gradeGap == 1 ? .75 : gradeGap == 2 ? .5 : .25);
        double style = period.getLearningStyleWeight() * similarity(list(navigator.getLearningStylesJson()), list(climber.getLearningStylesJson()));
        double personality = period.getPersonalityWeight() * similarity(list(navigator.getPersonalitiesJson()), list(climber.getPersonalitiesJson()));
        double gpaGap = navigator.getCumulativeGpa().subtract(climber.getCumulativeGpa()).doubleValue();
        double gpa = period.getGpaGapWeight() * Math.min(gpaGap, 1.0);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("subject", detail(subject, period.getSubjectWeight(), "相似课程：" + String.join("、", matched)));
        detail.put("major", detail(major, period.getMajorWeight(), np.getMajor().equalsIgnoreCase(cp.getMajor()) ? "专业相同" : major > 0 ? "专业名称相关" : "专业不同"));
        detail.put("grade", detail(grade, period.getGradeWeight(), "领航者年级满足约束，相差 " + gradeGap + " 届"));
        detail.put("learningStyle", detail(style, period.getLearningStyleWeight(), "学习风格相似度评分"));
        detail.put("personality", detail(personality, period.getPersonalityWeight(), "性格特点相似度评分"));
        detail.put("gpaGap", detail(gpa, period.getGpaGapWeight(), "报名累计平均绩点差 " + BigDecimal.valueOf(gpaGap).setScale(2, RoundingMode.HALF_UP)));
        double total = subject + major + grade + style + personality + gpa;
        return new ScoredPair(navigator, climber, BigDecimal.valueOf(total).setScale(2, RoundingMode.HALF_UP), detail, matched);
    }

    private Map<String, Object> detail(double score, int max, String explanation) {
        return Map.of("score", BigDecimal.valueOf(score).setScale(2, RoundingMode.HALF_UP), "max", max, "explanation", explanation);
    }

    private double similarity(List<String> first, List<String> second) {
        Set<String> a = first.stream().map(this::normalizedText).collect(Collectors.toSet());
        Set<String> b = second.stream().map(this::normalizedText).collect(Collectors.toSet());
        Set<String> union = new HashSet<>(a); union.addAll(b);
        if (union.isEmpty()) return 0;
        Set<String> intersection = new HashSet<>(a); intersection.retainAll(b);
        return (double) intersection.size() / union.size();
    }

    private List<String> matchedSubjects(List<String> navigator, List<String> climber) {
        List<String> matches = new ArrayList<>();
        for (String need : climber) {
            String canonicalNeed = canonicalSubject(need);
            if (navigator.stream().map(this::canonicalSubject).anyMatch(can -> can.equals(canonicalNeed)
                    || can.contains(canonicalNeed) || canonicalNeed.contains(can))) matches.add(need);
        }
        return matches.stream().distinct().toList();
    }

    private String canonicalSubject(String input) {
        String normalized = normalizedText(input).replaceAll("[（(][上下ABab一二12][）)]$", "")
                .replaceAll("[上下ABab一二12]$", "");
        return SUBJECT_ALIASES.getOrDefault(normalized, normalized);
    }

    private String normalizedText(String input) {
        return input == null ? "" : input.toLowerCase(Locale.ROOT).replaceAll("[\\s·•（）()_-]", "");
    }

    private boolean relatedMajor(String a, String b) {
        String left = normalizedText(a), right = normalizedText(b);
        return left.length() >= 2 && right.length() >= 2 && left.substring(0, 2).equals(right.substring(0, 2));
    }

    private Set<Long> unavailableApplicationIds(Long periodId) {
        Set<Long> ids = new HashSet<>();
        supportPairMapper.selectList(Wrappers.<SupportPair>lambdaQuery().eq(SupportPair::getPeriodId, periodId)).forEach(pair -> {
            ids.add(pair.getNavigatorApplicationId()); ids.add(pair.getClimberApplicationId());
        });
        List<MatchRound> rounds = roundMapper.selectList(Wrappers.<MatchRound>lambdaQuery().eq(MatchRound::getPeriodId, periodId));
        if (!rounds.isEmpty()) candidateMapper.selectList(Wrappers.<MatchCandidate>lambdaQuery()
                .in(MatchCandidate::getRoundId, rounds.stream().map(MatchRound::getId).toList())
                .eq(MatchCandidate::getStatus, "PUBLISHED")).forEach(candidate -> {
            ids.add(candidate.getNavigatorApplicationId()); ids.add(candidate.getClimberApplicationId());
        });
        return ids;
    }

    private boolean isRejected(Long periodId, Long navigatorUserId, Long climberUserId) {
        return rejectedPairMapper.selectCount(Wrappers.<RejectedMatchPair>lambdaQuery()
                .eq(RejectedMatchPair::getPeriodId, periodId)
                .and(q -> q.eq(RejectedMatchPair::getNavigatorUserId, navigatorUserId)
                        .eq(RejectedMatchPair::getClimberUserId, climberUserId)
                        .or(other -> other.eq(RejectedMatchPair::getNavigatorUserId, climberUserId)
                                .eq(RejectedMatchPair::getClimberUserId, navigatorUserId)))
                .eq(RejectedMatchPair::getActive, true)) > 0;
    }

    private MatchCandidateResponse response(MatchCandidate candidate, boolean studentView, Long viewerId) {
        MatchRound round = requiredRound(candidate.getRoundId());
        ParticipationApplication navigator = applicationMapper.selectById(candidate.getNavigatorApplicationId());
        ParticipationApplication climber = applicationMapper.selectById(candidate.getClimberApplicationId());
        ApplicationProfileSnapshot np = snapshot(navigator.getId()), cp = snapshot(climber.getId());
        SupportPair pair = supportPairMapper.selectOne(Wrappers.<SupportPair>lambdaQuery()
                .eq(SupportPair::getSourceCandidateId, candidate.getId()).last("LIMIT 1"));
        return response(candidate, round, navigator, climber, np, cp, pair, studentView, viewerId);
    }

    private MatchCandidateResponse response(MatchCandidate candidate, MatchRound round, ParticipationApplication navigator,
                                            ParticipationApplication climber, ApplicationProfileSnapshot np,
                                            ApplicationProfileSnapshot cp, SupportPair pair, boolean studentView, Long viewerId) {
        boolean formal = pair != null && "ACTIVE".equals(pair.getStatus());
        boolean viewerIsNavigator = viewerId != null && navigator.getStudentUserId().equals(viewerId);
        String navigatorName = studentView && !formal && !viewerIsNavigator ? mask(np.getRealName()) : np.getRealName();
        String climberName = studentView && !formal && viewerIsNavigator ? mask(cp.getRealName()) : cp.getRealName();
        String partnerWechat = null, partnerPhone = null;
        if (studentView && formal) {
            ApplicationProfileSnapshot partner = viewerIsNavigator ? cp : np;
            partnerWechat = partner.getWechat(); partnerPhone = partner.getPhone();
        }
        boolean legacy = round.getPolicyRevision() == null;
        return new MatchCandidateResponse(candidate.getId(), candidate.getRoundId(), round.getPeriodId(), round.getRoundType(), candidate.getStatus(),
                !studentView && legacy ? candidate.getTotalScore() : null, !studentView && legacy ? map(candidate.getScoreDetail()) : null,
                list(candidate.getMatchedSubjects()),
                navigator.getId(), navigator.getStudentUserId(), navigatorName, np.getMajor(), np.getGrade(),
                climber.getId(), climber.getStudentUserId(), climberName,
                cp.getMajor(), cp.getGrade(), candidate.getNavigatorDecision(), candidate.getClimberDecision(),
                candidate.getRejectionReason(), partnerWechat, partnerPhone, pair == null ? null : pair.getId(),
                candidate.getMatchKind(), !studentView && !legacy ? map(candidate.getScoreDetail()) : null,
                studentView ? null : candidate.getManuallySelected(), studentView ? null : candidate.getNavigatorSlot());
    }

    private void requireRoundOperationStage(MatchRound round, ActivityPeriod period) {
        boolean normal = "NORMAL".equals(round.getRoundType()) && "MATCHING".equals(period.getStatus());
        boolean localRematch = "LOCAL_REMATCH".equals(round.getRoundType()) && "CONFIRMATION".equals(period.getStatus());
        if (!normal && !localRematch) {
            throw new BusinessException("MATCHING_ROUND_OPERATION_NOT_OPEN",
                    "普通匹配仅可在匹配阶段操作，局部重匹配仅可在双方确认阶段操作");
        }
    }

    private boolean studentCanSee(String periodStatus) {
        return List.of("CONFIRMATION", "TRACKING", "ACCEPTANCE", "ENDED", "ARCHIVED").contains(periodStatus);
    }

    private String mask(String name) {
        if (name == null || name.isBlank()) return "匿名同学";
        return name.substring(0, 1) + "同学";
    }

    private Map<String, Object> weights(ActivityPeriod p) {
        return Map.of("subject", p.getSubjectWeight(), "major", p.getMajorWeight(), "grade", p.getGradeWeight(),
                "learningStyle", p.getLearningStyleWeight(), "personality", p.getPersonalityWeight(), "gpaGap", p.getGpaGapWeight());
    }

    private ActivityPeriod requiredPeriod(Long id) {
        ActivityPeriod period = periodMapper.selectById(id);
        if (period == null) throw new BusinessException("PERIOD_NOT_FOUND", "期次不存在", HttpStatus.NOT_FOUND);
        return period;
    }

    private ActivityPeriod lockPeriod(Long id) {
        ActivityPeriod period = periodMapper.lockById(id);
        if (period == null) throw new BusinessException("PERIOD_NOT_FOUND", "期次不存在", HttpStatus.NOT_FOUND);
        return period;
    }

    private ActivityPeriod lockRoundPeriod(Long roundId) {
        MatchRound reference = roundMapper.selectOne(Wrappers.<MatchRound>lambdaQuery().select(MatchRound::getPeriodId)
                .eq(MatchRound::getId, roundId));
        if (reference == null) throw new BusinessException("MATCH_ROUND_NOT_FOUND", "匹配轮次不存在", HttpStatus.NOT_FOUND);
        return lockPeriod(reference.getPeriodId());
    }

    private ActivityPeriod lockCandidatePeriod(Long candidateId) {
        MatchCandidate reference = candidateMapper.selectOne(Wrappers.<MatchCandidate>lambdaQuery().select(MatchCandidate::getRoundId)
                .eq(MatchCandidate::getId, candidateId));
        if (reference == null) throw new BusinessException("MATCH_CANDIDATE_NOT_FOUND", "匹配候选不存在", HttpStatus.NOT_FOUND);
        return lockRoundPeriod(reference.getRoundId());
    }
    private MatchRound requiredRound(Long id) {
        MatchRound round = roundMapper.selectById(id);
        if (round == null) throw new BusinessException("MATCH_ROUND_NOT_FOUND", "匹配轮次不存在", HttpStatus.NOT_FOUND);
        return round;
    }
    private MatchCandidate requiredCandidate(Long id) {
        MatchCandidate candidate = candidateMapper.selectById(id);
        if (candidate == null) throw new BusinessException("MATCH_CANDIDATE_NOT_FOUND", "匹配候选不存在", HttpStatus.NOT_FOUND);
        return candidate;
    }
    private ApplicationProfileSnapshot snapshot(Long applicationId) {
        ApplicationProfileSnapshot snapshot = snapshotMapper.selectOne(Wrappers.<ApplicationProfileSnapshot>lambdaQuery()
                .eq(ApplicationProfileSnapshot::getApplicationId, applicationId));
        if (snapshot == null) throw new BusinessException("APPLICATION_SNAPSHOT_MISSING", "报名资料快照不存在");
        return snapshot;
    }
    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new IllegalStateException(exception); }
    }
    private List<String> list(String json) {
        try { return objectMapper.readValue(json, new TypeReference<>() {}); }
        catch (Exception exception) { return List.of(); }
    }
    private Map<String, Object> map(String json) {
        try { return objectMapper.readValue(json, new TypeReference<>() {}); }
        catch (Exception exception) { return Map.of(); }
    }

    private record ScoredPair(ParticipationApplication navigator, ParticipationApplication climber,
                              BigDecimal totalScore, Map<String, Object> detail, List<String> matchedSubjects) {}
}
