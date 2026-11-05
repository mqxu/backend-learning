package top.mqxu.zhihu.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import top.mqxu.zhihu.entity.Follow;
import top.mqxu.zhihu.entity.Question;
import top.mqxu.zhihu.entity.Topic;
import top.mqxu.zhihu.entity.User;
import top.mqxu.zhihu.entity.Vote;
import top.mqxu.zhihu.mapper.FollowMapper;
import top.mqxu.zhihu.mapper.QuestionMapper;
import top.mqxu.zhihu.mapper.TopicMapper;
import top.mqxu.zhihu.mapper.UserMapper;
import top.mqxu.zhihu.mapper.VoteMapper;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 批量查名字与关系。
 *
 * <p>列表页有 20 条回答，如果每条都去查一次作者昵称，就是 20 次查询（N+1）。
 * 这里统一收口成"一次 in 查询 + 内存拼装"，每个列表接口只多两次 SQL。</p>
 *
 * @author mqxu
 */
@Service
@RequiredArgsConstructor
public class LookupService {

    private final UserMapper userMapper;
    private final TopicMapper topicMapper;
    private final QuestionMapper questionMapper;
    private final VoteMapper voteMapper;
    private final FollowMapper followMapper;

    public Map<Long, String> nicknames(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        return userMapper.selectByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getNickname));
    }

    public Map<Long, String> topicNames(Collection<Long> topicIds) {
        if (topicIds == null || topicIds.isEmpty()) {
            return Map.of();
        }
        return topicMapper.selectByIds(topicIds).stream()
                .collect(Collectors.toMap(Topic::getId, Topic::getName));
    }

    public Map<Long, String> questionTitles(Collection<Long> questionIds) {
        if (questionIds == null || questionIds.isEmpty()) {
            return Map.of();
        }
        return questionMapper.selectByIds(questionIds).stream()
                .collect(Collectors.toMap(Question::getId, Question::getTitle));
    }

    /**
     * 当前用户对一批目标的投票值。没登录直接返回空 Map。
     */
    public Map<Long, Integer> myVotes(Long userId, String targetType, Collection<Long> targetIds) {
        if (userId == null || targetIds == null || targetIds.isEmpty()) {
            return Map.of();
        }
        LambdaQueryWrapper<Vote> wrapper = new LambdaQueryWrapper<Vote>()
                .eq(Vote::getUserId, userId)
                .eq(Vote::getTargetType, targetType)
                .in(Vote::getTargetId, targetIds);
        Map<Long, Integer> result = new HashMap<>();
        for (Vote vote : voteMapper.selectList(wrapper)) {
            result.put(vote.getTargetId(), vote.getValue());
        }
        return result;
    }

    /**
     * 当前用户关注了哪些目标。
     */
    public Set<Long> myFollowTargets(Long userId, String targetType, Collection<Long> targetIds) {
        if (userId == null || targetIds == null || targetIds.isEmpty()) {
            return Set.of();
        }
        LambdaQueryWrapper<Follow> wrapper = new LambdaQueryWrapper<Follow>()
                .eq(Follow::getUserId, userId)
                .eq(Follow::getTargetType, targetType)
                .in(Follow::getTargetId, targetIds);
        return followMapper.selectList(wrapper).stream()
                .map(Follow::getTargetId)
                .collect(Collectors.toSet());
    }
}
