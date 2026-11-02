package cn.edu.academic.support.common.api;

import java.time.Instant;

public record ApiResponse<T>(String code, String message, T data, Instant timestamp) {
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>("OK", "操作成功", data, Instant.now());
    }

    public static ApiResponse<Void> success() {
        return success(null);
    }

    public static ApiResponse<Void> error(String code, String message) {
        return new ApiResponse<>(code, message, null, Instant.now());
    }
}

