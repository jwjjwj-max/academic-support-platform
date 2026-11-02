package cn.edu.academic.support.material.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
public record ReflectionSubmitRequest(@NotBlank(message = "活动感悟不能为空") @Size(max = 2000) String content) {}
