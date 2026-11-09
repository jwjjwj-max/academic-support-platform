package cn.edu.academic.support.user.service;

import cn.edu.academic.support.audit.service.AuditService;
import cn.edu.academic.support.common.exception.BusinessException;
import cn.edu.academic.support.security.AuthenticatedUser;
import cn.edu.academic.support.user.dto.ProfileReviewRequest;
import cn.edu.academic.support.user.dto.StudentProfileResponse;
import cn.edu.academic.support.user.dto.StudentProfileUpdateRequest;
import cn.edu.academic.support.user.dto.StudentRegistrationRequest;
import cn.edu.academic.support.user.mapper.StudentProfileMapper;
import cn.edu.academic.support.user.mapper.StudentProfileReviewHistoryMapper;
import cn.edu.academic.support.user.mapper.UserAccountMapper;
import cn.edu.academic.support.user.model.StudentProfile;
import cn.edu.academic.support.user.model.StudentProfileReviewHistory;
import cn.edu.academic.support.user.model.UserAccount;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class StudentService {
    private final UserAccountMapper userAccountMapper;
    private final StudentProfileMapper studentProfileMapper;
    private final StudentProfileReviewHistoryMapper historyMapper;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;
    private final AuditService auditService;

    @Transactional
    public StudentProfileResponse register(StudentRegistrationRequest request, HttpServletRequest servletRequest) {
        String studentNo = request.studentNo().trim();
        ensureStudentNoAvailable(studentNo, null);
        UserAccount user = new UserAccount();
        user.setLoginName(studentNo);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setAccountType("STUDENT");
        user.setDisplayName(request.realName().trim());
        user.setAccountStatus("ACTIVE");
        user.setTokenVersion(0);
        userAccountMapper.insert(user);

        StudentProfile profile = new StudentProfile();
        profile.setUserId(user.getId());
        apply(profile, request.studentNo(), request.realName(), request.college(), request.major(), request.grade(),
                request.wechat(), request.phone());
        profile.setReviewStatus("PENDING");
        profile.setSubmittedAt(LocalDateTime.now());
        profile.setProfileVersion(1);
        studentProfileMapper.insert(profile);
        addHistory(profile, "SUBMIT", null, user.getId());
        auditService.log(new AuthenticatedUser(user.getId(), user.getLoginName(), user.getDisplayName(),
                        user.getAccountType(), user.getTokenVersion()), "STUDENT_REGISTER", "STUDENT", user.getId(),
                null, StudentProfileResponse.from(profile), null, servletRequest);
        return StudentProfileResponse.from(profile);
    }

    public StudentProfileResponse mine(Long userId) {
        StudentProfile profile = requiredProfile(userId);
        return StudentProfileResponse.from(profile);
    }

    @Transactional
    public StudentProfileResponse updateMine(AuthenticatedUser actor, StudentProfileUpdateRequest request,
                                             HttpServletRequest servletRequest) {
        StudentProfile profile = requiredProfile(actor.id(), true);
        StudentProfileResponse before = StudentProfileResponse.from(profile);
        String studentNo = request.studentNo().trim();
        ensureStudentNoAvailable(studentNo, actor.id());
        boolean identityChanged = !profile.getStudentNo().equals(studentNo)
                || !profile.getRealName().equals(request.realName().trim())
                || !profile.getCollege().equals(request.college().trim())
                || !profile.getMajor().equals(request.major().trim())
                || !profile.getGrade().equals(request.grade());
        apply(profile, request.studentNo(), request.realName(), request.college(), request.major(), request.grade(),
                request.wechat(), request.phone());
        profile.setProfileVersion(profile.getProfileVersion() + 1);
        if (identityChanged || "REJECTED".equals(profile.getReviewStatus())) {
            profile.setReviewStatus("PENDING");
            profile.setRejectionReason(null);
            profile.setReviewedBy(null);
            profile.setReviewedAt(null);
            profile.setSubmittedAt(LocalDateTime.now());
        }
        studentProfileMapper.updateById(profile);
        UserAccount user = userAccountMapper.selectById(actor.id());
        user.setLoginName(studentNo);
        user.setDisplayName(profile.getRealName());
        userAccountMapper.updateById(user);
        addHistory(profile, "RESUBMIT", null, actor.id());
        auditService.log(actor, "STUDENT_PROFILE_UPDATE", "STUDENT", actor.id(), before,
                StudentProfileResponse.from(profile), null, servletRequest);
        return StudentProfileResponse.from(profile);
    }

    public Page<StudentProfile> listForReview(String status, String keyword, long page, long size) {
        String search = keyword == null ? "" : keyword.strip().toLowerCase(Locale.ROOT);
        String pattern = "%" + search.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        return studentProfileMapper.selectPage(Page.of(Math.max(1, page), Math.max(1, Math.min(size, 100))),
                Wrappers.<StudentProfile>lambdaQuery()
                        .eq(status != null && !status.isBlank(), StudentProfile::getReviewStatus, status)
                        .apply(!search.isEmpty(), "(LOWER(real_name) LIKE {0} ESCAPE '!' OR LOWER(student_no) LIKE {0} ESCAPE '!')", pattern)
                        .orderByAsc(StudentProfile::getSubmittedAt).orderByAsc(StudentProfile::getUserId));
    }

    @Transactional
    public StudentProfileResponse review(AuthenticatedUser actor, Long studentUserId, ProfileReviewRequest request,
                                         HttpServletRequest servletRequest) {
        StudentProfile profile = requiredProfile(studentUserId, true);
        StudentProfileResponse before = StudentProfileResponse.from(profile);
        profile.setReviewStatus(request.decision());
        profile.setRejectionReason("REJECTED".equals(request.decision()) ? request.reason().trim() : null);
        profile.setReviewedBy(actor.id());
        profile.setReviewedAt(LocalDateTime.now());
        studentProfileMapper.updateById(profile);
        addHistory(profile, "APPROVED".equals(request.decision()) ? "APPROVE" : "REJECT",
                profile.getRejectionReason(), actor.id());
        auditService.log(actor, "STUDENT_PROFILE_REVIEW", "STUDENT", studentUserId, before,
                StudentProfileResponse.from(profile), profile.getRejectionReason(), servletRequest);
        return StudentProfileResponse.from(profile);
    }

    private StudentProfile requiredProfile(Long userId) {
        return requiredProfile(userId, false);
    }

    private StudentProfile requiredProfile(Long userId, boolean lock) {
        StudentProfile profile = lock ? studentProfileMapper.lockById(userId) : studentProfileMapper.selectById(userId);
        if (profile == null) throw new BusinessException("STUDENT_NOT_FOUND", "学生不存在", HttpStatus.NOT_FOUND);
        return profile;
    }

    private void ensureStudentNoAvailable(String studentNo, Long excludeUserId) {
        Long count = userAccountMapper.selectCount(Wrappers.<UserAccount>lambdaQuery()
                .eq(UserAccount::getLoginName, studentNo)
                .ne(excludeUserId != null, UserAccount::getId, excludeUserId));
        if (count > 0) throw new BusinessException("STUDENT_NO_EXISTS", "该学号已注册");
    }

    private void apply(StudentProfile profile, String studentNo, String realName, String college, String major,
                       Integer grade, String wechat, String phone) {
        profile.setStudentNo(studentNo.trim());
        profile.setRealName(realName.trim());
        profile.setCollege(college.trim());
        profile.setMajor(major.trim());
        profile.setGrade(grade);
        profile.setWechat(wechat.trim());
        profile.setPhone(phone == null || phone.isBlank() ? null : phone.trim());
    }

    private void addHistory(StudentProfile profile, String action, String reason, Long operatorId) {
        StudentProfileReviewHistory history = new StudentProfileReviewHistory();
        history.setStudentUserId(profile.getUserId());
        history.setProfileVersion(profile.getProfileVersion());
        history.setAction(action);
        history.setReason(reason);
        history.setProfileSnapshot(json(StudentProfileResponse.from(profile)));
        history.setOperatorId(operatorId);
        historyMapper.insert(history);
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
