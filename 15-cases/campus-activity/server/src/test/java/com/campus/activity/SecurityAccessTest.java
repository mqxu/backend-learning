package com.campus.activity;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Spring Security 接入后的对外行为契约：
 * 未登录 401 + 10001、登录但角色不够 403 + 10002、登出后旧 token 立即失效。
 * <p>
 * 走真实 MySQL 与 Redis（database 11），所以每次测试后清掉 JWT 黑名单 key，
 * 避免上一次登出的 token 状态影响后续运行。
 *
 * @author mqxu
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityAccessTest {

    /** data.sql 里的后台管理员 */
    private static final String ADMIN_USERNAME = "admin";
    private static final String ADMIN_PASSWORD = "Admin@123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @AfterEach
    void clearBlacklist() {
        Set<String> keys = redisTemplate.keys("campus:auth:blacklist:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    /** 开发模式下用 devOpenId 登录学生账号，返回 token */
    private String loginStudent() throws Exception {
        String body = mockMvc.perform(post("/api/app/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"devOpenId\":\"mock-student-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.token");
    }

    /** 后台账号密码登录，返回 token */
    private String loginAdmin() throws Exception {
        String body = mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s"}""".formatted(ADMIN_USERNAME, ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.token");
    }

    @Test
    @DisplayName("未登录访问个人资料：401 + 业务码 10001")
    void anonymousProfileReturns401() throws Exception {
        mockMvc.perform(get("/api/app/user/profile"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(10001))
                .andExpect(jsonPath("$.message").value("未登录或登录已过期"))
                // 响应体必须和其他接口完全同构：data 字段保留为 null，由 Jackson 的 non_null 策略之外单独写死
                .andExpect(content().json(
                        "{\"code\":10001,\"message\":\"未登录或登录已过期\",\"data\":null}", true));
    }

    @Test
    @DisplayName("未登录访问路径规则保护的后台接口：401 + 业务码 10001")
    void anonymousAdminApiReturns401() throws Exception {
        // /api/admin/** 由 SecurityFilterChain 的路径规则拦截，走 ExceptionTranslationFilter
        mockMvc.perform(get("/api/admin/activities"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(10001))
                .andExpect(content().json(
                        "{\"code\":10001,\"message\":\"未登录或登录已过期\",\"data\":null}", true));
    }

    @Test
    @DisplayName("学生 token 访问后台活动管理：403 + 业务码 10002")
    void studentCannotAccessAdminApi() throws Exception {
        String token = loginStudent();

        mockMvc.perform(get("/api/admin/activities").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(10002))
                .andExpect(jsonPath("$.message").value("没有操作权限"))
                .andExpect(content().json(
                        "{\"code\":10002,\"message\":\"没有操作权限\",\"data\":null}", true));
    }

    @Test
    @DisplayName("管理员账号密码登录成功，拿到的 token 能进后台接口")
    void adminLoginSuccess() throws Exception {
        String token = loginAdmin();

        mockMvc.perform(get("/api/admin/activities").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("登出后原 token 立即失效：再访问受保护接口返回 401")
    void logoutInvalidatesToken() throws Exception {
        String token = loginStudent();

        mockMvc.perform(post("/api/app/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/app/user/profile").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(10001));
    }
}
