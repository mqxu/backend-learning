package top.mqxu.zhihu.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * @author mqxu
 * @date 2026/10/19
 * @description Special
 **/
@Data
@TableName("t_special")
public class Special {
    private Long id;
    private String title;
    private String introduction;
    private String banner;
    private String isFollowing;
    private Integer followersCount;
    private Integer viewCount;
    private Long updated;
}