package cn.edu.academic.support.grade.dto;
import java.math.BigDecimal;
public record StudentGradeResultResponse(Long periodId,String periodName,String role,BigDecimal beforeTermGpa,
 BigDecimal afterTermGpa,BigDecimal change) {}
