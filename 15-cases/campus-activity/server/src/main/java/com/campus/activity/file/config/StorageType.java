package com.campus.activity.file.config;

/**
 * 文件存储方式。
 * 由 campus.file.storage-type 决定，决定装配哪一套 StorageService 与静态资源映射。
 */
public enum StorageType {

    /** 本地磁盘 */
    LOCAL,

    /** 阿里云 OSS */
    OSS
}
