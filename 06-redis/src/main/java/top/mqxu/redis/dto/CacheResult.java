package top.mqxu.redis.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 带缓存来源信息的查询结果，用来对比命中缓存与回源数据库的区别
 *
 * @author mqxu
 */
@Data
@AllArgsConstructor
public class CacheResult<T> {

    /** 业务数据，可能为 null */
    private T data;

    /** 数据来自哪里：db 表示回源数据库，cache 表示命中缓存，cache-empty 表示命中空值标记 */
    private String source;

    /** 本次调用耗时，毫秒，带小数便于对比毫秒级以内的差距 */
    private double costMs;
}
