package top.mqxu.zhihu.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import top.mqxu.zhihu.entity.Question;

import java.time.LocalDateTime;

/**
 * 问题。列表页不带 content，详情页才带，两者共用一个形状，靠 content 是否为 null 区分。
 *
 * @param followed 当前用户是否关注了这个问题
 * @param myVote   当前用户给问题的投票：1 赞同、-1 反对、null 没投过
 * @author mqxu
 */
public record QuestionVO(Long id, String title, String content,
                         Long authorId, String authorName,
                         Long topicId, String topicName,
                         Integer answerCount, Integer viewCount, Integer voteCount,
                         Integer status, @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime createdAt,
                         boolean followed, Integer myVote) {

    public static QuestionVO of(Question question, String authorName, String topicName,
                                boolean followed, Integer myVote) {
        return new QuestionVO(question.getId(), question.getTitle(), question.getContent(),
                question.getAuthorId(), authorName,
                question.getTopicId(), topicName,
                question.getAnswerCount(), question.getViewCount(), question.getVoteCount(),
                question.getStatus(), question.getCreatedAt(),
                followed, myVote);
    }
}
