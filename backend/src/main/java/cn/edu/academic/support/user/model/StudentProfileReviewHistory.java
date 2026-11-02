package cn.edu.academic.support.user.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("student_profile_review_history")
public class StudentProfileReviewHistory {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long studentUserId;
    private Integer profileVersion;
    private String action;
    private String reason;
    private String profileSnapshot;
    private Long operatorId;
    private LocalDateTime createdAt;
}

