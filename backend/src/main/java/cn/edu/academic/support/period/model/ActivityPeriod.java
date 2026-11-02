package cn.edu.academic.support.period.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("activity_period")
public class ActivityPeriod {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String shortName;
    private String status;
    private LocalDateTime registrationStart;
    private LocalDateTime registrationEnd;
    private LocalDateTime confirmationDeadline;
    private LocalDateTime trackingStart;
    private LocalDateTime trackingEnd;
    private LocalDateTime acceptanceStart;
    private LocalDateTime acceptanceEnd;
    private Integer photoMin;
    private Integer photoMax;
    private Integer subjectWeight;
    private Integer majorWeight;
    private Integer gradeWeight;
    private Integer learningStyleWeight;
    private Integer personalityWeight;
    private Integer gpaGapWeight;
    private LocalDateTime archivedAt;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

