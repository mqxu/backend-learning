package com.campus.activity.controller.app;

import com.campus.activity.common.PageResult;
import com.campus.activity.common.Result;
import com.campus.activity.common.UserContext;
import com.campus.activity.entity.SysNotice;
import com.campus.activity.security.RequireLogin;
import com.campus.activity.service.NoticeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/app/messages")
@RequireLogin
@RequiredArgsConstructor
public class AppNoticeController {

    private final NoticeService noticeService;

    @GetMapping
    public Result<PageResult<SysNotice>> list(@RequestParam(defaultValue = "1") long page,
                                              @RequestParam(defaultValue = "10") long size) {
        return Result.ok(noticeService.page(UserContext.requireUserId(), page, size));
    }

    @GetMapping("/unread-count")
    public Result<Map<String, Long>> unreadCount() {
        return Result.ok(Map.of("count", noticeService.unreadCount(UserContext.requireUserId())));
    }

    @PostMapping("/read")
    public Result<Void> markRead(@RequestBody(required = false) Map<String, List<Long>> body) {
        List<Long> ids = body == null ? null : body.get("ids");
        noticeService.markRead(UserContext.requireUserId(), ids);
        return Result.ok();
    }
}
