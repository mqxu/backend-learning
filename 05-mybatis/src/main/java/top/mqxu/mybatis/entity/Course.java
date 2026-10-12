package top.mqxu.mybatis.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 课程实体，对应 t_course
 *
 * <p>字段刻意保持简单，没有审计字段与逻辑删除标记，
 * 用来演练 {@code saveBatch} 与 {@code removeByIds}，
 * 顺带验证自动填充器对缺少 updateTime 属性的实体不会报错。</p>
 *
 * @author mqxu
 */
@Data
@TableName("t_course")
public class Course {

    /**
     * BIGINT UNSIGNED —— 自增主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * VARCHAR(64) —— 课程名，建了唯一索引
     */
    private String name;

    /**
     * VARCHAR(32) —— 授课教师
     */
    private String teacher;

    /**
     * TINYINT UNSIGNED —— 学分
     */
    private Integer credit;
}
