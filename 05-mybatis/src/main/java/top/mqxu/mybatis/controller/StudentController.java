package top.mqxu.mybatis.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.mqxu.mybatis.entity.Student;
import top.mqxu.mybatis.service.StudentService;
import top.mqxu.mybatis.vo.ScoreSheetVO;

import java.util.List;
import java.util.Map;

/**
 * 学生成绩接口
 *
 * <p>每个方法对应教程里的一个小节，课堂上按顺序敲 URL 就能看到效果。</p>
 *
 * @author mqxu
 */
@RestController
@RequestMapping("/students")
@RequiredArgsConstructor
public class StudentController {

    private final StudentService studentService;

    /**
     * 全部学生
     */
    @GetMapping
    public List<Student> list() {
        return studentService.listAll();
    }

    /**
     * 按班级查，例如 /students/class?className=软件2301
     */
    @GetMapping("/class")
    public List<Student> listByClass(@RequestParam String className) {
        return studentService.listByClass(className);
    }

    /**
     * 按姓名模糊查，例如 /students/search?name=张
     */
    @GetMapping("/search")
    public List<Student> search(@RequestParam(required = false) String name) {
        return studentService.searchByName(name);
    }

    /**
     * 按年龄区间查，例如 /students/age-range?minAge=19&maxAge=20
     */
    @GetMapping("/age-range")
    public List<Student> listByAgeRange(@RequestParam(required = false) Integer minAge,
                                        @RequestParam(required = false) Integer maxAge) {
        return studentService.listByAgeRange(minAge, maxAge);
    }

    /**
     * 分页，例如 /students/page?current=1&size=2
     */
    @GetMapping("/page")
    public Page<Student> page(@RequestParam(defaultValue = "1") long current,
                              @RequestParam(defaultValue = "2") long size) {
        return studentService.pageStudents(current, size);
    }

    /**
     * 只要姓名这一列
     */
    @GetMapping("/names")
    public List<String> names() {
        return studentService.listNames();
    }

    /**
     * 每个学生的选课门数、总分、平均分
     */
    @GetMapping("/statistics")
    public List<Map<String, Object>> statistics() {
        return studentService.scoreStat();
    }

    /**
     * 某个学生的成绩单。mode 传 steps 走分步查询，传 join 走一条 SQL，
     * 两种写法的返回结果一模一样，方便对照
     */
    @GetMapping("/{id}/scores")
    public List<ScoreSheetVO> scores(@PathVariable Long id,
                                     @RequestParam(defaultValue = "steps") String mode) {
        return "join".equals(mode)
                ? studentService.scoreSheetByJoin(id)
                : studentService.scoreSheetBySteps(id);
    }

    /**
     * 按 id 查一个学生，放在最后，避免把 /class、/page 这些路径抢走
     */
    @GetMapping("/{id}")
    public Student get(@PathVariable Long id) {
        return studentService.getStudent(id);
    }
}
