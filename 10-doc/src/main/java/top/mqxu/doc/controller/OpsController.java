package top.mqxu.doc.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.mqxu.doc.common.Result;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 运维接口，用来演示文档分组。
 *
 * @author mqxu
 */
@Tag(name = "运维接口", description = "健康检查与文档自身的元信息")
@RestController
@RequestMapping("/ops")
public class OpsController {

    @Operation(summary = "健康检查", description = "返回服务状态与当前时间，供负载均衡探活")
    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "UP");
        data.put("time", LocalDateTime.now().toString());
        data.put("module", "10-doc");
        return Result.ok(data);
    }
}
