package top.mqxu.security.vo;

import top.mqxu.security.model.Task;

import java.time.Instant;

/**
 * @author mqxu
 * @date 2026/10/28
 * @description 任务的对外视图
 * <p>
 * status 给前端做逻辑判断，statusText 直接显示。前端不用再写一份“SUBMITTED 就是待批改”的映射，
 * 这也是前后端分离里常说的：枚举的中文由后端给，前端不要散落业务词汇。
 **/
public record TaskView(

        Long id,
        String title,
        String content,
        String owner,
        String ownerNickname,
        String status,
        String statusText,
        Integer score,
        String comment,
        Instant submittedAt,
        Instant reviewedAt
) {

    public static TaskView of(Task task) {
        return new TaskView(
                task.getId(),
                task.getTitle(),
                task.getContent(),
                task.getOwnerUsername(),
                task.getOwnerNickname(),
                task.getStatus().name(),
                task.getStatus().text(),
                task.getScore(),
                task.getComment(),
                task.getSubmittedAt(),
                task.getReviewedAt());
    }
}
