package top.mqxu.toolkit.dto;

/**
 * 短链创建结果
 *
 * @param code     短码
 * @param shortUrl 完整短链
 * @param url      原始长链
 * @param expireAt 过期时间戳（毫秒）
 * @author mqxu
 * @date 2026-10-21
 */
public record ShortLinkResponse(String code, String shortUrl, String url, long expireAt) {
}
