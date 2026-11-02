package cn.edu.academic.support.auth.controller;

import cn.edu.academic.support.auth.dto.CurrentUserResponse;
import cn.edu.academic.support.auth.dto.LoginRequest;
import cn.edu.academic.support.auth.dto.LoginResponse;
import cn.edu.academic.support.auth.service.AuthService;
import cn.edu.academic.support.common.api.ApiResponse;
import cn.edu.academic.support.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                            HttpServletRequest servletRequest) {
        return ApiResponse.success(authService.login(request, servletRequest.getRemoteAddr()));
    }

    @GetMapping("/me")
    public ApiResponse<CurrentUserResponse> me(@AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.success(authService.current(user));
    }
}
