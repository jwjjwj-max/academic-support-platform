package cn.edu.academic.support.owner.controller;

import cn.edu.academic.support.common.api.ApiResponse;
import cn.edu.academic.support.security.AuthenticatedUser;
import cn.edu.academic.support.user.dto.AccountStatusRequest;
import cn.edu.academic.support.user.dto.AdminAccountResponse;
import cn.edu.academic.support.user.dto.AdminCreateRequest;
import cn.edu.academic.support.user.service.AccountAdministrationService;
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
@RequestMapping("/owner/admins")
@PreAuthorize("hasRole('SYSTEM_OWNER')")
@RequiredArgsConstructor
public class AdminAccountController {
    private final AccountAdministrationService service;

    @GetMapping
    public ApiResponse<List<AdminAccountResponse>> list() {
        return ApiResponse.success(service.listAdmins());
    }

    @PostMapping
    public ApiResponse<AdminAccountResponse> create(@AuthenticationPrincipal AuthenticatedUser actor,
                                                    @Valid @RequestBody AdminCreateRequest request,
                                                    HttpServletRequest servletRequest) {
        return ApiResponse.success(service.createAdmin(actor, request, servletRequest));
    }

    @PutMapping("/{adminId}/status")
    public ApiResponse<AdminAccountResponse> status(@AuthenticationPrincipal AuthenticatedUser actor,
                                                    @PathVariable Long adminId,
                                                    @Valid @RequestBody AccountStatusRequest request,
                                                    HttpServletRequest servletRequest) {
        return ApiResponse.success(service.updateAdminStatus(actor, adminId, request, servletRequest));
    }
}
