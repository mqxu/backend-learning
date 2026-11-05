package top.mqxu.security.dto;

import java.util.List;

/**
 * 当前登录人信息。/me 接口用它确认“上下文里到底是谁、有什么权限”，
 * 排查权限问题时第一件事就是看这个。
 *
 * @author mqxu
 */
public record UserInfo(String username, List<String> authorities) {
}
