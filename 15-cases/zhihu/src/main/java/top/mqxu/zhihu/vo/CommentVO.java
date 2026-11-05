package top.mqxu.zhihu.vo;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

/**
 * 评论。replyToName 是"回复谁"的昵称，楼中楼里才非空。
 *
 * @author mqxu
 */
public record CommentVO(Long id, Long answerId, String content,
                        Long authorId, String authorName,
                        Long parentId, Long replyToUserId, String replyToName,
                        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime createdAt) {
}
