package top.mqxu.zhihu.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.mqxu.zhihu.common.PageResult;
import top.mqxu.zhihu.common.Result;
import top.mqxu.zhihu.service.SocialService;
import top.mqxu.zhihu.vo.AnswerVO;
import top.mqxu.zhihu.vo.VoteVO;

/**
 * 关注与动态流。
 *
 * @author mqxu
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class SocialController {

    private final SocialService socialService;

    @PostMapping("/follows")
    public Result<Boolean> follow(@RequestParam String targetType,
                                  @RequestParam Long targetId) {
        return Result.ok(socialService.follow(targetType, targetId, true));
    }

    @DeleteMapping("/follows")
    public Result<Boolean> unfollow(@RequestParam String targetType,
                                    @RequestParam Long targetId) {
        return Result.ok(socialService.follow(targetType, targetId, false));
    }

    /**
     * 关注的问题也能投票，复用同一套投票逻辑。
     */
    @PostMapping("/questions/{id}/vote")
    public Result<VoteVO> voteQuestion(@PathVariable Long id,
                                       @RequestParam(defaultValue = "1") int value) {
        return Result.ok(socialService.vote("QUESTION", id, value));
    }

    @GetMapping("/feed")
    public Result<PageResult<AnswerVO>> feed(@RequestParam(defaultValue = "1") long pageNum,
                                             @RequestParam(defaultValue = "10") long pageSize) {
        return Result.ok(socialService.feed(pageNum, pageSize));
    }
}
