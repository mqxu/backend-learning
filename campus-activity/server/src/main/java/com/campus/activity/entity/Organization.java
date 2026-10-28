package com.campus.activity.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("organization")
public class Organization {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String name;
    /** 1 社团 2 院系 3 部门 */
    private Integer type;
    private String logoUrl;
    private String description;
    private String contact;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
