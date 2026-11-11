package com.campus.activity.job;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campus.activity.entity.Activity;
import com.campus.activity.entity.ActivitySignup;
import com.campus.activity.enums.ActivityStatus;
import com.campus.activity.enums.SignupStatus;
import com.campus.activity.mapper.ActivityMapper;
import com.campus.activity.mapper.ActivitySignupMapper;
import com.campus.activity.support.RedisKeys;
import com.campus.activity.support.RedisLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 活动状态推进：
 * 1. 到开始时间，已发布 → 进行中
 * 2. 到结束时间，已发布/进行中 → 已结束
 * 3. 活动结束后，报名成功但未签到的记录 → 未到场
 *
 * <p>多实例部署时，每个实例都会触发 @Scheduled。
 * 这里用 Redis 分布式锁保证同一时刻只有一个实例真正执行，
 * 抢不到锁直接跳过本次，避免同一场活动被并发更新两遍。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ActivityStatusJob {

    /** 锁自动释放时间：任务通常几秒内跑完，120 秒足够兜底，避免实例崩溃后永久占锁 */
    private static final long LOCK_LEASE_SECONDS = 120;

    private final ActivityMapper activityMapper;
    private final ActivitySignupMapper signupMapper;
    private final RedisLock redisLock;

    @Scheduled(cron = "0 */5 * * * ?")
    @Transactional
    public void advanceStatus() {
        String lockKey = RedisKeys.activityStatusJobLock();
        // 抢不到锁说明其他实例正在执行，本实例直接跳过（不等待，避免定时任务堆积）
        String lockToken = redisLock.tryLock(lockKey, LOCK_LEASE_SECONDS);
        if (lockToken == null) {
            log.info("活动状态推进任务未抢到分布式锁，跳过本次执行");
            return;
        }
        try {
            doAdvanceStatus();
        } finally {
            redisLock.unlock(lockKey, lockToken);
        }
    }

    /** 实际的状态推进逻辑，必须持有分布式锁才能调用 */
    private void doAdvanceStatus() {
        LocalDateTime now = LocalDateTime.now();

        List<Activity> started = activityMapper.selectList(Wrappers.<Activity>lambdaQuery()
                .eq(Activity::getStatus, ActivityStatus.PUBLISHED.getCode())
                .le(Activity::getStartTime, now));
        for (Activity activity : started) {
            activity.setStatus(ActivityStatus.ONGOING.getCode());
            activityMapper.updateById(activity);
        }

        List<Activity> finished = activityMapper.selectList(Wrappers.<Activity>lambdaQuery()
                .in(Activity::getStatus, ActivityStatus.PUBLISHED.getCode(), ActivityStatus.ONGOING.getCode())
                .le(Activity::getEndTime, now));
        for (Activity activity : finished) {
            activity.setStatus(ActivityStatus.FINISHED.getCode());
            activityMapper.updateById(activity);

            ActivitySignup update = new ActivitySignup();
            update.setStatus(SignupStatus.NO_SHOW.getCode());
            int rows = signupMapper.update(update, Wrappers.<ActivitySignup>lambdaUpdate()
                    .eq(ActivitySignup::getActivityId, activity.getId())
                    .eq(ActivitySignup::getStatus, SignupStatus.SUCCESS.getCode()));
            if (rows > 0) {
                log.info("活动 {} 结束，{} 条报名标记为未到场", activity.getId(), rows);
            }
        }

        if (!started.isEmpty() || !finished.isEmpty()) {
            log.info("状态推进完成: 开始 {} 场, 结束 {} 场", started.size(), finished.size());
        }
    }
}
