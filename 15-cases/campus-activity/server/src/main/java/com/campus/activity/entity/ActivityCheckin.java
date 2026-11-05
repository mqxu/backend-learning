package com.campus.activity.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("activity_checkin")
public class ActivityCheckin {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long activityId;
    private Long signupId;
    private Long userId;
    /** 1 扫码 2 定位 3 补签 */
    private Integer checkinType;
    private Long operatorId;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String remark;
    private LocalDateTime checkinTime;
    private LocalDateTime createTime;
}
