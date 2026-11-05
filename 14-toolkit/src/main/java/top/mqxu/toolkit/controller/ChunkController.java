package top.mqxu.toolkit.controller;

import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import top.mqxu.toolkit.common.Result;
import top.mqxu.toolkit.dto.ChunkInitRequest;
import top.mqxu.toolkit.dto.ChunkInitResponse;
import top.mqxu.toolkit.dto.ChunkMergeResponse;
import top.mqxu.toolkit.dto.ChunkStatusResponse;
import top.mqxu.toolkit.service.ChunkService;

import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 案例 6：分片上传
 *
 * @author mqxu
 * @date 2026-10-21
 */
@RestController
@RequestMapping("/toolkit/chunk")
@RequiredArgsConstructor
public class ChunkController {

    private final ChunkService chunkService;

    @PostMapping("/init")
    public Result<ChunkInitResponse> init(@RequestBody ChunkInitRequest request) {
        return Result.ok(chunkService.init(request));
    }

    @PostMapping("/upload")
    public Result<ChunkStatusResponse> upload(@RequestParam String uploadId,
                                              @RequestParam int index,
                                              @RequestParam MultipartFile file) {
        return Result.ok(chunkService.upload(uploadId, index, file));
    }

    @GetMapping("/status")
    public Result<ChunkStatusResponse> status(@RequestParam String uploadId) {
        return Result.ok(chunkService.status(uploadId));
    }

    @PostMapping("/merge")
    public Result<ChunkMergeResponse> merge(@RequestParam String uploadId) {
        return Result.ok("合并成功", chunkService.merge(uploadId));
    }

    @GetMapping("/download/{fileId}")
    public ResponseEntity<Resource> download(@PathVariable String fileId) {
        File file = chunkService.file(fileId);
        String fileName = StrUtil.subAfter(file.getName(), "_", false);
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + encoded)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new FileSystemResource(file));
    }
}
