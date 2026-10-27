package top.mqxu.security.controller;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.mqxu.security.common.Result;
import top.mqxu.security.dto.ReviewTaskRequest;
import top.mqxu.security.dto.SubmitTaskRequest;
import top.mqxu.security.security.SecurityUser;
import top.mqxu.security.service.TaskService;
import top.mqxu.security.vo.TaskView;

import java.util.List;

/**
 * @author mqxu
 * @date 2026/10/28
 * @description 实训任务接口。每条路径后面写清“谁能调、为什么是他”
 * <p>
 * 前端调用顺序：登录拿令牌 -> 每个请求带 Authorization -> 收到 401 跳登录、403 提示无权限。
 **/
@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    /**
     * 提交任务：只有学生有这个权限点，教师调会得到 403
     */
    @PostMapping
    @PreAuthorize("hasAuthority('task:submit')")
    public Result<TaskView> submit(@Valid @RequestBody SubmitTaskRequest request,
                                   @AuthenticationPrincipal SecurityUser user) {
        return Result.ok(taskService.submit(request, user));
    }

    /**
     * 我的提交：登录即可，不需要额外权限点。
     * 注意 URL 里没有用户名，前端想查别人的也无从下手
     */
    @GetMapping("/mine")
    public Result<List<TaskView>> mine(@AuthenticationPrincipal SecurityUser user) {
        return Result.ok(taskService.listMine(user.getUsername()));
    }

    /**
     * 全部提交：教师要批改，所以按权限点放行，学生调会得到 403
     */
    @GetMapping
    @PreAuthorize("hasAuthority('task:review')")
    public Result<List<TaskView>> all() {
        return Result.ok(taskService.listAll());
    }

    /**
     * 看单条：本人或有批改权限的人。判断在 Service 里做，因为要看这条记录的数据
     */
    @GetMapping("/{id}")
    public Result<TaskView> detail(@PathVariable Long id, @AuthenticationPrincipal SecurityUser user) {
        return Result.ok(taskService.detail(id, user));
    }

    /**
     * 批改：教师与管理员
     */
    @PostMapping("/{id}/review")
    @PreAuthorize("hasAuthority('task:review')")
    public Result<TaskView> review(@PathVariable Long id, @Valid @RequestBody ReviewTaskRequest request) {
        return Result.ok(taskService.review(id, request));
    }
}
