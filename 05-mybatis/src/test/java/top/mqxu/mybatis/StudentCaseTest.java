package top.mqxu.mybatis;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import top.mqxu.mybatis.entity.Student;
import top.mqxu.mybatis.service.StudentService;
import top.mqxu.mybatis.vo.ScoreSheetVO;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 学生成绩案例的验证用例
 *
 * <p>对应教程 docs/05-mybatis-case.md。每条用例前先把三张表清空再插入固定数据，
 * 这样断言可以写具体的数字，不受启动时 data.sql 的影响。</p>
 *
 * <p>测试数据：</p>
 * <pre>
 * t_student  id=1 张三 20 软件2301
 *            id=2 李四 19 软件2301
 *            id=3 王五 21 软件2302
 *            id=4 赵六 20 软件2301  deleted = 1（已逻辑删除）
 * t_course   id=1 后端工程化开发 4 学分
 *            id=2 前端工程化开发 3 学分
 * t_score    id=1 张三-后端 88.50
 *            id=2 张三-前端 92.00
 *            id=3 李四-后端 75.00
 *            id=4 王五-后端 60.00
 *            id=5 赵六-后端 99.00（学生的记录已删除，成绩还在）
 * </pre>
 *
 * @author mqxu
 */
@SpringBootTest
class StudentCaseTest {

    @Resource
    private StudentService studentService;

    @Resource
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void prepareData() {
        jdbcTemplate.execute("TRUNCATE TABLE t_score");
        jdbcTemplate.execute("TRUNCATE TABLE t_student");
        jdbcTemplate.execute("TRUNCATE TABLE t_course");

        jdbcTemplate.update("""
                INSERT INTO t_course (id, name, teacher, credit)
                VALUES (1, '后端工程化开发', '陶老师', 4),
                       (2, '前端工程化开发', '陶老师', 3)
                """);

        jdbcTemplate.update("""
                INSERT INTO t_student (id, name, gender, age, class_name, create_time, update_time, deleted)
                VALUES (1, '张三', 1, 20, '软件2301', NOW(), NOW(), 0),
                       (2, '李四', 2, 19, '软件2301', NOW(), NOW(), 0),
                       (3, '王五', 1, 21, '软件2302', NOW(), NOW(), 0),
                       (4, '赵六', 1, 20, '软件2301', NOW(), NOW(), 1)
                """);

        jdbcTemplate.update("""
                INSERT INTO t_score (id, student_id, course_id, score, create_time, update_time, deleted)
                VALUES (1, 1, 1, 88.50, NOW(), NOW(), 0),
                       (2, 1, 2, 92.00, NOW(), NOW(), 0),
                       (3, 2, 1, 75.00, NOW(), NOW(), 0),
                       (4, 3, 1, 60.00, NOW(), NOW(), 0),
                       (5, 4, 1, 99.00, NOW(), NOW(), 0)
                """);
    }

    /* ==================== 单表查询 ==================== */

    @Test
    @DisplayName("按 id 查：存在就返回对象，不存在返回 null")
    void getById() {
        assertThat(studentService.getStudent(1L).getName()).isEqualTo("张三");
        assertThat(studentService.getStudent(99999L)).isNull();
    }

    @Test
    @DisplayName("逻辑删除的学生查不出来：id = 4 明明在库里，getById 返回 null")
    void logicDeletedStudentIsInvisible() {
        Integer stillInTable = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_student WHERE id = 4", Integer.class);
        assertThat(stillInTable).isEqualTo(1);

        assertThat(studentService.getStudent(4L)).isNull();
    }

    @Test
    @DisplayName("查全部只返回 3 条：已删除的那条被自动过滤")
    void listAll() {
        List<Student> students = studentService.listAll();
        assertThat(students).hasSize(3);
        assertThat(students).extracting(Student::getName)
                .containsExactly("张三", "李四", "王五");
    }

    @Test
    @DisplayName("按班级查：软件2301 有 2 人")
    void listByClass() {
        List<Student> students = studentService.listByClass("软件2301");
        assertThat(students).hasSize(2);
        assertThat(students).extracting(Student::getName)
                .containsExactly("张三", "李四");
    }

    @Test
    @DisplayName("按姓名模糊查：查“张”只出一条")
    void searchByName() {
        assertThat(studentService.searchByName("张")).hasSize(1);
        assertThat(studentService.searchByName("张").get(0).getName()).isEqualTo("张三");
    }

    @Test
    @DisplayName("按年龄区间查：19 到 20 岁有 2 人，按年龄升序")
    void listByAgeRange() {
        List<Student> students = studentService.listByAgeRange(19, 20);
        assertThat(students).extracting(Student::getAge)
                .containsExactly(19, 20);
    }

    @Test
    @DisplayName("分页：总数 3 条，每页 2 条，第一页拿到 2 条")
    void pageStudents() {
        Page<Student> page = studentService.pageStudents(1, 2);
        assertThat(page.getTotal()).isEqualTo(3);
        assertThat(page.getRecords()).hasSize(2);
        assertThat(page.getPages()).isEqualTo(2);
    }

    @Test
    @DisplayName("只查一列：返回的是姓名列表，不是学生对象")
    void listNames() {
        assertThat(studentService.listNames())
                .containsExactly("张三", "李四", "王五");
    }

    /* ==================== 分组统计 ==================== */

    @Test
    @DisplayName("按学生分组统计成绩：已删除学生的成绩仍会被算进去")
    void scoreStat() {
        List<Map<String, Object>> rows = studentService.scoreStat();

        // 4 个 student_id 都在 t_score 里出现过，其中一个（4）在学生表里已经删除
        assertThat(rows).hasSize(4);

        Map<String, Object> first = rows.get(0);
        // 总分最高的是张三：88.50 + 92.00
        assertThat(((Number) first.get("student_id")).longValue()).isEqualTo(1L);
        assertThat(first.get("course_count").toString()).isEqualTo("2");
        assertThat(first.get("total_score").toString()).isEqualTo("180.50");
        assertThat(first.get("avg_score").toString()).isEqualTo("90.25");
    }

    /* ==================== 多表查询 ==================== */

    @Test
    @DisplayName("分步查询成绩单：张三 2 条，带上了课程名")
    void scoreSheetBySteps() {
        List<ScoreSheetVO> sheet = studentService.scoreSheetBySteps(1L);

        assertThat(sheet).hasSize(2);
        assertThat(sheet).extracting(ScoreSheetVO::getStudentName).containsOnly("张三");
        assertThat(sheet).extracting(ScoreSheetVO::getCourseName)
                .containsExactly("后端工程化开发", "前端工程化开发");
        assertThat(sheet.get(0).getScore()).isEqualByComparingTo("88.50");
    }

    @Test
    @DisplayName("join 查询成绩单：结果和分步查询完全一致")
    void scoreSheetByJoin() {
        List<ScoreSheetVO> join = studentService.scoreSheetByJoin(1L);
        List<ScoreSheetVO> steps = studentService.scoreSheetBySteps(1L);

        assertThat(join).hasSize(2);
        assertThat(join).usingRecursiveComparison().isEqualTo(steps);
    }

    @Test
    @DisplayName("已删除学生的成绩单两种写法都查不到：一条靠 null 判断，一条靠 SQL 里的 deleted 条件")
    void deletedStudentHasNoSheet() {
        assertThat(studentService.scoreSheetBySteps(4L)).isEmpty();
        assertThat(studentService.scoreSheetByJoin(4L)).isEmpty();
    }

    @Test
    @DisplayName("不存在的学生：查成绩单返回空列表，不抛异常")
    void unknownStudentReturnsEmpty() {
        assertThat(studentService.scoreSheetBySteps(99999L)).isEmpty();
        assertThat(studentService.scoreSheetByJoin(99999L)).isEmpty();
    }
}
