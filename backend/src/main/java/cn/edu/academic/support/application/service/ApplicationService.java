package cn.edu.academic.support.application.service;

import cn.edu.academic.support.application.dto.ApplicationCorrectionRequest;
import cn.edu.academic.support.application.dto.ApplicationResponse;
import cn.edu.academic.support.application.dto.ApplicationReviewRequest;
import cn.edu.academic.support.application.dto.ApplicationRoleCorrectionRequest;
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
import cn.edu.academic.support.matching.service.MatchingWorkspace;
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
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ApplicationService {
    private static final List<String> REVIEWABLE = List.of("SUBMITTED", "UNDER_REVIEW");
    private static final List<String> CORRECTABLE = List.of("SUBMITTED", "UNDER_REVIEW", "APPROVED", "REJECTED");
    private static final Pattern SUBJECT_SEPARATOR = Pattern.compile("[、,，;；\\r\\n]");
    private static final Pattern EDGE_WHITESPACE = Pattern.compile("^[\\s\\uFEFF]+|[\\s\\uFEFF]+$", Pattern.UNICODE_CHARACTER_CLASS);
    private final ParticipationApplicationMapper applicationMapper;
    private final ApplicationProfileSnapshotMapper snapshotMapper;
    private final ApplicationReviewHistoryMapper historyMapper;
    private final StudentProfileMapper profileMapper;
    private final ActivityPeriodMapper periodMapper;
    private final ObjectMapper objectMapper;
    private final AuditService auditService;
    private final MatchingWorkspace matchingWorkspace;

    public List<ApplicationResponse> mine(Long userId) {
        return applicationMapper.selectList(Wrappers.<ParticipationApplication>lambdaQuery()
                        .eq(ParticipationApplication::getStudentUserId, userId)
                        .orderByDesc(ParticipationApplication::getSubmittedAt))
                .stream().map(this::response).toList();
    }

    @Transactional(readOnly = true)
    public List<ApplicationResponse> listForAdmin(Long periodId, String status) {
        Map<Long, ActivityPeriod> periods = new HashMap<>();
        Map<Long, Boolean> roleWindows = new HashMap<>();
        return applicationMapper.selectList(Wrappers.<ParticipationApplication>lambdaQuery()
                        .eq(periodId != null, ParticipationApplication::getPeriodId, periodId)
                        .eq(status != null && !status.isBlank(), ParticipationApplication::getApplicationStatus, status)
                        .orderByAsc(ParticipationApplication::getSubmittedAt).orderByAsc(ParticipationApplication::getId))
                .stream().map(application -> {
                    StudentProfile profile = profileMapper.selectById(application.getStudentUserId());
                    ActivityPeriod period = periods.computeIfAbsent(application.getPeriodId(), periodMapper::selectById);
                    boolean roleChangeAllowed = period != null && roleCorrectionStatusAllowed(application, period)
                            && roleWindows.computeIfAbsent(period.getId(), ignored -> matchingWorkspace.allowsApplicationRoleCorrection(period));
                    return response(application, period, profile == null ? null : profile.getProfileVersion(), roleChangeAllowed);
                }).toList();
    }

    @Transactional
    public ApplicationResponse submit(AuthenticatedUser actor, ApplicationSubmitRequest request,
                                      HttpServletRequest servletRequest) {
        ActivityPeriod period = lockPeriod(request.periodId());
        StudentProfile profile = requiredApprovedProfile(actor.id());
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
        if (resubmit && !existing.getParticipantRole().equals(request.participantRole())) {
            throw new BusinessException("APPLICATION_ROLE_FIXED", "重提时请保留本期报名身份，如需调整请联系管理员");
        }

        ApplicationResponse before = resubmit ? response(existing) : null;
        ParticipationApplication application = resubmit ? existing : new ParticipationApplication();
        application.setPeriodId(request.periodId());
        application.setStudentUserId(actor.id());
        application.setParticipantRole(request.participantRole());
        application.setCumulativeGpa(request.cumulativeGpa());
        application.setSubjectsJson(json(validateSubjects(request.subjects())));
        application.setStatementText(validateStatement(request.statementText()));
        application.setLearningStylesJson(json(normalize(request.learningStyles())));
        application.setPersonalitiesJson(json(normalize(request.personalities())));
        application.setApplicationStatus("SUBMITTED");
        application.setSelectionStatus("UNDECIDED");
        application.setRejectionReason(null);
        application.setSubmittedAt(LocalDateTime.now());
        application.setReviewedBy(null);
        application.setReviewedAt(null);
        if (resubmit) saveRevision(application);
        else {
            application.setRevision(0);
            applicationMapper.insert(application);
        }

        if (!resubmit) createSnapshot(application, profile);
        addHistory(application.getId(), resubmit ? "RESUBMIT" : "SUBMIT", null, actor.id());
        auditService.log(actor, resubmit ? "APPLICATION_RESUBMIT" : "APPLICATION_SUBMIT", "APPLICATION",
                application.getId(), before, response(application), null, servletRequest);
        return response(application);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ApplicationResponse review(AuthenticatedUser actor, Long id, ApplicationReviewRequest request,
                                      HttpServletRequest servletRequest) {
        ParticipationApplication application = lockReviewApplication(id, "APPLICATION_REVIEW_NOT_OPEN", "审核报名");
        if (request.revision() != null) requireRevision(application.getRevision(), request.revision());
        requireReviewable(application);
        if ("REJECTED".equals(request.decision()) && (request.reason() == null || request.reason().isBlank())) {
            throw new BusinessException("REJECTION_REASON_REQUIRED", "驳回报名必须填写原因");
        }
        ApplicationResponse before = response(application);
        applyReview(application, actor, request.decision(), request.reason());
        saveRevision(application);
        auditService.log(actor, "APPLICATION_REVIEW", "APPLICATION", id, before, response(application),
                application.getRejectionReason(), servletRequest);
        return response(application);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ApplicationResponse correct(AuthenticatedUser actor, Long id, ApplicationCorrectionRequest request,
                                       HttpServletRequest servletRequest) {
        ParticipationApplication application = lockReviewApplication(id, "APPLICATION_CORRECTION_NOT_OPEN", "更正报名");
        requireRevision(application.getRevision(), request.revision());
        if (!CORRECTABLE.contains(application.getApplicationStatus())) {
            throw new BusinessException("APPLICATION_NOT_CORRECTABLE", "当前报名状态不能更正");
        }
        if (request.approve()) requireReviewable(application);

        ApplicationProfileSnapshot snapshot = snapshot(id);
        if (snapshot == null) throw new BusinessException("APPLICATION_SNAPSHOT_MISSING", "报名资料快照不存在");
        Map<String, Object> before = correctionState(application, snapshot);
        Map<String, Object> afterProfile = Map.of();
        if (request.grade() != null || request.major() != null) {
            StudentProfile profile = profileMapper.lockById(application.getStudentUserId());
            if (profile == null) throw new BusinessException("STUDENT_NOT_FOUND", "学生资料不存在", HttpStatus.NOT_FOUND);
            requireRevision(profile.getProfileVersion(), request.currentProfileVersion());
            before.put("studentProfile", profileFields(profile));
            if (request.grade() != null) {
                snapshot.setGrade(request.grade());
                profile.setGrade(request.grade());
            }
            if (request.major() != null) {
                snapshot.setMajor(request.major());
                profile.setMajor(request.major());
            }
            if (!before.get("studentProfile").equals(profileFields(profile))) {
                profile.setProfileVersion(profile.getProfileVersion() + 1);
                profileMapper.updateById(profile);
            }
            snapshot.setProfileVersion(profile.getProfileVersion());
            snapshotMapper.updateById(snapshot);
            afterProfile = profileFields(profile);
        }
        if (request.cumulativeGpa() != null) {
            application.setCumulativeGpa(request.cumulativeGpa().setScale(2, RoundingMode.HALF_UP));
        }
        if (request.subjects() != null) application.setSubjectsJson(json(validateSubjects(request.subjects())));
        if (request.statementText() != null) application.setStatementText(validateStatement(request.statementText()));
        Map<String, Object> after = correctionState(application, snapshot);
        if (!afterProfile.isEmpty()) after.put("studentProfile", afterProfile);
        if (before.equals(after)) throw new BusinessException("APPLICATION_CORRECTION_EMPTY", "请修改至少一项报名资料");
        if (request.approve()) {
            applyReview(application, actor, "APPROVED", null);
            after.put("applicationStatus", application.getApplicationStatus());
        }
        saveRevision(application);
        auditService.log(actor, "APPLICATION_CORRECTION", "APPLICATION", id, before, after,
                request.reason().strip(), servletRequest);
        StudentProfile currentProfile = profileMapper.selectById(application.getStudentUserId());
        return response(application, currentProfile == null ? null : currentProfile.getProfileVersion());
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ApplicationResponse correctRole(AuthenticatedUser actor, Long id, ApplicationRoleCorrectionRequest request,
                                           HttpServletRequest servletRequest) {
        ActivityPeriod period = lockApplicationPeriod(id);
        ParticipationApplication application = applicationMapper.selectById(id);
        requireRevision(application.getRevision(), request.revision());
        if (!matchingWorkspace.allowsApplicationRoleCorrection(period)) {
            throw new BusinessException("APPLICATION_ROLE_CORRECTION_CLOSED", "仅报名审核阶段和首次发布前的匹配阶段可调整身份");
        }
        if (!roleCorrectionStatusAllowed(application, period)) {
            throw new BusinessException("APPLICATION_NOT_CORRECTABLE", "当前报名状态不能更正身份");
        }
        if (application.getParticipantRole().equals(request.participantRole())) {
            throw new BusinessException("APPLICATION_ROLE_UNCHANGED", "请选择新的报名身份");
        }
        var before = roleFields(application);
        application.setSubjectsJson(json(validateSubjects(request.subjects())));
        application.setParticipantRole(request.participantRole());
        saveRevision(application);
        if ("MATCHING_POOL".equals(application.getSelectionStatus())) matchingWorkspace.invalidateForRoleCorrection(period);
        auditService.log(actor, "APPLICATION_ROLE_CORRECTION", "APPLICATION", id, before, roleFields(application),
                request.reason().strip(), servletRequest);
        return response(application, period, null, true);
    }

    private boolean roleCorrectionStatusAllowed(ParticipationApplication application, ActivityPeriod period) {
        return "REVIEW".equals(period.getStatus()) && CORRECTABLE.contains(application.getApplicationStatus())
                || "MATCHING".equals(period.getStatus()) && "APPROVED".equals(application.getApplicationStatus());
    }

    private Map<String, Object> roleFields(ParticipationApplication application) {
        return Map.of("participantRole", application.getParticipantRole(), "subjects", list(application.getSubjectsJson()));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ApplicationResponse select(AuthenticatedUser actor, Long id, ApplicationSelectionRequest request,
                                      HttpServletRequest servletRequest) {
        ParticipationApplication application = lockReviewApplication(id, "APPLICATION_SELECTION_NOT_OPEN", "设置入选状态");
        if (!"APPROVED".equals(application.getApplicationStatus())) {
            throw new BusinessException("APPLICATION_NOT_APPROVED", "只有审核通过的报名可以设置入选状态");
        }
        String before = application.getSelectionStatus();
        application.setSelectionStatus(request.selectionStatus());
        saveRevision(application);
        addHistory(id, request.selectionStatus(), null, actor.id());
        auditService.log(actor, "APPLICATION_SELECTION", "APPLICATION", id,
                java.util.Map.of("selectionStatus", before),
                java.util.Map.of("selectionStatus", request.selectionStatus()), null, servletRequest);
        return response(application);
    }

    private void applyReview(ParticipationApplication application, AuthenticatedUser actor, String decision, String reason) {
        application.setApplicationStatus(decision);
        application.setRejectionReason("REJECTED".equals(decision) ? reason.trim() : null);
        application.setReviewedBy(actor.id());
        application.setReviewedAt(LocalDateTime.now());
        addHistory(application.getId(), "APPROVED".equals(decision) ? "APPROVE" : "REJECT",
                application.getRejectionReason(), actor.id());
    }

    private void requireReviewable(ParticipationApplication application) {
        if (!REVIEWABLE.contains(application.getApplicationStatus())) {
            throw new BusinessException("APPLICATION_NOT_REVIEWABLE", "当前报名状态不能审核");
        }
    }

    private void requireRevision(Integer current, Integer expected) {
        if (!Objects.equals(current, expected)) {
            throw new BusinessException("APPLICATION_VERSION_CONFLICT", "报名或学生资料已更新，请重新加载后再操作", HttpStatus.CONFLICT);
        }
    }

    private void saveRevision(ParticipationApplication application) {
        application.setRevision(application.getRevision() + 1);
        applicationMapper.updateById(application);
    }

    private Map<String, Object> correctionState(ParticipationApplication application, ApplicationProfileSnapshot snapshot) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("grade", snapshot.getGrade());
        values.put("major", snapshot.getMajor());
        values.put("cumulativeGpa", application.getCumulativeGpa().stripTrailingZeros());
        values.put("subjects", list(application.getSubjectsJson()));
        values.put("statementText", application.getStatementText());
        values.put("applicationStatus", application.getApplicationStatus());
        return values;
    }

    private Map<String, Object> profileFields(StudentProfile profile) {
        return Map.of("grade", profile.getGrade(), "major", profile.getMajor(), "profileVersion", profile.getProfileVersion());
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
        StudentProfile profile = profileMapper.lockById(userId);
        if (profile == null || !"APPROVED".equals(profile.getReviewStatus())) {
            throw new BusinessException("PROFILE_NOT_APPROVED", "个人资料审核通过后才能报名", HttpStatus.FORBIDDEN);
        }
        return profile;
    }

    private ActivityPeriod lockPeriod(Long id) {
        ActivityPeriod period = periodMapper.lockById(id);
        if (period == null) throw new BusinessException("PERIOD_NOT_FOUND", "期次不存在", HttpStatus.NOT_FOUND);
        return period;
    }

    private ActivityPeriod lockApplicationPeriod(Long id) {
        ParticipationApplication application = applicationMapper.selectOne(Wrappers.<ParticipationApplication>lambdaQuery()
                .select(ParticipationApplication::getPeriodId).eq(ParticipationApplication::getId, id));
        if (application == null) throw new BusinessException("APPLICATION_NOT_FOUND", "报名记录不存在", HttpStatus.NOT_FOUND);
        return lockPeriod(application.getPeriodId());
    }

    private ParticipationApplication lockReviewApplication(Long id, String code, String action) {
        if (!"REVIEW".equals(lockApplicationPeriod(id).getStatus())) {
            throw new BusinessException(code, "仅报名审核阶段可以" + action);
        }
        return applicationMapper.selectById(id);
    }

    private ApplicationResponse response(ParticipationApplication application) {
        return response(application, null);
    }

    private ApplicationProfileSnapshot snapshot(Long applicationId) {
        return snapshotMapper.selectOne(Wrappers.<ApplicationProfileSnapshot>lambdaQuery()
                .eq(ApplicationProfileSnapshot::getApplicationId, applicationId));
    }

    private ApplicationResponse response(ParticipationApplication application, Integer currentProfileVersion) {
        ActivityPeriod period = periodMapper.selectById(application.getPeriodId());
        return response(application, period, currentProfileVersion, null);
    }

    private ApplicationResponse response(ParticipationApplication application, ActivityPeriod period, Integer currentProfileVersion,
                                         Boolean roleChangeAllowed) {
        ApplicationProfileSnapshot snapshot = snapshot(application.getId());
        return new ApplicationResponse(application.getId(), application.getPeriodId(), period == null ? "—" : period.getName(),
                application.getStudentUserId(), snapshot == null ? "—" : snapshot.getStudentNo(),
                snapshot == null ? "—" : snapshot.getRealName(), snapshot == null ? "—" : snapshot.getMajor(),
                snapshot == null ? null : snapshot.getGrade(), application.getParticipantRole(),
                application.getCumulativeGpa(), list(application.getSubjectsJson()), application.getStatementText(),
                list(application.getLearningStylesJson()), list(application.getPersonalitiesJson()),
                application.getApplicationStatus(), application.getSelectionStatus(), application.getRejectionReason(),
                application.getSubmittedAt(), application.getReviewedAt(), application.getRevision(), currentProfileVersion, roleChangeAllowed);
    }

    private List<String> validateSubjects(List<String> subjects) {
        List<String> validated = new ArrayList<>();
        var seen = new LinkedHashSet<String>();
        for (int i = 0; i < subjects.size(); i++) {
            String raw = subjects.get(i);
            String subject = raw == null ? "" : EDGE_WHITESPACE.matcher(raw).replaceAll("");
            String prefix = "第 " + (i + 1) + " 门课程";
            if (subject.isEmpty()) throw new BusinessException("INVALID_SUBJECTS", prefix + "不能为空");
            if (SUBJECT_SEPARATOR.matcher(raw).find()) {
                throw new BusinessException("INVALID_SUBJECTS", prefix + "含分隔符，请逐科填写，每行只填一门");
            }
            if (subject.length() > 80) throw new BusinessException("INVALID_SUBJECTS", prefix + "不能超过 80 字");
            if (!seen.add(subject.toLowerCase(Locale.ROOT))) {
                throw new BusinessException("INVALID_SUBJECTS", prefix + "与前面填写的课程重复");
            }
            validated.add(subject);
        }
        return validated;
    }

    private String validateStatement(String raw) {
        String statement = EDGE_WHITESPACE.matcher(raw).replaceAll("");
        if (statement.length() < 30 || statement.length() > 500) {
            throw new BusinessException("INVALID_APPLICATION_STATEMENT", "报名自述须为 30 至 500 字");
        }
        return statement;
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
