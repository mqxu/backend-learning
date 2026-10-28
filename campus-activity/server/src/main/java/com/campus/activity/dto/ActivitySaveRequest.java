package com.campus.activity.dto;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

/**
 * 组织者创建或编辑活动。时间字段统一用字符串，格式见 DateTimes。
 */
public record ActivitySaveRequest(
        @NotBlank(message = "请填写活动标题") String title,
        String coverUrl,
        Long categoryId,
        String summary,
        String content,
        String startTime,
        String endTime,
        String signupStartTime,
        String signupEndTime,
        String cancelDeadline,
        @NotBlank(message = "请填写活动地点") String locationName,
        String address,
        BigDecimal latitude,
        BigDecimal longitude,
        Integer capacity,
        Integer waitlistEnabled,
        Integer auditRequired,
        Integer checkinType,
        String formFields,
        String orgId
) {
}
