package top.mqxu.mybatis.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import top.mqxu.mybatis.entity.Student;

/**
 * 学生 Mapper
 *
 * <p>继承 BaseMapper 之后，增删改查的方法全都有了，接口里一个字都不用写。
 * MyBatis-Plus 会在启动时扫描到这个接口，自动为它生成实现。</p>
 *
 * @author mqxu
 */
public interface StudentMapper extends BaseMapper<Student> {
}
