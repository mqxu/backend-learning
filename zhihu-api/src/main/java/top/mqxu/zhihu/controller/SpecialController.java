package top.mqxu.zhihu.controller;

import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.mqxu.zhihu.common.ResponseResult;
import top.mqxu.zhihu.entity.Special;
import top.mqxu.zhihu.service.SpecialService;

import java.util.List;

/**
 * @author mqxu
 * @date 2026/10/19
 * @description SpecialController
 **/
@RestController
@RequestMapping("/api/v1/special")
public class SpecialController {
    @Resource
    private SpecialService specialService;

    @GetMapping("all")
    public ResponseResult getAll() {
        List<Special> all = specialService.getAll();
        return ResponseResult.builder()
                .code(200)
                .msg("数据获取成功")
                .data(all)
                .build();
    }
}