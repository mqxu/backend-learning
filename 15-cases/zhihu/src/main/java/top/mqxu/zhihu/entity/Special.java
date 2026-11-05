package top.mqxu.zhihu.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 专栏。原案例唯一的数据表，结构保持不变，前端仍在读它。
 *
 * @author mqxu
 */
@Data
@TableName("z_special")
public class Special {

    @TableId
    private Long id;

    private String title;

    private String introduction;

    private String banner;

    private Integer viewCount;

    private Integer followersCount;

    /**
     * 前端直接读这个字符串判断是否已关注，历史遗留写法，保持不动。
     */
    private String isFollowing;

    private Long updated;
}
