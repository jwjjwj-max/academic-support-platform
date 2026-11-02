package cn.edu.academic.support.user.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ProfileReviewRequest(
        @NotBlank(message = "请选择审核结果") @Pattern(regexp = "APPROVED|REJECTED", message = "审核结果不正确") String decision,
        @Size(max = 500, message = "驳回原因不能超过 500 个字符") String reason
) {
    @AssertTrue(message = "驳回时必须填写原因")
    public boolean isReasonValid() {
        return !"REJECTED".equals(decision) || (reason != null && !reason.isBlank());
    }
}

