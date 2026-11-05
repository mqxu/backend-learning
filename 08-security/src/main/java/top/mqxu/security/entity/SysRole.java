package top.mqxu.security.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 账号的角色与权限点
 *
 * @author mqxu
 */
@Data
@TableName("sec_role")
public class SysRole {

    /** grant_type 取值：角色 */
    public static final String TYPE_ROLE = "ROLE";

    /** grant_type 取值：细粒度权限点 */
    public static final String TYPE_PERM = "PERM";

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** ADMIN / AUDITOR / USER / ARTICLE_DELETE */
    private String roleCode;

    /** ROLE 表示要补 ROLE_ 前缀，PERM 表示原样使用 */
    private String grantType;

    /**
     * 转成 Spring Security 认识的 authority 字符串。
     * <p>
     * hasRole("ADMIN") 查的是 ROLE_ADMIN，而库里存的是 ADMIN，前缀必须在这里补；
     * hasAuthority("ARTICLE_DELETE") 原样比对，不能补。
     */
    public String toAuthority() {
        return TYPE_PERM.equals(grantType) ? roleCode : "ROLE_" + roleCode;
    }
}
