package top.mqxu.mybatis.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.baomidou.mybatisplus.extension.handlers.Jackson3TypeHandler;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * 用户实体，字段与 MySQL 常用数据类型一一对应
 *
 * @author mqxu
 */
@Data
@TableName(value = "t_user", autoResultMap = true)
public class User {

    /**
     * BIGINT UNSIGNED —— 自增主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * VARCHAR(50) —— 变长字符串，建了唯一索引
     */
    private String username;

    /**
     * CHAR(64) —— 定长字符串，存 SHA-256 摘要；只接收不返回，避免接口把摘要吐出去
     */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    /**
     * VARCHAR(50) —— 昵称
     */
    private String nickname;

    /**
     * TINYINT —— 0 未设置 / 1 男 / 2 女
     */
    private Integer gender;

    /**
     * TINYINT UNSIGNED —— 年龄
     */
    private Integer age;

    /**
     * ENUM('BRONZE','SILVER','GOLD') —— 取值受限的字符串
     */
    private String memberLevel;

    /**
     * INT —— 积分
     */
    private Integer points;

    /**
     * DECIMAL(12,2) —— 金额，定点数不丢精度
     */
    private BigDecimal balance;

    /**
     * FLOAT —— 推荐评分，近似小数
     */
    private Float score;

    /**
     * VARCHAR(20) —— 手机号
     */
    private String phone;

    /**
     * VARCHAR(100) —— 邮箱
     */
    private String email;

    /**
     * VARCHAR(255) —— 头像地址
     */
    private String avatar;

    /**
     * TEXT —— 长文本
     */
    private String intro;

    /**
     * JSON —— 标签数组。
     * 表上要开 autoResultMap = true，查询结果才会走 typeHandler 反序列化；
     * Jackson3TypeHandler 对应 Jackson 3（Spring Boot 4 自带），Jackson 2 请换成 JacksonTypeHandler。
     */
    @TableField(typeHandler = Jackson3TypeHandler.class)
    private List<String> tags;

    /**
     * DATE —— 生日
     */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate birthday;

    /**
     * TIME —— 每日上班时间
     */
    @JsonFormat(pattern = "HH:mm:ss")
    private LocalTime workTime;

    /**
     * TINYINT —— 是否会员，映射为 Boolean
     */
    private Boolean vip;

    /**
     * TINYINT —— 1 正常 / 0 禁用
     */
    private Integer status;

    /**
     * DATETIME —— 最后登录时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastLoginTime;

    /**
     * DATETIME —— 新增时自动填充
     */
    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /**
     * DATETIME —— 新增与更新时自动填充
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    /**
     * TINYINT —— 逻辑删除标记，查询自动追加 deleted = 0
     */
    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;

    /**
     * INT —— 乐观锁版本号，更新时自动带上 version 条件并把版本加一
     */
    @Version
    @TableField(fill = FieldFill.INSERT)
    private Integer version;
}
