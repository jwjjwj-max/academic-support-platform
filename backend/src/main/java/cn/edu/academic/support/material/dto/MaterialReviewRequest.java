package cn.edu.academic.support.material.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
public record MaterialReviewRequest(
        @NotBlank @Pattern(regexp = "APPROVED|REJECTED", message = "审核结果只能为通过或驳回") String decision,
        @Size(max = 500, message = "原因最多500字") String reason) {}
