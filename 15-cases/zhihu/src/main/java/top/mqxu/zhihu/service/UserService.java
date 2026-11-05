package top.mqxu.zhihu.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import top.mqxu.zhihu.common.BusinessException;
import top.mqxu.zhihu.common.ErrorCode;
import top.mqxu.zhihu.entity.Answer;
import top.mqxu.zhihu.entity.Follow;
import top.mqxu.zhihu.entity.Question;
import top.mqxu.zhihu.entity.User;
import top.mqxu.zhihu.mapper.AnswerMapper;
import top.mqxu.zhihu.mapper.FollowMapper;
import top.mqxu.zhihu.mapper.QuestionMapper;
import top.mqxu.zhihu.mapper.UserMapper;
import top.mqxu.zhihu.vo.UserProfileVO;
import top.mqxu.zhihu.vo.UserVO;

/**
 * 个人主页。
 *
 * @author mqxu
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final QuestionMapper questionMapper;
    private final AnswerMapper answerMapper;
    private final FollowMapper followMapper;

    public UserProfileVO profile(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }

        long questionCount = questionMapper.selectCount(new LambdaQueryWrapper<Question>()
                .eq(Question::getAuthorId, userId));
        long answerCount = answerMapper.selectCount(new LambdaQueryWrapper<Answer>()
                .eq(Answer::getAuthorId, userId));
        // 获赞数：把这个人所有回答的 vote_up 加起来。数据量大时应该加一列缓存，这里演示用聚合
        int voteUpTotal = answerMapper.selectList(new LambdaQueryWrapper<Answer>()
                        .select(Answer::getVoteUp)
                        .eq(Answer::getAuthorId, userId))
                .stream()
                .mapToInt(a -> a.getVoteUp() == null ? 0 : a.getVoteUp())
                .sum();
        long followerCount = followMapper.selectCount(new LambdaQueryWrapper<Follow>()
                .eq(Follow::getTargetType, "USER")
                .eq(Follow::getTargetId, userId));
        long followingCount = followMapper.selectCount(new LambdaQueryWrapper<Follow>()
                .eq(Follow::getUserId, userId));

        return new UserProfileVO(UserVO.of(user), questionCount, answerCount,
                voteUpTotal, followerCount, followingCount);
    }
}
