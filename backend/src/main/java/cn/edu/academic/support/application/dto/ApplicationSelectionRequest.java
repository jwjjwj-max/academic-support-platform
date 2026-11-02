package cn.edu.academic.support.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ApplicationSelectionRequest(
        @NotBlank @Pattern(regexp = "MATCHING_POOL|WAITLIST|NOT_SELECTED|REMOVED_FROM_POOL") String selectionStatus
) {}
