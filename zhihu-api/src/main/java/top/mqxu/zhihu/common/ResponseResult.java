package top.mqxu.zhihu.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author mqxu
 * @date 2026/10/19
 * @description ResponseResult
 **/
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ResponseResult {
    private Integer code;
    private String msg;
    private Object data;
}
