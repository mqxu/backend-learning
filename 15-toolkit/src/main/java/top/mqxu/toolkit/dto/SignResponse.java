package top.mqxu.toolkit.dto;

/**
 * 签名示例
 *
 * @param appId     应用标识
 * @param timestamp 秒级时间戳
 * @param nonce     随机串
 * @param body      请求体原文
 * @param canonical 参与签名的字符串
 * @param sign      签名结果
 * @author mqxu
 * @date 2026-10-21
 */
public record SignResponse(String appId, String timestamp, String nonce,
                           String body, String canonical, String sign) {
}
