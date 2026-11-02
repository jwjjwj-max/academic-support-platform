package cn.edu.academic.support.notification.model;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
@Data @TableName("notification")
public class Notification {
    @TableId(type = IdType.AUTO) private Long id;
    private Long periodId;
    private String title;
    private String content;
    private String audienceType;
    private String audienceLabel;
    private Long createdBy;
    private LocalDateTime createdAt;
}
