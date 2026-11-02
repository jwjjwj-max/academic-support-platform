package cn.edu.academic.support.auth.service;

import cn.edu.academic.support.auth.dto.CurrentUserResponse;
import cn.edu.academic.support.auth.dto.LoginRequest;
import cn.edu.academic.support.auth.dto.LoginResponse;
import cn.edu.academic.support.common.exception.BusinessException;
import cn.edu.academic.support.security.AuthenticatedUser;
import cn.edu.academic.support.security.JwtService;
import cn.edu.academic.support.user.mapper.StudentProfileMapper;
import cn.edu.academic.support.user.mapper.UserAccountMapper;
import cn.edu.academic.support.user.model.StudentProfile;
import cn.edu.academic.support.user.model.UserAccount;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserAccountMapper userAccountMapper;
    private final StudentProfileMapper studentProfileMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LoginAttemptService loginAttemptService;

    public LoginResponse login(LoginRequest request, String remoteAddress) {
        String loginName = request.loginName().trim();
        String attemptKey = loginName.toLowerCase() + "|" + remoteAddress;
        if (loginAttemptService.blocked(attemptKey)) {
            throw new BusinessException("LOGIN_RATE_LIMITED", "登录失败次数过多，请 15 分钟后再试", HttpStatus.TOO_MANY_REQUESTS);
        }
        UserAccount user = userAccountMapper.selectOne(Wrappers.<UserAccount>lambdaQuery()
                .eq(UserAccount::getLoginName, loginName));
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            loginAttemptService.failed(attemptKey);
            throw new BusinessException("INVALID_CREDENTIALS", "账号或密码错误", HttpStatus.UNAUTHORIZED);
        }
        if (!"ACTIVE".equals(user.getAccountStatus())) {
            throw new BusinessException("ACCOUNT_DISABLED", "账号已停用，请联系系统负责人", HttpStatus.FORBIDDEN);
        }
        loginAttemptService.succeeded(attemptKey);
        user.setLastLoginAt(LocalDateTime.now());
        userAccountMapper.updateById(user);
        return new LoginResponse(jwtService.issue(user), "Bearer", jwtService.expiresInSeconds(), current(user));
    }

    public CurrentUserResponse current(AuthenticatedUser principal) {
        UserAccount user = userAccountMapper.selectById(principal.id());
        if (user == null) throw new BusinessException("USER_NOT_FOUND", "账号不存在", HttpStatus.NOT_FOUND);
        return current(user);
    }

    private CurrentUserResponse current(UserAccount user) {
        StudentProfile profile = "STUDENT".equals(user.getAccountType())
                ? studentProfileMapper.selectById(user.getId()) : null;
        return new CurrentUserResponse(user.getId(), user.getLoginName(), user.getDisplayName(),
                user.getAccountType(), user.getAccountStatus(),
                profile == null ? null : profile.getReviewStatus(),
                profile == null ? null : profile.getRejectionReason());
    }
}

