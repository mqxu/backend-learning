package top.mqxu.security.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author mqxu
 * @date 2026/10/28
 * @description 账号实体，对应 sec_user 表
 * <p>
 * 实体只在 service 与 mapper 之间传递，不作为接口出参。
 * 出参统一用 UserProfile，避免 password 顺着实体漏出去。
 **/
@Data
@TableName("sec_user")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    /**
     * BCrypt 密文。任何接口都不返回这一列
     */
    private String password;

    private String nickname;

    /**
     * 1 启用 / 0 停用。停用账号密码对也登不进来
     */
    private Boolean enabled;

    private LocalDateTime createdAt;
}
