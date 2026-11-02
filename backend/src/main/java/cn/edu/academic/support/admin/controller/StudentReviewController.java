package cn.edu.academic.support.admin.controller;

import cn.edu.academic.support.common.api.ApiResponse;
import cn.edu.academic.support.security.AuthenticatedUser;
import cn.edu.academic.support.user.dto.PasswordResetRequest;
import cn.edu.academic.support.user.dto.ProfileReviewRequest;
import cn.edu.academic.support.user.dto.StudentProfileResponse;
import cn.edu.academic.support.user.model.StudentProfile;
import cn.edu.academic.support.user.service.AccountAdministrationService;
import cn.edu.academic.support.user.service.StudentService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/students")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class StudentReviewController {
    private final StudentService studentService;
    private final AccountAdministrationService accountAdministrationService;

    @GetMapping
    public ApiResponse<Page<StudentProfile>> list(@RequestParam(required = false) String status,
                                                  @RequestParam(defaultValue = "1") long page,
                                                  @RequestParam(defaultValue = "20") long size) {
        return ApiResponse.success(studentService.listForReview(status, page, size));
    }

    @PostMapping("/{studentUserId}/review")
    public ApiResponse<StudentProfileResponse> review(@AuthenticationPrincipal AuthenticatedUser actor,
                                                      @PathVariable Long studentUserId,
                                                      @Valid @RequestBody ProfileReviewRequest request,
                                                      HttpServletRequest servletRequest) {
        return ApiResponse.success(studentService.review(actor, studentUserId, request, servletRequest));
    }

    @PostMapping("/{studentUserId}/password-reset")
    public ApiResponse<Void> resetPassword(@AuthenticationPrincipal AuthenticatedUser actor,
                                           @PathVariable Long studentUserId,
                                           @Valid @RequestBody PasswordResetRequest request,
                                           HttpServletRequest servletRequest) {
        accountAdministrationService.resetStudentPassword(actor, studentUserId, request, servletRequest);
        return ApiResponse.success();
    }
}

