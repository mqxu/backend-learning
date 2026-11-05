package top.mqxu.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @author mqxu
 * @date 2026/10/28
 * @description 提交实训任务的请求
 * <p>
 * 注意这里没有 owner 字段。提交人由服务端从令牌里取，
 * 前端就算在请求体里多写一个 owner 也不会被采纳。
 **/
public record SubmitTaskRequest(

        @NotBlank(message = "任务标题不能为空")
        @Size(max = 100, message = "任务标题不超过 100 个字")
        String title,

        @NotBlank(message = "任务内容不能为空")
        @Size(max = 2000, message = "任务内容不超过 2000 个字")
        String content
) {
}
