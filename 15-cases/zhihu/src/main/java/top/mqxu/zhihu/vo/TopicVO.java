package top.mqxu.zhihu.vo;

import top.mqxu.zhihu.entity.Topic;

/**
 * 话题。followed 表示当前登录用户有没有关注，未登录时恒为 false。
 *
 * @author mqxu
 */
public record TopicVO(Long id, String name, String description,
                      Integer followersCount, Integer questionCount, boolean followed) {

    public static TopicVO of(Topic topic, boolean followed) {
        return new TopicVO(topic.getId(), topic.getName(), topic.getDescription(),
                topic.getFollowersCount(), topic.getQuestionCount(), followed);
    }
}
