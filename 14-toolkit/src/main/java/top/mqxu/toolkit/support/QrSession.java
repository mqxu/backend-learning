package top.mqxu.toolkit.support;

import lombok.Getter;
import lombok.Setter;

/**
 * 扫码登录会话
 *
 * @author mqxu
 * @date 2026-10-21
 * @description 状态流转：WAITING -> SCANNED -> CONFIRMED，任意阶段可 CANCELED
 */
@Getter
public class QrSession {

    public enum State {
        WAITING, SCANNED, CONFIRMED, CANCELED
    }

    private final String ticket;
    private final String content;
    private final long createdAt = System.currentTimeMillis();

    @Setter
    private volatile State state = State.WAITING;

    @Setter
    private volatile String phone;

    @Setter
    private volatile String token;

    public QrSession(String ticket, String content) {
        this.ticket = ticket;
        this.content = content;
    }
}
