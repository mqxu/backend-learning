package top.mqxu.toolkit.dto;

/**
 * 单个地址的探测结果
 *
 * @param url    地址
 * @param status HTTP 状态码，连不上时为 0
 * @param costMs 耗时（毫秒）
 * @param ok     是否可用（2xx 或 3xx）
 * @param error  失败原因
 * @author mqxu
 * @date 2026-10-21
 */
public record InspectResult(String url, int status, long costMs, boolean ok, String error) {
}
