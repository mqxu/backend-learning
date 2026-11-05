package com.campus.activity.dto;

import jakarta.validation.constraints.NotNull;

public record ActivityAuditRequest(
        @NotNull(message = "请选择审核结果") Boolean pass,
        String reason
) {
}
