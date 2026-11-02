package cn.edu.academic.support.matching.controller;

import cn.edu.academic.support.common.api.ApiResponse;
import cn.edu.academic.support.matching.dto.MatchCandidateResponse;
import cn.edu.academic.support.matching.dto.MatchDecisionRequest;
import cn.edu.academic.support.matching.service.MatchingService;
import cn.edu.academic.support.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class MatchingController {
    private final MatchingService service;

    @PostMapping("/admin/matching/periods/{periodId}/rounds")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<MatchCandidateResponse>> generate(@AuthenticationPrincipal AuthenticatedUser actor,
                                                              @PathVariable Long periodId,
                                                              HttpServletRequest request) {
        return ApiResponse.success(service.generate(actor, periodId, request));
    }

    @PostMapping("/admin/matching/periods/{periodId}/rematch")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<MatchCandidateResponse>> rematch(@AuthenticationPrincipal AuthenticatedUser actor,
                                                             @PathVariable Long periodId,
                                                             HttpServletRequest request) {
        return ApiResponse.success(service.rematch(actor, periodId, request));
    }

    @GetMapping("/admin/matching/periods/{periodId}/candidates")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<MatchCandidateResponse>> list(@PathVariable Long periodId,
                                                          @RequestParam(required = false) Long roundId) {
        return ApiResponse.success(service.listAdmin(periodId, roundId));
    }

    @PostMapping("/admin/matching/candidates/{candidateId}/select")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<MatchCandidateResponse> select(@AuthenticationPrincipal AuthenticatedUser actor,
                                                      @PathVariable Long candidateId,
                                                      HttpServletRequest request) {
        return ApiResponse.success(service.select(actor, candidateId, request));
    }

    @PostMapping("/admin/matching/rounds/{roundId}/publish")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<MatchCandidateResponse>> publish(@AuthenticationPrincipal AuthenticatedUser actor,
                                                             @PathVariable Long roundId,
                                                             HttpServletRequest request) {
        return ApiResponse.success(service.publish(actor, roundId, request));
    }

    @GetMapping("/matching/mine")
    @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<List<MatchCandidateResponse>> mine(@AuthenticationPrincipal AuthenticatedUser actor) {
        return ApiResponse.success(service.mine(actor.id()));
    }

    @PostMapping("/matching/candidates/{candidateId}/decision")
    @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<MatchCandidateResponse> decide(@AuthenticationPrincipal AuthenticatedUser actor,
                                                      @PathVariable Long candidateId,
                                                      @Valid @RequestBody MatchDecisionRequest decision,
                                                      HttpServletRequest request) {
        return ApiResponse.success(service.decide(actor, candidateId, decision, request));
    }
}
