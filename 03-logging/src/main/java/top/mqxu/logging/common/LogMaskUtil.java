package top.mqxu.logging.common;

/**
 * 日志脱敏工具
 *
 * @author mqxu
 */
public final class LogMaskUtil {

    private LogMaskUtil() {
    }

    public static String maskPhone(String phone) {
        if (phone == null || phone.length() != 11) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }
}