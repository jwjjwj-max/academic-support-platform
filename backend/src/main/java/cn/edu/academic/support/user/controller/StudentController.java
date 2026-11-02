package cn.edu.academic.support.user.controller;

import cn.edu.academic.support.common.api.ApiResponse;
import cn.edu.academic.support.security.AuthenticatedUser;
import cn.edu.academic.support.user.dto.StudentProfileResponse;
import cn.edu.academic.support.user.dto.StudentProfileUpdateRequest;
import cn.edu.academic.support.user.dto.StudentRegistrationRequest;
import cn.edu.academic.support.user.service.StudentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/students")
@RequiredArgsConstructor
public class StudentController {
    private final StudentService studentService;

    @PostMapping("/register")
    public ApiResponse<StudentProfileResponse> register(@Valid @RequestBody StudentRegistrationRequest request,
                                                        HttpServletRequest servletRequest) {
        return ApiResponse.success(studentService.register(request, servletRequest));
    }

    @GetMapping("/me/profile")
    @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<StudentProfileResponse> profile(@AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.success(studentService.mine(user.id()));
    }

    @PutMapping("/me/profile")
    @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<StudentProfileResponse> update(@AuthenticationPrincipal AuthenticatedUser user,
                                                      @Valid @RequestBody StudentProfileUpdateRequest request,
                                                      HttpServletRequest servletRequest) {
        return ApiResponse.success(studentService.updateMine(user, request, servletRequest));
    }
}

