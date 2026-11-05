package top.mqxu.zhihu.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import top.mqxu.zhihu.common.UserContext;
import top.mqxu.zhihu.entity.Topic;
import top.mqxu.zhihu.mapper.TopicMapper;
import top.mqxu.zhihu.vo.TopicVO;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 话题列表。
 *
 * @author mqxu
 */
@Service
@RequiredArgsConstructor
public class TopicService {

    private final TopicMapper topicMapper;
    private final LookupService lookupService;

    public List<TopicVO> list() {
        List<Topic> topics = topicMapper.selectList(new LambdaQueryWrapper<Topic>()
                .orderByAsc(Topic::getSort));
        Set<Long> followed = lookupService.myFollowTargets(
                UserContext.get(), "TOPIC",
                topics.stream().map(Topic::getId).collect(Collectors.toList()));
        return topics.stream()
                .map(topic -> TopicVO.of(topic, followed.contains(topic.getId())))
                .toList();
    }
}
