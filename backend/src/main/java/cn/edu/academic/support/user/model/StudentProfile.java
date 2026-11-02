package cn.edu.academic.support.user.model;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("student_profile")
public class StudentProfile {
    @TableId
    private Long userId;
    private String studentNo;
    private String realName;
    private String college;
    private String major;
    private Integer grade;
    private String wechat;
    private String phone;
    private String reviewStatus;
    private String rejectionReason;
    private LocalDateTime submittedAt;
    private Long reviewedBy;
    private LocalDateTime reviewedAt;
    private Integer profileVersion;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

