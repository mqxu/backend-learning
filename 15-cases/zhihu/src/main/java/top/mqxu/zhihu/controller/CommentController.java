package top.mqxu.zhihu.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.mqxu.zhihu.common.PageResult;
import top.mqxu.zhihu.common.Result;
import top.mqxu.zhihu.service.CommentService;
import top.mqxu.zhihu.vo.CommentVO;

/**
 * 评论维度的操作。发表评论挂在回答下，删除单拎出来。
 *
 * @author mqxu
 */
@RestController
@RequestMapping("/api/v1/comments")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        commentService.delete(id);
        return Result.ok();
    }
}
