package cn.edu.academic.support.matching.dto;
import jakarta.validation.constraints.NotNull;
public record ManualMatchRequest(@NotNull Long candidateId) {}
