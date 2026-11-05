package top.mqxu.security.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * @author mqxu
 * @date 2026/10/28
 * @description 教师批改的请求
 **/
public record ReviewTaskRequest(

        @NotNull(message = "分数不能为空")
        @Min(value = 0, message = "分数不能小于 0")
        @Max(value = 100, message = "分数不能大于 100")
        Integer score,

        @Size(max = 500, message = "评语不超过 500 个字")
        String comment
) {
}
