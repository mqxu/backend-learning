package top.mqxu.mybatis.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.mqxu.mybatis.entity.Course;
import top.mqxu.mybatis.mapper.CourseMapper;
import top.mqxu.mybatis.service.CourseService;

import java.util.List;

/**
 * 课程服务实现
 *
 * @author mqxu
 */
@Service
public class CourseServiceImpl extends ServiceImpl<CourseMapper, Course> implements CourseService {

    @Transactional(rollbackFor = Exception.class)
    @Override
    public boolean saveCourses(List<Course> courses) {
        if (courses == null || courses.isEmpty()) {
            return false;
        }
        return saveBatch(courses);
    }

    @Override
    public boolean removeCourses(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return false;
        }
        return removeByIds(ids);
    }

    @Override
    public List<Course> listCourses() {
        return list(new LambdaQueryWrapper<Course>().orderByAsc(Course::getId));
    }
}
