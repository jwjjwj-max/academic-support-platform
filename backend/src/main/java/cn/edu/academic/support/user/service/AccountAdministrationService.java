package cn.edu.academic.support.user.service;

import cn.edu.academic.support.audit.service.AuditService;
import cn.edu.academic.support.common.exception.BusinessException;
import cn.edu.academic.support.security.AuthenticatedUser;
import cn.edu.academic.support.user.dto.AccountStatusRequest;
import cn.edu.academic.support.user.dto.AdminAccountResponse;
import cn.edu.academic.support.user.dto.AdminCreateRequest;
import cn.edu.academic.support.user.dto.PasswordResetRequest;
import cn.edu.academic.support.user.mapper.UserAccountMapper;
import cn.edu.academic.support.user.model.UserAccount;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AccountAdministrationService {
    private final UserAccountMapper userAccountMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public List<AdminAccountResponse> listAdmins() {
        return userAccountMapper.selectList(Wrappers.<UserAccount>lambdaQuery()
                        .eq(UserAccount::getAccountType, "ADMIN")
                        .orderByDesc(UserAccount::getCreatedAt))
                .stream().map(AdminAccountResponse::from).toList();
    }

    @Transactional
    public AdminAccountResponse createAdmin(AuthenticatedUser actor, AdminCreateRequest request,
                                            HttpServletRequest servletRequest) {
        Long count = userAccountMapper.selectCount(Wrappers.<UserAccount>lambdaQuery()
                .eq(UserAccount::getLoginName, request.loginName().trim()));
        if (count > 0) throw new BusinessException("LOGIN_NAME_EXISTS", "该账号已存在");
        UserAccount user = new UserAccount();
        user.setLoginName(request.loginName().trim());
        user.setPasswordHash(passwordEncoder.encode(request.initialPassword()));
        user.setAccountType("ADMIN");
        user.setDisplayName(request.displayName().trim());
        user.setAccountStatus("ACTIVE");
        user.setTokenVersion(0);
        user.setCreatedBy(actor.id());
        userAccountMapper.insert(user);
        AdminAccountResponse response = AdminAccountResponse.from(user);
        auditService.log(actor, "ADMIN_CREATE", "ADMIN", user.getId(), null, response, null, servletRequest);
        return response;
    }

    @Transactional
    public AdminAccountResponse updateAdminStatus(AuthenticatedUser actor, Long adminId, AccountStatusRequest request,
                                                   HttpServletRequest servletRequest) {
        UserAccount admin = requiredUser(adminId, "ADMIN");
        AdminAccountResponse before = AdminAccountResponse.from(admin);
        admin.setAccountStatus(request.status());
        admin.setTokenVersion(admin.getTokenVersion() + 1);
        userAccountMapper.updateById(admin);
        AdminAccountResponse after = AdminAccountResponse.from(admin);
        auditService.log(actor, "ADMIN_STATUS_CHANGE", "ADMIN", adminId, before, after,
                request.reason().trim(), servletRequest);
        return after;
    }

    @Transactional
    public void resetStudentPassword(AuthenticatedUser actor, Long studentId, PasswordResetRequest request,
                                     HttpServletRequest servletRequest) {
        UserAccount student = requiredUser(studentId, "STUDENT");
        student.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        student.setPasswordResetAt(LocalDateTime.now());
        student.setTokenVersion(student.getTokenVersion() + 1);
        userAccountMapper.updateById(student);
        auditService.log(actor, "STUDENT_PASSWORD_RESET", "STUDENT", studentId, null,
                java.util.Map.of("passwordResetAt", student.getPasswordResetAt()), request.reason().trim(), servletRequest);
    }

    private UserAccount requiredUser(Long userId, String type) {
        UserAccount user = userAccountMapper.selectById(userId);
        if (user == null || !type.equals(user.getAccountType())) {
            throw new BusinessException("ACCOUNT_NOT_FOUND", "账号不存在", HttpStatus.NOT_FOUND);
        }
        return user;
    }
}

