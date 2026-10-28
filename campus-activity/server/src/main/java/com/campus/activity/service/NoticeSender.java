package com.campus.activity.service;

import lombok.extern.slf4j.Slf4j;

/**
 * 微信订阅消息出口。
 * V1 未接入模板 ID 时只打日志；接入时把 access_token 与模板参数填进下面的方法即可，
 * 调用方不需要改动。
 */
@Slf4j
public final class NoticeSender {

    private NoticeSender() {
    }

    public static void sendWechatSubscribeMessage(Long userId, String type, String title, String content) {
        log.info("[订阅消息预留] userId={}, type={}, title={}, content={}", userId, type, title, content);
    }
}
