package top.mqxu.zhihu.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.mqxu.zhihu.common.BusinessException;
import top.mqxu.zhihu.common.ErrorCode;
import top.mqxu.zhihu.common.PageResult;
import top.mqxu.zhihu.common.UserContext;
import top.mqxu.zhihu.entity.Answer;
import top.mqxu.zhihu.entity.Question;
import top.mqxu.zhihu.mapper.AnswerMapper;
import top.mqxu.zhihu.mapper.QuestionMapper;
import top.mqxu.zhihu.vo.AnswerVO;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 回答的发布、列表、采纳与删除。
 *
 * @author mqxu
 */
@Service
@RequiredArgsConstructor
public class AnswerService {

    private final AnswerMapper answerMapper;
    private final QuestionMapper questionMapper;
    private final QuestionService questionService;
    private final LookupService lookupService;

    /**
     * 某个问题下的回答。sort 支持 vote（默认，采纳的排最前）与 latest。
     */
    public PageResult<AnswerVO> page(Long questionId, long pageNum, long pageSize, String sort) {
        questionService.require(questionId);

        LambdaQueryWrapper<Answer> wrapper = new LambdaQueryWrapper<Answer>()
                .eq(Answer::getQuestionId, questionId);
        if ("latest".equals(sort)) {
            wrapper.orderByDesc(Answer::getCreatedAt);
        } else {
            wrapper.orderByDesc(Answer::getAccepted)
                    .orderByDesc(Answer::getVoteUp)
                    .orderByDesc(Answer::getCreatedAt);
        }

        Page<Answer> page = answerMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return PageResult.of(toVOs(page.getRecords()), page.getTotal(), pageNum, pageSize);
    }

    @Transactional
    public AnswerVO create(Long questionId, String content) {
        Long userId = UserContext.requireUserId();
        Question question = questionService.require(questionId);
        if (question.getStatus() != null && question.getStatus() == 1) {
            throw new BusinessException(ErrorCode.CONFLICT, "该问题已关闭，不能再回答");
        }

        Answer answer = new Answer();
        answer.setQuestionId(questionId);
        answer.setAuthorId(userId);
        answer.setContent(content);
        answer.setVoteUp(0);
        answer.setVoteDown(0);
        answer.setCommentCount(0);
        answer.setAccepted(0);
        answerMapper.insert(answer);

        // 冗余计数同步加一，与插入放在同一个事务里
        questionMapper.update(null, new LambdaUpdateWrapper<Question>()
                .eq(Question::getId, questionId)
                .setSql("answer_count = answer_count + 1, updated_at = NOW()"));
        return toVOs(List.of(answer)).get(0);
    }

    /**
     * 采纳。只有提问者能操作，同一个问题只保留一个采纳标记。
     */
    @Transactional
    public AnswerVO accept(Long answerId) {
        Long userId = UserContext.requireUserId();
        Answer answer = require(answerId);
        Question question = questionService.require(answer.getQuestionId());
        if (!question.getAuthorId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只有提问者可以采纳回答");
        }

        // 先把同问题下的其他回答全部取消采纳，再把这一条置 1
        answerMapper.update(null, new LambdaUpdateWrapper<Answer>()
                .eq(Answer::getQuestionId, answer.getQuestionId())
                .set(Answer::getAccepted, 0));
        answerMapper.update(null, new LambdaUpdateWrapper<Answer>()
                .eq(Answer::getId, answerId)
                .set(Answer::getAccepted, 1));
        answer.setAccepted(1);
        return toVOs(List.of(answer)).get(0);
    }

    @Transactional
    public void delete(Long answerId) {
        Long userId = UserContext.requireUserId();
        Answer answer = require(answerId);
        if (!answer.getAuthorId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能删除自己的回答");
        }
        answerMapper.deleteById(answerId);
        questionMapper.update(null, new LambdaUpdateWrapper<Question>()
                .eq(Question::getId, answer.getQuestionId())
                .setSql("answer_count = GREATEST(answer_count - 1, 0)"));
    }

    public Answer require(Long id) {
        Answer answer = answerMapper.selectById(id);
        if (answer == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "回答不存在或已删除");
        }
        return answer;
    }

    /**
     * 实体转 VO。动态流也要用，所以是 public。
     */
    public List<AnswerVO> toVOs(List<Answer> answers) {
        if (answers.isEmpty()) {
            return List.of();
        }
        List<Long> authorIds = answers.stream().map(Answer::getAuthorId).distinct().toList();
        List<Long> questionIds = answers.stream().map(Answer::getQuestionId).distinct().toList();
        List<Long> ids = answers.stream().map(Answer::getId).toList();

        Map<Long, String> authors = lookupService.nicknames(authorIds);
        Map<Long, String> titles = lookupService.questionTitles(questionIds);
        Map<Long, Integer> votes = lookupService.myVotes(UserContext.get(), "ANSWER", ids);

        return answers.stream()
                .map(a -> AnswerVO.of(a,
                        authors.get(a.getAuthorId()),
                        titles.get(a.getQuestionId()),
                        votes.get(a.getId())))
                .collect(Collectors.toList());
    }
}
