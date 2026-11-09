package cn.edu.academic.support.application.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ApplicationRoleCorrectionRequest(
        @NotNull @Min(0) Integer revision,
        @NotBlank @Pattern(regexp = "CLIMBER|NAVIGATOR", message = "请选择攀登者或领航者") String participantRole,
        @NotNull @Size(min = 1, max = 8, message = "请核对并填写新身份对应的 1 至 8 门课程") List<String> subjects,
        @NotBlank(message = "请填写身份更正原因") @Size(max = 500) String reason
) {}
