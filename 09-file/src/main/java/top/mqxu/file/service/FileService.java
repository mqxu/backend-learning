package top.mqxu.file.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import top.mqxu.file.common.BusinessException;
import top.mqxu.file.common.ErrorCode;
import top.mqxu.file.config.FileProperties;
import top.mqxu.file.dto.FileUploadResult;
import top.mqxu.file.storage.StorageService;
import top.mqxu.file.storage.StoredObject;
import top.mqxu.file.support.FileTypeDetector;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 文件业务
 *
 * @author mqxu
 * @date 2026-11-04
 * @description 上传前统一校验大小、扩展名、文件头，通过后生成 key 交给存储层，不关心存在磁盘还是 OSS
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileService {

    /**
     * 子目录只允许字母、数字、下划线、连字符，挡住 ../../ 这类参数
     */
    private static final Pattern DIR_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{1,32}$");

    private static final String DEFAULT_DIR = "misc";

    private final StorageService storageService;
    private final FileProperties properties;

    public FileUploadResult upload(MultipartFile file, String dir) {
        validate(file);
        String objectKey = buildObjectKey(file, dir);
        storageService.write(objectKey, file);
        return toResult(objectKey, file);
    }

    public FileUploadResult uploadImage(MultipartFile file, String dir) {
        validate(file);
        String extension = extensionOf(file);
        if (!FileTypeDetector.isImageExtension(extension)
                || !FileTypeDetector.isImageType(FileTypeDetector.detect(file))) {
            throw new BusinessException(ErrorCode.FILE_TYPE_NOT_ALLOWED, "只允许上传图片");
        }
        String objectKey = buildObjectKey(file, dir);
        storageService.write(objectKey, file);
        return toResult(objectKey, file);
    }

    /**
     * 批量上传，中途有一个不合格就把已经写进去的清掉，避免留下半截数据
     */
    public List<FileUploadResult> uploadBatch(MultipartFile[] files, String dir) {
        if (files == null || files.length == 0) {
            throw new BusinessException(ErrorCode.FILE_EMPTY, "请至少选择一个文件");
        }
        List<FileUploadResult> results = new ArrayList<>(files.length);
        try {
            for (MultipartFile file : files) {
                results.add(upload(file, dir));
            }
            return results;
        } catch (RuntimeException e) {
            results.forEach(r -> storageService.delete(r.objectKey()));
            throw e;
        }
    }

    public StoredObject read(String objectKey) {
        return storageService.read(objectKey);
    }

    public void delete(String objectKey) {
        storageService.delete(objectKey);
    }

    public String url(String objectKey) {
        return storageService.url(objectKey);
    }

    /**
     * OSS 会返回带签名和过期时间的地址，本地存储原样返回访问前缀
     */
    public String presignedUrl(String objectKey, Duration ttl) {
        if (!storageService.exists(objectKey)) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
        }
        return storageService.presignedUrl(objectKey, ttl);
    }

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

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.FILE_EMPTY);
        }
        if (file.getSize() > properties.getMaxFileSize().toBytes()) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE,
                    "文件不能超过 " + properties.getMaxFileSize().toMegabytes() + "MB");
        }
        String extension = extensionOf(file);
        if (!properties.getAllowedExtensions().contains(extension)) {
            throw new BusinessException(ErrorCode.FILE_TYPE_NOT_ALLOWED, "不支持的扩展名：" + extension);
        }
        String realType = FileTypeDetector.detect(file);
        if (FileTypeDetector.isExecutable(realType)) {
            throw new BusinessException(ErrorCode.FILE_TYPE_NOT_ALLOWED, "不允许上传可执行文件");
        }
        // 图片类的扩展名必须和文件头对得上，防止 a.png 实际是个 exe
        if (FileTypeDetector.isImageExtension(extension) && !FileTypeDetector.isImageType(realType)) {
            throw new BusinessException(ErrorCode.FILE_TYPE_NOT_ALLOWED,
                    "文件内容与扩展名不符，实际类型：" + realType);
        }
    }

    private String buildObjectKey(MultipartFile file, String dir) {
        String folder = StringUtils.hasText(dir) ? dir.trim() : DEFAULT_DIR;
        if (!DIR_PATTERN.matcher(folder).matches()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "目录只能是字母、数字、下划线或连字符");
        }
        DateTimeFormatter formatter = properties.dateFormatter();
        String date = LocalDate.now().format(formatter);
        return folder + "/" + date + "/" + UUID.randomUUID().toString().replace("-", "") + "." + extensionOf(file);
    }

    private String extensionOf(MultipartFile file) {
        String originalName = file.getOriginalFilename();
        if (!StringUtils.hasText(originalName) || !originalName.contains(".")) {
            return "";
        }
        // 浏览器在某些系统上会带上完整路径，只取最后一段
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
