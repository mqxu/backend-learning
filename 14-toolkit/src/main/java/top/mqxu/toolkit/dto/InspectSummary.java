package top.mqxu.toolkit.dto;

import java.util.List;

/**
 * 巡检汇总
 *
 * @param total     总数
 * @param success   可用数
 * @param failed    失败数
 * @param avgCostMs 平均耗时
 * @param results   明细，按耗时从高到低
 * @author mqxu
 * @date 2026-10-21
 */
public record InspectSummary(int total, int success, int failed,
                             long avgCostMs, List<InspectResult> results) {
}
