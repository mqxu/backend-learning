package top.mqxu.file.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import top.mqxu.file.common.Result;
import top.mqxu.file.dto.FileUploadResult;
import top.mqxu.file.service.FileService;
import top.mqxu.file.storage.StoredObject;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 文件接口
 *
 * @author mqxu
 * @date 2026-11-04
 * @description objectKey 里带斜杠，路径变量统一用 {*key} 通配
 */
@Slf4j
@RestController
@RequestMapping("/file")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    /**
     * 单文件上传
     */
    @PostMapping("/upload")
    public Result<FileUploadResult> upload(@RequestParam("file") MultipartFile file,
                                           @RequestParam(required = false) String dir) {
        return Result.ok(fileService.upload(file, dir));
    }

    /**
     * 多文件上传，字段名统一用 files
     */
    @PostMapping("/upload/batch")
    public Result<List<FileUploadResult>> uploadBatch(@RequestParam("files") MultipartFile[] files,
                                                      @RequestParam(required = false) String dir) {
        return Result.ok(fileService.uploadBatch(files, dir));
    }

    /**
     * 图片上传，扩展名和文件头都得是图片
     */
    @PostMapping("/upload/image")
    public Result<FileUploadResult> uploadImage(@RequestParam("file") MultipartFile file,
                                                @RequestParam(required = false) String dir) {
        return Result.ok(fileService.uploadImage(file, dir));
    }

    /**
     * 下载：浏览器弹保存框
     */
    @GetMapping("/download/{*key}")
    public ResponseEntity<StreamingResponseBody> download(@PathVariable String key,
                                                          @RequestParam(required = false) String name) {
        String objectKey = normalizeKey(key);
        StoredObject object = fileService.read(objectKey);
        return stream(object, StringUtils.hasText(name) ? name : fileNameOf(objectKey), true);
    }

    /**
     * 预览：浏览器内直接打开，图片、PDF 用这个
     */
    @GetMapping("/preview/{*key}")
    public ResponseEntity<StreamingResponseBody> preview(@PathVariable String key) {
        String objectKey = normalizeKey(key);
        StoredObject object = fileService.read(objectKey);
        return stream(object, fileNameOf(objectKey), false);
    }

    /**
     * 取访问地址，OSS 模式下给的是带签名的临时地址
     */
    @GetMapping("/url/{*key}")
    public Result<String> url(@PathVariable String key,
                              @RequestParam(defaultValue = "10") long expireMinutes) {
        if (expireMinutes <= 0 || expireMinutes > 1440) {
            return Result.fail(400, "有效期只能是 1 到 1440 分钟");
        }
        return Result.ok(fileService.presignedUrl(normalizeKey(key), Duration.ofMinutes(expireMinutes)));
    }

    @DeleteMapping("/{*key}")
    public Result<Void> delete(@PathVariable String key) {
        fileService.delete(normalizeKey(key));
        return Result.ok();
    }

    /**
     * 当前生效的存储方式，方便确认配置有没有切过去
     */
    @GetMapping("/storage")
    public Result<Map<String, Object>> storage() {
        return Result.ok(fileService.storageInfo());
    }

    /**
     * {*key} 会把开头的斜杠一起捕获进来
     */
    private String normalizeKey(String key) {
        String objectKey = key.startsWith("/") ? key.substring(1) : key;
        if (!StringUtils.hasText(objectKey)) {
            throw new IllegalArgumentException("文件 key 不能为空");
        }
        return objectKey;
    }

    private String fileNameOf(String objectKey) {
        return objectKey.substring(objectKey.lastIndexOf('/') + 1);
    }

    private ResponseEntity<StreamingResponseBody> stream(StoredObject object, String fileName, boolean attachment) {
        ContentDisposition disposition = (attachment ? ContentDisposition.attachment() : ContentDisposition.inline())
                .filename(fileName, StandardCharsets.UTF_8)
                .build();
        StreamingResponseBody body = out -> {
            // 用完必须关，否则本地文件句柄和 OSS 连接都会泄漏
            try (object) {
                object.stream().transferTo(out);
            }
        };
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.parseMediaType(object.contentType()))
                .contentLength(object.size())
                .body(body);
    }
}
