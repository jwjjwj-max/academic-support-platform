package cn.edu.academic.support.application.service;

import cn.edu.academic.support.application.dto.ApplicationResponse;
import cn.edu.academic.support.application.dto.ApplicationReviewRequest;
import cn.edu.academic.support.application.dto.ApplicationSelectionRequest;
import cn.edu.academic.support.application.dto.ApplicationSubmitRequest;
import cn.edu.academic.support.application.mapper.ApplicationProfileSnapshotMapper;
import cn.edu.academic.support.application.mapper.ApplicationReviewHistoryMapper;
import cn.edu.academic.support.application.mapper.ParticipationApplicationMapper;
import cn.edu.academic.support.application.model.ApplicationProfileSnapshot;
import cn.edu.academic.support.application.model.ApplicationReviewHistory;
import cn.edu.academic.support.application.model.ParticipationApplication;
import cn.edu.academic.support.audit.service.AuditService;
import cn.edu.academic.support.common.exception.BusinessException;
import cn.edu.academic.support.period.mapper.ActivityPeriodMapper;
import cn.edu.academic.support.period.model.ActivityPeriod;
import cn.edu.academic.support.security.AuthenticatedUser;
import cn.edu.academic.support.user.mapper.StudentProfileMapper;
import cn.edu.academic.support.user.model.StudentProfile;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ApplicationService {
    private final ParticipationApplicationMapper applicationMapper;
    private final ApplicationProfileSnapshotMapper snapshotMapper;
    private final ApplicationReviewHistoryMapper historyMapper;
    private final StudentProfileMapper profileMapper;
    private final ActivityPeriodMapper periodMapper;
    private final ObjectMapper objectMapper;
    private final AuditService auditService;

    public List<ApplicationResponse> mine(Long userId) {
        return applicationMapper.selectList(Wrappers.<ParticipationApplication>lambdaQuery()
                        .eq(ParticipationApplication::getStudentUserId, userId)
                        .orderByDesc(ParticipationApplication::getSubmittedAt))
                .stream().map(this::response).toList();
    }

    public List<ApplicationResponse> listForAdmin(Long periodId, String status) {
        return applicationMapper.selectList(Wrappers.<ParticipationApplication>lambdaQuery()
                        .eq(periodId != null, ParticipationApplication::getPeriodId, periodId)
                        .eq(status != null && !status.isBlank(), ParticipationApplication::getApplicationStatus, status)
                        .orderByAsc(ParticipationApplication::getSubmittedAt).orderByAsc(ParticipationApplication::getId))
                .stream().map(this::response).toList();
    }

    @Transactional
    public ApplicationResponse submit(AuthenticatedUser actor, ApplicationSubmitRequest request,
                                      HttpServletRequest servletRequest) {
        StudentProfile profile = requiredApprovedProfile(actor.id());
        ActivityPeriod period = requiredPeriod(request.periodId());
        ParticipationApplication existing = applicationMapper.selectOne(Wrappers.<ParticipationApplication>lambdaQuery()
                .eq(ParticipationApplication::getPeriodId, request.periodId())
                .eq(ParticipationApplication::getStudentUserId, actor.id()));
        boolean resubmit = existing != null;
        if (resubmit && !"REJECTED".equals(existing.getApplicationStatus())) {
            throw new BusinessException("APPLICATION_EXISTS", "本期报名已提交，学生不能自行退出或重复报名");
        }
        if (!resubmit && !"REGISTRATION".equals(period.getStatus())) {
            throw new BusinessException("REGISTRATION_NOT_OPEN", "当前期次不在报名阶段");
        }
        LocalDateTime now = LocalDateTime.now();
        if (!resubmit && now.isBefore(period.getRegistrationStart())) {
            throw new BusinessException("REGISTRATION_NOT_STARTED", "报名尚未开始");
        }
        if (!resubmit && !now.isBefore(period.getRegistrationEnd())) {
            throw new BusinessException("REGISTRATION_ENDED", "报名已截止，等待管理员开始审核");
        }
        if (resubmit && !"REVIEW".equals(period.getStatus())) {
            throw new BusinessException("RESUBMISSION_NOT_OPEN", "仅报名审核阶段允许被驳回学生修改重提");
        }

        ParticipationApplication application = resubmit ? existing : new ParticipationApplication();
        application.setPeriodId(request.periodId());
        application.setStudentUserId(actor.id());
        application.setParticipantRole(request.participantRole());
        application.setCumulativeGpa(request.cumulativeGpa());
        application.setSubjectsJson(json(normalize(request.subjects())));
        application.setStatementText(request.statementText().trim());
        application.setLearningStylesJson(json(normalize(request.learningStyles())));
        application.setPersonalitiesJson(json(normalize(request.personalities())));
        application.setApplicationStatus("SUBMITTED");
        application.setSelectionStatus("UNDECIDED");
        application.setRejectionReason(null);
        application.setSubmittedAt(LocalDateTime.now());
        application.setReviewedBy(null);
        application.setReviewedAt(null);
        if (resubmit) applicationMapper.updateById(application); else applicationMapper.insert(application);

        if (!resubmit) createSnapshot(application, profile);
        addHistory(application.getId(), resubmit ? "RESUBMIT" : "SUBMIT", null, actor.id());
        auditService.log(actor, resubmit ? "APPLICATION_RESUBMIT" : "APPLICATION_SUBMIT", "APPLICATION",
                application.getId(), resubmit ? existing : null, response(application), null, servletRequest);
        return response(application);
    }

    @Transactional
    public ApplicationResponse review(AuthenticatedUser actor, Long id, ApplicationReviewRequest request,
                                      HttpServletRequest servletRequest) {
        ParticipationApplication application = required(id);
        ActivityPeriod period = requiredPeriod(application.getPeriodId());
        if (!"REVIEW".equals(period.getStatus())) {
            throw new BusinessException("APPLICATION_REVIEW_NOT_OPEN", "仅报名审核阶段可以审核报名");
        }
        if (!List.of("SUBMITTED", "UNDER_REVIEW").contains(application.getApplicationStatus())) {
            throw new BusinessException("APPLICATION_NOT_REVIEWABLE", "当前报名状态不能审核");
        }
        if ("REJECTED".equals(request.decision()) && (request.reason() == null || request.reason().isBlank())) {
            throw new BusinessException("REJECTION_REASON_REQUIRED", "驳回报名必须填写原因");
        }
        ApplicationResponse before = response(application);
        application.setApplicationStatus(request.decision());
        application.setRejectionReason("REJECTED".equals(request.decision()) ? request.reason().trim() : null);
        application.setReviewedBy(actor.id());
        application.setReviewedAt(LocalDateTime.now());
        applicationMapper.updateById(application);
        addHistory(id, request.decision().equals("APPROVED") ? "APPROVE" : "REJECT",
                application.getRejectionReason(), actor.id());
        auditService.log(actor, "APPLICATION_REVIEW", "APPLICATION", id, before, response(application),
                application.getRejectionReason(), servletRequest);
        return response(application);
    }

    @Transactional
    public ApplicationResponse select(AuthenticatedUser actor, Long id, ApplicationSelectionRequest request,
                                      HttpServletRequest servletRequest) {
        ParticipationApplication application = required(id);
        ActivityPeriod period = requiredPeriod(application.getPeriodId());
        if (!"REVIEW".equals(period.getStatus())) {
            throw new BusinessException("APPLICATION_SELECTION_NOT_OPEN", "仅报名审核阶段可以设置入选状态");
        }
        if (!"APPROVED".equals(application.getApplicationStatus())) {
            throw new BusinessException("APPLICATION_NOT_APPROVED", "只有审核通过的报名可以设置入选状态");
        }
        String before = application.getSelectionStatus();
        application.setSelectionStatus(request.selectionStatus());
        applicationMapper.updateById(application);
        addHistory(id, request.selectionStatus(), null, actor.id());
        auditService.log(actor, "APPLICATION_SELECTION", "APPLICATION", id,
                java.util.Map.of("selectionStatus", before),
                java.util.Map.of("selectionStatus", request.selectionStatus()), null, servletRequest);
        return response(application);
    }

    private void createSnapshot(ParticipationApplication application, StudentProfile profile) {
        ApplicationProfileSnapshot snapshot = new ApplicationProfileSnapshot();
        snapshot.setApplicationId(application.getId());
        snapshot.setStudentUserId(profile.getUserId());
        snapshot.setProfileVersion(profile.getProfileVersion());
        snapshot.setStudentNo(profile.getStudentNo());
        snapshot.setRealName(profile.getRealName());
        snapshot.setCollege(profile.getCollege());
        snapshot.setMajor(profile.getMajor());
        snapshot.setGrade(profile.getGrade());
        snapshot.setWechat(profile.getWechat());
        snapshot.setPhone(profile.getPhone());
        snapshotMapper.insert(snapshot);
    }

    private void addHistory(Long id, String action, String reason, Long operatorId) {
        ApplicationReviewHistory history = new ApplicationReviewHistory();
        history.setApplicationId(id);
        history.setAction(action);
        history.setReason(reason);
        history.setOperatorId(operatorId);
        historyMapper.insert(history);
    }

    private StudentProfile requiredApprovedProfile(Long userId) {
        StudentProfile profile = profileMapper.selectById(userId);
        if (profile == null || !"APPROVED".equals(profile.getReviewStatus())) {
            throw new BusinessException("PROFILE_NOT_APPROVED", "个人资料审核通过后才能报名", HttpStatus.FORBIDDEN);
        }
        return profile;
    }

    private ActivityPeriod requiredPeriod(Long id) {
        ActivityPeriod period = periodMapper.selectById(id);
        if (period == null) throw new BusinessException("PERIOD_NOT_FOUND", "期次不存在", HttpStatus.NOT_FOUND);
        return period;
    }

    private ParticipationApplication required(Long id) {
        ParticipationApplication application = applicationMapper.selectById(id);
        if (application == null) throw new BusinessException("APPLICATION_NOT_FOUND", "报名记录不存在", HttpStatus.NOT_FOUND);
        return application;
    }

    private ApplicationResponse response(ParticipationApplication application) {
        ActivityPeriod period = periodMapper.selectById(application.getPeriodId());
        ApplicationProfileSnapshot snapshot = snapshotMapper.selectOne(Wrappers.<ApplicationProfileSnapshot>lambdaQuery()
                .eq(ApplicationProfileSnapshot::getApplicationId, application.getId()));
        return new ApplicationResponse(application.getId(), application.getPeriodId(), period == null ? "—" : period.getName(),
                application.getStudentUserId(), snapshot == null ? "—" : snapshot.getStudentNo(),
                snapshot == null ? "—" : snapshot.getRealName(), snapshot == null ? "—" : snapshot.getMajor(),
                snapshot == null ? null : snapshot.getGrade(), application.getParticipantRole(),
                application.getCumulativeGpa(), list(application.getSubjectsJson()), application.getStatementText(),
                list(application.getLearningStylesJson()), list(application.getPersonalitiesJson()),
                application.getApplicationStatus(), application.getSelectionStatus(), application.getRejectionReason(),
                application.getSubmittedAt(), application.getReviewedAt());
    }

    private List<String> normalize(List<String> input) {
        return new LinkedHashSet<>(input.stream().map(String::trim).filter(v -> !v.isBlank()).toList()).stream().toList();
    }

    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new IllegalStateException(exception); }
    }

    private List<String> list(String json) {
        try { return objectMapper.readValue(json, new TypeReference<>() {}); }
        catch (JsonProcessingException exception) { return List.of(); }
    }
}
