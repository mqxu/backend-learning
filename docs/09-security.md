# 09 · 认证与授权 —— 用 Spring Security 保护接口

> - 难度：进阶
> - 前置模块：05-mybatis
> - 预计时长：约 75 分钟
> - 对应代码：[09-security](https://github.com/mqxu/backend-learning/tree/main/09-security)

## 1. 本节导读

学完本节，你将能：

1. 用 Spring Security 6 的 `SecurityFilterChain` 配置安全规则；
2. 用 JWT 做无状态认证，登录后签发 token；
3. 理解 RBAC 权限模型与接口鉴权；
4. 用 BCrypt 加密存储密码。

**前置知识**：会 `05-mybatis`（用户存数据库）。

---

## 2. 概念铺垫

### 2.1 认证 vs 授权

| 概念 | 回答的问题 | 示例 |
| --- | --- | --- |
| 认证（Authentication） | 你是谁？ | 用户名密码登录 |
| 授权（Authorization） | 你能做什么？ | 普通用户 vs 管理员 |

### 2.2 什么是 JWT

登录成功后，服务端签发一个**签名的令牌（token）**给客户端。之后客户端每次请求都带上 token，服务端验证签名即可知道「你是谁」，**无需在服务端存 session**——这就是无状态认证。

JWT 由三段组成，用 `.` 分隔：`Header.Payload.Signature`。签名防篡改，任何一段被改都会校验失败。

### 2.3 Spring Security 的过滤链

Spring Security 的核心是一串「过滤器」。请求进来依次经过它们，决定是否放行。Spring Security 6 用 `SecurityFilterChain` Bean 来配置（**旧版的 `WebSecurityConfigurerAdapter` 已废弃，不要再用**）。

---

## 3. 环境准备

依赖：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
</dependency>
```

> 引入 `spring-boot-starter-security` 后，所有接口默认都需要认证，会跳转一个默认登录页。

---

## 4. 动手实战

> 本模块当前是骨架，下面带你逐步补全安全能力。

### 步骤 1：配置安全规则

```java
package top.mqxu.security.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 安全配置
 *
 * @author mqxu
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // 无状态，不使用 session（JWT 场景）
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // 关闭 CSRF（纯 API 项目通常关闭）
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/auth/login").permitAll()   // 登录接口放行
                .requestMatchers("/admin/**").hasRole("ADMIN") // 需要 ADMIN 角色
                .anyRequest().authenticated()                  // 其余都要认证
            );
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
```

讲解：

- `sessionCreationPolicy(STATELESS)`：不创建 session，配合 JWT 无状态；
- `requestMatchers(...).permitAll()` 放行；`hasRole("ADMIN")` 要求 ADMIN 角色；
- **规范点**：密码用 `BCryptPasswordEncoder` 加密，**绝不存明文**。

### 步骤 2：签发 JWT

工具类：

```java
package top.mqxu.security.util;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具
 *
 * @author mqxu
 */
public final class JwtUtil {

    private JwtUtil() {
    }

    private static final String SECRET = "your-256-bit-secret-key-please-change-it!!";
    private static final long EXPIRE = 24 * 60 * 60 * 1000L; // 24 小时

    public static String generate(String username) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject(username)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + EXPIRE))
                .signWith(key)
                .compact();
    }

    public static String parseUsername(String token) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token).getPayload().getSubject();
    }
}
```

登录接口：

```java
package top.mqxu.security.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import top.mqxu.security.util.JwtUtil;

/**
 * 认证接口
 *
 * @author mqxu
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final PasswordEncoder passwordEncoder;

    @PostMapping("/login")
    public String login(@RequestParam String username, @RequestParam String password) {
        // 实际应从数据库校验用户名密码，这里演示流程
        if (!passwordEncoder.matches(password, passwordEncoder.encode("123456"))) {
            throw new RuntimeException("用户名或密码错误");
        }
        return JwtUtil.generate(username);
    }
}
```

> 上面 `matches(password, encode("123456"))` 仅演示；真实场景：`passwordEncoder.matches(rawPassword, dbPassword)`。

### 步骤 3：JWT 过滤器校验 token

```java
package top.mqxu.security.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import top.mqxu.security.util.JwtUtil;

import java.io.IOException;

/**
 * JWT 认证过滤器
 *
 * @author mqxu
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                String username = JwtUtil.parseUsername(token);
                // 放入安全上下文，后续接口可拿到当前用户
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(username, null, null);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (Exception e) {
                // token 无效则忽略，后续会被拦截
            }
        }
        filterChain.doFilter(request, response);
    }
}
```

把过滤器挂到安全链（在 `SecurityConfig` 中）：

```java
http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
```

---

## 5. 完整代码与目录结构

```
09-security
└── src/main
    ├── java/top/mqxu/security
    │   ├── SecurityApplication.java
    │   ├── config/SecurityConfig.java
    │   ├── filter/JwtAuthenticationFilter.java
    │   ├── controller/AuthController.java
    │   └── util/JwtUtil.java
    └── resources/application.yml
```

---

## 6. 运行与验证

```bash
mvn -pl 09-security -am spring-boot:run
```

1. 直接访问受保护接口 → 返回 401 未认证；
2. `POST /auth/login?username=admin&password=123456` → 返回 token；
3. 带上 `Authorization: Bearer <token>` 再访问 → 放行。

---

## 7. 常见问题与避坑

| 现象 | 原因 | 解决 |
| --- | --- | --- |
| 所有接口 401 | 没放行登录接口 | `requestMatchers("/auth/login").permitAll()` |
| 还是 `WebSecurityConfigurerAdapter` 报错 | 用了旧写法 | 改用 `SecurityFilterChain` Bean |
| token 解析失败 | 密钥长度不足 / 不一致 | HS256 密钥至少 256 bit，前后端一致 |
| 密码明文存储 | 没加密 | `BCryptPasswordEncoder` |
| 鉴权失败但能登录 | 过滤器没挂到链上 | `addFilterBefore(...)` |

---

## 8. 练习

### 8.1 课堂随堂练习（课上约 10 分钟）

| # | 练习 | 验证点 |
| --- | --- | --- |
| 1 | 把 `/auth/login` 放行，其他接口默认 401 | 未带 token 访问返回 401 |
| 2 | 登录拿到 token，带上再访问 | 接口放行 |
| 3 | 把 `hasRole("ADMIN")` 加到某接口，普通 token 访问 | 返回 403 |

### 8.2 课后练习（课后完成并提交）

- **基础**：用 `BCryptPasswordEncoder` 把「123456」加密并打印，观察两次加密结果不同（加盐）。
- **进阶**：实现一个 `UserDetailsService`，从数据库加载用户并校验密码。
- **挑战**：给 JWT 加「角色」声明，并在过滤器里解析角色、配合 `hasRole` 完成 RBAC。

---

## 9. 小结与知识地图

| 知识点 | 一句话回顾 |
| --- | --- |
| 认证 vs 授权 | 「你是谁」vs「你能做什么」 |
| JWT | 三段式令牌，签名防篡改，无状态 |
| `SecurityFilterChain` | Spring Security 6 的安全配置入口 |
| BCrypt | 密码加盐哈希，绝不存明文 |
| RBAC | 用户-角色-权限 |

下一节 [10-file](10-file.md) 会讲文件上传下载。

> 本节遵循的规范（官方 + 阿里手册）：用 `SecurityFilterChain`（弃用旧写法）；密码 BCrypt 加密；token 密钥走配置、不硬编码；敏感接口最小授权。
