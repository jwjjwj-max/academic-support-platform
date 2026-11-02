package cn.edu.academic.support.grade.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data @TableName("participant_term_grade")
public class ParticipantTermGrade {
    @TableId(type = IdType.AUTO) private Long id;
    private Long periodId; private Long studentUserId; private Long applicationId; private String stage;
    private Long importBatchId; private BigDecimal termGpa; private Integer termGpaRank; private Integer professionalStudentCount;
    private String studentNoSnapshot; private String nameSnapshot; private String collegeSnapshot;
    private String gradeSnapshot; private String majorSnapshot; private String classNameSnapshot;
    private LocalDateTime createdAt; private LocalDateTime updatedAt;
}
