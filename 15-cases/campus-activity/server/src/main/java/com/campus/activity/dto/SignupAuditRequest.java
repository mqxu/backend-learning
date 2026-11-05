package com.campus.activity.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record SignupAuditRequest(
        @NotNull(message = "请选择报名记录") List<Long> signupIds,
        @NotNull(message = "请选择通过或驳回") Boolean pass,
        String reason
) {
}
