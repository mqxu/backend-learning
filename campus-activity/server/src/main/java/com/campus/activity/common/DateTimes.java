package com.campus.activity.common;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 时间格式统一在这里处理，接口出参与入参都用字符串，避免前端处理时区与格式差异。
 */
public final class DateTimes {

    private static final DateTimeFormatter STANDARD = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter MINUTE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private DateTimes() {
    }

    /** 支持 2026-10-31 14:00:00 / 2026-10-31T14:00:00 / 2026-10-31 14:00 */
    public static LocalDateTime parse(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String value = text.trim();
        try {
            if (value.length() == 16) {
                return LocalDateTime.parse(value, MINUTE);
            }
            if (value.contains("T")) {
                return LocalDateTime.parse(value);
            }
            return LocalDateTime.parse(value, STANDARD);
        } catch (Exception e) {
            throw new BizException(ResultCode.PARAM_ERROR, "时间格式不正确: " + text);
        }
    }

    public static String format(LocalDateTime time) {
        return time == null ? null : time.format(STANDARD);
    }

    public static String formatMinute(LocalDateTime time) {
        return time == null ? null : time.format(MINUTE);
    }
}
