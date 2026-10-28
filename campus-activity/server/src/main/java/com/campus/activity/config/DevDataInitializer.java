package com.campus.activity.config;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campus.activity.entity.Activity;
import com.campus.activity.enums.ActivityStatus;
import com.campus.activity.mapper.ActivityMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 演示数据保鲜：dev 模式下如果示例活动全部过期，把它们整体平移到未来，
 * 避免隔天打开演示数据全是“已结束”。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "campus", name = "dev-mode", havingValue = "true")
public class DevDataInitializer implements ApplicationRunner {

    private final ActivityMapper activityMapper;

    @Override
    public void run(ApplicationArguments args) {
        LocalDateTime now = LocalDateTime.now();
        List<Activity> activities = activityMapper.selectList(Wrappers.<Activity>lambdaQuery()
                .in(Activity::getStatus, ActivityStatus.PUBLISHED.getCode(),
                        ActivityStatus.ONGOING.getCode(), ActivityStatus.FINISHED.getCode()));

        boolean allExpired = !activities.isEmpty()
                && activities.stream().allMatch(a -> a.getEndTime() != null && a.getEndTime().isBefore(now));
        if (!allExpired) {
            return;
        }

        for (Activity activity : activities) {
            long days = Duration.between(activity.getStartTime(), now).toDays() + 2;
            activity.setStartTime(activity.getStartTime().plusDays(days));
            activity.setEndTime(activity.getEndTime().plusDays(days));
            activity.setSignupStartTime(activity.getSignupStartTime().plusDays(days));
            activity.setSignupEndTime(activity.getSignupEndTime().plusDays(days));
            if (activity.getCancelDeadline() != null) {
                activity.setCancelDeadline(activity.getCancelDeadline().plusDays(days));
            }
            activity.setStatus(ActivityStatus.PUBLISHED.getCode());
            activityMapper.updateById(activity);
        }
        log.info("演示数据已平移 {} 天，共 {} 场活动", "2 起", activities.size());
    }
}
