package top.mqxu.redis.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.mqxu.redis.entity.Goods;
import top.mqxu.redis.service.GoodsService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 用 ZSet 做销量排行榜：写入即排序，取前几名不需要每次都对数据库做排序查询
 *
 * @author mqxu
 */
@RestController
@RequestMapping("/rank")
@RequiredArgsConstructor
public class RankController {

    private static final String SALES_RANK_KEY = "rank:goods:sales";

    private final GoodsService goodsService;
    private final StringRedisTemplate stringRedisTemplate;

    /** 把数据库里的销量灌进 ZSet，做一次预热 */
    @PostMapping("/warmup")
    public Map<String, Object> warmUp() {
        List<Goods> goodsList = goodsService.listAll();
        for (Goods goods : goodsList) {
            stringRedisTemplate.opsForZSet()
                    .add(SALES_RANK_KEY, String.valueOf(goods.getId()), goods.getSales());
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("warmed", goodsList.size());
        result.put("size", stringRedisTemplate.opsForZSet().size(SALES_RANK_KEY));
        return result;
    }

    /** 给某个商品加分，ZSet 会自动把它挪到正确位置 */
    @PostMapping("/increase")
    public Map<String, Object> increase(@RequestParam Long id,
                                        @RequestParam(defaultValue = "1") double delta) {
        Double score = stringRedisTemplate.opsForZSet().incrementScore(SALES_RANK_KEY, String.valueOf(id), delta);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("goodsId", id);
        result.put("score", score);
        result.put("rank", rankOf(id));
        return result;
    }

    /** 取前若干名，分数从高到低 */
    @GetMapping("/top")
    public List<Map<String, Object>> top(@RequestParam(defaultValue = "5") int top) {
        Set<ZSetOperations.TypedTuple<String>> tuples =
                stringRedisTemplate.opsForZSet().reverseRangeWithScores(SALES_RANK_KEY, 0, top - 1L);
        if (tuples == null || tuples.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        int rank = 1;
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("rank", rank++);
            item.put("goodsId", tuple.getValue());
            item.put("sales", tuple.getScore());
            Goods goods = tuple.getValue() == null ? null : goodsService.getFromDb(Long.valueOf(tuple.getValue()));
            item.put("name", goods == null ? null : goods.getName());
            result.add(item);
        }
        return result;
    }

    /** 查某个商品当前排第几，从 1 开始，没上榜返回 -1 */
    @GetMapping("/rank-of")
    public Map<String, Object> rankOfGoods(@RequestParam Long id) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("goodsId", id);
        result.put("rank", rankOf(id));
        result.put("sales", stringRedisTemplate.opsForZSet().score(SALES_RANK_KEY, String.valueOf(id)));
        return result;
    }

    private long rankOf(Long id) {
        Long rank = stringRedisTemplate.opsForZSet().reverseRank(SALES_RANK_KEY, String.valueOf(id));
        return rank == null ? -1 : rank + 1;
    }
}
