package top.mqxu.mybatis.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import top.mqxu.mybatis.entity.Score;
import top.mqxu.mybatis.vo.ScoreSheetVO;

import java.util.List;

/**
 * 成绩 Mapper
 *
 * <p>除了继承来的单表方法，这里额外加了一个多表查询 {@link #selectScoreSheet(Long)}。
 * 多表查询 MyBatis-Plus 的通用方法帮不上忙，只能自己写 SQL，
 * 最简单的方式就是用 {@code @Select} 注解把 SQL 直接写在方法上面。</p>
 *
 * @author mqxu
 */
public interface ScoreMapper extends BaseMapper<Score> {

    /**
     * 查某个学生的成绩单：课程名、学生名、分数一起返回
     *
     * <p>三个要点：</p>
     * <ol>
     *   <li>别名要和 {@link ScoreSheetVO} 的属性名一致。数据库返回的列叫 studentName，
     *       对象里的属性也叫 studentName，MyBatis 才能自动填进去；写成 student_name 就对不上了。</li>
     *   <li>两张表的 deleted = 0 要自己写。逻辑删除只对 MyBatis-Plus 自己生成的那几条 SQL 生效，
     *       手写的 SQL 它不管。</li>
     *   <li>用 JOIN ... ON 把学生表和课程表接上，接的条件就是成绩表里存的那两个 id。</li>
     * </ol>
     *
     * @param studentId 学生 id
     * @return 该学生的所有成绩，按成绩记录 id 升序
     */
    @Select("""
            SELECT s.id        AS scoreId,
                   st.name     AS studentName,
                   c.name      AS courseName,
                   s.score     AS score
            FROM t_score s
                     JOIN t_student st ON st.id = s.student_id AND st.deleted = 0
                     JOIN t_course c ON c.id = s.course_id
            WHERE s.deleted = 0
              AND s.student_id = #{studentId}
            ORDER BY s.id
            """)
    List<ScoreSheetVO> selectScoreSheet(@Param("studentId") Long studentId);
}
