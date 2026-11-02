package cn.edu.academic.support.user.dto;

import cn.edu.academic.support.user.model.UserAccount;

import java.time.LocalDateTime;

public record AdminAccountResponse(Long id, String loginName, String displayName, String status,
                                   LocalDateTime createdAt, LocalDateTime lastLoginAt) {
    public static AdminAccountResponse from(UserAccount user) {
        return new AdminAccountResponse(user.getId(), user.getLoginName(), user.getDisplayName(),
                user.getAccountStatus(), user.getCreatedAt(), user.getLastLoginAt());
    }
}

