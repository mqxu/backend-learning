package top.mqxu.redis.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import top.mqxu.redis.entity.Goods;

/**
 * 商品 Mapper
 *
 * @author mqxu
 */
@Mapper
public interface GoodsMapper extends BaseMapper<Goods> {
}
