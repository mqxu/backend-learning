package com.campus.activity.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("activity")
public class Activity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String title;
    private String coverUrl;
    private Long categoryId;
    private Long orgId;
    private Long creatorId;
    private String summary;
    private String content;

    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime signupStartTime;
    private LocalDateTime signupEndTime;
    private LocalDateTime cancelDeadline;

    private String locationName;
    private String address;
    private BigDecimal latitude;
    private BigDecimal longitude;

    /** 0 表示不限人数 */
    private Integer capacity;
    private Integer signedCount;

    private Integer waitlistEnabled;
    private Integer auditRequired;
    /** 1 扫码 2 扫码+定位 3 仅手动 */
    private Integer checkinType;

    /** 自定义报名字段定义，JSON 字符串 */
    private String formFields;

    /** 见 ActivityStatus */
    private Integer status;
    private String rejectReason;

    private Integer viewCount;
    private Integer favoriteCount;
    private Integer signupCount;
    private LocalDateTime publishTime;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;

    public boolean isUnlimited() {
        return capacity == null || capacity <= 0;
    }

    public boolean hasSeat() {
        return isUnlimited() || (signedCount == null ? 0 : signedCount) < capacity;
    }

    public boolean waitlistOn() {
        return waitlistEnabled != null && waitlistEnabled == 1;
    }

    public boolean needAudit() {
        return auditRequired != null && auditRequired == 1;
    }
}
