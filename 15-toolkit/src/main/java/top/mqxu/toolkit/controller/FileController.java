package top.mqxu.toolkit.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import top.mqxu.toolkit.common.Result;
import top.mqxu.toolkit.dto.FileCheckResult;
import top.mqxu.toolkit.service.FileService;

/**
 * 案例 7：上传安全
 *
 * @author mqxu
 * @date 2026-10-21
 */
@RestController
@RequestMapping("/toolkit/file")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    /**
     * 检查真实文件类型，改扩展名的文件会在这里露出来
     */
    @PostMapping("/check")
    public Result<FileCheckResult> check(@RequestParam MultipartFile file) {
        return Result.ok(fileService.check(file));
    }

    /**
     * 图片等比压缩，返回处理后的图片
     */
    @PostMapping(value = "/compress", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> compress(
            @RequestParam MultipartFile file,
            @RequestParam(defaultValue = "800") int maxWidth) {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(fileService.compress(file, maxWidth));
    }

    /**
     * 多个文件打包下载
     */
    @PostMapping(value = "/zip", produces = "application/zip")
    public ResponseEntity<byte[]> zip(@RequestParam MultipartFile[] files) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header("Content-Disposition", "attachment; filename=bundle.zip")
                .body(fileService.zip(files));
    }
}
