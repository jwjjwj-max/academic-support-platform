package cn.edu.academic.support.auth.dto;

public record CurrentUserResponse(
        Long id,
        String loginName,
        String displayName,
        String accountType,
        String accountStatus,
        String profileReviewStatus,
        String profileRejectionReason
) {
}

