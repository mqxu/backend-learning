package com.campus.activity.dto;

/**
 * 报名提交。formData 为自定义字段答案的 JSON 字符串，可为空。
 */
public record SignupRequest(String formData) {
}
