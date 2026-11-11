package com.campus.activity.file.dto;

/**
 * 文件上传结果。
 *
 * @param objectKey    对象 key，形如 activity-cover/2026/11/10/xxx.png
 * @param originalName 原始文件名
 * @param url          可直接放进 img 标签的访问地址
 * @param size         字节数
 * @param contentType  内容类型
 * @param storage      实际存储方式：local / oss
 */
public record FileUploadResult(String objectKey, String originalName, String url,
                               long size, String contentType, String storage) {
}
