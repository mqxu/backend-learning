package com.campus.activity.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("activity_favorite")
public class ActivityFavorite {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long activityId;
    private Long userId;
    private LocalDateTime createTime;
}
