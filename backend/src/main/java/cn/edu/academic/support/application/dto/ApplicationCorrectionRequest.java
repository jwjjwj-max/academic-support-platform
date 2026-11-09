package cn.edu.academic.support.application.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record ApplicationCorrectionRequest(
        @NotNull Integer revision,
        Integer currentProfileVersion,
        @Min(2000) @Max(2100) Integer grade,
        @Pattern(regexp = "人工智能|软件工程|计算机科学与技术", message = "请选择有效专业") String major,
        @DecimalMin("0.00") @DecimalMax("5.00") BigDecimal cumulativeGpa,
        @Size(min = 1, max = 8, message = "请填写 1 至 8 门课程") List<String> subjects,
        @Size(min = 30, max = 500, message = "报名自述须为 30 至 500 字") String statementText,
        @NotBlank(message = "请填写更正原因") @Size(max = 500) String reason,
        boolean approve
) {}
