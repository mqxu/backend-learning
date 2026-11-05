package top.mqxu.security.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.mqxu.security.common.Result;

import java.util.List;
import java.util.Map;

/**
 * 前后端分离下的业务示例。
 * <p>
 * 路径规则写在 SecurityConfig 的第五条链里，方法级规则用 @PreAuthorize，
 * 两者是叠加关系，不是二选一。
 *
 * @author mqxu
 */
@RestController
@RequestMapping("/api")
public class ApiController {

    /**
     * 需要登录。返回当前身份与权限，方便确认 token 到底带上了什么。
     */
    @GetMapping("/hello")
    public Result<Map<String, Object>> hello(Authentication authentication) {
        List<String> authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
        return Result.ok(Map.of(
                "message", "你好，" + authentication.getName(),
                "authorities", authorities));
    }

    /**
     * 公开接口，permitAll 放行
     */
    @GetMapping("/public/info")
    public Result<String> publicInfo() {
        return Result.ok("这是公开接口，无需登录");
    }

    /**
     * 管理员接口，/api/admin/** 配了 hasRole("ADMIN")
     */
    @GetMapping("/admin/dashboard")
    public Result<String> adminDashboard() {
        return Result.ok("管理员后台数据");
    }

    /**
     * 方法级权限。少写 @EnableMethodSecurity 的话这个注解不报错、不生效，请求会直接进方法。
     */
    @GetMapping("/reports")
    @PreAuthorize("hasAnyRole('ADMIN','AUDITOR')")
    public Result<String> reports() {
        return Result.ok("报表数据");
    }

    /**
     * 细粒度权限点，DELETE 方法配了 hasAuthority("ARTICLE_DELETE")
     */
    @DeleteMapping("/articles/{id}")
    public Result<String> deleteArticle(@PathVariable Long id) {
        return Result.ok("已删除文章 " + id);
    }
}
