package top.mqxu.schedule;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import top.mqxu.schedule.service.TaskExecutionRecorder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 执行记录器的单元测试：不启动 Spring，直接 new 一个对象。
 *
 * @author mqxu
 */
class TaskExecutionRecorderTest {

    private final TaskExecutionRecorder recorder = new TaskExecutionRecorder();

    @Test
    @DisplayName("记录按时间倒序返回，最近的排在最前")
    void recentReturnsNewestFirst() {
        recorder.record("taskA", LocalDateTime.now(), 10, true);
        recorder.record("taskB", LocalDateTime.now(), 20, true);
        recorder.record("taskC", LocalDateTime.now(), 30, true);

        List<TaskExecutionRecorder.Execution> recent = recorder.recent(2);

        assertThat(recent).hasSize(2);
        assertThat(recent.get(0).taskName()).isEqualTo("taskC");
        assertThat(recent.get(1).taskName()).isEqualTo("taskB");
    }

    @Test
    @DisplayName("按任务名汇总：次数、平均耗时、执行线程")
    void statsGroupByTaskName() {
        recorder.record("job", LocalDateTime.now(), 10, true);
        recorder.record("job", LocalDateTime.now(), 30, true);
        recorder.record("other", LocalDateTime.now(), 100, true);

        List<Map<String, Object>> stats = recorder.stats();
        Map<String, Object> job = stats.stream()
                .filter(m -> "job".equals(m.get("任务名")))
                .findFirst()
                .orElseThrow();

        assertThat(job.get("执行次数")).isEqualTo(2);
        assertThat(job.get("平均耗时ms")).isEqualTo(20L);
        assertThat(String.valueOf(job.get("执行线程"))).isNotEmpty();
    }

    @Test
    @DisplayName("只保留最近 200 条，超出的从尾部丢弃")
    void keepsAtMostCapacity() {
        for (int i = 0; i < 210; i++) {
            recorder.record("flood", LocalDateTime.now(), i, true);
        }

        List<TaskExecutionRecorder.Execution> all = recorder.recent(1000);

        assertThat(all).hasSize(200);
        // 最后写入的那条 id 最大，应该还在；最早的那 10 条已经被丢掉
        assertThat(all.get(0).id()).isEqualTo(210);
        assertThat(all.get(199).id()).isEqualTo(11);
    }
}
