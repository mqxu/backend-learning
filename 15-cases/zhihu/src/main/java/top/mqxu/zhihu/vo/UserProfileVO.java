package top.mqxu.zhihu.vo;

/**
 * 个人主页：用户信息 + 四个统计数字。
 *
 * @author mqxu
 */
public record UserProfileVO(UserVO user, long questionCount, long answerCount,
                            long voteUpTotal, long followerCount, long followingCount) {
}
