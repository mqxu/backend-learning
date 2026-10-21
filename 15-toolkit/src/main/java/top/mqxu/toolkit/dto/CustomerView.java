package top.mqxu.toolkit.dto;

/**
 * 客户视图
 *
 * @param id     客户 ID
 * @param name   姓名
 * @param phone  手机号，按角色脱敏
 * @param idCard 身份证，按角色脱敏
 * @param age    年龄，由身份证算出
 * @param gender 性别，由身份证算出
 * @param level  等级
 * @author mqxu
 * @date 2026-10-21
 */
public record CustomerView(String id, String name, String phone, String idCard,
                           int age, String gender, String level) {
}
