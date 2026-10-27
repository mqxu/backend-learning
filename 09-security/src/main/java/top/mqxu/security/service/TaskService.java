package top.mqxu.security.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import top.mqxu.security.common.BusinessException;
import top.mqxu.security.common.ErrorCode;
import top.mqxu.security.dto.ReviewTaskRequest;
import top.mqxu.security.dto.SubmitTaskRequest;
import top.mqxu.security.model.Task;
import top.mqxu.security.model.TaskStatus;
import top.mqxu.security.repository.TaskRepository;
import top.mqxu.security.security.SecurityUser;
import top.mqxu.security.vo.TaskView;

import java.time.Instant;
import java.util.List;

/**
 * @author mqxu
 * @date 2026/10/28
 * @description 实训任务：学生提交、教师批改、按归属决定能看哪几条
 **/
@Slf4j
@Service
public class TaskService {

    /**
     * 能批改任务的人持有的权限点，写常量避免字符串散落
     */
    private static final String REVIEW_PERMISSION = "task:review";

    private final TaskRepository taskRepository;

    public TaskService(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    /**
     * 提交人从登录取，不从请求体取。这是本模块最重要的一条规矩：
     * 凡是“归谁所有”，只能由服务端按令牌判定。
     */
    public TaskView submit(SubmitTaskRequest request, SecurityUser user) {
        if (taskRepository.existsUnreviewed(user.getUsername(), request.title())) {
            throw new BusinessException(ErrorCode.TASK_DUPLICATED);
        }
        Task task = new Task(request.title(), request.content(),
                user.getUsername(), user.account().getNickname(), Instant.now());
        taskRepository.save(task);
        log.info("{} 提交任务：{}", user.getUsername(), request.title());
        return TaskView.of(task);
    }

    /**
     * 只返回自己的。参数是用户名而不是过滤器条件，
     * 前端没法通过改参数看到别人的提交
     */
    public List<TaskView> listMine(String username) {
        return taskRepository.findByOwner(username).stream().map(TaskView::of).toList();
    }

    /**
     * 全部提交，给教师批改用，权限点已在 Controller 上校验
     */
    public List<TaskView> listAll() {
        return taskRepository.findAll().stream().map(TaskView::of).toList();
    }

    /**
     * 看单条。URL 里的 id 是可以随便改的，所以必须在服务端判断这条记录归不归你
     */
    public TaskView detail(Long id, SecurityUser viewer) {
        Task task = find(id);
        if (!canView(task, viewer)) {
            log.warn("{} 试图查看 {} 的任务 {}", viewer.getUsername(), task.getOwnerUsername(), id);
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return TaskView.of(task);
    }

    public TaskView review(Long id, ReviewTaskRequest request) {
        Task task = find(id);
        if (task.getStatus() == TaskStatus.REVIEWED) {
            throw new BusinessException(ErrorCode.TASK_ALREADY_REVIEWED);
        }
        task.reviewed(request.score(), request.comment(), Instant.now());
        taskRepository.save(task);
        log.info("任务 {} 已批改，得分 {}", id, request.score());
        return TaskView.of(task);
    }

    /**
     * 两种人能看：提交人本人，或者有批改权限的教师与管理员
     */
    private boolean canView(Task task, SecurityUser viewer) {
        if (task.getOwnerUsername().equals(viewer.getUsername())) {
            return true;
        }
        return viewer.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals(REVIEW_PERMISSION));
    }

    private Task find(Long id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.TASK_NOT_FOUND));
    }
}
