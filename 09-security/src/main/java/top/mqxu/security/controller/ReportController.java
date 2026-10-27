package top.mqxu.security.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.mqxu.security.common.Result;

import java.util.List;
import java.util.Map;

/**
 * @author mqxu
 * @date 2026/10/28
 * @description 方法级鉴权示例：同一组接口里按权限点、角色、数据归属三种粒度分别放行
 **/
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    /**
     * 按权限点放行，ADMIN 与 USER 都持有 report:read
     */
    @GetMapping
    @PreAuthorize("hasAuthority('report:read')")
    public Result<List<String>> list() {
        return Result.ok(List.of("2026 秋季学期班级周报", "课程实训进度月报"));
    }

    /**
     * 按角色放行
     */
    @GetMapping("/admin-summary")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Map<String, Object>> adminSummary() {
        return Result.ok(Map.of("用户总数", 3, "本周登录人次", 128, "异常接口调用", 0));
    }

    /**
     * 按数据归属放行：只能看自己的报告，ADMIN 例外
     */
    @GetMapping("/{owner}")
    @PreAuthorize("#owner == authentication.name or hasRole('ADMIN')")
    public Result<String> byOwner(@PathVariable String owner) {
        return Result.ok(owner + " 的报告：本周一切正常");
    }
}
