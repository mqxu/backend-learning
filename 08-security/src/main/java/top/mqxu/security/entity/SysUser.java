package top.mqxu.security.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 账号
 *
 * @author mqxu
 */
@Data
@TableName("sec_user")
public class SysUser {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 登录名，密码登录按它查 */
    private String username;

    /** 手机号，短信登录按它查 */
    private String phone;

    /** 带前缀的密文，如 {bcrypt}$2a$10$... */
    private String password;

    private String nickname;

    /** false → 登录时抛 DisabledException */
    private Boolean enabled;

    /** true → 登录时抛 LockedException */
    private Boolean locked;

    private LocalDateTime createTime;
}
