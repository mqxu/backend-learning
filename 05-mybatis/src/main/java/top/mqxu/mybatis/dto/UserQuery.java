package top.mqxu.mybatis.dto;

import lombok.Data;

/**
 * 用户列表查询条件，字段全为空时等价于查全部
 *
 * @author mqxu
 */
@Data
public class UserQuery {

    /** 关键词，匹配 username 或 nickname */
    private String keyword;

    /** 性别 */
    private Integer gender;

    /** 会员等级 */
    private String memberLevel;

    /** 是否会员 */
    private Boolean vip;

    /** 状态 */
    private Integer status;

    /** 最小年龄 */
    private Integer minAge;

    /** 最大年龄 */
    private Integer maxAge;
}
