package top.mqxu.toolkit.dto;

import cn.hutool.core.annotation.Alias;
import lombok.Data;

/**
 * 订单行
 *
 * @author mqxu
 * @date 2026-10-21
 * @description 导入时所有字段先按字符串接收，日期和金额在业务层解析，避免格式差异直接抛异常
 */
@Data
public class OrderRow {

    @Alias("订单号")
    private String orderNo;

    @Alias("客户")
    private String customer;

    @Alias("手机号")
    private String phone;

    @Alias("金额")
    private String amount;

    @Alias("状态")
    private String status;

    @Alias("下单时间")
    private String createdAt;
}
