package cn.edu.academic.support.matching.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record MatchCandidateResponse(
        Long id, Long roundId, Long periodId, String roundType, String status, BigDecimal totalScore,
        Map<String, Object> scoreDetail, List<String> matchedSubjects,
        Long navigatorApplicationId, Long navigatorUserId, String navigatorName, String navigatorMajor, Integer navigatorGrade,
        Long climberApplicationId, Long climberUserId, String climberName, String climberMajor, Integer climberGrade,
        String navigatorDecision, String climberDecision, String rejectionReason,
        String partnerWechat, String partnerPhone, Long supportPairId,
        String matchKind, Map<String, Object> coverage, Boolean manuallySelected
) {}
