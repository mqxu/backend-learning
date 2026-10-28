package com.campus.activity.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("org_member")
public class OrgMember {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long orgId;
    private Long userId;
    private String title;
    private LocalDateTime createTime;
}
