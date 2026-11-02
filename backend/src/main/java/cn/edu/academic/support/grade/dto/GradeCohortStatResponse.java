package cn.edu.academic.support.grade.dto;

import java.time.LocalDateTime;

public record GradeCohortStatResponse(Long id,Long periodId,String periodName,String stage,String grade,String major,
                                      int studentCount,Long importBatchId,LocalDateTime importedAt) {}
