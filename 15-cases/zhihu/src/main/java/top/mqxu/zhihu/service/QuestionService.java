package top.mqxu.zhihu.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import top.mqxu.zhihu.common.BusinessException;
import top.mqxu.zhihu.common.ErrorCode;
import top.mqxu.zhihu.common.PageResult;
import top.mqxu.zhihu.common.UserContext;
import top.mqxu.zhihu.dto.QuestionSaveRequest;
import top.mqxu.zhihu.entity.Question;
import top.mqxu.zhihu.entity.Topic;
import top.mqxu.zhihu.mapper.QuestionMapper;
import top.mqxu.zhihu.mapper.TopicMapper;
import top.mqxu.zhihu.vo.QuestionVO;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 问题的发布、浏览、编辑、删除。
 *
 * @author mqxu
 */
@Service
@RequiredArgsConstructor
public class QuestionService {

    private final QuestionMapper questionMapper;
    private final TopicMapper topicMapper;
    private final LookupService lookupService;

    /**
     * 列表。sort 支持三种：latest（默认）、hot、unanswered。
     */
    public PageResult<QuestionVO> page(long pageNum, long pageSize, Long topicId,
                                       String keyword, String sort) {
        LambdaQueryWrapper<Question> wrapper = new LambdaQueryWrapper<>();
        if (topicId != null) {
            wrapper.eq(Question::getTopicId, topicId);
        }
        if (StringUtils.hasText(keyword)) {
            // 条件用 and 包起来，否则会和前面的 topic 条件变成 OR，把别的分类也带出来
            wrapper.and(w -> w.like(Question::getTitle, keyword)
                    .or().like(Question::getContent, keyword));
        }
        switch (sort == null ? "latest" : sort) {
            case "hot" -> wrapper.orderByDesc(Question::getViewCount)
                    .orderByDesc(Question::getAnswerCount);
            case "unanswered" -> wrapper.eq(Question::getAnswerCount, 0)
                    .orderByDesc(Question::getCreatedAt);
            default -> wrapper.orderByDesc(Question::getCreatedAt);
        }

        Page<Question> page = questionMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return PageResult.of(toVOs(page.getRecords()), page.getTotal(), pageNum, pageSize);
    }

    /**
     * 详情。每打开一次浏览数加一。
     */
    public QuestionVO detail(Long id) {
        Question question = require(id);
        questionMapper.update(null, new LambdaUpdateWrapper<Question>()
                .eq(Question::getId, id)
                // 用 SQL 自增而不是"读出来加一再写回"，并发下不会丢计数
                .setSql("view_count = view_count + 1"));
        question.setViewCount(question.getViewCount() + 1);
        return toVOs(List.of(question)).get(0);
    }

    @Transactional
    public QuestionVO create(QuestionSaveRequest request) {
        Long userId = UserContext.requireUserId();
        Topic topic = topicMapper.selectById(request.getTopicId());
        if (topic == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "话题不存在");
        }

        Question question = new Question();
        question.setTitle(request.getTitle());
        question.setContent(request.getContent());
        question.setAuthorId(userId);
        question.setTopicId(request.getTopicId());
        question.setAnswerCount(0);
        question.setViewCount(0);
        question.setVoteCount(0);
        question.setStatus(0);
        questionMapper.insert(question);

        topicMapper.update(null, new LambdaUpdateWrapper<Topic>()
                .eq(Topic::getId, topic.getId())
                .setSql("question_count = question_count + 1"));
        return toVOs(List.of(question)).get(0);
    }

    @Transactional
    public QuestionVO update(Long id, QuestionSaveRequest request) {
        Long userId = UserContext.requireUserId();
        Question question = require(id);
        if (!question.getAuthorId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能修改自己提的问题");
        }
        if (topicMapper.selectById(request.getTopicId()) == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "话题不存在");
        }
        question.setTitle(request.getTitle());
        question.setContent(request.getContent());
        question.setTopicId(request.getTopicId());
        questionMapper.updateById(question);
        return toVOs(List.of(question)).get(0);
    }

    @Transactional
    public void delete(Long id) {
        Long userId = UserContext.requireUserId();
        Question question = require(id);
        if (!question.getAuthorId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能删除自己提的问题");
        }
        // 逻辑删除：deleted 置 1，行还在，回答也不会变成孤儿
        questionMapper.deleteById(id);
        topicMapper.update(null, new LambdaUpdateWrapper<Topic>()
                .eq(Topic::getId, question.getTopicId())
                .setSql("question_count = GREATEST(question_count - 1, 0)"));
    }

    public Question require(Long id) {
        Question question = questionMapper.selectById(id);
        if (question == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "问题不存在或已删除");
        }
        return question;
    }

    /**
     * 把实体列表翻译成 VO：作者名、话题名、是否关注、我的投票，各查一次。
     */
    private List<QuestionVO> toVOs(List<Question> questions) {
        if (questions.isEmpty()) {
            return List.of();
        }
        List<Long> authorIds = questions.stream().map(Question::getAuthorId).distinct().toList();
        List<Long> topicIds = questions.stream().map(Question::getTopicId).distinct().toList();
        List<Long> ids = questions.stream().map(Question::getId).toList();

        Map<Long, String> authors = lookupService.nicknames(authorIds);
        Map<Long, String> topics = lookupService.topicNames(topicIds);
        Long current = UserContext.get();
        Set<Long> followed = lookupService.myFollowTargets(current, "QUESTION", ids);
        Map<Long, Integer> votes = lookupService.myVotes(current, "QUESTION", ids);

        return questions.stream()
                .map(q -> QuestionVO.of(q,
                        authors.get(q.getAuthorId()),
                        topics.get(q.getTopicId()),
                        followed.contains(q.getId()),
                        votes.get(q.getId())))
                .collect(Collectors.toList());
    }
}
