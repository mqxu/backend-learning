package top.mqxu.schedule.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * @author mqxu
 * @date 2026/10/21
 * @description TaskService
 **/
@Service
@Slf4j
public class TaskService {

    // Cron 表达式执行：支持复杂时间规则（最灵活）
    // 场景：固定时间执行，如 cron="0 0 0 * * ?"（每天0点） cron="0 0 14 ? * MON"（星期一的14点）
    @Scheduled(cron = "0/3 * * * * ?") // 每3秒触发一次
    //@Scheduled(cron = "56 24 8 *  * ?") // 指定时间触发
    public void cronTask() {
        log.info("【Cron任务】执行时间：{},执行线程：{}", LocalDateTime.now(), Thread.currentThread().getName());
    }
}
