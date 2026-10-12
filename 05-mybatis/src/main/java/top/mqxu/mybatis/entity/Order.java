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
 * 订单实体，对应 t_order
 *
 * <p>挑战练习里演示“创建订单 + 扣库存”原子性时写入的表，
 * 同样带上自动填充、逻辑删除与乐观锁，用来和 t_user、t_product 对照。</p>
 *
 * @author mqxu
 */
@Data
@TableName("t_order")
public class Order {

    /**
     * BIGINT UNSIGNED —— 自增主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * VARCHAR(32) —— 订单号，建了唯一索引
     */
    private String orderNo;

    /**
     * BIGINT UNSIGNED —— 下单用户 id
     */
    private Long userId;

    /**
     * BIGINT UNSIGNED —— 商品 id
     */
    private Long productId;

    /**
     * INT —— 购买数量
     */
    private Integer quantity;

    /**
     * DECIMAL(12,2) —— 订单金额
     */
    private BigDecimal amount;

    /**
     * TINYINT —— 1 已创建 / 0 已取消
     */
    private Integer status;

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
