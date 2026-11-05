package top.mqxu.toolkit.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.mqxu.toolkit.common.Result;
import top.mqxu.toolkit.dto.SensitiveResult;
import top.mqxu.toolkit.dto.TextExtractResult;
import top.mqxu.toolkit.service.TextService;

/**
 * 案例 12：文本清洗
 *
 * @author mqxu
 * @date 2026-10-21
 */
@RestController
@RequestMapping("/toolkit/text")
@RequiredArgsConstructor
public class TextController {

    private final TextService textService;

    /**
     * 提取并脱敏
     */
    @PostMapping("/extract")
    public Result<TextExtractResult> extract(@RequestBody TextRequest request) {
        return Result.ok(textService.extract(request.text()));
    }

    /**
     * 敏感词过滤
     */
    @PostMapping("/sensitive")
    public Result<SensitiveResult> sensitive(@RequestBody TextRequest request) {
        return Result.ok(textService.filter(request.text()));
    }

    public record TextRequest(String text) {
    }
}
