package cn.edu.academic.support.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PasswordResetRequest(
        @NotBlank(message = "请输入新密码") @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,72}$", message = "新密码至少 8 位且必须包含字母和数字") String newPassword,
        @NotBlank(message = "请输入重置原因") @Size(max = 500, message = "重置原因不能超过 500 个字符") String reason
) {
}

