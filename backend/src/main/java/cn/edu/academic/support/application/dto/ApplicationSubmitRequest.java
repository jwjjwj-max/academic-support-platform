package cn.edu.academic.support.application.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record ApplicationSubmitRequest(
        @NotNull Long periodId,
        @NotBlank @Pattern(regexp = "CLIMBER|NAVIGATOR", message = "请选择攀登者或领航者身份") String participantRole,
        @NotNull @DecimalMin("0.00") @DecimalMax("5.00") BigDecimal cumulativeGpa,
        @NotEmpty @Size(max = 8) List<@NotBlank @Size(max = 80) String> subjects,
        @NotBlank @Size(min = 30, max = 500) String statementText,
        @NotEmpty @Size(max = 3) List<@NotBlank @Size(max = 40) String> learningStyles,
        @NotEmpty @Size(max = 3) List<@NotBlank @Size(max = 40) String> personalities
) {}
