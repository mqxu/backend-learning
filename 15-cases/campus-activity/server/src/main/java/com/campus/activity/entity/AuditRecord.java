package com.campus.activity.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("audit_record")
public class AuditRecord {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** ACTIVITY / SIGNUP / CHECKIN / USER */
    private String bizType;
    private Long bizId;
    /** APPROVE / REJECT / CANCEL / MANUAL_CHECKIN / ROLE_CHANGE */
    private String action;
    private Long operatorId;
    private String operatorName;
    private String beforeStatus;
    private String afterStatus;
    private String remark;
    private LocalDateTime createTime;
}
