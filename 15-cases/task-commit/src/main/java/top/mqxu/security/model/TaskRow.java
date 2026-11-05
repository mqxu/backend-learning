package top.mqxu.security.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author mqxu
 * @date 2026/10/28
 * @description 任务与提交人的联表投影
 * <p>
 * 只用来接 Mapper 的查询结果，不外传。
 * 对外统一转成 TaskView，页面需要的字段和数据库列不必一一对应。
 **/
@Data
public class TaskRow {

    private Long id;

    private String title;

    private String content;

    private Long ownerId;

    /**
     * 提交人登录名，来自 sec_user.username
     */
    private String owner;

    /**
     * 提交人姓名，来自 sec_user.nickname
     */
    private String ownerNickname;

    /**
     * SUBMITTED / REVIEWED
     */
    private String status;

    private Integer score;

    private String comment;

    private LocalDateTime submittedAt;

    private LocalDateTime reviewedAt;
}
