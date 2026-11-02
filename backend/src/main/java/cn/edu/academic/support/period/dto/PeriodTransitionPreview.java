package cn.edu.academic.support.period.dto;

import java.util.List;
import java.util.Map;

public record PeriodTransitionPreview(
        Long periodId,
        String currentStatus,
        String nextStatus,
        boolean allowed,
        boolean requiresConfirmation,
        List<String> warnings,
        List<String> blockers,
        Map<String, Long> impact
) {
}
