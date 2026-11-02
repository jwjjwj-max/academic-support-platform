package cn.edu.academic.support.application.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("application_profile_snapshot")
public class ApplicationProfileSnapshot {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long applicationId;
    private Long studentUserId;
    private Integer profileVersion;
    private String studentNo;
    private String realName;
    private String college;
    private String major;
    private Integer grade;
    private String wechat;
    private String phone;
    private LocalDateTime snapshotAt;
}
