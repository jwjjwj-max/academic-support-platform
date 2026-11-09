package cn.edu.academic.support.matching.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record MatchingPolicy(
        @NotNull @Min(2000) @Max(2100) Integer freshmanGrade,
        boolean allowExternalAnalysis,
        @NotNull @Size(min = 3, max = 3) List<@NotNull @Valid Rule> rules
) {
    public enum Kind { PEER, REGULAR, FRESHMAN_SUPPORT }

    public record Rule(
            @NotNull Kind kind,
            boolean enabled,
            @Min(0) @Max(8) int minimumCoverage,
            @Min(0) @Max(200) Integer maximumPairs,
            boolean allowOlderNavigator,
            boolean requireHigherGpa
    ) {}

    public static MatchingPolicy defaults(int freshmanGrade) {
        return new MatchingPolicy(freshmanGrade, false, List.of(
                new Rule(Kind.REGULAR, true, 1, null, true, true),
                new Rule(Kind.FRESHMAN_SUPPORT, true, 1, null, true, false),
                new Rule(Kind.PEER, true, 0, null, false, false)));
    }
}
