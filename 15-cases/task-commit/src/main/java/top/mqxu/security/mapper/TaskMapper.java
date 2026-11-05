package top.mqxu.security.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import top.mqxu.security.entity.Task;
import top.mqxu.security.model.TaskRow;

import java.util.List;

/**
 * @author mqxu
 * @date 2026/10/28
 * @description 任务 Mapper
 * <p>
 * 单表操作继承 BaseMapper，联表查询用注解写 SQL。
 * MyBatis-Plus 的条件构造器只覆盖单表，涉及 join 时老老实实写 SQL 更清楚。
 **/
public interface TaskMapper extends BaseMapper<Task> {

    /**
     * 联表要查的列。下划线列名靠 map-underscore-to-camel-case 转成驼峰属性
     */
    String SELECT_COLUMNS = """
            t.id, t.title, t.content, t.owner_id, t.status, t.score, t.comment,
            t.submitted_at, t.reviewed_at,
            u.username AS owner, u.nickname AS owner_nickname
            """;

    String FROM_JOIN = " FROM sec_task t JOIN sec_user u ON u.id = t.owner_id ";

    /**
     * 全部提交，教师批改用
     */
    @Select("SELECT " + SELECT_COLUMNS + FROM_JOIN + "ORDER BY t.submitted_at DESC")
    List<TaskRow> selectAllRows();

    /**
     * 按提交人过滤。参数是登录时令牌里的用户 id，不是前端传的用户名
     */
    @Select("SELECT " + SELECT_COLUMNS + FROM_JOIN + "WHERE t.owner_id = #{ownerId} ORDER BY t.submitted_at DESC")
    List<TaskRow> selectRowsByOwnerId(@Param("ownerId") Long ownerId);

    @Select("SELECT " + SELECT_COLUMNS + FROM_JOIN + "WHERE t.id = #{id}")
    TaskRow selectRowById(@Param("id") Long id);
}
