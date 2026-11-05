package com.campus.activity.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.activity.entity.ActivitySignup;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface ActivitySignupMapper extends BaseMapper<ActivitySignup> {

    @Select("SELECT COUNT(*) FROM activity_signup WHERE activity_id = #{activityId} AND status = #{status}")
    int countByStatus(@Param("activityId") Long activityId, @Param("status") int status);

    /** 候补队列队首 */
    @Select("""
            SELECT * FROM activity_signup
             WHERE activity_id = #{activityId} AND status = 2
             ORDER BY waitlist_seq ASC, create_time ASC
             LIMIT 1
            """)
    ActivitySignup findFirstWaitlist(@Param("activityId") Long activityId);
}
