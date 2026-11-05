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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 回答、投票、采纳、评论、动态流的集成测试。
 *
 * @author mqxu
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AnswerApiTest {

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
    @DisplayName("回答列表把采纳的排在最前")
    void acceptedAnswerComesFirst() throws Exception {
        mockMvc.perform(get("/api/v1/questions/3/answers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3))
                // 种子数据里第 5 条回答 accepted=1
                .andExpect(jsonPath("$.data.records[0].id").value(5))
                .andExpect(jsonPath("$.data.records[0].accepted").value(true));
    }

    @Test
    @DisplayName("新增回答后，问题上的冗余计数同步加一")
    void createAnswerIncrementsQuestionCount() throws Exception {
        String token = login("carol");

        mockMvc.perform(post("/api/v1/questions/4/answers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"函数上做运算会失效，改成范围查询就能走索引。\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.authorName").value("陈默"))
                .andExpect(jsonPath("$.data.questionTitle").value("MySQL 索引失效的常见场景有哪些？"));

        mockMvc.perform(get("/api/v1/questions/4"))
                .andExpect(jsonPath("$.data.answerCount").value(2));
    }

    @Test
    @DisplayName("只有提问者能采纳，提问者采纳后标记生效")
    void onlyAuthorCanAccept() throws Exception {
        String bob = login("bob");
        String alice = login("alice");

        // 问题 6 是 bob 提的，回答 11 是陶老师写的
        mockMvc.perform(post("/api/v1/answers/11/accept")
                        .header("Authorization", "Bearer " + alice))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/answers/11/accept")
                        .header("Authorization", "Bearer " + bob))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accepted").value(true));
    }

    @Test
    @DisplayName("同一个按钮点两次等于取消，换按钮等于改票")
    void voteTogglesAndSwitches() throws Exception {
        String token = login("alice");

        // 回答 2 初始赞同 18。第一次点赞 19
        mockMvc.perform(post("/api/v1/answers/2/vote").param("value", "1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.voteUp").value(19))
                .andExpect(jsonPath("$.data.myVote").value(1));

        // 再点一次同一按钮：取消，回到 18
        mockMvc.perform(post("/api/v1/answers/2/vote").param("value", "1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.voteUp").value(18))
                .andExpect(jsonPath("$.data.myVote").doesNotExist());

        // 换成反对：赞同不动，反对加一
        mockMvc.perform(post("/api/v1/answers/2/vote").param("value", "-1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.voteUp").value(18))
                .andExpect(jsonPath("$.data.voteDown").value(1))
                .andExpect(jsonPath("$.data.myVote").value(-1));
    }

    @Test
    @DisplayName("评论发布后能查到，删除自己的评论成功")
    void commentCreateAndDelete() throws Exception {
        String token = login("alice");

        String created = mockMvc.perform(post("/api/v1/answers/2/comments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"这个补充很有用\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.authorName").value("林知远"))
                .andReturn().getResponse().getContentAsString();
        Integer commentId = JsonPath.read(created, "$.data.id");

        mockMvc.perform(get("/api/v1/answers/2/comments"))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].content").value("这个补充很有用"));

        mockMvc.perform(delete("/api/v1/comments/" + commentId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/answers/2/comments"))
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    @DisplayName("删别人的评论返回 403")
    void deleteOthersCommentForbidden() throws Exception {
        String bob = login("bob");
        // 评论 1 是陈默写的
        mockMvc.perform(delete("/api/v1/comments/1")
                        .header("Authorization", "Bearer " + bob))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("动态流汇总关注的人与关注的问题，带出问题标题")
    void feedCombinesFollows() throws Exception {
        String token = login("alice");

        mockMvc.perform(get("/api/v1/feed").param("pageSize", "20")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").isNumber())
                // 至少能看到陶老师写的回答
                .andExpect(jsonPath("$.data.records[?(@.authorName == '陶老师')]").exists())
                .andExpect(jsonPath("$.data.records[0].questionTitle").isNotEmpty());
    }

    @Test
    @DisplayName("未登录看动态流返回 401")
    void feedRequiresLogin() throws Exception {
        mockMvc.perform(get("/api/v1/feed"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
    }
}
