package top.mqxu.zhihu.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.mqxu.zhihu.common.Result;
import top.mqxu.zhihu.service.TopicService;
import top.mqxu.zhihu.vo.TopicVO;

import java.util.List;

/**
 * 话题。
 *
 * @author mqxu
 */
@RestController
@RequestMapping("/api/v1/topics")
@RequiredArgsConstructor
public class TopicController {

    private final TopicService topicService;

    @GetMapping
    public Result<List<TopicVO>> list() {
        return Result.ok(topicService.list());
    }
}
