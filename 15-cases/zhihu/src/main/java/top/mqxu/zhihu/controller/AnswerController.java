package top.mqxu.zhihu.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.mqxu.zhihu.common.PageResult;
import top.mqxu.zhihu.common.Result;
import top.mqxu.zhihu.dto.CommentSaveRequest;
import top.mqxu.zhihu.service.AnswerService;
import top.mqxu.zhihu.service.CommentService;
import top.mqxu.zhihu.service.SocialService;
import top.mqxu.zhihu.vo.AnswerVO;
import top.mqxu.zhihu.vo.CommentVO;
import top.mqxu.zhihu.vo.VoteVO;

/**
 * 回答维度的操作：采纳、删除、投票、评论。
 *
 * @author mqxu
 */
@RestController
@RequestMapping("/api/v1/answers")
@RequiredArgsConstructor
public class AnswerController {

    private final AnswerService answerService;
    private final CommentService commentService;
    private final SocialService socialService;

    @PostMapping("/{id}/accept")
    public Result<AnswerVO> accept(@PathVariable Long id) {
        return Result.ok(answerService.accept(id));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        answerService.delete(id);
        return Result.ok();
    }

    /**
     * value：1 赞同、-1 反对、0 取消。
     */
    @PostMapping("/{id}/vote")
    public Result<VoteVO> vote(@PathVariable Long id,
                               @RequestParam(defaultValue = "1") int value) {
        return Result.ok(socialService.vote("ANSWER", id, value));
    }

    @GetMapping("/{id}/comments")
    public Result<PageResult<CommentVO>> comments(@PathVariable Long id,
                                                  @RequestParam(defaultValue = "1") long pageNum,
                                                  @RequestParam(defaultValue = "10") long pageSize) {
        return Result.ok(commentService.page(id, pageNum, pageSize));
    }

    @PostMapping("/{id}/comments")
    public Result<CommentVO> comment(@PathVariable Long id,
                                     @Valid @RequestBody CommentSaveRequest request) {
        return Result.ok(commentService.create(id, request));
    }
}
