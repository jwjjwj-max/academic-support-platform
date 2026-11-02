package cn.edu.academic.support.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AdminCreateRequest(
        @NotBlank(message = "请输入管理员账号") @Pattern(regexp = "^[A-Za-z0-9_.-]{4,64}$", message = "管理员账号格式不正确") String loginName,
        @NotBlank(message = "请输入管理员姓名") @Size(max = 64, message = "姓名不能超过 64 个字符") String displayName,
        @NotBlank(message = "请输入初始密码") @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{10,72}$", message = "初始密码至少 10 位且必须包含字母和数字") String initialPassword
) {
}

