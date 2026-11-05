package top.mqxu.zhihu.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 发布或修改问题。新增和编辑共用一个请求体。
 *
 * @author mqxu
 */
@Data
public class QuestionSaveRequest {

    @NotBlank(message = "标题不能为空")
    @Size(max = 200, message = "标题最多 200 个字")
    private String title;

    @NotBlank(message = "问题描述不能为空")
    @Size(max = 5000, message = "问题描述最多 5000 个字")
    private String content;

    @NotNull(message = "请选择话题")
    private Long topicId;
}
