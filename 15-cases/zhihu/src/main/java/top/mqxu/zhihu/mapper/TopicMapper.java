package top.mqxu.zhihu.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import top.mqxu.zhihu.entity.Topic;

/**
 * Topic 的基础 CRUD 由 BaseMapper 提供，这里不写方法。
 *
 * @author mqxu
 */
@Mapper
public interface TopicMapper extends BaseMapper<Topic> {
}
