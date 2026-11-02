package cn.edu.academic.support.moral.dto;
import java.time.LocalDateTime;
public record MoralFileResponse(Long id,Long periodId,String periodName,String title,String originalFilename,String mimeType,
 long fileSize,String status,LocalDateTime createdAt,LocalDateTime publishedAt,LocalDateTime withdrawnAt,String downloadUrl) {}
