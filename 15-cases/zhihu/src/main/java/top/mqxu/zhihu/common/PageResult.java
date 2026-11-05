package top.mqxu.zhihu.common;

import lombok.Data;

import java.util.List;

/**
 * 分页结果。不要把 MyBatis-Plus 的 Page 直接返回给前端：
 * 它带了一堆 current、size、orders 之类的内部字段，还把总页数的计算规则暴露出去。
 *
 * @author mqxu
 */
@Data
public class PageResult<T> {

    private List<T> records;
    private long total;
    private long pageNum;
    private long pageSize;
    private long pages;

    public static <T> PageResult<T> of(List<T> records, long total, long pageNum, long pageSize) {
        PageResult<T> result = new PageResult<>();
        result.setRecords(records);
        result.setTotal(total);
        result.setPageNum(pageNum);
        result.setPageSize(pageSize);
        // 向上取整：21 条 / 每页 10 = 3 页
        result.setPages((total + pageSize - 1) / pageSize);
        return result;
    }

    public static <T> PageResult<T> empty(long pageNum, long pageSize) {
        return of(List.of(), 0, pageNum, pageSize);
    }
}
