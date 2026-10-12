package top.mqxu.mybatis.service;

import com.baomidou.mybatisplus.spring.service.IService;
import top.mqxu.mybatis.entity.Course;

import java.util.List;

/**
 * 课程服务，练习 saveBatch 与 removeByIds
 *
 * @author mqxu
 */
public interface CourseService extends IService<Course> {

    /**
     * 批量新增，底层是 saveBatch
     */
    boolean saveCourses(List<Course> courses);

    /** 按主键批量删除，底层是 removeByIds，t_course 没有逻辑删除列，这里是物理删除 */
    boolean removeCourses(List<Long> ids);

    /** 查询全部，按 id 升序 */
    List<Course> listCourses();
}
