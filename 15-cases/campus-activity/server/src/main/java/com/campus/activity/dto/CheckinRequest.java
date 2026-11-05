package com.campus.activity.dto;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

public record CheckinRequest(
        @NotBlank(message = "签到码不能为空") String token,
        BigDecimal latitude,
        BigDecimal longitude
) {
}
