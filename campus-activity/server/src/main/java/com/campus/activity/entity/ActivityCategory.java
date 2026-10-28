package com.campus.activity.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("activity_category")
public class ActivityCategory {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String name;
    private String code;
    private String icon;
    private Integer sort;
    private Integer status;
}
