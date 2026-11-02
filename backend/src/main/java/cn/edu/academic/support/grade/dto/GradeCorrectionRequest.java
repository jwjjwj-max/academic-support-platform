package cn.edu.academic.support.grade.dto;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
public record GradeCorrectionRequest(@NotNull @DecimalMin("0.00") @DecimalMax("5.00") BigDecimal termGpa,
                                     @Positive Integer termGpaRank,
                                     @NotBlank @Size(max=500) String reason) {}
