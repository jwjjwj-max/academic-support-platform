package cn.edu.academic.support.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "请输入账号") String loginName,
        @NotBlank(message = "请输入密码") String password
) {
}

