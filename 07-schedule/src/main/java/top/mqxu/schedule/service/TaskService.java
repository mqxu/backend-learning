package top.mqxu.schedule.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 三种触发方式的对照示例。
 * 每个任务执行完都把结果写进 TaskExecutionRecorder，接口里能查到时间与线程名。
 *
 * @author mqxu
 * @date 2026/10/21
 * @description TaskService
 **/
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskExecutionRecorder recorder;

    private final AtomicInteger heartBeatTimes = new AtomicInteger();
    private final AtomicInteger cleanTimes = new AtomicInteger();
    private final AtomicInteger reportTimes = new AtomicInteger();

    /**
     * 固定频率：上次开始执行后固定 3 秒再触发。
     * 周期从"开始"算起，所以任务本身耗时越长，两次执行之间的间隔越短。
     */
    @Scheduled(fixedRate = 3000, initialDelay = 1000)
    public void heartBeat() {
        LocalDateTime start = LocalDateTime.now();
        int times = heartBeatTimes.incrementAndGet();
        log.info("【fixedRate】心跳第 {} 次，线程 {}", times, Thread.currentThread().getName());
        recorder.record("heartBeat", start, 0, true);
    }

    /**
     * 固定间隔：上次执行结束后再等 5 秒才触发。
     * 周期从"结束"算起，任务耗时长也不会造成任务堆积。
     */
    @Scheduled(fixedDelay = 5000, initialDelay = 2000)
    public void cleanExpiredData() {
        LocalDateTime start = LocalDateTime.now();
        long begin = System.currentTimeMillis();
        int times = cleanTimes.incrementAndGet();
        log.info("【fixedDelay】清理临时数据第 {} 次，线程 {}", times, Thread.currentThread().getName());
        recorder.record("cleanExpiredData", start, System.currentTimeMillis() - begin, true);
    }

    /**
     * cron：按日历规则触发，这里是每 10 秒一次。格式为 秒 分 时 日 月 周，六位。
     */
    @Scheduled(cron = "0/10 * * * * ?")
    public void dailyReport() {
        LocalDateTime start = LocalDateTime.now();
        int times = reportTimes.incrementAndGet();
        log.info("【cron】生成统计报表第 {} 次，线程 {}", times, Thread.currentThread().getName());
        recorder.record("dailyReport", start, 0, true);
    }
}
