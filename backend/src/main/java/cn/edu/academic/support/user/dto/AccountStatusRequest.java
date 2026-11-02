package cn.edu.academic.support.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AccountStatusRequest(
        @NotBlank(message = "请选择账号状态") @Pattern(regexp = "ACTIVE|DISABLED", message = "账号状态不正确") String status,
        @NotBlank(message = "请输入变更原因") @Size(max = 500, message = "原因不能超过 500 个字符") String reason
) {
}

