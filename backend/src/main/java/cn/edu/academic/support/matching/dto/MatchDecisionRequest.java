package cn.edu.academic.support.matching.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
public record MatchDecisionRequest(@NotBlank @Pattern(regexp="CONFIRMED|REJECTED") String decision,
                                   @Size(max=500) String reason) {}
