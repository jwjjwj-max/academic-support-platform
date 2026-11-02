package cn.edu.academic.support.period.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PeriodStatusRequest(
        @NotBlank @Pattern(regexp = "ANNOUNCED|REGISTRATION|REVIEW|MATCHING|CONFIRMATION|TRACKING|ACCEPTANCE|ENDED|ARCHIVED") String status,
        @NotBlank(message = "请输入阶段变更原因") @Size(max = 500) String reason,
        Boolean confirmedRisk
) {
    public boolean isRiskConfirmed() {
        return Boolean.TRUE.equals(confirmedRisk);
    }
}
