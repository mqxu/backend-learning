package top.mqxu.security;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.hamcrest.Matchers.hasItem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 五条链一起跑：JWT 登录、角色与权限点、Basic 分阶段、短信登录。
 *
 * <p>六个演示账号的密码都是 123456，账号状态见 data.sql。</p>
 *
 * @author mqxu
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityIntegrationTest {

    private static final String PASSWORD = "123456";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StringRedisTemplate redis;

    /**
     * 验证码与发送冷却都存在 Redis 里，上次跑测试留下的 key 会让本次发码直接 429。
     * 每个用例前清掉，保证从干净状态开始。
     */
    @BeforeEach
    void clearSmsKeys() {
        Set<String> keys = redis.keys("sms:*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    /** 密码登录换 token */
    private String tokenOf(String username) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s"}""".formatted(username, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.token");
    }

    /** HTTP Basic 头，前四条链用这个 */
    private String basic(String username) {
        return "Basic " + Base64.getEncoder()
                .encodeToString((username + ":" + PASSWORD).getBytes(StandardCharsets.UTF_8));
    }

    // ---------------------------------------------------------------- 登录与 JWT

    @Test
    @DisplayName("匿名访问受保护接口：401 + 统一 JSON，不是 302 也不是 HTML")
    void anonymousGetsJson401() throws Exception {
        mockMvc.perform(get("/api/hello"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("未登录或登录已过期"));
    }

    @Test
    @DisplayName("密码登录返回 token 与权限列表")
    void loginReturnsToken() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"alice","password":"123456"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                // 权限的顺序取决于数据库返回顺序，断言只关心包含什么
                .andExpect(jsonPath("$.data.authorities", hasItem("ROLE_USER")))
                .andExpect(jsonPath("$.data.authorities", hasItem("ARTICLE_DELETE")));
    }

    @Test
    @DisplayName("带上 token 能进业务接口，身份与权限都从 token 里还原")
    void tokenGrantsAccess() throws Exception {
        String token = tokenOf("alice");

        mockMvc.perform(get("/api/hello").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.message").value("你好，alice"))
                .andExpect(jsonPath("$.data.authorities").isArray());
    }

    @Test
    @DisplayName("密码错误返回 401，文案统一成“用户名或密码错误”")
    void wrongPasswordGets401() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"alice","password":"wrong"}"""))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("用户名或密码错误"));
    }

    @Test
    @DisplayName("禁用与锁定的账号返回 403，与密码错区分开")
    void disabledAndLockedAccounts() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"dave","password":"123456"}"""))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"frank","password":"123456"}"""))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    // ---------------------------------------------------------------- 角色与权限点

    @Test
    @DisplayName("hasRole：alice 是普通用户被拒，bob 是管理员放行")
    void roleBasedAccess() throws Exception {
        String alice = tokenOf("alice");
        String bob = tokenOf("bob");

        mockMvc.perform(get("/api/admin/dashboard").header(HttpHeaders.AUTHORIZATION, "Bearer " + alice))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        mockMvc.perform(get("/api/admin/dashboard").header(HttpHeaders.AUTHORIZATION, "Bearer " + bob))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("方法级 @PreAuthorize：路径放行了，方法上还能再拦一道")
    void methodLevelPreAuthorize() throws Exception {
        String alice = tokenOf("alice");
        String bob = tokenOf("bob");

        mockMvc.perform(get("/api/reports").header(HttpHeaders.AUTHORIZATION, "Bearer " + alice))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/reports").header(HttpHeaders.AUTHORIZATION, "Bearer " + bob))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("报表数据"));
    }

    @Test
    @DisplayName("hasAuthority：只有 alice 带 ARTICLE_DELETE 权限点")
    void authorityBasedAccess() throws Exception {
        String alice = tokenOf("alice");
        String bob = tokenOf("bob");

        mockMvc.perform(delete("/api/articles/1001").header(HttpHeaders.AUTHORIZATION, "Bearer " + alice))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/articles/1001").header(HttpHeaders.AUTHORIZATION, "Bearer " + bob))
                .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------- 分阶段

    @Test
    @DisplayName("stage1：全拦截，匿名 401，带 Basic 才进得来")
    void stage1RequiresAuthentication() throws Exception {
        mockMvc.perform(get("/stage1/hello"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/stage1/hello").header(HttpHeaders.AUTHORIZATION, basic("alice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("阶段一通过，当前用户：alice"));
    }

    @Test
    @DisplayName("stage2：GET 放行，同一个路径的 DELETE 仍要登录")
    void stage2WhitelistByMethod() throws Exception {
        mockMvc.perform(get("/stage2/articles/1"))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/stage2/articles/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("stage3 hasAllRoles：carol 只有 ADMIN 被拒，bob 两个都有才放行")
    void stage3RequiresAllRoles() throws Exception {
        mockMvc.perform(get("/stage3/audit/logs").header(HttpHeaders.AUTHORIZATION, basic("carol")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/stage3/audit/logs").header(HttpHeaders.AUTHORIZATION, basic("bob")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("stage3 hasAnyRole：erin 只有 AUDITOR，也能进 ops")
    void stage3AcceptsAnyRole() throws Exception {
        mockMvc.perform(get("/stage3/ops/alerts").header(HttpHeaders.AUTHORIZATION, basic("erin")))
                .andExpect(status().isOk());
    }

    // ---------------------------------------------------------------- 短信登录

    @Test
    @DisplayName("短信登录：发码、取码、换 token，新手机号自动建号")
    void smsLogin() throws Exception {
        String phone = "13900001111";

        String body = mockMvc.perform(post("/api/auth/sms/code").param("phone", phone))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // 演示模式把验证码写在返回文案里：验证码已发送（演示模式，验证码：123456）
        Matcher matcher = Pattern.compile("验证码：(\\d{6})").matcher(body);
        assertThat(matcher.find()).isTrue();
        String code = matcher.group(1);

        mockMvc.perform(post("/api/auth/sms/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"%s","code":"%s"}""".formatted(phone, code)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.authorities", hasItem("ROLE_USER")));
    }

    @Test
    @DisplayName("同一个验证码不能用第二次")
    void smsCodeIsSingleUse() throws Exception {
        String phone = "13900002222";

        String body = mockMvc.perform(post("/api/auth/sms/code").param("phone", phone))
                .andReturn().getResponse().getContentAsString();
        Matcher matcher = Pattern.compile("验证码：(\\d{6})").matcher(body);
        assertThat(matcher.find()).isTrue();
        String code = matcher.group(1);

        String payload = """
                {"phone":"%s","code":"%s"}""".formatted(phone, code);

        mockMvc.perform(post("/api/auth/sms/login")
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/sms/login")
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("验证码错误或已过期"));
    }
}
