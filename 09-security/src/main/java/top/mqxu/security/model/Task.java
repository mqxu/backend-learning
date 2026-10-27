package top.mqxu.security.model;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * @author mqxu
 * @date 2026/10/28
 * @description 一次实训任务的提交记录
 * <p>
 * ownerUsername 是归属判断的唯一依据，写入后不允许修改：
 * 判断“这条记录归谁”必须看服务端存下来的值，不能信前端传来的用户名。
 **/
@Getter
public class Task {

    @Setter
    private Long id;

    private final String title;

    private final String content;

    private final String ownerUsername;

    private final String ownerNickname;

    private final Instant submittedAt;

    @Setter
    private TaskStatus status;

    @Setter
    private Integer score;

    @Setter
    private String comment;

    @Setter
    private Instant reviewedAt;

    public Task(String title, String content, String ownerUsername, String ownerNickname, Instant submittedAt) {
        this.title = title;
        this.content = content;
        this.ownerUsername = ownerUsername;
        this.ownerNickname = ownerNickname;
        this.submittedAt = submittedAt;
        this.status = TaskStatus.SUBMITTED;
    }

    /**
     * 批改后状态、分数、评语一起变，避免出现“已批改但没有分数”的中间态
     */
    public void reviewed(Integer score, String comment, Instant reviewedAt) {
        this.status = TaskStatus.REVIEWED;
        this.score = score;
        this.comment = comment;
        this.reviewedAt = reviewedAt;
    }
}
