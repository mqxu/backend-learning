package top.mqxu.toolkit.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import top.mqxu.toolkit.service.PosterService;

/**
 * 案例 3：商品海报
 *
 * @author mqxu
 * @date 2026-10-21
 */
@RestController
@RequestMapping("/toolkit/poster")
@RequiredArgsConstructor
public class PosterController {

    private final PosterService posterService;

    /**
     * 生成海报，直接返回 PNG 二进制，浏览器打开就能看到
     *
     * @param file      商品图，可不传，不传时用渐变底图
     * @param title     标题
     * @param price     价格
     * @param qrContent 二维码内容
     */
    @PostMapping(produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> render(
            @RequestParam(required = false) MultipartFile file,
            @RequestParam(defaultValue = "春季新品") String title,
            @RequestParam(defaultValue = "199.00") String price,
            @RequestParam(required = false) String qrContent) {
        byte[] png = posterService.render(file, title, price, qrContent);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .header("Content-Disposition", "inline; filename=poster.png")
                .body(png);
    }
}
