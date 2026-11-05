package com.campus.activity.dto;

import jakarta.validation.constraints.NotBlank;

public record BindRequest(
        @NotBlank(message = "请填写学号") String studentNo,
        @NotBlank(message = "请填写姓名") String realName,
        String college,
        String grade,
        String major
) {
}
