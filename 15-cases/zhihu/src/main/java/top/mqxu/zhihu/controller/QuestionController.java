package top.mqxu.zhihu.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.mqxu.zhihu.common.PageResult;
import top.mqxu.zhihu.common.Result;
import top.mqxu.zhihu.dto.AnswerSaveRequest;
import top.mqxu.zhihu.dto.QuestionSaveRequest;
import top.mqxu.zhihu.service.AnswerService;
import top.mqxu.zhihu.service.QuestionService;
import top.mqxu.zhihu.vo.AnswerVO;
import top.mqxu.zhihu.vo.QuestionVO;

/**
 * 问题与它下面的回答。
 *
 * @author mqxu
 */
@RestController
@RequestMapping("/api/v1/questions")
@RequiredArgsConstructor
public class QuestionController {

    private final QuestionService questionService;
    private final AnswerService answerService;

    @GetMapping
    public Result<PageResult<QuestionVO>> page(@RequestParam(defaultValue = "1") long pageNum,
                                               @RequestParam(defaultValue = "10") long pageSize,
                                               @RequestParam(required = false) Long topicId,
                                               @RequestParam(required = false) String keyword,
                                               @RequestParam(defaultValue = "latest") String sort) {
        return Result.ok(questionService.page(pageNum, pageSize, topicId, keyword, sort));
    }

    @GetMapping("/{id}")
    public Result<QuestionVO> detail(@PathVariable Long id) {
        return Result.ok(questionService.detail(id));
    }

    @PostMapping
    public Result<QuestionVO> create(@Valid @RequestBody QuestionSaveRequest request) {
        return Result.ok(questionService.create(request));
    }

    @PutMapping("/{id}")
    public Result<QuestionVO> update(@PathVariable Long id,
                                     @Valid @RequestBody QuestionSaveRequest request) {
        return Result.ok(questionService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        questionService.delete(id);
        return Result.ok();
    }

    @GetMapping("/{id}/answers")
    public Result<PageResult<AnswerVO>> answers(@PathVariable Long id,
                                                @RequestParam(defaultValue = "1") long pageNum,
                                                @RequestParam(defaultValue = "10") long pageSize,
                                                @RequestParam(defaultValue = "vote") String sort) {
        return Result.ok(answerService.page(id, pageNum, pageSize, sort));
    }

    @PostMapping("/{id}/answers")
    public Result<AnswerVO> answer(@PathVariable Long id,
                                   @Valid @RequestBody AnswerSaveRequest request) {
        return Result.ok(answerService.create(id, request.getContent()));
    }
}
