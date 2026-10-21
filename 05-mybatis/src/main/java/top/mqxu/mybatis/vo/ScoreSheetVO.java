package top.mqxu.mybatis.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 成绩单的一行：一条成绩记录，加上学生名和课程名
 *
 * <p>数据库里没有这张表，它只是查询结果的形状。
 * 实体 {@link top.mqxu.mybatis.entity.Score} 只有 studentId 和 courseId，
 * 页面上要显示名字，就需要把三张表的数据拼成这样一行。
 * 这种“只为返回给前端而建的类”，习惯上放在 vo 包。</p>
 *
 * @author mqxu
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScoreSheetVO {

    /**
     * 成绩记录自己的 id，用来区分同一个人同一门课的多次考试
     */
    private Long scoreId;

    /**
     * 学生姓名，来自 t_student.name
     */
    private String studentName;

    /**
     * 课程名，来自 t_course.name
     */
    private String courseName;

    /**
     * 分数，来自 t_score.score
     */
    private BigDecimal score;
}
