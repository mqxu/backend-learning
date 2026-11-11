package com.campus.activity.file.controller;

import com.campus.activity.common.Result;
import com.campus.activity.file.dto.FileUploadResult;
import com.campus.activity.file.service.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 文件接口。
 * 管理后台上传活动封面、组织者上传 logo、小程序上传头像都走同一个上传接口，
 * 通过 bizType 区分对象 key 的目录；需要登录，鉴权由 Spring Security 统一接管。
 */
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    /**
     * 上传单文件，multipart 字段名固定为 file。
     *
     * @param file    上传的文件
     * @param bizType 业务类型：activity-cover / org-logo / avatar / common，默认 common
     */
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/upload")
    public Result<FileUploadResult> upload(@RequestParam(value = "file", required = false) MultipartFile file,
                                           @RequestParam(value = "bizType", required = false) String bizType) {
        return Result.ok(fileService.upload(file, bizType));
    }

    /** 当前生效的存储方式与约束，方便前端确认配置有没有切过去 */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/storage")
    public Result<Map<String, Object>> storage() {
        return Result.ok(fileService.storageInfo());
    }
}
