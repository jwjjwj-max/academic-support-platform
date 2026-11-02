package cn.edu.academic.support.auth.dto;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        CurrentUserResponse user
) {
}

