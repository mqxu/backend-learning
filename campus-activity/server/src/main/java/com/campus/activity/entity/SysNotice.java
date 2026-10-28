package com.campus.activity.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_notice")
public class SysNotice {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;
    /** SIGNUP / AUDIT / REMIND / CANCEL / WAITLIST */
    private String type;
    private String title;
    private String content;
    private Long bizId;
    private Integer readFlag;
    private LocalDateTime createTime;
}
