package top.mqxu.toolkit.dto;

import java.util.List;

/**
 * 巡检请求
 *
 * @param urls 待探测的地址
 * @author mqxu
 * @date 2026-10-21
 */
public record InspectRequest(List<String> urls) {
}
