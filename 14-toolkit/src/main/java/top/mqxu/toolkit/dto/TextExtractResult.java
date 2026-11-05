package top.mqxu.toolkit.dto;

import java.util.List;

/**
 * 文本提取结果
 *
 * @param phones     手机号
 * @param emails     邮箱
 * @param ips        IP
 * @param urls       链接
 * @param idCards    身份证
 * @param maskedText 脱敏后的原文
 * @author mqxu
 * @date 2026-10-21
 */
public record TextExtractResult(List<String> phones, List<String> emails, List<String> ips,
                                List<String> urls, List<String> idCards, String maskedText) {
}
