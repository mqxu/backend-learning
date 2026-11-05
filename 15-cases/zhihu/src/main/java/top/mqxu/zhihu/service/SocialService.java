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
import top.mqxu.zhihu.entity.Follow;
import top.mqxu.zhihu.entity.Question;
import top.mqxu.zhihu.entity.Topic;
import top.mqxu.zhihu.entity.User;
import top.mqxu.zhihu.entity.Vote;
import top.mqxu.zhihu.mapper.AnswerMapper;
import top.mqxu.zhihu.mapper.FollowMapper;
import top.mqxu.zhihu.mapper.QuestionMapper;
import top.mqxu.zhihu.mapper.TopicMapper;
import top.mqxu.zhihu.mapper.UserMapper;
import top.mqxu.zhihu.mapper.VoteMapper;
import top.mqxu.zhihu.vo.AnswerVO;
import top.mqxu.zhihu.vo.VoteVO;

import java.util.ArrayList;
import java.util.List;

/**
 * 社交动作：投票、关注、动态流。
 *
 * @author mqxu
 */
@Service
@RequiredArgsConstructor
public class SocialService {

    private final VoteMapper voteMapper;
    private final FollowMapper followMapper;
    private final UserMapper userMapper;
    private final TopicMapper topicMapper;
    private final QuestionMapper questionMapper;
    private final AnswerMapper answerMapper;
    private final AnswerService answerService;
    private final QuestionService questionService;

    /**
     * 投票。value 传 1 赞同、-1 反对、0 取消。
     *
     * <p>同一个人对同一个目标只有一票，重复点同一个按钮等于取消（再点一次回来），
     * 点另一个按钮等于改票。三种情况分别对应下面三个分支。</p>
     */
    @Transactional
    public VoteVO vote(String targetType, Long targetId, int value) {
        Long userId = UserContext.requireUserId();
        if (value != 1 && value != -1 && value != 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "投票值只能是 1、-1 或 0");
        }
        String type = normalizeType(targetType);
        if ("ANSWER".equals(type)) {
            answerService.require(targetId);
        } else {
            questionService.require(targetId);
        }

        Vote existing = voteMapper.selectOne(new LambdaQueryWrapper<Vote>()
                .eq(Vote::getUserId, userId)
                .eq(Vote::getTargetType, type)
                .eq(Vote::getTargetId, targetId));

        // 记下操作完成后的状态，而不是入参：取消投票时入参可能是 1，但结果应该是"没投"
        Integer myVote;
        if (existing == null) {
            if (value != 0) {
                Vote vote = new Vote();
                vote.setUserId(userId);
                vote.setTargetType(type);
                vote.setTargetId(targetId);
                vote.setValue(value);
                voteMapper.insert(vote);
                applyCounter(type, targetId, value, 1);
                myVote = value;
            } else {
                myVote = null;
            }
        } else if (existing.getValue() == value) {
            // 再点一次同一个按钮：取消
            voteMapper.deleteById(existing.getId());
            applyCounter(type, targetId, existing.getValue(), -1);
            myVote = null;
        } else {
            // 改票：先撤销旧票，再记新票
            applyCounter(type, targetId, existing.getValue(), -1);
            existing.setValue(value);
            voteMapper.updateById(existing);
            applyCounter(type, targetId, value, 1);
            myVote = value;
        }

        if ("ANSWER".equals(type)) {
            Answer answer = answerService.require(targetId);
            return new VoteVO(answer.getVoteUp(), answer.getVoteDown(), myVote);
        }
        Question question = questionService.require(targetId);
        return new VoteVO(question.getVoteCount(), 0, myVote);
    }

    /**
     * 计数列加减。COUNT 靠 SQL 自增，不做"读-改-写"，避免并发丢更新。
     */
    private void applyCounter(String type, Long targetId, int value, int delta) {
        if ("ANSWER".equals(type)) {
            // 赞同加的是 vote_up，反对加的是 vote_down；delta 为负就是撤销
            String column = value == 1 ? "vote_up" : "vote_down";
            answerMapper.update(null, new LambdaUpdateWrapper<Answer>()
                    .eq(Answer::getId, targetId)
                    .setSql(column + " = GREATEST(" + column + " + (" + delta + "), 0)"));
        } else {
            questionMapper.update(null, new LambdaUpdateWrapper<Question>()
                    .eq(Question::getId, targetId)
                    .setSql("vote_count = GREATEST(vote_count + (" + delta + "), 0)"));
        }
    }

    /**
     * 关注或取消关注。接口做成幂等的：已经关注再点一次也是成功。
     */
    @Transactional
    public boolean follow(String targetType, Long targetId, boolean follow) {
        Long userId = UserContext.requireUserId();
        String type = normalizeType(targetType);
        checkTargetExists(type, targetId);

        Follow existing = followMapper.selectOne(new LambdaQueryWrapper<Follow>()
                .eq(Follow::getUserId, userId)
                .eq(Follow::getTargetType, type)
                .eq(Follow::getTargetId, targetId));

        if (follow && existing == null) {
            Follow record = new Follow();
            record.setUserId(userId);
            record.setTargetType(type);
            record.setTargetId(targetId);
            followMapper.insert(record);
            if ("TOPIC".equals(type)) {
                topicMapper.update(null, new LambdaUpdateWrapper<Topic>()
                        .eq(Topic::getId, targetId)
                        .setSql("followers_count = followers_count + 1"));
            }
        } else if (!follow && existing != null) {
            followMapper.deleteById(existing.getId());
            if ("TOPIC".equals(type)) {
                topicMapper.update(null, new LambdaUpdateWrapper<Topic>()
                        .eq(Topic::getId, targetId)
                        .setSql("followers_count = GREATEST(followers_count - 1, 0)"));
            }
        }
        return follow;
    }

    /**
     * 动态流：关注的人写的回答 + 关注的问题下的新回答 + 关注话题下的新回答。
     */
    public PageResult<AnswerVO> feed(long pageNum, long pageSize) {
        Long userId = UserContext.requireUserId();

        List<Long> authorIds = followTargets(userId, "USER");
        List<Long> questionIds = new ArrayList<>(followTargets(userId, "QUESTION"));
        List<Long> topicIds = followTargets(userId, "TOPIC");
        if (!topicIds.isEmpty()) {
            List<Question> questions = questionMapper.selectList(new LambdaQueryWrapper<Question>()
                    .in(Question::getTopicId, topicIds)
                    .orderByDesc(Question::getCreatedAt)
                    .last("LIMIT 200"));
            questions.forEach(q -> questionIds.add(q.getId()));
        }
        if (authorIds.isEmpty() && questionIds.isEmpty()) {
            return PageResult.empty(pageNum, pageSize);
        }

        LambdaQueryWrapper<Answer> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(w -> {
            if (!authorIds.isEmpty()) {
                w.in(Answer::getAuthorId, authorIds);
            }
            if (!questionIds.isEmpty()) {
                if (!authorIds.isEmpty()) {
                    w.or();
                }
                w.in(Answer::getQuestionId, questionIds);
            }
        });
        wrapper.orderByDesc(Answer::getCreatedAt);

        Page<Answer> page = answerMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return PageResult.of(answerService.toVOs(page.getRecords()), page.getTotal(), pageNum, pageSize);
    }

    private List<Long> followTargets(Long userId, String type) {
        return followMapper.selectList(new LambdaQueryWrapper<Follow>()
                        .eq(Follow::getUserId, userId)
                        .eq(Follow::getTargetType, type))
                .stream().map(Follow::getTargetId).toList();
    }

    private void checkTargetExists(String type, Long targetId) {
        boolean exists = switch (type) {
            case "USER" -> userMapper.selectById(targetId) != null;
            case "TOPIC" -> topicMapper.selectById(targetId) != null;
            case "QUESTION" -> questionMapper.selectById(targetId) != null;
            default -> false;
        };
        if (!exists) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "关注对象不存在");
        }
    }

    private String normalizeType(String targetType) {
        if (targetType == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "targetType 不能为空");
        }
        String type = targetType.toUpperCase();
        if (!type.equals("USER") && !type.equals("TOPIC")
                && !type.equals("QUESTION") && !type.equals("ANSWER")) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "targetType 只支持 USER / TOPIC / QUESTION / ANSWER");
        }
        return type;
    }
}
