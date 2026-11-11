package com.campus.activity.support;

/**
 * 项目内使用的 Redis key 统一定义，避免各处手写字符串导致拼错或前缀不统一。
 *
 * <p>短信相关 key 都带手机号后缀；分布式锁 key 全局唯一。</p>
 */
public final class RedisKeys {

    private RedisKeys() {
    }

    /** 验证码 Hash：field=code 验证码，field=status 状态（0 未使用 / 1 已使用） */
    private static final String SMS_CODE = "campus:sms:code:";

    /** 发送间隔：key 存在即代表还在冷却中，SET NX EX 占位 */
    private static final String SMS_INTERVAL = "campus:sms:interval:";

    /** 当日发送次数计数器，首次自增时设置 24 小时过期 */
    private static final String SMS_DAILY = "campus:sms:daily:";

    /** 验证码连续输错次数计数器 */
    private static final String SMS_FAIL = "campus:sms:fail:";

    /** 活动状态推进定时任务的分布式锁 */
    private static final String ACTIVITY_STATUS_JOB_LOCK = "campus:lock:activity-status-job";

    public static String smsCode(String phone) {
        return SMS_CODE + phone;
    }

    public static String smsInterval(String phone) {
        return SMS_INTERVAL + phone;
    }

    public static String smsDaily(String phone) {
        return SMS_DAILY + phone;
    }

    public static String smsFail(String phone) {
        return SMS_FAIL + phone;
    }

    public static String activityStatusJobLock() {
        return ACTIVITY_STATUS_JOB_LOCK;
    }
}
