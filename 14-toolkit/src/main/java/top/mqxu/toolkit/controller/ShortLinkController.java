package top.mqxu.toolkit.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.mqxu.toolkit.common.Result;
import top.mqxu.toolkit.dto.ShortLinkResponse;
import top.mqxu.toolkit.dto.ShortLinkStats;
import top.mqxu.toolkit.service.ShortLinkService;

import java.net.URI;

/**
 * 案例 4：短链服务
 *
 * @author mqxu
 * @date 2026-10-21
 */
@RestController
@RequestMapping("/toolkit")
@RequiredArgsConstructor
public class ShortLinkController {

    private final ShortLinkService shortLinkService;

    @PostMapping("/short-link")
    public Result<ShortLinkResponse> create(@Valid @RequestBody CreateRequest request) {
        return Result.ok(shortLinkService.create(request.url(), request.ttlSeconds()));
    }

    /**
     * 访问短链，302 跳到原始地址
     */
    @GetMapping("/s/{code}")
    public ResponseEntity<Void> redirect(@PathVariable String code) {
        String url = shortLinkService.resolve(code);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(url))
                .build();
    }

    @GetMapping("/short-link/{code}")
    public Result<ShortLinkStats> stats(@PathVariable String code) {
        return Result.ok(shortLinkService.stats(code));
    }

    public record CreateRequest(
            @NotBlank(message = "url 不能为空") String url,
            Long ttlSeconds) {
    }
}
