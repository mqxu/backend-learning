package top.mqxu.toolkit.support;

import lombok.Data;

/**
 * 客户存储对象
 *
 * @author mqxu
 * @date 2026-10-21
 * @description 手机号、身份证以密文保存，密码只保存 BCrypt 哈希
 */
@Data
public class Customer {

    private String id;
    private String name;
    private String phoneEncrypted;
    private String idCardEncrypted;
    private String passwordHash;
    private String level;
    private long createdAt;
}
