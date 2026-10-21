package top.mqxu.toolkit.dto;

/**
 * 二维码票据
 *
 * @param ticket        票据，轮询状态时回传
 * @param content       二维码里的内容
 * @param image         data URI
 * @param expireSeconds 有效期（秒）
 * @author mqxu
 * @date 2026-10-21
 */
public record QrTicketResponse(String ticket, String content, String image, long expireSeconds) {
}
