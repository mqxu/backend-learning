package top.mqxu.security.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import top.mqxu.security.common.BusinessException;
import top.mqxu.security.common.ErrorCode;
import top.mqxu.security.dto.ReviewTaskRequest;
import top.mqxu.security.dto.SubmitTaskRequest;
import top.mqxu.security.entity.Task;
import top.mqxu.security.mapper.TaskMapper;
import top.mqxu.security.model.TaskRow;
import top.mqxu.security.model.TaskStatus;
import top.mqxu.security.security.SecurityUser;
import top.mqxu.security.vo.TaskView;

import java.time.LocalDateTime;
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

    private final TaskMapper taskMapper;

    public TaskService(TaskMapper taskMapper) {
        this.taskMapper = taskMapper;
    }

    /**
     * 提交人从令牌取，不从请求体取。这是本模块最重要的一条规矩：
     * 凡是“归谁所有”，只能由服务端按令牌判定。
     */
    public TaskView submit(SubmitTaskRequest request, SecurityUser user) {
        Long duplicated = taskMapper.selectCount(new LambdaQueryWrapper<Task>()
                .eq(Task::getOwnerId, user.id())
                .eq(Task::getTitle, request.title())
                .eq(Task::getStatus, TaskStatus.SUBMITTED));
        if (duplicated != null && duplicated > 0) {
            throw new BusinessException(ErrorCode.TASK_DUPLICATED);
        }

        Task task = new Task();
        task.setTitle(request.title());
        task.setContent(request.content());
        task.setOwnerId(user.id());
        task.setStatus(TaskStatus.SUBMITTED);
        task.setSubmittedAt(LocalDateTime.now());
        taskMapper.insert(task);

        log.info("{} 提交任务：{}", user.getUsername(), request.title());
        return TaskView.of(findRow(task.getId()));
    }

    /**
     * 只返回自己的。参数是令牌里的用户 id，前端没法通过改参数看到别人的提交
     */
    public List<TaskView> listMine(Long ownerId) {
        return taskMapper.selectRowsByOwnerId(ownerId).stream().map(TaskView::of).toList();
    }

    /**
     * 全部提交，给教师批改用，权限点已在 Controller 上校验
     */
    public List<TaskView> listAll() {
        return taskMapper.selectAllRows().stream().map(TaskView::of).toList();
    }

    /**
     * 看单条。URL 里的 id 是可以随便改的，所以必须在服务端判断这条记录归不归你
     */
    public TaskView detail(Long id, SecurityUser viewer) {
        TaskRow row = findRow(id);
        if (!canView(row, viewer)) {
            log.warn("{} 试图查看 {} 的任务 {}", viewer.getUsername(), row.getOwner(), id);
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return TaskView.of(row);
    }

    public TaskView review(Long id, ReviewTaskRequest request) {
        Task task = taskMapper.selectById(id);
        if (task == null) {
            throw new BusinessException(ErrorCode.TASK_NOT_FOUND);
        }
        if (task.getStatus() == TaskStatus.REVIEWED) {
            throw new BusinessException(ErrorCode.TASK_ALREADY_REVIEWED);
        }
        task.markReviewed(request.score(), request.comment(), LocalDateTime.now());
        taskMapper.updateById(task);

        log.info("任务 {} 已批改，得分 {}", id, request.score());
        return TaskView.of(findRow(id));
    }

    /**
     * 两种人能看：提交人本人，或者有批改权限的教师与管理员
     */
    private boolean canView(TaskRow row, SecurityUser viewer) {
        if (row.getOwnerId().equals(viewer.id())) {
            return true;
        }
        return viewer.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals(REVIEW_PERMISSION));
    }

    private TaskRow findRow(Long id) {
        TaskRow row = taskMapper.selectRowById(id);
        if (row == null) {
            throw new BusinessException(ErrorCode.TASK_NOT_FOUND);
        }
        return row;
    }
}
