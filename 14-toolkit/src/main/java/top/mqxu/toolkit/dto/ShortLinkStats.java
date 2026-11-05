package top.mqxu.toolkit.dto;

/**
 * 短链统计
 *
 * @param code     短码
 * @param url      原始长链
 * @param clicks   点击次数
 * @param createdAt 创建时间戳（毫秒）
 * @param expireAt 过期时间戳（毫秒）
 * @author mqxu
 * @date 2026-10-21
 */
public record ShortLinkStats(String code, String url, long clicks, long createdAt, long expireAt) {
}
