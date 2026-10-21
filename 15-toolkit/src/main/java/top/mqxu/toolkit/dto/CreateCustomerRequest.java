package top.mqxu.toolkit.dto;

/**
 * 创建客户
 *
 * @param name     姓名
 * @param phone    手机号
 * @param idCard   身份证号
 * @param password 明文密码
 * @param level    等级，normal / vip
 * @author mqxu
 * @date 2026-10-21
 */
public record CreateCustomerRequest(String name, String phone, String idCard,
                                    String password, String level) {
}
