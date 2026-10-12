package top.mqxu.mybatis.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.mqxu.mybatis.entity.Course;
import top.mqxu.mybatis.service.CourseService;

import java.util.List;

/**
 * 课程接口，演示批量新增与批量删除
 *
 * @author mqxu
 */
@RestController
@RequestMapping("/courses")
@RequiredArgsConstructor
public class CourseController {

    private final CourseService courseService;

    /**
     * 批量新增，返回成功条数
     */
    @PostMapping("/batch")
    public int createBatch(@RequestBody List<Course> courses) {
        courseService.saveCourses(courses);
        return courses.size();
    }

    /**
     * 全部课程
     */
    @GetMapping
    public List<Course> list() {
        return courseService.listCourses();
    }

    /**
     * 按主键查询
     */
    @GetMapping("/{id}")
    public Course getById(@PathVariable Long id) {
        return courseService.getById(id);
    }

    /**
     * 批量删除，ids 用逗号分隔，如 4,5
     */
    @DeleteMapping("/batch")
    public boolean deleteBatch(@RequestParam List<Long> ids) {
        return courseService.removeCourses(ids);
    }
}
