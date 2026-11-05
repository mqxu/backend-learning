package com.campus.activity.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.activity.entity.Activity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface ActivityMapper extends BaseMapper<Activity> {

    /**
     * 条件更新占名额：影响行数为 0 表示名额已满，不依赖先查询再判断。
     */
    @Update("""
            UPDATE activity
               SET signed_count = signed_count + 1
             WHERE id = #{activityId}
               AND deleted = 0
               AND (capacity = 0 OR signed_count < capacity)
            """)
    int occupySeat(@Param("activityId") Long activityId);

    /** 释放名额，signed_count 不允许为负 */
    @Update("""
            UPDATE activity
               SET signed_count = CASE WHEN signed_count > 0 THEN signed_count - 1 ELSE 0 END
             WHERE id = #{activityId}
               AND deleted = 0
            """)
    int releaseSeat(@Param("activityId") Long activityId);

    @Update("UPDATE activity SET view_count = view_count + 1 WHERE id = #{activityId} AND deleted = 0")
    int increaseView(@Param("activityId") Long activityId);

    @Update("""
            UPDATE activity
               SET favorite_count = CASE WHEN favorite_count > 0 THEN favorite_count - 1 ELSE 0 END
             WHERE id = #{activityId} AND deleted = 0
            """)
    int decreaseFavorite(@Param("activityId") Long activityId);

    @Update("UPDATE activity SET favorite_count = favorite_count + 1 WHERE id = #{activityId} AND deleted = 0")
    int increaseFavorite(@Param("activityId") Long activityId);

    @Select("SELECT COALESCE(MAX(waitlist_seq), 0) FROM activity_signup WHERE activity_id = #{activityId} AND status = 2")
    int maxWaitlistSeq(@Param("activityId") Long activityId);
}
