package top.mqxu.schedule;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import top.mqxu.schedule.service.DynamicTaskService;
import top.mqxu.schedule.service.TaskExecutionRecorder;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 定时任务的集成测试：启动真实容器，让调度器真的按周期跑。
 *
 * <p>定时任务的测试绕不开等待，这里的等待都写在用例里并注明原因，
 * 目的是验证"真的触发了"，而不是只验证方法能被调用。</p>
 *
 * @author mqxu
 */
@SpringBootTest
@AutoConfigureMockMvc
class ScheduleIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TaskExecutionRecorder recorder;

    @Autowired
    private DynamicTaskService dynamicTaskService;

    @Test
    @DisplayName("三种触发方式都会自行执行：等 11 秒，三类任务的记录都出现")
    void allThreeTriggersFire() throws InterruptedException {
        // heartBeat 3 秒、cleanExpiredData 5 秒、dailyReport 10 秒，
        // 等 11 秒能保证三类任务各至少跑一次
        Thread.sleep(11_000);

        List<TaskExecutionRecorder.Execution> records = recorder.recent(200);

        assertThat(records).extracting(TaskExecutionRecorder.Execution::taskName)
                .contains("heartBeat", "cleanExpiredData", "dailyReport");
    }

    @Test
    @DisplayName("统计接口返回每个任务的执行次数与线程名")
    void statsEndpointReportsCounts() throws Exception {
        mockMvc.perform(get("/schedule/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.统计").isArray());
    }

    @Test
    @DisplayName("动态任务：注册后每秒执行，取消后停止，再取消返回失败")
    void dynamicTaskRegisterAndCancel() throws Exception {
        String name = "testDynamic";

        mockMvc.perform(post("/schedule/tasks/dynamic")
                        .param("name", name)
                        .param("cron", "0/1 * * * * ?"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // 等 2.5 秒，让每秒触发的任务至少跑两轮
        Thread.sleep(2500);

        Map<String, Object> running = dynamicTaskService.list();
        assertThat(String.valueOf(running.get("运行中"))).contains(name);

        @SuppressWarnings("unchecked")
        Map<String, Integer> counters = (Map<String, Integer>) running.get("各任务执行次数");
        assertThat(counters.get(name)).isGreaterThanOrEqualTo(2);

        mockMvc.perform(delete("/schedule/tasks/dynamic/{name}", name))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("已取消"));

        // 已经取消过一次，第二次找不到任务
        mockMvc.perform(delete("/schedule/tasks/dynamic/{name}", name))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("任务不存在"));
    }

    @Test
    @DisplayName("重复注册同名任务会被拒绝")
    void duplicateRegistrationRejected() {
        dynamicTaskService.register("dupTask", "0/5 * * * * ?");

        assertThatThrownBy(() -> dynamicTaskService.register("dupTask", "0/5 * * * * ?"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("任务已存在");

        dynamicTaskService.cancel("dupTask");
    }

    @Test
    @DisplayName("非法 cron 在注册时被拒，接口返回 success=false 而不是 500")
    void invalidCronReturnsFalse() throws Exception {
        mockMvc.perform(post("/schedule/tasks/dynamic")
                        .param("name", "badCron")
                        .param("cron", "这不是 cron"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("天气接口在模拟模式下返回今天的日期")
    void weatherEndpointReturnsToday() throws Exception {
        mockMvc.perform(get("/schedule/weather"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.fxDate").value(LocalDate.now().toString()))
                .andExpect(jsonPath("$.data.textDay").value("多云"));
    }

    @Test
    @DisplayName("手动发送天气邮件：模拟通道下返回成功，不真的连 SMTP")
    void sendWeatherMailInMockMode() throws Exception {
        mockMvc.perform(post("/schedule/weather/send"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
