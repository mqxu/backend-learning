package top.mqxu.toolkit.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import top.mqxu.toolkit.common.Result;
import top.mqxu.toolkit.dto.ImportResult;
import top.mqxu.toolkit.service.ExcelService;

import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 案例 5：Excel 导入导出
 *
 * @author mqxu
 * @date 2026-10-21
 */
@RestController
@RequestMapping("/toolkit/excel")
@RequiredArgsConstructor
public class ExcelController {

    private static final MediaType XLSX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final ExcelService excelService;

    /**
     * 导出订单报表
     */
    @GetMapping("/export")
    public ResponseEntity<byte[]> export() {
        return attachment(excelService.export(), "订单报表.xlsx");
    }

    /**
     * 下载导入模板，状态列带下拉框
     */
    @GetMapping("/template")
    public ResponseEntity<byte[]> template() {
        return attachment(excelService.template(), "订单导入模板.xlsx");
    }

    /**
     * 导入并校验，有错误时返回错误明细的下载地址
     */
    @PostMapping("/import")
    public Result<ImportResult> importFile(@RequestParam MultipartFile file) {
        return Result.ok(excelService.importFile(file));
    }

    /**
     * 下载错误明细
     */
    @GetMapping("/download/{fileName}")
    public ResponseEntity<Resource> download(@PathVariable String fileName) {
        File file = excelService.errorFile(fileName);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, attachmentHeader("导入错误明细.xlsx"))
                .contentType(XLSX)
                .body(new FileSystemResource(file));
    }

    private ResponseEntity<byte[]> attachment(byte[] bytes, String fileName) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, attachmentHeader(fileName))
                .contentType(XLSX)
                .body(bytes);
    }

    private String attachmentHeader(String fileName) {
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        return "attachment; filename*=UTF-8''" + encoded;
    }
}
