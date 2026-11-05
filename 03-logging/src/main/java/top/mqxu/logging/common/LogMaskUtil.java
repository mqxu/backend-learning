package top.mqxu.logging.common;

/**
 * 日志脱敏工具。凡是会写进日志的个人信息，先过一遍这里。
 *
 * @author mqxu
 */
public final class LogMaskUtil {

    private LogMaskUtil() {
    }

    /** 13812345678 -> 138****7278 */
    public static String maskPhone(String phone) {
        if (phone == null || phone.length() != 11) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }

    /** 320102200510241234 -> 320102********1234 */
    public static String maskIdCard(String idCard) {
        if (idCard == null || idCard.length() < 10) {
            return idCard;
        }
        return idCard.substring(0, 6) + "********" + idCard.substring(idCard.length() - 4);
    }

    /** zhangsan@qq.com -> z*******@qq.com */
    public static String maskEmail(String email) {
        if (email == null) {
            return null;
        }
        int at = email.indexOf('@');
        if (at <= 1) {
            return email;
        }
        return email.charAt(0) + "*".repeat(at - 1) + email.substring(at);
    }

    /** 6222020200112345678 -> ************5678 */
    public static String maskBankCard(String card) {
        if (card == null || card.length() < 8) {
            return card;
        }
        return "*".repeat(card.length() - 4) + card.substring(card.length() - 4);
    }

    /** 张三 -> 张* */
    public static String maskName(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        return name.charAt(0) + "*".repeat(Math.max(1, name.length() - 1));
    }
}
