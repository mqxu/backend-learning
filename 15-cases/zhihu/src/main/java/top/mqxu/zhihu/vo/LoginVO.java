package top.mqxu.zhihu.vo;

/**
 * 登录结果：token 放请求头，用户信息给页面直接用。
 *
 * @author mqxu
 */
public record LoginVO(String token, UserVO user) {
}
