package com.campus.activity.controller.admin;

import com.campus.activity.common.PageResult;
import com.campus.activity.common.Result;
import com.campus.activity.common.UserContext;
import com.campus.activity.dto.ActivityAuditRequest;
import com.campus.activity.dto.CancelRequest;
import com.campus.activity.security.RequireLogin;
import com.campus.activity.security.RequireRole;
import com.campus.activity.service.ActivityService;
import com.campus.activity.vo.ActivityItemVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/activities")
@RequireLogin
@RequireRole({"REVIEWER", "ADMIN"})
@RequiredArgsConstructor
public class AdminActivityController {

    private final ActivityService activityService;

    @GetMapping("/audit-list")
    public Result<PageResult<ActivityItemVO>> auditList(@RequestParam(defaultValue = "1") long page,
                                                       @RequestParam(defaultValue = "10") long size) {
        return Result.ok(activityService.auditList(page, size));
    }

    @PostMapping("/{id}/audit")
    public Result<Void> audit(@PathVariable Long id, @Valid @RequestBody ActivityAuditRequest request) {
        activityService.audit(UserContext.get(), id, Boolean.TRUE.equals(request.pass()), request.reason());
        return Result.ok();
    }

    @GetMapping
    public Result<PageResult<ActivityItemVO>> list(@RequestParam(required = false) String keyword,
                                                   @RequestParam(required = false) Integer status,
                                                   @RequestParam(defaultValue = "1") long page,
                                                   @RequestParam(defaultValue = "10") long size) {
        return Result.ok(activityService.adminPage(keyword, status, page, size));
    }

    @PostMapping("/{id}/offline")
    public Result<Void> offline(@PathVariable Long id, @RequestBody(required = false) CancelRequest request) {
        activityService.offline(UserContext.get(), id, request == null ? "管理员下架" : request.reason());
        return Result.ok();
    }
}
