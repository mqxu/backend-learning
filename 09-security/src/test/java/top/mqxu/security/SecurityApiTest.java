package top.mqxu.security;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author mqxu
 * @date 2026/10/28
 * @description 认证与授权接口测试，覆盖 docs/09-security.md 第 6 节的验证清单
 **/
@SpringBootTest
@AutoConfigureMockMvc
class SecurityApiTest {

    private static final String ADMIN = "admin";
    private static final String ADMIN_PASSWORD = "admin123";
    private static final String TEACHER = "teacher";
    private static final String TEACHER_PASSWORD = "teacher123";

    @Autowired
    private MockMvc mockMvc;

    private String loginBody(String username, String password) {
        return "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
    }

    private String tokenOf(String username, String password) throws Exception {
        String body = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(username, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.token");
    }

    private MockHttpServletRequestBuilder withToken(MockHttpServletRequestBuilder builder, String token) {
        return builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    /* ==================== 认证 ==================== */

    @Test
    @DisplayName("放行的 /public/** 不带令牌也能访问")
    void publicEndpointIsOpen() throws Exception {
        mockMvc.perform(get("/public/ping"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("ok"));
    }

    @Test
    @DisplayName("未带令牌访问受保护接口返回 401 与统一响应体")
    void anonymousIsRejected() throws Exception {
        mockMvc.perform(get("/security/hello"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));

        mockMvc.perform(get("/admin/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("登录成功返回令牌、类型、过期时间与角色")
    void loginIssuesToken() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(ADMIN, ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresAt").isNotEmpty())
                .andExpect(jsonPath("$.data.username").value(ADMIN))
                .andExpect(jsonPath("$.data.roles", hasItem("ADMIN")));
    }

    @Test
    @DisplayName("密码错误与用户名不存在返回同一个业务码，不泄露账号是否存在")
    void loginWithBadCredentials() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(ADMIN, "wrong-password")))
                .andExpect(jsonPath("$.code").value(30001))
                .andExpect(jsonPath("$.message").value("用户名或密码错误"));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("nobody", "whatever")))
                .andExpect(jsonPath("$.code").value(30001));
    }

    @Test
    @DisplayName("禁用账号登录返回 30002")
    void loginWithDisabledAccount() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("guest", "guest123")))
                .andExpect(jsonPath("$.code").value(30002));
    }

    @Test
    @DisplayName("登录入参缺失时返回参数校验错误")
    void loginValidatesInput() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\"}"))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("密码不能为空"));
    }

    @Test
    @DisplayName("带上令牌可以访问受保护接口，@AuthenticationPrincipal 拿得到当前用户")
    void tokenGrantsAccess() throws Exception {
        String token = tokenOf(TEACHER, TEACHER_PASSWORD);

        mockMvc.perform(withToken(get("/security/hello"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("认证与授权 模块，当前用户：" + TEACHER));

        mockMvc.perform(withToken(get("/auth/me"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value(TEACHER))
                .andExpect(jsonPath("$.data.password").doesNotExist());
    }

    @Test
    @DisplayName("令牌被篡改后按未认证处理")
    void tamperedTokenIsRejected() throws Exception {
        String token = tokenOf(TEACHER, TEACHER_PASSWORD);
        String tampered = token.substring(0, token.length() - 3) + "abc";

        mockMvc.perform(withToken(get("/auth/me"), tampered))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    /* ==================== 授权 ==================== */

    @Test
    @DisplayName("USER 访问 /admin/** 返回 403")
    void userCannotReachAdminArea() throws Exception {
        String token = tokenOf(TEACHER, TEACHER_PASSWORD);

        mockMvc.perform(withToken(get("/admin/users"), token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("ADMIN 可以查看用户列表，响应里不含密码")
    void adminCanListUsers() throws Exception {
        String token = tokenOf(ADMIN, ADMIN_PASSWORD);

        mockMvc.perform(withToken(get("/admin/users"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].username").value(ADMIN))
                .andExpect(jsonPath("$.data[0].password").doesNotExist());
    }

    @Test
    @DisplayName("方法级鉴权：权限点放行、角色拒绝")
    void methodSecurityWorks() throws Exception {
        String token = tokenOf(TEACHER, TEACHER_PASSWORD);

        mockMvc.perform(withToken(get("/api/reports"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());

        mockMvc.perform(withToken(get("/api/reports/admin-summary"), token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("方法级鉴权：只能看自己的报告，ADMIN 不受限")
    void dataOwnershipIsChecked() throws Exception {
        String teacherToken = tokenOf(TEACHER, TEACHER_PASSWORD);
        String adminToken = tokenOf(ADMIN, ADMIN_PASSWORD);

        mockMvc.perform(withToken(get("/api/reports/" + TEACHER), teacherToken))
                .andExpect(status().isOk());

        mockMvc.perform(withToken(get("/api/reports/" + ADMIN), teacherToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(withToken(get("/api/reports/" + TEACHER), adminToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("ADMIN 新增用户后，新用户可以用新密码登录")
    void createdUserCanLogin() throws Exception {
        String adminToken = tokenOf(ADMIN, ADMIN_PASSWORD);

        mockMvc.perform(withToken(post("/admin/users"), adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"zhangsan","password":"zhangsan123","nickname":"张三","roles":["USER"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("zhangsan"))
                .andExpect(jsonPath("$.data.roles", hasItem("USER")))
                .andExpect(jsonPath("$.data.password").doesNotExist());

        assertThat(tokenOf("zhangsan", "zhangsan123")).isNotBlank();
    }

    @Test
    @DisplayName("不能删除当前登录账号")
    void cannotDeleteSelf() throws Exception {
        String adminToken = tokenOf(ADMIN, ADMIN_PASSWORD);

        mockMvc.perform(withToken(delete("/admin/users/" + ADMIN), adminToken))
                .andExpect(jsonPath("$.code").value(30005));
    }
}
