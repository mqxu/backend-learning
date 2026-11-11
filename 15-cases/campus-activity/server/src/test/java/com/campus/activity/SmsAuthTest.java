package com.campus.activity;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 手机号 + 短信验证码登录测试。
 *
 * <p>注意：测试会真实连接本地 MySQL 与 Redis（database 11）。
 * 每个用例使用随机手机号，结束后物理删除新建的 sys_user 并清理短信相关 Redis key，
 * 不会污染种子数据。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
class SmsAuthTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /** 本用例创建过的手机号，用于结束后的清理 */
    private final List<String> phones = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (String phone : phones) {
            jdbcTemplate.update("DELETE FROM sys_user WHERE phone = ?", phone);
            stringRedisTemplate.delete(List.of(
                    "campus:sms:code:" + phone,
                    "campus:sms:interval:" + phone,
                    "campus:sms:daily:" + phone,
                    "campus:sms:fail:" + phone));
        }
    }

    @Test
    @DisplayName("发码拿 mockCode → 验证码登录拿 token → 个人资料可访问")
    void smsLoginFlow() throws Exception {
        String phone = randomPhone();
        String code = sendCode(phone);

        String loginBody = mockMvc.perform(post("/api/app/auth/sms/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"" + phone + "\",\"code\":\"" + code + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                // 新建学生账号还没绑定学号，需要引导绑定
                .andExpect(jsonPath("$.data.needBind").value(true))
                .andExpect(jsonPath("$.data.user.phone").value(phone))
                .andExpect(jsonPath("$.data.user.nickname")
                        .value("手机用户" + phone.substring(phone.length() - 4)))
                .andReturn().getResponse().getContentAsString();

        String token = JsonPath.read(loginBody, "$.data.token");
        mockMvc.perform(get("/api/app/user/profile").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.phone").value(phone));
    }

    @Test
    @DisplayName("验证码错误返回 60005")
    void wrongCodeReturns60005() throws Exception {
        String phone = randomPhone();
        String code = sendCode(phone);
        // 故意构造一个与真实验证码不同的 6 位数字
        String wrongCode = "000000".equals(code) ? "111111" : "000000";

        mockMvc.perform(post("/api/app/auth/sms/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"" + phone + "\",\"code\":\"" + wrongCode + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(60005))
                .andExpect(jsonPath("$.message").value("验证码错误"));
    }

    @Test
    @DisplayName("60 秒内重复发送返回 60002")
    void repeatedSendReturns60002() throws Exception {
        String phone = randomPhone();
        sendCode(phone);

        mockMvc.perform(post("/api/app/auth/sms/send-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"" + phone + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(60002))
                .andExpect(jsonPath("$.message").value("验证码发送太频繁，请稍后再试"));
    }

    /** 发送验证码并返回 Mock 通道回传的验证码；随机手机号登记到清理列表 */
    private String sendCode(String phone) throws Exception {
        String body = mockMvc.perform(post("/api/app/auth/sms/send-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"" + phone + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.phone").value(phone))
                .andExpect(jsonPath("$.data.mockCode").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.mockCode");
    }

    /** 139 开头 + 8 位随机数字，避开种子数据里的 1380000000x */
    private String randomPhone() {
        String phone = "139" + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
        phones.add(phone);
        return phone;
    }
}
