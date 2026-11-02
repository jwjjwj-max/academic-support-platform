package cn.edu.academic.support.application.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record ApplicationResponse(
        Long id, Long periodId, String periodName, Long studentUserId, String studentNo, String realName,
        String major, Integer grade, String participantRole, BigDecimal cumulativeGpa, List<String> subjects,
        String statementText, List<String> learningStyles, List<String> personalities,
        String applicationStatus, String selectionStatus, String rejectionReason,
        LocalDateTime submittedAt, LocalDateTime reviewedAt
) {}
