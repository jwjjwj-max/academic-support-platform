package cn.edu.academic.support.user.mapper;

import cn.edu.academic.support.user.model.StudentProfile;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Select;

public interface StudentProfileMapper extends BaseMapper<StudentProfile> {
    @Select("SELECT * FROM student_profile WHERE user_id = #{userId} FOR UPDATE")
    StudentProfile lockById(Long userId);
}

