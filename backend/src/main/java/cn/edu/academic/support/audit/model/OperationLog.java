package cn.edu.academic.support.audit.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("operation_log")
public class OperationLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long actorId;
    private String actorLogin;
    private String action;
    private String targetType;
    private String targetId;
    private String beforeSnapshot;
    private String afterSnapshot;
    private String reason;
    private String ipAddress;
    private String userAgent;
    private LocalDateTime createdAt;
}

