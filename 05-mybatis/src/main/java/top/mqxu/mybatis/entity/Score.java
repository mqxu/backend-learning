package top.mqxu.mybatis.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 成绩实体，对应 t_score 表
 *
 * <p>一条成绩记录只存两个 id：student_id 和 course_id，不存学生名和课程名。
 * 这么设计是为了避免同一份数据存两份，代价是查询时要自己把名字关联出来，
 * 这也是后面多表查询要解决的问题。</p>
 *
 * @author mqxu
 */
@Data
@TableName("t_score")
public class Score {

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 学生 id，对应 t_student.id
     */
    private Long studentId;

    /**
     * 课程 id，对应 t_course.id
     */
    private Long courseId;

    /**
     * 分数。金额、分数这类不能有误差的数字用 DECIMAL，Java 里对应 BigDecimal
     */
    private BigDecimal score;

    /**
     * 创建时间，新增时自动填
     */
    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /**
     * 更新时间，新增和更新时都自动填
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    /**
     * 逻辑删除标记
     */
    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;
}
