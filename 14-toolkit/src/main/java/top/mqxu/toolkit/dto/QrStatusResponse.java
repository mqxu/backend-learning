package top.mqxu.toolkit.dto;

/**
 * 扫码状态
 *
 * @param ticket 票据
 * @param state  WAITING / SCANNED / CONFIRMED / CANCELED
 * @param phone  确认登录的手机号，未确认时为 null
 * @param token  确认登录后签发的 token
 * @author mqxu
 * @date 2026-10-21
 */
public record QrStatusResponse(String ticket, String state, String phone, String token) {
}
