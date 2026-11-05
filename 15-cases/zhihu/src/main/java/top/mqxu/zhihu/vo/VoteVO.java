package top.mqxu.zhihu.vo;

/**
 * 投票结果。前端拿这三个数就能直接更新按钮状态，不用再查一次。
 *
 * @param myVote 1 已赞同、-1 已反对、null 没投
 * @author mqxu
 */
public record VoteVO(Integer voteUp, Integer voteDown, Integer myVote) {
}
