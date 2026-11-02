package cn.edu.academic.support.period.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record AcceptanceWindowExtensionRequest(
        @NotNull(message = "请选择新的验收提交截止时间") LocalDateTime newAcceptanceEnd,
        @NotBlank(message = "请填写延长原因") @Size(max = 500) String reason
) {}
