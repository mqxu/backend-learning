package top.mqxu.schedule.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 动态任务：不用重启就能加一个定时任务，也能随时取消。
 * @Scheduled 是编译期确定的，做不到这一点；这里用 TaskScheduler + CronTrigger 在运行期注册。
 *
 * @author mqxu
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DynamicTaskService {

    private final TaskScheduler taskScheduler;

    private final TaskExecutionRecorder recorder;

    /** 任务名 -> 已注册的调度句柄，取消时要用它 */
    private final Map<String, ScheduledFuture<?>> running = new ConcurrentHashMap<>();

    private final Map<String, AtomicInteger> counters = new ConcurrentHashMap<>();

    /**
     * 注册一个动态任务
     *
     * @param name 任务名，重复注册会被拒绝
     * @param cron cron 表达式，非法表达式会直接抛异常
     * @return 当前所有动态任务名
     */
    public Set<String> register(String name, String cron) {
        if (running.containsKey(name)) {
            throw new IllegalArgumentException("任务已存在：" + name);
        }
        // CronTrigger 在构造时校验表达式，写错会立刻抛 IllegalArgumentException
        CronTrigger trigger = new CronTrigger(cron);
        AtomicInteger counter = counters.computeIfAbsent(name, k -> new AtomicInteger());

        ScheduledFuture<?> future = taskScheduler.schedule(() -> {
            LocalDateTime start = LocalDateTime.now();
            long begin = System.currentTimeMillis();
            int times = counter.incrementAndGet();
            log.info("【动态任务】{} 第 {} 次执行，线程 {}", name, times, Thread.currentThread().getName());
            recorder.record(name, start, System.currentTimeMillis() - begin, true);
        }, trigger);

        running.put(name, future);
        log.info("动态任务已注册：{}，cron={}", name, cron);
        return running.keySet();
    }

    /**
     * 取消动态任务
     */
    public boolean cancel(String name) {
        ScheduledFuture<?> future = running.remove(name);
        if (future == null) {
            return false;
        }
        future.cancel(false);
        log.info("动态任务已取消：{}", name);
        return true;
    }

    /** 当前在跑的动态任务 */
    public Map<String, Object> list() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("运行中", running.keySet());
        result.put("各任务执行次数", counters.entrySet().stream()
                .collect(LinkedHashMap::new, (m, e) -> m.put(e.getKey(), e.getValue().get()), Map::putAll));
        return result;
    }
}
