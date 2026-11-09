package cn.edu.academic.support.application.controller;

import cn.edu.academic.support.application.dto.ApplicationCorrectionRequest;
import cn.edu.academic.support.application.dto.ApplicationResponse;
import cn.edu.academic.support.application.dto.ApplicationReviewRequest;
import cn.edu.academic.support.application.dto.ApplicationRoleCorrectionRequest;
import cn.edu.academic.support.application.dto.ApplicationSelectionRequest;
import cn.edu.academic.support.application.dto.ApplicationSubmitRequest;
import cn.edu.academic.support.application.service.ApplicationService;
import cn.edu.academic.support.common.api.ApiResponse;
import cn.edu.academic.support.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class ApplicationController {
    private final ApplicationService service;

    @GetMapping("/applications/mine")
    @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<List<ApplicationResponse>> mine(@AuthenticationPrincipal AuthenticatedUser actor) {
        return ApiResponse.success(service.mine(actor.id()));
    }

    @PostMapping("/applications")
    @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<ApplicationResponse> submit(@AuthenticationPrincipal AuthenticatedUser actor,
                                                   @Valid @RequestBody ApplicationSubmitRequest request,
                                                   HttpServletRequest servletRequest) {
        return ApiResponse.success(service.submit(actor, request, servletRequest));
    }

    @GetMapping("/admin/applications")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<ApplicationResponse>> list(@RequestParam(required = false) Long periodId,
                                                       @RequestParam(required = false) String status) {
        return ApiResponse.success(service.listForAdmin(periodId, status));
    }

    @PostMapping("/admin/applications/{id}/review")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<ApplicationResponse> review(@AuthenticationPrincipal AuthenticatedUser actor,
                                                   @PathVariable Long id,
                                                   @Valid @RequestBody ApplicationReviewRequest request,
                                                   HttpServletRequest servletRequest) {
        return ApiResponse.success(service.review(actor, id, request, servletRequest));
    }

    @PostMapping("/admin/applications/{id}/correction")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<ApplicationResponse> correct(@AuthenticationPrincipal AuthenticatedUser actor,
                                                     @PathVariable Long id,
                                                     @Valid @RequestBody ApplicationCorrectionRequest request,
                                                     HttpServletRequest servletRequest) {
        return ApiResponse.success(service.correct(actor, id, request, servletRequest));
    }

    @PostMapping("/admin/applications/{id}/selection")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<ApplicationResponse> selection(@AuthenticationPrincipal AuthenticatedUser actor,
                                                      @PathVariable Long id,
                                                      @Valid @RequestBody ApplicationSelectionRequest request,
                                                      HttpServletRequest servletRequest) {
        return ApiResponse.success(service.select(actor, id, request, servletRequest));
    }

    @PostMapping("/admin/applications/{id}/role-correction")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<ApplicationResponse> correctRole(@AuthenticationPrincipal AuthenticatedUser actor,
                                                         @PathVariable Long id,
                                                         @Valid @RequestBody ApplicationRoleCorrectionRequest request,
                                                         HttpServletRequest servletRequest) {
        return ApiResponse.success(service.correctRole(actor, id, request, servletRequest));
    }
}
