package top.mqxu.zhihu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 回答。
 *
 * @author mqxu
 */
@Data
@TableName("z_answer")
public class Answer {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long questionId;

    private Long authorId;

    private String content;

    private Integer voteUp;

    private Integer voteDown;

    private Integer commentCount;

    /**
     * 1 表示被提问者采纳。每个问题最多一个。
     */
    private Integer accepted;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
