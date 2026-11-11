package cn.edu.academic.support.matching.controller;

import cn.edu.academic.support.common.api.ApiResponse;
import cn.edu.academic.support.matching.dto.MatchingEvidence;
import cn.edu.academic.support.matching.dto.MatchingPolicy;
import cn.edu.academic.support.matching.service.DeepSeekMatchingClient;
import cn.edu.academic.support.matching.service.MatchingService;
import cn.edu.academic.support.matching.service.MatchingWorkspace;
import cn.edu.academic.support.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/matching")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class MatchingWorkspaceController {
    private final MatchingWorkspace workspace;
    private final MatchingService matching;
    private final DeepSeekMatchingClient deepSeek;

    public record PolicyRequest(@NotNull @Min(0) Integer revision, @NotNull @Valid MatchingPolicy policy) {}
    public record EvidenceRequest(@NotNull @Min(0) Integer applicationRevision, @NotNull @Min(0) Integer revision,
                                  @NotNull @Valid MatchingEvidence facts) {}
    public record AnalysisRequest(@NotNull @Min(0) Integer applicationRevision,
                                  @NotBlank @Size(max = 1500) String confirmedPreview) {}
    public record ExclusionRequest(@NotNull Long firstApplicationId, @NotNull Long secondApplicationId,
                                   boolean excluded, @NotBlank @Size(max = 500) String reason) {}
    public record PreviewRequest(@NotNull @Min(0) Integer revision, Long replaceRoundId, @Min(0) Integer replaceRevision) {}
    public record AdjustmentRequest(@NotNull @Min(0) Integer revision, Long removeId, Long selectId,
                                    @Min(1) Long applicationId, @Min(1) Long partnerId,
                                    @NotBlank @Size(max = 500) String reason) {}
    public record DiscardRequest(@Min(0) Integer revision, @NotBlank @Size(max = 500) String reason) {}
    public record KeyRequest(@NotBlank @Size(max = 256) String apiKey) {}

    @GetMapping("/periods/{periodId}/workspace")
    public ApiResponse<MatchingWorkspace.WorkspaceView> view(@PathVariable Long periodId) {
        return ApiResponse.success(workspace.view(periodId));
    }

    @PutMapping("/periods/{periodId}/workspace/policy")
    public ApiResponse<MatchingWorkspace.PolicyVersion> policy(@AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable Long periodId, @Valid @RequestBody PolicyRequest body, HttpServletRequest request) {
        return ApiResponse.success(workspace.savePolicy(actor, periodId, body.revision(), body.policy(), request));
    }

    @PostMapping("/periods/{periodId}/workspace/students/{applicationId}/analyze")
    public ApiResponse<MatchingWorkspace.EvidenceRecord> analyze(@AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable Long periodId, @PathVariable Long applicationId, @Valid @RequestBody AnalysisRequest body,
            HttpServletRequest request) {
        return ApiResponse.success(workspace.analyze(actor, periodId, applicationId, body.applicationRevision(),
                body.confirmedPreview(), request));
    }

    @PutMapping("/periods/{periodId}/workspace/students/{applicationId}/evidence")
    public ApiResponse<MatchingWorkspace.EvidenceRecord> evidence(@AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable Long periodId, @PathVariable Long applicationId, @Valid @RequestBody EvidenceRequest body,
            HttpServletRequest request) {
        return ApiResponse.success(workspace.reviewEvidence(actor, periodId, applicationId, body.applicationRevision(),
                body.revision(), body.facts(), request));
    }

    @PostMapping("/periods/{periodId}/workspace/exclusions")
    public ApiResponse<Void> exclusion(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable Long periodId,
            @Valid @RequestBody ExclusionRequest body, HttpServletRequest request) {
        workspace.setExclusion(actor, periodId, body.firstApplicationId(), body.secondApplicationId(), body.excluded(),
                body.reason(), request);
        return ApiResponse.success();
    }

    @GetMapping("/periods/{periodId}/workspace/rounds")
    public ApiResponse<List<MatchingService.RoundSummary>> rounds(@PathVariable Long periodId) {
        return ApiResponse.success(matching.rounds(periodId));
    }

    @GetMapping("/periods/{periodId}/workspace/rounds/{roundId}")
    public ApiResponse<MatchingService.RoundView> round(@PathVariable Long periodId, @PathVariable Long roundId) {
        return ApiResponse.success(matching.roundView(periodId, roundId));
    }

    @PostMapping("/periods/{periodId}/workspace/preview")
    public ApiResponse<MatchingService.RoundView> preview(@AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable Long periodId, @Valid @RequestBody PreviewRequest body, HttpServletRequest request) {
        return ApiResponse.success(matching.preview(actor, periodId, body.revision(), body.replaceRoundId(), body.replaceRevision(), request));
    }

    @GetMapping("/rounds/{roundId}/manual-options")
    public ApiResponse<List<MatchingService.ManualOption>> manualOptions(@PathVariable Long roundId,
            @RequestParam int revision, @RequestParam Long applicationId) {
        return ApiResponse.success(matching.manualOptions(roundId, revision, applicationId));
    }

    @PostMapping("/rounds/{roundId}/adjust")
    public ApiResponse<MatchingService.RoundView> adjust(@AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable Long roundId, @Valid @RequestBody AdjustmentRequest body, HttpServletRequest request) {
        return ApiResponse.success(matching.adjust(actor, roundId, body.revision(), body.removeId(), body.selectId(),
                body.applicationId(), body.partnerId(), body.reason(), request));
    }

    @PostMapping("/rounds/{roundId}/discard")
    public ApiResponse<Void> discard(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable Long roundId,
            @Valid @RequestBody DiscardRequest body, HttpServletRequest request) {
        matching.discard(actor, roundId, body.revision(), body.reason(), request);
        return ApiResponse.success();
    }

    @GetMapping("/deepseek")
    public ApiResponse<DeepSeekMatchingClient.Status> provider() {
        return ApiResponse.success(deepSeek.status());
    }

    @PutMapping("/deepseek")
    public ApiResponse<DeepSeekMatchingClient.Status> configure(@AuthenticationPrincipal AuthenticatedUser actor,
            @Valid @RequestBody KeyRequest body, HttpServletRequest request) {
        return ApiResponse.success(deepSeek.save(actor, body.apiKey(), request));
    }

    @DeleteMapping("/deepseek")
    public ApiResponse<DeepSeekMatchingClient.Status> clear(@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest request) {
        return ApiResponse.success(deepSeek.clear(actor, request));
    }

    @PostMapping("/deepseek/test")
    public ApiResponse<Void> test() {
        deepSeek.test();
        return ApiResponse.success();
    }
}
