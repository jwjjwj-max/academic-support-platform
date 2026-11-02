package cn.edu.academic.support.matching.service;

import cn.edu.academic.support.application.mapper.ApplicationProfileSnapshotMapper;
import cn.edu.academic.support.application.mapper.ParticipationApplicationMapper;
import cn.edu.academic.support.application.model.ApplicationProfileSnapshot;
import cn.edu.academic.support.application.model.ParticipationApplication;
import cn.edu.academic.support.audit.service.AuditService;
import cn.edu.academic.support.common.exception.BusinessException;
import cn.edu.academic.support.matching.dto.MatchCandidateResponse;
import cn.edu.academic.support.matching.dto.MatchDecisionRequest;
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
        ActivityPeriod period = requiredPeriod(periodId);
        if (!"MATCHING".equals(period.getStatus())) {
            throw new BusinessException("PERIOD_NOT_MATCHING", "仅匹配阶段可以运行匹配");
        }
        return generateRound(actor, period, "NORMAL", Set.of(), request);
    }

    @Transactional
    public List<MatchCandidateResponse> rematch(AuthenticatedUser actor, Long periodId,
                                                HttpServletRequest request) {
        ActivityPeriod period = requiredPeriod(periodId);
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
        if (draft != null) {
            throw new BusinessException("LOCAL_REMATCH_DRAFT_EXISTS", "已有待调整的局部重匹配方案，请先处理并发布");
        }
        MatchRound latestLocalRound = roundMapper.selectOne(Wrappers.<MatchRound>lambdaQuery()
                .eq(MatchRound::getPeriodId, periodId)
                .eq(MatchRound::getRoundType, "LOCAL_REMATCH")
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
        Integer maxRound = roundMapper.selectObjs(Wrappers.<MatchRound>query()
                        .select("MAX(round_no)").eq("period_id", periodId)).stream()
                .filter(Objects::nonNull).map(value -> ((Number) value).intValue()).findFirst().orElse(0);
        round.setPeriodId(periodId);
        round.setRoundNo(maxRound + 1);
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

    public List<MatchCandidateResponse> listAdmin(Long periodId, Long roundId) {
        Long targetRound = roundId;
        if (targetRound == null) {
            MatchRound latest = roundMapper.selectOne(Wrappers.<MatchRound>lambdaQuery()
                    .eq(MatchRound::getPeriodId, periodId).orderByDesc(MatchRound::getRoundNo).last("LIMIT 1"));
            if (latest == null) return List.of();
            targetRound = latest.getId();
        }
        return candidateMapper.selectList(Wrappers.<MatchCandidate>lambdaQuery()
                        .eq(MatchCandidate::getRoundId, targetRound)
                        .orderByDesc(MatchCandidate::getAdminConfirmed)
                        .orderByDesc(MatchCandidate::getTotalScore))
                .stream().map(candidate -> response(candidate, false, null)).toList();
    }

    @Transactional
    public MatchCandidateResponse select(AuthenticatedUser actor, Long candidateId, HttpServletRequest request) {
        MatchCandidate selected = requiredCandidate(candidateId);
        MatchRound round = requiredRound(selected.getRoundId());
        ActivityPeriod period = requiredPeriod(round.getPeriodId());
        requireRoundOperationStage(round, period);
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

    @Transactional
    public List<MatchCandidateResponse> publish(AuthenticatedUser actor, Long roundId, HttpServletRequest request) {
        MatchRound round = requiredRound(roundId);
        ActivityPeriod period = requiredPeriod(round.getPeriodId());
        requireRoundOperationStage(round, period);
        if (!LocalDateTime.now().isBefore(period.getConfirmationDeadline())) {
            throw new BusinessException("CONFIRMATION_DEADLINE_REACHED", "匹配确认截止时间已到，请调整期次时间后再发布");
        }
        if (!"DRAFT".equals(round.getStatus())) throw new BusinessException("ROUND_ALREADY_PUBLISHED", "该轮匹配已经发布");
        List<MatchCandidate> selected = candidateMapper.selectList(Wrappers.<MatchCandidate>lambdaQuery()
                .eq(MatchCandidate::getRoundId, roundId).eq(MatchCandidate::getAdminConfirmed, true));
        if (selected.isEmpty()) throw new BusinessException("NO_SELECTED_CANDIDATE", "请先确认至少一个候选组合");
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

    @Transactional
    public MatchCandidateResponse decide(AuthenticatedUser actor, Long candidateId, MatchDecisionRequest decision,
                                         HttpServletRequest request) {
        MatchCandidate candidate = requiredCandidate(candidateId);
        ActivityPeriod period = requiredPeriod(requiredRound(candidate.getRoundId()).getPeriodId());
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
        SupportPair pair = new SupportPair();
        pair.setPeriodId(periodId);
        pair.setPairNo("PAIR-" + periodId + "-" + candidate.getId());
        pair.setNavigatorApplicationId(candidate.getNavigatorApplicationId());
        pair.setClimberApplicationId(candidate.getClimberApplicationId());
        pair.setSourceCandidateId(candidate.getId());
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
        supportPairMapper.selectList(Wrappers.<SupportPair>lambdaQuery().eq(SupportPair::getPeriodId, periodId)
                .eq(SupportPair::getStatus, "ACTIVE")).forEach(pair -> {
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
                .eq(RejectedMatchPair::getNavigatorUserId, navigatorUserId)
                .eq(RejectedMatchPair::getClimberUserId, climberUserId)
                .eq(RejectedMatchPair::getActive, true)) > 0;
    }

    private MatchCandidateResponse response(MatchCandidate candidate, boolean studentView, Long viewerId) {
        MatchRound round = requiredRound(candidate.getRoundId());
        ParticipationApplication navigator = applicationMapper.selectById(candidate.getNavigatorApplicationId());
        ParticipationApplication climber = applicationMapper.selectById(candidate.getClimberApplicationId());
        ApplicationProfileSnapshot np = snapshot(navigator.getId()), cp = snapshot(climber.getId());
        SupportPair pair = supportPairMapper.selectOne(Wrappers.<SupportPair>lambdaQuery()
                .eq(SupportPair::getSourceCandidateId, candidate.getId()).last("LIMIT 1"));
        boolean formal = pair != null && "ACTIVE".equals(pair.getStatus());
        boolean viewerIsNavigator = viewerId != null && navigator.getStudentUserId().equals(viewerId);
        String navigatorName = studentView && !formal && !viewerIsNavigator ? mask(np.getRealName()) : np.getRealName();
        String climberName = studentView && !formal && viewerIsNavigator ? mask(cp.getRealName()) : cp.getRealName();
        String partnerWechat = null, partnerPhone = null;
        if (studentView && formal) {
            ApplicationProfileSnapshot partner = viewerIsNavigator ? cp : np;
            partnerWechat = partner.getWechat(); partnerPhone = partner.getPhone();
        }
        return new MatchCandidateResponse(candidate.getId(), candidate.getRoundId(), round.getPeriodId(), round.getRoundType(), candidate.getStatus(),
                studentView ? null : candidate.getTotalScore(), studentView ? null : map(candidate.getScoreDetail()), list(candidate.getMatchedSubjects()),
                navigator.getId(), navigator.getStudentUserId(), navigatorName, np.getMajor(), np.getGrade(),
                climber.getId(), climber.getStudentUserId(), climberName,
                cp.getMajor(), cp.getGrade(), candidate.getNavigatorDecision(), candidate.getClimberDecision(),
                candidate.getRejectionReason(), partnerWechat, partnerPhone, pair == null ? null : pair.getId());
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
