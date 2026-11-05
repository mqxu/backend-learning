package com.campus.activity.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.activity.common.PageResult;
import com.campus.activity.entity.SysNotice;
import com.campus.activity.mapper.SysNoticeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 站内消息。V1 只落库并展示，微信订阅消息在 NoticeSender 中预留，接入模板 ID 后即可开启。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NoticeService {

    private final SysNoticeMapper noticeMapper;

    public void send(Long userId, String type, String title, String content, Long bizId) {
        SysNotice notice = new SysNotice();
        notice.setUserId(userId);
        notice.setType(type);
        notice.setTitle(title);
        notice.setContent(content);
        notice.setBizId(bizId);
        notice.setReadFlag(0);
        noticeMapper.insert(notice);
        NoticeSender.sendWechatSubscribeMessage(userId, type, title, content);
    }

    public PageResult<SysNotice> page(Long userId, long page, long size) {
        Page<SysNotice> result = noticeMapper.selectPage(new Page<>(page, size),
                Wrappers.<SysNotice>lambdaQuery()
                        .eq(SysNotice::getUserId, userId)
                        .orderByDesc(SysNotice::getCreateTime));
        return PageResult.of(result);
    }

    public long unreadCount(Long userId) {
        return noticeMapper.selectCount(Wrappers.<SysNotice>lambdaQuery()
                .eq(SysNotice::getUserId, userId)
                .eq(SysNotice::getReadFlag, 0));
    }

    public void markRead(Long userId, List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            // 不传 id 表示全部已读
            SysNotice update = new SysNotice();
            update.setReadFlag(1);
            noticeMapper.update(update, Wrappers.<SysNotice>lambdaUpdate()
                    .eq(SysNotice::getUserId, userId)
                    .eq(SysNotice::getReadFlag, 0));
            return;
        }
        SysNotice update = new SysNotice();
        update.setReadFlag(1);
        noticeMapper.update(update, Wrappers.<SysNotice>lambdaUpdate()
                .eq(SysNotice::getUserId, userId)
                .in(SysNotice::getId, ids));
    }
}
