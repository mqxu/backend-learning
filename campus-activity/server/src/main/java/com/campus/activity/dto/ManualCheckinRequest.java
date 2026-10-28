package com.campus.activity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ManualCheckinRequest(
        @NotNull(message = "请选择报名记录") Long signupId,
        @NotBlank(message = "请填写补签原因") String remark
) {
}
