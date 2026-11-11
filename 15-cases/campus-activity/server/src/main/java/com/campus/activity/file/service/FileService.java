package com.campus.activity.file.service;

import com.campus.activity.common.BizException;
import com.campus.activity.common.ResultCode;
import com.campus.activity.file.config.FileProperties;
import com.campus.activity.file.dto.FileUploadResult;
import com.campus.activity.file.storage.StorageService;
import com.campus.activity.file.storage.StoredObject;
import com.campus.activity.file.support.FileTypeDetector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 文件业务。
 * 上传前统一校验大小、扩展名、文件头，通过后生成 objectKey 交给存储层，
 * 具体落磁盘还是传 OSS 由 StorageService 的实现决定。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileService {

    /** 业务类型白名单：管理后台活动封面、组织者 logo、小程序头像、通用文件 */
    private static final Set<String> ALLOWED_BIZ_TYPES =
            Set.of("activity-cover", "org-logo", "avatar", "common");

    private static final String DEFAULT_BIZ_TYPE = "common";

    private final StorageService storageService;
    private final FileProperties properties;

    /**
     * 上传单个文件。
     *
     * @param file    前端 multipart 文件
     * @param bizType 业务类型，决定 objectKey 的一级目录，非法值报 PARAM_ERROR
     */
    public FileUploadResult upload(MultipartFile file, String bizType) {
        validate(file);
        String folder = resolveBizType(bizType);
        String objectKey = buildObjectKey(file, folder);
        storageService.write(objectKey, file);
        return toResult(objectKey, file);
    }

    /** 当前生效的存储方式，方便确认配置有没有切过去 */
    public Map<String, Object> storageInfo() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("storage", storageService.type());
        info.put("maxFileSize", properties.getMaxFileSize().toMegabytes() + "MB");
        info.put("allowedExtensions", properties.getAllowedExtensions());
        if ("local".equals(storageService.type())) {
            info.put("baseDir", properties.getLocal().getBaseDir());
            info.put("urlPrefix", properties.getLocal().getUrlPrefix());
        } else {
            info.put("bucket", properties.getOss().getBucketName());
            info.put("dir", properties.getOss().getDir());
        }
        return info;
    }

    /**
     * 上传校验，顺序有讲究：先便宜的后昂贵的。
     * 空文件 → 大小上限 → 扩展名白名单 → 文件头；图片扩展名还必须和真实文件头一致。
     */
    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ResultCode.FILE_EMPTY);
        }
        long maxBytes = properties.getMaxFileSize().toBytes();
        if (file.getSize() > maxBytes) {
            throw new BizException(ResultCode.FILE_TOO_LARGE,
                    "文件不能超过 " + properties.getMaxFileSize().toMegabytes() + "MB");
        }
        String extension = extensionOf(file);
        List<String> allowed = properties.getAllowedExtensions();
        if (allowed == null || !allowed.contains(extension)) {
            throw new BizException(ResultCode.FILE_TYPE_NOT_ALLOWED, "不支持的扩展名：" + extension);
        }
        String realType = FileTypeDetector.detect(file);
        if (FileTypeDetector.isExecutable(realType)) {
            throw new BizException(ResultCode.FILE_TYPE_NOT_ALLOWED, "不允许上传可执行文件");
        }
        // 图片类的扩展名必须和文件头对得上，防止 a.png 实际是个可执行文件或伪装文本
        if (FileTypeDetector.isImageExtension(extension) && !FileTypeDetector.isImageType(realType)) {
            throw new BizException(ResultCode.FILE_TYPE_NOT_ALLOWED,
                    "文件内容与扩展名不符，实际类型：" + realType);
        }
    }

    /** 业务类型校验，只允许约定好的四类，挡住 ../ 之类的路径参数 */
    private String resolveBizType(String bizType) {
        String value = StringUtils.hasText(bizType) ? bizType.trim() : DEFAULT_BIZ_TYPE;
        if (!ALLOWED_BIZ_TYPES.contains(value)) {
            throw new BizException(ResultCode.PARAM_ERROR,
                    "bizType 只能是 " + String.join(" / ", ALLOWED_BIZ_TYPES));
        }
        return value;
    }

    /** objectKey 形如 <bizType>/yyyy/MM/dd/<uuid>.<ext> */
    private String buildObjectKey(MultipartFile file, String bizType) {
        String date = LocalDate.now().format(properties.dateFormatter());
        String uuid = UUID.randomUUID().toString().replace("-", "");
        return bizType + "/" + date + "/" + uuid + "." + extensionOf(file);
    }

    /** 取小写扩展名；浏览器在部分系统上会带上完整路径，只取最后一段 */
    private String extensionOf(MultipartFile file) {
        String originalName = file.getOriginalFilename();
        if (!StringUtils.hasText(originalName) || !originalName.contains(".")) {
            return "";
        }
        String name = originalName.substring(originalName.lastIndexOf('/') + 1);
        return name.substring(name.lastIndexOf('.') + 1).toLowerCase();
    }

    private FileUploadResult toResult(String objectKey, MultipartFile file) {
        return new FileUploadResult(objectKey, file.getOriginalFilename(),
                storageService.url(objectKey), file.getSize(),
                file.getContentType() == null ? StoredObject.DEFAULT_CONTENT_TYPE : file.getContentType(),
                storageService.type());
    }
}
