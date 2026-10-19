package top.mqxu.redis.common;

import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author mqxu
 * @date 2026/10/14
 * @description CommonUtils
 **/
public class CommonUtils {

    /**
     * 校验手机号码
     *
     * @param phone 电话
     * @return boolean
     */
    public static boolean checkPhone(String phone) {
        if (phone == null || phone.length() != 11) {
            return false;
        }
        // 中国大陆手机号码的正则表达式，可能需要根据实际情况进行调整
        String regex = "^1[3-9]\\d{9}$";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(phone);
        return matcher.matches();
    }

    /**
     * 生成4位随机验证码，范围 [1000, 9999]。
     * nextInt 的右边界是开区间，写 9999 会让 9999 永远取不到，这里要多加一个数。
     *
     * @return int
     */
    public static int generateCode() {
        return ThreadLocalRandom.current().nextInt(1000, 10000);
    }

}