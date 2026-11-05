package top.mqxu.zhihu.vo;

import top.mqxu.zhihu.entity.User;

/**
 * 对外的用户信息。刻意不包含 username 与 password：
 * 实体直接返回给前端，密码字段迟早会漏出去。
 *
 * @author mqxu
 */
public record UserVO(Long id, String nickname, String avatar, String bio) {

    public static UserVO of(User user) {
        if (user == null) {
            return null;
        }
        return new UserVO(user.getId(), user.getNickname(), user.getAvatar(), user.getBio());
    }
}
