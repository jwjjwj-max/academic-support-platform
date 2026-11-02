package cn.edu.academic.support.moral.model;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
@Data @TableName("moral_score_file")
public class MoralScoreFile {
 @TableId(type=IdType.AUTO) private Long id; private Long periodId; private String title; private String storageKey;
 private String originalFilename; private String mimeType; private Long fileSize; private String status; private Long uploadedBy;
 private Long publishedBy; private LocalDateTime publishedAt; private LocalDateTime withdrawnAt; private Long withdrawnBy;
 @TableLogic private Boolean deleted; private Long deletedBy; private LocalDateTime deletedAt; private LocalDateTime createdAt;
}
