package top.mqxu.zhihu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户。
 *
 * @author mqxu
 */
@Data
@TableName("z_user")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    /**
     * BCrypt 密文。任何时候都不要把它放进返回给前端的对象里。
     */
    private String password;

    private String nickname;

    private String avatar;

    private String bio;

    private LocalDateTime createdAt;
}
