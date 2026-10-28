package com.campus.activity.controller.org;

import com.campus.activity.common.LoginUser;
import com.campus.activity.common.PageResult;
import com.campus.activity.common.Result;
import com.campus.activity.common.UserContext;
import com.campus.activity.dto.ActivitySaveRequest;
import com.campus.activity.dto.CancelRequest;
import com.campus.activity.dto.ManualCheckinRequest;
import com.campus.activity.dto.SignupAuditRequest;
import com.campus.activity.security.RequireLogin;
import com.campus.activity.security.RequireRole;
import com.campus.activity.service.ActivityService;
import com.campus.activity.service.CheckinService;
import com.campus.activity.service.SignupService;
import com.campus.activity.vo.ActivityItemVO;
import com.campus.activity.vo.ActivityStatVO;
import com.campus.activity.vo.CheckinQrVO;
import com.campus.activity.vo.SignupDetailVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/org")
@RequireLogin
@RequireRole({"ORGANIZER", "ADMIN"})
@RequiredArgsConstructor
public class OrgController {

    private final ActivityService activityService;
    private final SignupService signupService;
    private final CheckinService checkinService;

    @GetMapping("/activities")
    public Result<PageResult<ActivityItemVO>> myActivities(@RequestParam(required = false) Integer status,
                                                           @RequestParam(defaultValue = "1") long page,
                                                           @RequestParam(defaultValue = "10") long size) {
        return Result.ok(activityService.pageForOrg(UserContext.get(), status, page, size));
    }

    @PostMapping("/activities")
    public Result<Map<String, Long>> create(@Valid @RequestBody ActivitySaveRequest request) {
        Long id = activityService.create(UserContext.get(), request);
        return Result.ok(Map.of("id", id));
    }

    @PutMapping("/activities/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody ActivitySaveRequest request) {
        activityService.update(UserContext.get(), id, request);
        return Result.ok();
    }

    @PostMapping("/activities/{id}/submit")
    public Result<Void> submit(@PathVariable Long id) {
        activityService.submit(UserContext.get(), id);
        return Result.ok();
    }

    @PostMapping("/activities/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id, @RequestBody(required = false) CancelRequest request) {
        activityService.cancelByOrg(UserContext.get(), id, request == null ? null : request.reason());
        return Result.ok();
    }

    @GetMapping("/activities/{id}/signups")
    public Result<PageResult<SignupDetailVO>> signups(@PathVariable Long id,
                                                      @RequestParam(required = false) Integer status,
                                                      @RequestParam(required = false) String keyword,
                                                      @RequestParam(defaultValue = "1") long page,
                                                      @RequestParam(defaultValue = "10") long size) {
        return Result.ok(signupService.pageByActivity(UserContext.get(), id, status, keyword, page, size));
    }

    @PostMapping("/activities/{id}/signups/audit")
    public Result<Map<String, Integer>> auditSignups(@PathVariable Long id,
                                                     @Valid @RequestBody SignupAuditRequest request) {
        int handled = signupService.auditByOrg(UserContext.get(), id, request);
        return Result.ok(Map.of("handled", handled));
    }

    @GetMapping("/activities/{id}/checkin-qr")
    public Result<CheckinQrVO> checkinQr(@PathVariable Long id) {
        return Result.ok(checkinService.qr(UserContext.get(), id));
    }

    @PostMapping("/activities/{id}/checkin/manual")
    public Result<Void> manualCheckin(@PathVariable Long id, @Valid @RequestBody ManualCheckinRequest request) {
        checkinService.manualCheckin(UserContext.get(), id, request);
        return Result.ok();
    }

    @GetMapping("/activities/{id}/stat")
    public Result<ActivityStatVO> stat(@PathVariable Long id) {
        return Result.ok(checkinService.stat(UserContext.get(), id));
    }

    @GetMapping("/dashboard")
    public Result<Map<String, Object>> dashboard() {
        LoginUser user = UserContext.get();
        var page = activityService.pageForOrg(user, null, 1, 100);
        List<ActivityItemVO> list = page.records();
        long published = list.stream().filter(a -> a.getStatus() != null && a.getStatus() == 3).count();
        long pending = list.stream().filter(a -> a.getStatus() != null && a.getStatus() == 1).count();
        long totalSignup = list.stream().mapToLong(a -> a.getSignedCount() == null ? 0 : a.getSignedCount()).sum();
        return Result.ok(Map.of(
                "activityTotal", page.total(),
                "publishedCount", published,
                "pendingAuditCount", pending,
                "totalSignup", totalSignup
        ));
    }
}
