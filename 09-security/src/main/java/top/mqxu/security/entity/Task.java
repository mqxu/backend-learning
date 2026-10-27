package top.mqxu.security.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import top.mqxu.security.model.TaskStatus;

import java.time.LocalDateTime;

/**
 * @author mqxu
 * @date 2026/10/28
 * @description 实训任务实体，对应 sec_task 表
 * <p>
 * 归属存 owner_id 而不是用户名：用户名将来可以改，id 不会变。
 * 页面要显示提交人时再 join sec_user 取，见 TaskMapper 的联表查询。
 **/
@Data
@TableName("sec_task")
public class Task {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private String content;

    /**
     * 提交人 id，指向 sec_user.id
     */
    private Long ownerId;

    /**
     * 库里的列是 VARCHAR，MyBatis 默认按枚举名 SUBMITTED / REVIEWED 读写
     */
    private TaskStatus status;

    private Integer score;

    private String comment;

    private LocalDateTime submittedAt;

    private LocalDateTime reviewedAt;

    /**
     * 批改后状态、分数、评语、时间一起变，避免出现“已批改但没分数”的中间态
     */
    public void markReviewed(int score, String comment, LocalDateTime reviewedAt) {
        this.status = TaskStatus.REVIEWED;
        this.score = score;
        this.comment = comment;
        this.reviewedAt = reviewedAt;
    }
}
