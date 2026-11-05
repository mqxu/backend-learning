package top.mqxu.zhihu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 投票。一个人对同一个目标只能有一票，(user_id, target_type, target_id) 上有唯一键。
 *
 * @author mqxu
 */
@Data
@TableName("z_vote")
public class Vote {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /**
     * ANSWER / QUESTION。
     */
    private String targetType;

    private Long targetId;

    /**
     * 1 赞同 / -1 反对。
     */
    private Integer value;

    private LocalDateTime createdAt;
}
