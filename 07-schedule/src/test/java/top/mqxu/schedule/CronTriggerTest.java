package top.mqxu.schedule;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.scheduling.support.SimpleTriggerContext;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * cron 表达式的解析与触发时间计算。不启动 Spring，纯单元测试。
 *
 * @author mqxu
 */
class CronTriggerTest {

    /** 把"某天某时某分某秒"换算成 Instant，方便构造触发上下文 */
    private Instant at(int hour, int minute, int second) {
        return ZonedDateTime.of(2026, 11, 4, hour, minute, second, 0, ZoneId.systemDefault()).toInstant();
    }

    /** 用给定的"上一次执行时间"算下一次触发时刻 */
    private Instant next(String cron, Instant last) {
        SimpleTriggerContext context = new SimpleTriggerContext(last, last, last);
        return new CronTrigger(cron, ZoneId.systemDefault()).nextExecution(context);
    }

    @Test
    @DisplayName("每 10 秒一次：05 秒触发后，下一次是 10 秒")
    void everyTenSeconds() {
        Instant next = next("0/10 * * * * ?", at(9, 0, 5));

        assertThat(next).isNotNull();
        assertThat(next.atZone(ZoneId.systemDefault()).getSecond()).isEqualTo(10);
    }

    @Test
    @DisplayName("每天 9:22 一次：当天 10 点触发后，下一次是第二天 9:22")
    void dailyAtNineTwentyTwo() {
        Instant next = next("0 22 9 * * ?", at(10, 0, 0));

        assertThat(next).isNotNull();
        ZonedDateTime time = next.atZone(ZoneId.systemDefault());
        assertThat(time.getHour()).isEqualTo(9);
        assertThat(time.getMinute()).isEqualTo(22);
        assertThat(time.getDayOfMonth()).isEqualTo(5);
    }

    @Test
    @DisplayName("工作日 9 点：周五触发后，下一次跳过周六周日到周一")
    void workdaySkipsWeekend() {
        // 2026-11-06 是周五
        Instant friday = ZonedDateTime.of(2026, 11, 6, 9, 0, 0, 0, ZoneId.systemDefault()).toInstant();
        Instant next = next("0 0 9 ? * MON-FRI", friday);

        assertThat(next).isNotNull();
        ZonedDateTime time = next.atZone(ZoneId.systemDefault());
        assertThat(time.getDayOfWeek().getValue()).isEqualTo(1);
        assertThat(time.getDayOfMonth()).isEqualTo(9);
    }

    @Test
    @DisplayName("写错的表达式在构造时就被拒，不会等到触发才报错")
    void invalidExpressionFailsFast() {
        assertThatThrownBy(() -> new CronTrigger("0/10 * * * *"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
