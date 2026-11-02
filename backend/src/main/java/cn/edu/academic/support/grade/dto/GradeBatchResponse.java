package cn.edu.academic.support.grade.dto;
import java.time.LocalDateTime;
import java.util.*;
public record GradeBatchResponse(Long id,Long periodId,String periodName,String stage,String label,String originalFilename,
 String status,int totalRows,int participantRows,int errorRows,Map<String,String> fieldMapping,LocalDateTime createdAt,LocalDateTime completedAt) {}
