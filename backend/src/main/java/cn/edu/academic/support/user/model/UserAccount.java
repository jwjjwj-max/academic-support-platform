package cn.edu.academic.support.user.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_user")
public class UserAccount {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String loginName;
    private String passwordHash;
    private String accountType;
    private String displayName;
    private String accountStatus;
    private Integer tokenVersion;
    private LocalDateTime lastLoginAt;
    private LocalDateTime passwordResetAt;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

