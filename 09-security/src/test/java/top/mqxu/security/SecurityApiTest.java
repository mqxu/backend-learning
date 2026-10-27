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
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author mqxu
 * @date 2026/10/28
 * @description 案例主流程测试：学生提交、只看自己的、教师批改、管理员管账号
 * <p>
 * 断言的顺序就是前端实际调用的顺序，出问题时能直接看出卡在哪一步。
 **/
@SpringBootTest
@AutoConfigureMockMvc
class SecurityApiTest {

    private static final String ADMIN = "admin";
    private static final String ADMIN_PASSWORD = "Admin#2026";

    private static final String TEACHER = "teacher01";
    private static final String TEACHER_PASSWORD = "Teacher#2026";

    private static final String STUDENT = "student01";
    private static final String STUDENT_PASSWORD = "Student#2026";

    /**
     * 预置数据里 student02 的那条任务，用来验证越权查看
     */
    private static final long STUDENT2_TASK_ID = 2L;

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

    private String submitBody(String title) {
        return "{\"title\":\"" + title + "\",\"content\":\"按实训要求做完了，代码见附件。\"}";
    }

    private long submitTask(String token, String title) throws Exception {
        String body = mockMvc.perform(withToken(post("/api/tasks"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitBody(title)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Number id = JsonPath.read(body, "$.data.id");
        return id.longValue();
    }

    /* ==================== 一、登录拿到身份 ==================== */

    @Test
    @DisplayName("放行接口不带令牌也能访问")
    void publicEndpointIsOpen() throws Exception {
        mockMvc.perform(get("/public/ping"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ok"));
    }

    @Test
    @DisplayName("没带令牌访问任务接口返回 401")
    void anonymousIsRejected() throws Exception {
        mockMvc.perform(get("/api/tasks/mine"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));

        mockMvc.perform(get("/api/tasks"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("登录成功返回令牌与角色")
    void loginIssuesToken() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(STUDENT, STUDENT_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresAt").isNotEmpty())
                .andExpect(jsonPath("$.data.roles", hasItem("STUDENT")));
    }

    @Test
    @DisplayName("密码错误与用户不存在给同一个提示")
    void loginWithBadCredentials() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(STUDENT, "wrong-password")))
                .andExpect(jsonPath("$.code").value(30001));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("nobody", "whatever")))
                .andExpect(jsonPath("$.code").value(30001));
    }

    @Test
    @DisplayName("停用账号登录返回 30002")
    void loginWithDisabledAccount() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("guest", "Guest#2026")))
                .andExpect(jsonPath("$.code").value(30002));
    }

    @Test
    @DisplayName("缺少密码时返回参数校验提示")
    void loginValidatesInput() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"student01\"}"))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("密码不能为空"));
    }

    @Test
    @DisplayName("带令牌换当前用户，响应里没有密码")
    void tokenGrantsAccess() throws Exception {
        String token = tokenOf(STUDENT, STUDENT_PASSWORD);

        mockMvc.perform(withToken(get("/auth/me"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value(STUDENT))
                .andExpect(jsonPath("$.data.roles", hasItem("STUDENT")))
                .andExpect(jsonPath("$.data.password").doesNotExist());
    }

    @Test
    @DisplayName("令牌被改过一位就按未认证处理")
    void tamperedTokenIsRejected() throws Exception {
        String token = tokenOf(STUDENT, STUDENT_PASSWORD);
        String tampered = token.substring(0, token.length() - 3) + "abc";

        mockMvc.perform(withToken(get("/auth/me"), tampered))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    /* ==================== 二、学生只能碰自己的数据 ==================== */

    @Test
    @DisplayName("学生提交任务，归属由服务端写死成当前登录人")
    void studentSubmitsTask() throws Exception {
        String token = tokenOf(STUDENT, STUDENT_PASSWORD);

        mockMvc.perform(withToken(post("/api/tasks"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitBody("实训三：接口鉴权改造")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.owner").value(STUDENT))
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.data.statusText").value("待批改"));
    }

    @Test
    @DisplayName("请求体里塞别人的用户名不生效，归属仍是当前登录人")
    void ownerCannotBeForgedByBody() throws Exception {
        String token = tokenOf(STUDENT, STUDENT_PASSWORD);

        mockMvc.perform(withToken(post("/api/tasks"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"实训四：伪造归属\",\"content\":\"试试改 owner\",\"owner\":\"student02\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.owner").value(STUDENT));
    }

    @Test
    @DisplayName("我的提交里只有自己的")
    void studentSeesOnlyOwnTasks() throws Exception {
        String token = tokenOf(STUDENT, STUDENT_PASSWORD);

        mockMvc.perform(withToken(get("/api/tasks/mine"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].owner", everyItem(is(STUDENT))));
    }

    @Test
    @DisplayName("学生看别人的任务返回真实 403，不是 200 里裹一个错误码")
    void studentCannotViewOthersTask() throws Exception {
        String token = tokenOf(STUDENT, STUDENT_PASSWORD);

        mockMvc.perform(withToken(get("/api/tasks/" + STUDENT2_TASK_ID), token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("学生能看自己那条的详情，含分数与评语")
    void studentCanViewOwnTask() throws Exception {
        String token = tokenOf(STUDENT, STUDENT_PASSWORD);
        long id = submitTask(token, "实训五：查看详情");

        mockMvc.perform(withToken(get("/api/tasks/" + id), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id))
                .andExpect(jsonPath("$.data.owner").value(STUDENT));
    }

    /* ==================== 三、教师的权限点与批改 ==================== */

    @Test
    @DisplayName("学生不能看全部提交，也不能批改")
    void studentCannotReview() throws Exception {
        String token = tokenOf(STUDENT, STUDENT_PASSWORD);

        mockMvc.perform(withToken(get("/api/tasks"), token))
                .andExpect(status().isForbidden());

        mockMvc.perform(withToken(post("/api/tasks/" + STUDENT2_TASK_ID + "/review"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":100,\"comment\":\"自己给自己打满分\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("教师看得到全部提交，也能看到是谁交的")
    void teacherSeesAllTasks() throws Exception {
        String token = tokenOf(TEACHER, TEACHER_PASSWORD);

        mockMvc.perform(withToken(get("/api/tasks"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].owner").isNotEmpty());
    }

    @Test
    @DisplayName("教师批改后学生能看到分数，同一份不能批改两次")
    void teacherReviewsTask() throws Exception {
        String studentToken = tokenOf(STUDENT, STUDENT_PASSWORD);
        String teacherToken = tokenOf(TEACHER, TEACHER_PASSWORD);
        long id = submitTask(studentToken, "实训六：等待批改");

        mockMvc.perform(withToken(post("/api/tasks/" + id + "/review"), teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":88,\"comment\":\"归属判断对了，注意异常分支\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REVIEWED"))
                .andExpect(jsonPath("$.data.score").value(88));

        // 学生这边立刻能看到分数
        mockMvc.perform(withToken(get("/api/tasks/" + id), studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.score").value(88))
                .andExpect(jsonPath("$.data.comment").value("归属判断对了，注意异常分支"));

        // 再批一次被拒
        mockMvc.perform(withToken(post("/api/tasks/" + id + "/review"), teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":60,\"comment\":\"改个分\"}"))
                .andExpect(jsonPath("$.code").value(30012));
    }

    @Test
    @DisplayName("教师没有提交权限，调提交接口返回 403")
    void teacherCannotSubmit() throws Exception {
        String token = tokenOf(TEACHER, TEACHER_PASSWORD);

        mockMvc.perform(withToken(post("/api/tasks"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitBody("教师不该能交任务")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("分数超出 0 到 100 时被参数校验拦住")
    void reviewValidatesScore() throws Exception {
        String studentToken = tokenOf(STUDENT, STUDENT_PASSWORD);
        String teacherToken = tokenOf(TEACHER, TEACHER_PASSWORD);
        long id = submitTask(studentToken, "实训七：分数越界");

        mockMvc.perform(withToken(post("/api/tasks/" + id + "/review"), teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":120}"))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("分数不能大于 100"));
    }

    /* ==================== 四、管理员管账号 ==================== */

    @Test
    @DisplayName("教师进不了管理接口")
    void teacherCannotReachAdminArea() throws Exception {
        String token = tokenOf(TEACHER, TEACHER_PASSWORD);

        mockMvc.perform(withToken(get("/admin/users"), token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("管理员能看到账号列表，响应里没有密码")
    void adminCanListUsers() throws Exception {
        String token = tokenOf(ADMIN, ADMIN_PASSWORD);

        mockMvc.perform(withToken(get("/admin/users"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].username").isNotEmpty())
                .andExpect(jsonPath("$.data[0].password").doesNotExist());
    }

    @Test
    @DisplayName("管理员新增学生后，这个学生能登录并提交任务")
    void createdUserCanLogin() throws Exception {
        String adminToken = tokenOf(ADMIN, ADMIN_PASSWORD);

        mockMvc.perform(withToken(post("/admin/users"), adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"student99","password": "NewStu#2026","nickname":"新加的同学","roles":["STUDENT"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roles", hasItem("STUDENT")));

        String token = tokenOf("student99", "NewStu#2026");
        assertThat(token).isNotBlank();

        mockMvc.perform(withToken(get("/api/tasks/mine"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @DisplayName("不能删掉当前登录的账号")
    void cannotDeleteSelf() throws Exception {
        String token = tokenOf(ADMIN, ADMIN_PASSWORD);

        mockMvc.perform(withToken(delete("/admin/users/" + ADMIN), token))
                .andExpect(jsonPath("$.code").value(30005));
    }
}
