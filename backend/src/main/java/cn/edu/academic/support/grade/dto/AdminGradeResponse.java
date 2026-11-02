package cn.edu.academic.support.grade.dto;
import java.math.BigDecimal;
import java.time.LocalDateTime;
public record AdminGradeResponse(Long id,Long periodId,String periodName,Long studentUserId,String studentNo,String name,
 String role,String stage,BigDecimal termGpa,Integer termGpaRank,Integer professionalStudentCount,BigDecimal professionalTopPercent,
 String college,String grade,String major,String className,
 Long importBatchId,LocalDateTime updatedAt) {}
