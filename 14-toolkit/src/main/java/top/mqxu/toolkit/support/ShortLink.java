package top.mqxu.toolkit.support;

import lombok.Getter;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 短链记录
 *
 * @author mqxu
 * @date 2026-10-21
 */
@Getter
public class ShortLink {

    private final String code;
    private final String url;
    private final long createdAt = System.currentTimeMillis();
    private final long expireAt;
    private final AtomicLong clicks = new AtomicLong();

    public ShortLink(String code, String url, long expireAt) {
        this.code = code;
        this.url = url;
        this.expireAt = expireAt;
    }
}
