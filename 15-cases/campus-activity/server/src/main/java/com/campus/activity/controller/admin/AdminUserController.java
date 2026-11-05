package com.campus.activity.controller.admin;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campus.activity.common.PageResult;
import com.campus.activity.common.Result;
import com.campus.activity.common.UserContext;
import com.campus.activity.entity.Organization;
import com.campus.activity.mapper.OrganizationMapper;
import com.campus.activity.security.RequireLogin;
import com.campus.activity.security.RequireRole;
import com.campus.activity.service.AdminService;
import com.campus.activity.vo.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequireLogin
@RequireRole({"ADMIN"})
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminService adminService;
    private final OrganizationMapper organizationMapper;

    @GetMapping("/users")
    public Result<PageResult<UserVO>> users(@RequestParam(required = false) String keyword,
                                            @RequestParam(required = false) String role,
                                            @RequestParam(defaultValue = "1") long page,
                                            @RequestParam(defaultValue = "10") long size) {
        return Result.ok(adminService.pageUsers(keyword, role, page, size));
    }

    @PostMapping("/users/{id}/roles")
    public Result<Void> changeRole(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String role = String.valueOf(body.get("role"));
        Long orgId = body.get("orgId") == null ? null : Long.valueOf(String.valueOf(body.get("orgId")));
        adminService.changeRole(UserContext.get(), id, role, orgId);
        return Result.ok();
    }

    @GetMapping("/organizations")
    public Result<List<Organization>> organizations() {
        return Result.ok(organizationMapper.selectList(Wrappers.<Organization>lambdaQuery()
                .eq(Organization::getStatus, 1)
                .orderByAsc(Organization::getId)));
    }
}
