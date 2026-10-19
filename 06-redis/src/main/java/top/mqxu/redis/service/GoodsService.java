package top.mqxu.redis.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import top.mqxu.redis.entity.Goods;
import top.mqxu.redis.mapper.GoodsMapper;

import java.math.BigDecimal;
import java.util.List;

/**
 * 商品查询，每次直接读库，作为有缓存版本的对照
 *
 * @author mqxu
 */
@Service
@RequiredArgsConstructor
public class GoodsService {

    private final GoodsMapper goodsMapper;

    /** 按主键查商品，不走缓存 */
    public Goods getFromDb(Long id) {
        return goodsMapper.selectById(id);
    }

    /** 查全部商品 */
    public List<Goods> listAll() {
        return goodsMapper.selectList(null);
    }

    /** 改价格，只动数据库 */
    public boolean updatePrice(Long id, BigDecimal price) {
        Goods goods = new Goods();
        goods.setId(id);
        goods.setPrice(price);
        return goodsMapper.updateById(goods) > 0;
    }
}
