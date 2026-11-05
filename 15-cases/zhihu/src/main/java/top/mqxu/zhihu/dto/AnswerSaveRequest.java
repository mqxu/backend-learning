package top.mqxu.zhihu.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 写回答。
 *
 * @author mqxu
 */
@Data
public class AnswerSaveRequest {

    @NotBlank(message = "回答内容不能为空")
    @Size(max = 5000, message = "回答最多 5000 个字")
    private String content;
}
