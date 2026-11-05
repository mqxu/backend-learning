package top.mqxu.zhihu;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 问题接口的集成测试。
 *
 * <p>类上加 @Transactional：每个用例结束后回滚，跑多少遍都不会污染演示数据。</p>
 *
 * @author mqxu
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class QuestionApiTest {

    private static final String PASSWORD = "Zhihu#2026";

    @Autowired
    private MockMvc mockMvc;

    private String login(String username) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.token");
    }

    @Test
    @DisplayName("列表返回分页结构，默认按时间倒序")
    void pageReturnsWrappedRecords() throws Exception {
        mockMvc.perform(get("/api/v1/questions").param("pageNum", "1").param("pageSize", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records.length()").value(5))
                .andExpect(jsonPath("$.data.total").value(8))
                .andExpect(jsonPath("$.data.pages").value(2))
                // 最近一天发布的问题排第一
                .andExpect(jsonPath("$.data.records[0].id").value(8));
    }

    @Test
    @DisplayName("话题过滤与关键词同时生效，不会互相覆盖")
    void topicAndKeywordCombineWithAnd() throws Exception {
        // “Redis”只出现在数据库话题的问题里；限定到前端话题应该查不到
        mockMvc.perform(get("/api/v1/questions")
                        .param("topicId", "2")
                        .param("keyword", "Redis"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    @DisplayName("详情每看一次，浏览数加一")
    void detailIncrementsViewCount() throws Exception {
        Integer before = JsonPath.read(mockMvc.perform(get("/api/v1/questions/1"))
                .andReturn().getResponse().getContentAsString(), "$.data.viewCount");

        mockMvc.perform(get("/api/v1/questions/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.viewCount").value(before + 1))
                .andExpect(jsonPath("$.data.authorName").value("陈默"))
                .andExpect(jsonPath("$.data.topicName").value("后端开发"));
    }

    @Test
    @DisplayName("查不存在的问题返回 404 与业务码 40400")
    void detailNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/questions/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40400));
    }

    @Test
    @DisplayName("未登录提问返回 401")
    void createRequiresLogin() throws Exception {
        mockMvc.perform(post("/api/v1/questions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"匿名提问\",\"content\":\"内容\",\"topicId\":1}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
    }

    @Test
    @DisplayName("登录后提问成功，返回作者名与初始计数")
    void createReturnsFilledVo() throws Exception {
        String token = login("alice");

        mockMvc.perform(post("/api/v1/questions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"新问题：如何理解事务传播？\",\"content\":\"REQUIRED 和 REQUIRES_NEW 的区别\",\"topicId\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("新问题：如何理解事务传播？"))
                .andExpect(jsonPath("$.data.authorName").value("林知远"))
                .andExpect(jsonPath("$.data.topicName").value("后端开发"))
                .andExpect(jsonPath("$.data.answerCount").value(0))
                .andExpect(jsonPath("$.data.viewCount").value(0));
    }

    @Test
    @DisplayName("改别人的问题返回 403")
    void updateOthersQuestionForbidden() throws Exception {
        // 问题 8 是 alice 提的，这里用 bob 去改
        String token = login("bob");

        mockMvc.perform(put("/api/v1/questions/8")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"被改掉的标题\",\"content\":\"内容\",\"topicId\":3}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300));
    }

    @Test
    @DisplayName("标题为空返回 400，消息来自校验注解")
    void blankTitleRejected() throws Exception {
        String token = login("alice");

        String body = mockMvc.perform(post("/api/v1/questions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\",\"content\":\"内容\",\"topicId\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000))
                .andReturn().getResponse().getContentAsString();

        assertThat((String) JsonPath.read(body, "$.message")).isEqualTo("标题不能为空");
    }
}
