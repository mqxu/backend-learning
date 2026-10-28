package com.campus.activity.controller.admin;

import com.campus.activity.common.PageResult;
import com.campus.activity.common.Result;
import com.campus.activity.entity.AuditRecord;
import com.campus.activity.security.RequireLogin;
import com.campus.activity.security.RequireRole;
import com.campus.activity.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequireLogin
@RequireRole({"REVIEWER", "ADMIN"})
@RequiredArgsConstructor
public class AdminDashboardController {

    private final AdminService adminService;

    @GetMapping("/dashboard")
    public Result<Map<String, Object>> dashboard() {
        return Result.ok(adminService.dashboard());
    }

    @GetMapping("/logs")
    public Result<PageResult<AuditRecord>> logs(@RequestParam(defaultValue = "1") long page,
                                                @RequestParam(defaultValue = "20") long size) {
        return Result.ok(adminService.pageLogs(page, size));
    }
}
