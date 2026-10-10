package top.mqxu.web.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 更新用户请求
 *
 * @author mqxu
 */
@Data
public class UserUpdateRequest {

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "邮箱不能为空")
    private String email;
}
