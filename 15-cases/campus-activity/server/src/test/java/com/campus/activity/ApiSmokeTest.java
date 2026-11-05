package com.campus.activity;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 校园活动后端的冒烟测试：只覆盖入口、鉴权与分页，业务规则由 scripts/smoke.sh 端到端验证。
 *
 * @author mqxu
 */
@SpringBootTest
@AutoConfigureMockMvc
class ApiSmokeTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("探活接口不查库也不校验身份")
    void ping() throws Exception {
        mockMvc.perform(get("/api/ping"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("up"));
    }

    @Test
    @DisplayName("活动分类按 sort 升序返回")
    void categories() throws Exception {
        mockMvc.perform(get("/api/app/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].name").value("文体活动"));
    }

    @Test
    @DisplayName("活动列表返回分页结构与状态文案")
    void activities() throws Exception {
        mockMvc.perform(get("/api/app/activities").param("page", "1").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records.length()").value(2))
                .andExpect(jsonPath("$.data.records[0].statusLabel").isNotEmpty())
                .andExpect(jsonPath("$.data.total").isNumber());
    }

    @Test
    @DisplayName("未登录访问个人资料返回 401 与业务码 10001")
    void profileRequiresLogin() throws Exception {
        mockMvc.perform(get("/api/app/user/profile"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(10001))
                .andExpect(jsonPath("$.message").value("未登录或登录已过期"));
    }

    @Test
    @DisplayName("开发模式下用 devOpenId 登录拿到令牌")
    void loginWithDevOpenId() throws Exception {
        String body = mockMvc.perform(post("/api/app/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"devOpenId\":\"mock-student-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();

        String token = JsonPath.read(body, "$.data.token");
        mockMvc.perform(get("/api/app/user/profile").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("张小明"));
    }
}
