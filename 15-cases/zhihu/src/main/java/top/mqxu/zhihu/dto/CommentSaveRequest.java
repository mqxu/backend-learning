package top.mqxu.zhihu.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 发表评论。parentId 与 replyToUserId 都不传就是一级评论。
 *
 * @author mqxu
 */
@Data
public class CommentSaveRequest {

    @NotBlank(message = "评论内容不能为空")
    @Size(max = 500, message = "评论最多 500 个字")
    private String content;

    /**
     * 父评论 id，0 或不传表示一级评论。
     */
    private Long parentId;

    /**
     * 回复的目标用户。只在楼中楼里用，前端点"回复某人"时带上。
     */
    private Long replyToUserId;
}
