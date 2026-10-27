package top.mqxu.security.vo;

import top.mqxu.security.model.TaskRow;
import top.mqxu.security.model.TaskStatus;

import java.time.LocalDateTime;

/**
 * @author mqxu
 * @date 2026/10/28
 * @description 任务的对外视图
 * <p>
 * status 给前端做逻辑判断，statusText 直接显示。前端不用再写一份
 * “SUBMITTED 就是待批改”的映射，枚举的中文由后端给。
 **/
public record TaskView(

        Long id,
        String title,
        String content,
        Long ownerId,
        String owner,
        String ownerNickname,
        String status,
        String statusText,
        Integer score,
        String comment,
        LocalDateTime submittedAt,
        LocalDateTime reviewedAt
) {

    public static TaskView of(TaskRow row) {
        return new TaskView(
                row.getId(),
                row.getTitle(),
                row.getContent(),
                row.getOwnerId(),
                row.getOwner(),
                row.getOwnerNickname(),
                row.getStatus(),
                TaskStatus.valueOf(row.getStatus()).text(),
                row.getScore(),
                row.getComment(),
                row.getSubmittedAt(),
                row.getReviewedAt());
    }
}
