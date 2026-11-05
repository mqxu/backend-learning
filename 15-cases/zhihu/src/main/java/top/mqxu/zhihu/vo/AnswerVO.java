package top.mqxu.zhihu.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import top.mqxu.zhihu.entity.Answer;

import java.time.LocalDateTime;

/**
 * 回答。动态流也复用它，靠 questionTitle 告诉前端这条回答来自哪个问题。
 *
 * @param myVote 当前用户对这一条的回答的投票
 * @author mqxu
 */
public record AnswerVO(Long id, Long questionId, String questionTitle,
                       String content, Long authorId, String authorName,
                       Integer voteUp, Integer voteDown, Integer commentCount,
                       boolean accepted, @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime createdAt, Integer myVote) {

    public static AnswerVO of(Answer answer, String authorName, String questionTitle, Integer myVote) {
        return new AnswerVO(answer.getId(), answer.getQuestionId(), questionTitle,
                answer.getContent(), answer.getAuthorId(), authorName,
                answer.getVoteUp(), answer.getVoteDown(), answer.getCommentCount(),
                answer.getAccepted() != null && answer.getAccepted() == 1,
                answer.getCreatedAt(), myVote);
    }
}
