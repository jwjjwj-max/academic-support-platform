package cn.edu.academic.support.period.service;

import cn.edu.academic.support.audit.service.AuditService;
import cn.edu.academic.support.application.mapper.ParticipationApplicationMapper;
import cn.edu.academic.support.application.model.ParticipationApplication;
import cn.edu.academic.support.common.exception.BusinessException;
import cn.edu.academic.support.matching.mapper.MatchCandidateMapper;
import cn.edu.academic.support.matching.mapper.MatchRoundMapper;
import cn.edu.academic.support.matching.mapper.SupportPairMapper;
import cn.edu.academic.support.matching.model.MatchCandidate;
import cn.edu.academic.support.matching.model.MatchRound;
import cn.edu.academic.support.matching.model.SupportPair;
import cn.edu.academic.support.period.dto.PeriodRequest;
import cn.edu.academic.support.period.dto.PeriodStatusRequest;
import cn.edu.academic.support.period.dto.PeriodTransitionPreview;
import cn.edu.academic.support.period.dto.AcceptanceWindowExtensionRequest;
import cn.edu.academic.support.period.mapper.ActivityPeriodMapper;
import cn.edu.academic.support.period.mapper.ActivityPeriodStatusHistoryMapper;
import cn.edu.academic.support.period.model.ActivityPeriod;
import cn.edu.academic.support.period.model.ActivityPeriodStatusHistory;
import cn.edu.academic.support.security.AuthenticatedUser;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PeriodService {
    private static final List<String> STATUS_ORDER = List.of("ANNOUNCED", "REGISTRATION", "REVIEW", "MATCHING",
            "CONFIRMATION", "TRACKING", "ACCEPTANCE", "ENDED", "ARCHIVED");
    private final ActivityPeriodMapper periodMapper;
    private final ActivityPeriodStatusHistoryMapper statusHistoryMapper;
    private final ParticipationApplicationMapper applicationMapper;
    private final MatchRoundMapper roundMapper;
    private final MatchCandidateMapper candidateMapper;
    private final SupportPairMapper supportPairMapper;
    private final AuditService auditService;

    public List<ActivityPeriod> list() {
        return periodMapper.selectList(Wrappers.<ActivityPeriod>lambdaQuery()
                .orderByDesc(ActivityPeriod::getRegistrationStart));
    }

    @Transactional
    public ActivityPeriod create(AuthenticatedUser actor, PeriodRequest request, HttpServletRequest servletRequest) {
        ensureNameUnique(request.name().trim(), request.shortName().trim(), null);
        ActivityPeriod period = new ActivityPeriod();
        apply(period, request);
        period.setCreatedBy(actor.id());
        periodMapper.insert(period);
        auditService.log(actor, "PERIOD_CREATE", "PERIOD", period.getId(), null, period, null, servletRequest);
        return period;
    }

    @Transactional
    public ActivityPeriod update(AuthenticatedUser actor, Long periodId, PeriodRequest request,
                                 HttpServletRequest servletRequest) {
        ActivityPeriod period = requiredForUpdate(periodId);
        if ("ARCHIVED".equals(period.getStatus())) throw new BusinessException("PERIOD_ARCHIVED", "已归档期次不可编辑");
        if (!period.getStatus().equals(request.status())) {
            throw new BusinessException("PERIOD_STATUS_REQUIRES_REASON", "期次阶段请通过阶段变更操作修改并填写原因");
        }
        if ("ACCEPTANCE".equals(period.getStatus()) && !period.getAcceptanceEnd().equals(request.acceptanceEnd())) {
            throw new BusinessException("ACCEPTANCE_END_REQUIRES_REASON", "验收阶段请使用延长提交窗口操作并填写原因");
        }
        ensureNameUnique(request.name().trim(), request.shortName().trim(), periodId);
        ActivityPeriod before = copy(period);
        apply(period, request);
        periodMapper.updateById(period);
        auditService.log(actor, "PERIOD_UPDATE", "PERIOD", periodId, before, period, null, servletRequest);
        return period;
    }

    @Transactional
    public ActivityPeriod extendAcceptanceWindow(AuthenticatedUser actor, Long periodId,
                                                 AcceptanceWindowExtensionRequest request,
                                                 HttpServletRequest servletRequest) {
        ActivityPeriod period = requiredForUpdate(periodId);
        if (!"ACCEPTANCE".equals(period.getStatus()))
            throw new BusinessException("ACCEPTANCE_EXTENSION_NOT_OPEN", "仅验收阶段可以延长学生材料提交窗口");
        if (!request.newAcceptanceEnd().isAfter(period.getAcceptanceEnd()))
            throw new BusinessException("ACCEPTANCE_EXTENSION_INVALID", "新的验收提交截止时间必须晚于当前截止时间");
        if (!request.newAcceptanceEnd().isAfter(LocalDateTime.now()))
            throw new BusinessException("ACCEPTANCE_EXTENSION_INVALID", "新的验收提交截止时间必须晚于当前时间");

        ActivityPeriod before = copy(period);
        period.setAcceptanceEnd(request.newAcceptanceEnd());
        periodMapper.updateById(period);
        auditService.log(actor, "ACCEPTANCE_WINDOW_EXTEND", "PERIOD", periodId, before, period,
                request.reason().trim(), servletRequest);
        return period;
    }

    @Transactional
    public ActivityPeriod changeStatus(AuthenticatedUser actor, Long periodId, PeriodStatusRequest request,
                                       HttpServletRequest servletRequest) {
        ActivityPeriod period = requiredForUpdate(periodId);
        if (period.getStatus().equals(request.status())) return period;
        int oldIndex = STATUS_ORDER.indexOf(period.getStatus());
        int newIndex = STATUS_ORDER.indexOf(request.status());
        if (newIndex != oldIndex + 1) {
            throw new BusinessException("INVALID_PERIOD_TRANSITION", "期次状态必须按既定顺序逐步变更");
        }
        PeriodTransitionPreview preview = transitionPreview(period, request.status());
        if (!preview.allowed()) {
            throw new BusinessException("PERIOD_TRANSITION_BLOCKED", String.join("；", preview.blockers()));
        }
        if (preview.requiresConfirmation() && !request.isRiskConfirmed()) {
            throw new BusinessException("PERIOD_TRANSITION_CONFIRMATION_REQUIRED", String.join("；", preview.warnings()));
        }
        String oldStatus = period.getStatus();
        period.setStatus(request.status());
        if ("ARCHIVED".equals(request.status())) period.setArchivedAt(LocalDateTime.now());
        periodMapper.updateById(period);
        ActivityPeriodStatusHistory history = new ActivityPeriodStatusHistory();
        history.setPeriodId(periodId);
        history.setOldStatus(oldStatus);
        history.setNewStatus(request.status());
        history.setReason(request.reason().trim());
        history.setOperatorId(actor.id());
        statusHistoryMapper.insert(history);
        auditService.log(actor, "PERIOD_STATUS_CHANGE", "PERIOD", periodId,
                java.util.Map.of("status", oldStatus), java.util.Map.of("status", request.status()),
                request.reason().trim(), servletRequest);
        return period;
    }

    public PeriodTransitionPreview transitionPreview(Long periodId) {
        ActivityPeriod period = required(periodId);
        int currentIndex = STATUS_ORDER.indexOf(period.getStatus());
        String nextStatus = currentIndex >= 0 && currentIndex + 1 < STATUS_ORDER.size()
                ? STATUS_ORDER.get(currentIndex + 1) : null;
        return transitionPreview(period, nextStatus);
    }

    private PeriodTransitionPreview transitionPreview(ActivityPeriod period, String nextStatus) {
        List<String> warnings = new ArrayList<>();
        List<String> blockers = new ArrayList<>();
        Map<String, Long> impact = new LinkedHashMap<>();
        LocalDateTime now = LocalDateTime.now();
        long totalApplications = applicationCount(period.getId(), null, null);
        impact.put("totalApplications", totalApplications);

        if (nextStatus == null) {
            blockers.add("当前期次已经是最终阶段");
        } else if ("ANNOUNCED".equals(period.getStatus()) && "REGISTRATION".equals(nextStatus)) {
            if (now.isBefore(period.getRegistrationStart())) {
                warnings.add("尚未到报名开始时间；推进后期次会显示为报名阶段，但学生仍需等到报名开始时间才能提交");
            }
        } else if ("REGISTRATION".equals(period.getStatus()) && "REVIEW".equals(nextStatus)) {
            impact.put("submittedApplications", applicationCount(period.getId(), "SUBMITTED", null));
            if (now.isBefore(period.getRegistrationEnd())) {
                warnings.add("尚未到报名截止时间，推进后将提前关闭学生首次报名，且阶段不能回退");
            }
        } else if ("REVIEW".equals(period.getStatus()) && "MATCHING".equals(nextStatus)) {
            long pending = applicationMapper.selectCount(Wrappers.<ParticipationApplication>lambdaQuery()
                    .eq(ParticipationApplication::getPeriodId, period.getId())
                    .in(ParticipationApplication::getApplicationStatus, List.of("SUBMITTED", "UNDER_REVIEW")));
            long undecidedApproved = applicationCount(period.getId(), "APPROVED", "UNDECIDED");
            impact.put("pendingApplications", pending);
            impact.put("undecidedApprovedApplications", undecidedApproved);
            impact.put("matchingPoolApplications", applicationCount(period.getId(), "APPROVED", "MATCHING_POOL"));
            if (pending > 0) blockers.add("仍有 " + pending + " 条报名待审核或处理中");
            if (undecidedApproved > 0) blockers.add("仍有 " + undecidedApproved + " 条已通过报名未设置入选状态");
        } else if ("MATCHING".equals(period.getStatus()) && "CONFIRMATION".equals(nextStatus)) {
            List<MatchRound> rounds = roundMapper.selectList(Wrappers.<MatchRound>lambdaQuery()
                    .eq(MatchRound::getPeriodId, period.getId()));
            long published = rounds.isEmpty() ? 0 : candidateMapper.selectCount(Wrappers.<MatchCandidate>lambdaQuery()
                    .in(MatchCandidate::getRoundId, rounds.stream().map(MatchRound::getId).toList())
                    .eq(MatchCandidate::getStatus, "PUBLISHED"));
            impact.put("publishedMatches", published);
            if (published == 0) blockers.add("尚未确认并发布任何匹配结果");
            if (!now.isBefore(period.getConfirmationDeadline())) {
                warnings.add("匹配确认截止时间已到，请确认线下处理安排后再推进");
            }
        } else if ("CONFIRMATION".equals(period.getStatus()) && "TRACKING".equals(nextStatus)) {
            long pending = pendingConfirmationCount(period.getId());
            impact.put("pendingConfirmations", pending);
            impact.put("activeSupportPairs", supportPairMapper.selectCount(Wrappers.<SupportPair>lambdaQuery()
                    .eq(SupportPair::getPeriodId, period.getId()).eq(SupportPair::getStatus, "ACTIVE")));
            if (pending > 0) warnings.add("仍有 " + pending + " 组匹配未完成双方确认，继续推进需要管理员承担线下处置责任");
            if (now.isBefore(period.getTrackingStart())) warnings.add("尚未到帮扶开展开始时间");
        } else if ("TRACKING".equals(period.getStatus()) && "ACCEPTANCE".equals(nextStatus)) {
            if (now.isBefore(period.getAcceptanceStart())) warnings.add("尚未到验收开始时间，推进后将提前开放验收流程");
        } else if ("ACCEPTANCE".equals(period.getStatus()) && "ENDED".equals(nextStatus)) {
            long unfinished = supportPairMapper.selectCount(Wrappers.<SupportPair>lambdaQuery()
                    .eq(SupportPair::getPeriodId, period.getId()).eq(SupportPair::getStatus, "ACTIVE"));
            impact.put("unfinishedSupportPairs", unfinished);
            if (unfinished > 0) blockers.add("仍有 " + unfinished + " 个帮扶对未完成验收或终止");
        }

        return new PeriodTransitionPreview(period.getId(), period.getStatus(), nextStatus,
                blockers.isEmpty(), !warnings.isEmpty(), List.copyOf(warnings), List.copyOf(blockers), Map.copyOf(impact));
    }

    private long applicationCount(Long periodId, String applicationStatus, String selectionStatus) {
        return applicationMapper.selectCount(Wrappers.<ParticipationApplication>lambdaQuery()
                .eq(ParticipationApplication::getPeriodId, periodId)
                .eq(applicationStatus != null, ParticipationApplication::getApplicationStatus, applicationStatus)
                .eq(selectionStatus != null, ParticipationApplication::getSelectionStatus, selectionStatus));
    }

    private long pendingConfirmationCount(Long periodId) {
        List<MatchRound> rounds = roundMapper.selectList(Wrappers.<MatchRound>lambdaQuery()
                .eq(MatchRound::getPeriodId, periodId));
        if (rounds.isEmpty()) return 0;
        return candidateMapper.selectCount(Wrappers.<MatchCandidate>lambdaQuery()
                .in(MatchCandidate::getRoundId, rounds.stream().map(MatchRound::getId).toList())
                .eq(MatchCandidate::getStatus, "PUBLISHED")
                .and(query -> query.eq(MatchCandidate::getNavigatorDecision, "PENDING")
                        .or().eq(MatchCandidate::getClimberDecision, "PENDING")));
    }

    private ActivityPeriod requiredForUpdate(Long id) {
        ActivityPeriod period = periodMapper.lockById(id);
        if (period == null) throw new BusinessException("PERIOD_NOT_FOUND", "期次不存在", HttpStatus.NOT_FOUND);
        return period;
    }

    private ActivityPeriod required(Long id) {
        ActivityPeriod period = periodMapper.selectById(id);
        if (period == null) throw new BusinessException("PERIOD_NOT_FOUND", "期次不存在", HttpStatus.NOT_FOUND);
        return period;
    }

    private void ensureNameUnique(String name, String shortName, Long excludeId) {
        Long count = periodMapper.selectCount(Wrappers.<ActivityPeriod>lambdaQuery()
                .and(query -> query.eq(ActivityPeriod::getName, name).or().eq(ActivityPeriod::getShortName, shortName))
                .ne(excludeId != null, ActivityPeriod::getId, excludeId));
        if (count > 0) throw new BusinessException("PERIOD_NAME_EXISTS", "期次名称或简称已存在");
    }

    private void apply(ActivityPeriod period, PeriodRequest r) {
        period.setName(r.name().trim());
        period.setShortName(r.shortName().trim());
        period.setStatus(r.status());
        period.setRegistrationStart(r.registrationStart());
        period.setRegistrationEnd(r.registrationEnd());
        period.setConfirmationDeadline(r.confirmationDeadline());
        period.setTrackingStart(r.trackingStart());
        period.setTrackingEnd(r.trackingEnd());
        period.setAcceptanceStart(r.acceptanceStart());
        period.setAcceptanceEnd(r.acceptanceEnd());
        period.setPhotoMin(r.photoMin());
        period.setPhotoMax(r.photoMax());
        period.setSubjectWeight(r.subjectWeight());
        period.setMajorWeight(r.majorWeight());
        period.setGradeWeight(r.gradeWeight());
        period.setLearningStyleWeight(r.learningStyleWeight());
        period.setPersonalityWeight(r.personalityWeight());
        period.setGpaGapWeight(r.gpaGapWeight());
    }

    private ActivityPeriod copy(ActivityPeriod source) {
        ActivityPeriod target = new ActivityPeriod();
        org.springframework.beans.BeanUtils.copyProperties(source, target);
        return target;
    }
}
