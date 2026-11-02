package cn.edu.academic.support.material.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
public record PairActionRequest(@NotBlank(message = "请填写原因或验收备注") @Size(max = 500) String reason) {}
