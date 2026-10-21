package top.mqxu.mybatis.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 学生实体，对应 t_student 表
 *
 * <p>类名 Student 对应表名 t_student，规则是“驼峰转下划线再加前缀”，
 * 加前缀这件事 MyBatis-Plus 猜不到，所以必须写 {@code @TableName}。</p>
 *
 * <p>每个属性名对应表里同名的列：name 对应 name，className 对应 class_name
 * （因为配置里开了 map-underscore-to-camel-case）。</p>
 *
 * @author mqxu
 */
@Data
@TableName("t_student")
public class Student {

    /**
     * 主键。IdType.AUTO 表示交给数据库自增，插入后 MyBatis-Plus 会把生成的 id 回填到对象里
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 姓名
     */
    private String name;

    /**
     * 性别，0 未知 / 1 男 / 2 女
     */
    private Integer gender;

    /**
     * 年龄
     */
    private Integer age;

    /**
     * 班级，如 软件2301
     */
    private String className;

    /**
     * 创建时间。FieldFill.INSERT 表示只在新增时自动填，更新时不碰
     */
    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /**
     * 更新时间。INSERT_UPDATE 表示新增和更新都自动填
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    /**
     * 逻辑删除标记。加上这个注解以后，delete 会变成 update deleted = 1，
     * 而所有查询会自动追加 deleted = 0，业务代码里看不到这些条件
     */
    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;
}
