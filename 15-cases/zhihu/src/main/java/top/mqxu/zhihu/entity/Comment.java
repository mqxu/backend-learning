package top.mqxu.zhihu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 评论。parentId 为 0 是一级评论，否则是楼中楼。
 *
 * @author mqxu
 */
@Data
@TableName("z_comment")
public class Comment {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long answerId;

    private Long authorId;

    private String content;

    private Long parentId;

    private Long replyToUserId;

    private LocalDateTime createdAt;

    @TableLogic
    private Integer deleted;
}
