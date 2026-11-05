package top.mqxu.zhihu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 话题：问题的分类。
 *
 * @author mqxu
 */
@Data
@TableName("z_topic")
public class Topic {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String description;

    private Integer followersCount;

    private Integer questionCount;

    private Integer sort;
}
