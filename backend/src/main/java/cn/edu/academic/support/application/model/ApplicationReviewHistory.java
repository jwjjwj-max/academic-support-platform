package cn.edu.academic.support.application.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("application_review_history")
public class ApplicationReviewHistory {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long applicationId;
    private String action;
    private String reason;
    private Long operatorId;
    private LocalDateTime createdAt;
}
