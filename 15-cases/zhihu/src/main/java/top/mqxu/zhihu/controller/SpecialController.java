package top.mqxu.zhihu.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.mqxu.zhihu.common.Result;
import top.mqxu.zhihu.entity.Special;
import top.mqxu.zhihu.service.SpecialService;

import java.util.List;

/**
 * 专栏。原案例唯一的接口，路径与返回结构都保持兼容，前端不用改。
 *
 * @author mqxu
 */
@RestController
@RequestMapping("/api/v1/special")
@RequiredArgsConstructor
public class SpecialController {

    private final SpecialService specialService;

    @GetMapping("/all")
    public Result<List<Special>> getAll() {
        return Result.ok(specialService.getAll());
    }
}
