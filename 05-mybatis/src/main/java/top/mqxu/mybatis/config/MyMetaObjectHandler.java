package top.mqxu.mybatis.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 字段自动填充：新增时写 createTime / updateTime / deleted / version，更新时只刷新 updateTime
 *
 * @author mqxu
 */
@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        strictInsertFill(metaObject, "createTime", LocalDateTime::now, LocalDateTime.class);
        strictInsertFill(metaObject, "updateTime", LocalDateTime::now, LocalDateTime.class);
        strictInsertFill(metaObject, "deleted", () -> 0, Integer.class);
        strictInsertFill(metaObject, "version", () -> 0, Integer.class);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        // 这里不能用 strictUpdateFill：严格填充只在字段为 null 时才写，
        // 而从库里查出来的实体 updateTime 已有旧值，会被原样写回去，等于没刷新
        //
        // 先判断属性是否存在：t_course 这类没有审计字段的实体直接 set 会抛
        // "There is no setter for property named 'updateTime'"
        if (metaObject.hasSetter("updateTime")) {
            setFieldValByName("updateTime", LocalDateTime.now(), metaObject);
        }
    }
}
