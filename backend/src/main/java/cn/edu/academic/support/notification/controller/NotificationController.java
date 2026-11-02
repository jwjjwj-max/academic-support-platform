package cn.edu.academic.support.notification.controller;

import cn.edu.academic.support.common.api.ApiResponse;
import cn.edu.academic.support.notification.dto.*;
import cn.edu.academic.support.notification.model.Notification;
import cn.edu.academic.support.notification.service.NotificationService;
import cn.edu.academic.support.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequiredArgsConstructor
public class NotificationController {
    private final NotificationService service;
    @GetMapping("/notifications/mine") public ApiResponse<List<Map<String,Object>>> mine(@AuthenticationPrincipal AuthenticatedUser actor){ return ApiResponse.success(service.mine(actor.id())); }
    @GetMapping("/notifications/unread-count") public ApiResponse<Long> unread(@AuthenticationPrincipal AuthenticatedUser actor){ return ApiResponse.success(service.unread(actor.id())); }
    @PostMapping("/notifications/{id}/read") public ApiResponse<Void> read(@AuthenticationPrincipal AuthenticatedUser actor,@PathVariable Long id){ service.read(actor.id(),id); return ApiResponse.success(); }
    @PostMapping("/admin/notifications") @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Notification> create(@AuthenticationPrincipal AuthenticatedUser actor,@Valid @RequestBody NotificationCreateRequest body,HttpServletRequest request){ return ApiResponse.success(service.create(actor,body,request)); }
    @GetMapping("/todos/mine") @PreAuthorize("hasRole('STUDENT')") public ApiResponse<List<TodoResponse>> student(@AuthenticationPrincipal AuthenticatedUser actor){ return ApiResponse.success(service.studentTodos(actor.id())); }
    @GetMapping("/admin/todos") @PreAuthorize("hasRole('ADMIN')") public ApiResponse<List<TodoResponse>> admin(){ return ApiResponse.success(service.adminTodos()); }
}
