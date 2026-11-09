package cn.edu.academic.support.matching.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record MatchingEvidence(
        @NotNull @Size(max = 8) List<@NotNull @Valid Topic> needs,
        @NotNull @Size(max = 8) List<@NotNull @Valid Topic> offers
) {
    public record Topic(
            @NotBlank @Size(max = 80) String course,
            @Size(max = 80) String knowledgePoint,
            @NotBlank @Size(max = 200) String quote
    ) {}

    public static MatchingEvidence empty() {
        return new MatchingEvidence(List.of(), List.of());
    }
}
