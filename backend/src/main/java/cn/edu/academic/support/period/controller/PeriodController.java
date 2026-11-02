package cn.edu.academic.support.period.controller;

import cn.edu.academic.support.common.api.ApiResponse;
import cn.edu.academic.support.period.dto.AcceptanceWindowExtensionRequest;
import cn.edu.academic.support.period.dto.PeriodRequest;
import cn.edu.academic.support.period.dto.PeriodStatusRequest;
import cn.edu.academic.support.period.dto.PeriodTransitionPreview;
import cn.edu.academic.support.period.model.ActivityPeriod;
import cn.edu.academic.support.period.service.PeriodService;
import cn.edu.academic.support.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/periods")
@RequiredArgsConstructor
public class PeriodController {
    private final PeriodService service;

    @GetMapping
    public ApiResponse<List<ActivityPeriod>> list() {
        return ApiResponse.success(service.list());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<ActivityPeriod> create(@AuthenticationPrincipal AuthenticatedUser actor,
                                              @Valid @RequestBody PeriodRequest request,
                                              HttpServletRequest servletRequest) {
        return ApiResponse.success(service.create(actor, request, servletRequest));
    }

    @PutMapping("/{periodId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<ActivityPeriod> update(@AuthenticationPrincipal AuthenticatedUser actor,
                                              @PathVariable Long periodId,
                                              @Valid @RequestBody PeriodRequest request,
                                              HttpServletRequest servletRequest) {
        return ApiResponse.success(service.update(actor, periodId, request, servletRequest));
    }

    @PostMapping("/{periodId}/acceptance-extension")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<ActivityPeriod> extendAcceptanceWindow(@AuthenticationPrincipal AuthenticatedUser actor,
                                                              @PathVariable Long periodId,
                                                              @Valid @RequestBody AcceptanceWindowExtensionRequest request,
                                                              HttpServletRequest servletRequest) {
        return ApiResponse.success(service.extendAcceptanceWindow(actor, periodId, request, servletRequest));
    }

    @PostMapping("/{periodId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<ActivityPeriod> changeStatus(@AuthenticationPrincipal AuthenticatedUser actor,
                                                    @PathVariable Long periodId,
                                                    @Valid @RequestBody PeriodStatusRequest request,
                                                    HttpServletRequest servletRequest) {
        return ApiResponse.success(service.changeStatus(actor, periodId, request, servletRequest));
    }

    @GetMapping("/{periodId}/transition-preview")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PeriodTransitionPreview> transitionPreview(@PathVariable Long periodId) {
        return ApiResponse.success(service.transitionPreview(periodId));
    }
}
