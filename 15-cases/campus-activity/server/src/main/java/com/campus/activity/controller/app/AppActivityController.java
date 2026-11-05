package com.campus.activity.controller.app;

import com.campus.activity.common.PageResult;
import com.campus.activity.common.Result;
import com.campus.activity.common.UserContext;
import com.campus.activity.entity.ActivityCategory;
import com.campus.activity.security.RequireLogin;
import com.campus.activity.service.ActivityService;
import com.campus.activity.vo.ActivityDetailVO;
import com.campus.activity.vo.ActivityItemVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/app")
@RequiredArgsConstructor
public class AppActivityController {

    private final ActivityService activityService;

    @GetMapping("/categories")
    public Result<List<ActivityCategory>> categories() {
        return Result.ok(activityService.categories());
    }

    @GetMapping("/activities")
    public Result<PageResult<ActivityItemVO>> activities(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String timeScope,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        return Result.ok(activityService.pageForStudent(categoryId, keyword, timeScope, sort, page, size));
    }

    @GetMapping("/activities/hot")
    public Result<List<ActivityItemVO>> hot(@RequestParam(defaultValue = "5") int limit) {
        return Result.ok(activityService.hot(limit));
    }

    @GetMapping("/activities/{id}")
    public Result<ActivityDetailVO> detail(@PathVariable Long id) {
        return Result.ok(activityService.detail(id, UserContext.userId()));
    }

    @RequireLogin
    @PostMapping("/activities/{id}/favorite")
    public Result<Map<String, Object>> favorite(@PathVariable Long id) {
        boolean favorited = activityService.toggleFavorite(UserContext.requireUserId(), id);
        return Result.ok(Map.of("favorited", favorited));
    }
}
