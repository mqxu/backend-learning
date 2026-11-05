package top.mqxu.security.controller;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.mqxu.security.common.Result;
import top.mqxu.security.dto.UserInfo;

import java.util.List;
import java.util.Map;

/**
 * 把教程前六个阶段的验证端点收在一处。
 * <p>
 * 路径前缀对应 SecurityConfig 里的五条链，同样的代码配不同的规则，行为就能放在一起对照。
 *
 * @author mqxu
 */
@RestController
@RequestMapping
public class StageController {

    // ---------------------------------------------------------------- 阶段一

    /**
     * 阶段一：全拦截。匿名访问返回 401，带 Basic 认证才进得来。
     */
    @GetMapping("/stage1/hello")
    public Result<String> stage1Hello(Authentication authentication) {
        return Result.ok("阶段一通过，当前用户：" + authentication.getName());
    }

    // ---------------------------------------------------------------- 阶段二

    @GetMapping("/stage2/public/info")
    public Result<String> stage2PublicInfo() {
        return Result.ok("公开接口，匿名可访问");
    }

    @GetMapping("/stage2/articles/{id}")
    public Result<Map<String, Object>> stage2Article(@PathVariable Long id) {
        return Result.ok(Map.of("id", id, "title", "Spring Security 7 入门"));
    }

    /**
     * 同一个路径的 DELETE 没有放行，掉进 anyRequest().authenticated()
     */
    @DeleteMapping("/stage2/articles/{id}")
    public Result<String> stage2DeleteArticle(@PathVariable Long id) {
        return Result.ok("已删除文章 " + id);
    }

    // ---------------------------------------------------------------- 阶段三

    @GetMapping("/stage3/admin/reports")
    public Result<String> stage3AdminReports() {
        return Result.ok("管理员报表（/stage3/admin/** 需要 ROLE_ADMIN）");
    }

    @GetMapping("/stage3/ops/alerts")
    public Result<String> stage3OpsAlerts() {
        return Result.ok("运维告警（需要 ROLE_ADMIN 或 ROLE_AUDITOR）");
    }

    @GetMapping("/stage3/audit/logs")
    public Result<String> stage3AuditLogs() {
        return Result.ok("审计日志（ROLE_ADMIN 与 ROLE_AUDITOR 必须同时具备）");
    }

    @DeleteMapping("/stage3/articles/{id}")
    public Result<String> stage3DeleteArticle(@PathVariable Long id) {
        return Result.ok("已删除文章 " + id + "（需要权限点 ARTICLE_DELETE）");
    }

    // ---------------------------------------------------------------- 阶段四

    /**
     * 表单登录成功后跳到这里
     */
    @GetMapping("/stage4/me")
    public Result<UserInfo> stage4Me(Authentication authentication) {
        List<String> authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
        return Result.ok(new UserInfo(authentication.getName(), authorities));
    }

    @GetMapping("/stage4/admin/panel")
    public Result<String> stage4AdminPanel() {
        return Result.ok("管理员面板（需要 ROLE_ADMIN）");
    }

    /**
     * 登录页用这段逻辑把 CSRF token 填进表单隐藏域。
     * <p>
     * CookieCsrfTokenRepository 在这个请求里把 XSRF-TOKEN 写进 Cookie，
     * 因为 stage4 用的是不做 XOR 掩码的 handler，Cookie 值和这里返回的值都是 36 字符的裸 UUID。
     */
    @GetMapping("/stage4/csrf-token")
    public Result<Map<String, String>> stage4CsrfToken(CsrfToken csrfToken) {
        return Result.ok(Map.of("token", csrfToken.getToken()));
    }
}
