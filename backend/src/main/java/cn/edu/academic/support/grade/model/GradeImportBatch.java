package cn.edu.academic.support.grade.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data @TableName("grade_import_batch")
public class GradeImportBatch {
    @TableId(type = IdType.AUTO) private Long id;
    private Long periodId; private String stage; private String label; private String storageKey;
    private String originalFilename; private String mimeType; private Long fileSize; private String fieldMapping;
    private String status; private Integer totalRows; private Integer participantRows; private Integer errorRows;
    private Long uploadedBy; private LocalDateTime createdAt; private LocalDateTime completedAt;
}
