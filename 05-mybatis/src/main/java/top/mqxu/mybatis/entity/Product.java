package top.mqxu.mybatis.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品实体，对应 t_product
 *
 * <p>库存字段是下单事务里被扣减的对象。</p>
 *
 * @author mqxu
 */
@Data
@TableName("t_product")
public class Product {

    /**
     * BIGINT UNSIGNED —— 自增主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * VARCHAR(64) —— 商品名，建了唯一索引
     */
    private String name;

    /**
     * DECIMAL(10,2) —— 单价
     */
    private BigDecimal price;

    /**
     * INT —— 库存
     */
    private Integer stock;

    /**
     * DATETIME —— 新增时自动填充
     */
    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /**
     * DATETIME —— 新增与更新时自动填充
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    /**
     * TINYINT —— 逻辑删除标记
     */
    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;

    /**
     * INT —— 乐观锁版本号
     */
    @Version
    @TableField(fill = FieldFill.INSERT)
    private Integer version;
}
