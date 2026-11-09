package cn.edu.academic.support.period.mapper;

import cn.edu.academic.support.period.model.ActivityPeriod;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Select;

public interface ActivityPeriodMapper extends BaseMapper<ActivityPeriod> {
    @Select("SELECT * FROM activity_period WHERE id = #{id} FOR UPDATE")
    ActivityPeriod lockById(Long id);
}

