package top.mqxu.zhihu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 关注关系。关注人、问题、话题共用一张表，靠 targetType 区分。
 *
 * @author mqxu
 */
@Data
@TableName("z_follow")
public class Follow {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /**
     * USER / QUESTION / TOPIC。
     */
    private String targetType;

    private Long targetId;

    private LocalDateTime createdAt;
}
