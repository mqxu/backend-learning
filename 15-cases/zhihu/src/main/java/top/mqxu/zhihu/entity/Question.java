package top.mqxu.zhihu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 问题。
 *
 * <p>answerCount、viewCount 是冗余计数列。列表页每行都去 COUNT 一次，
 * 20 条数据就是 20 次子查询，代价比维护一个计数列高得多。</p>
 *
 * @author mqxu
 */
@Data
@TableName("z_question")
public class Question {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private String content;

    private Long authorId;

    private Long topicId;

    private Integer answerCount;

    private Integer viewCount;

    private Integer voteCount;

    /**
     * 0 正常 / 1 已关闭。关闭后还能看，但不能新增回答。
     */
    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
