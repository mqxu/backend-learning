package top.mqxu.security.model;

import lombok.Data;

/**
 * @author mqxu
 * @date 2026/10/28
 * @description 用户与角色关联的查询结果
 * <p>
 * 账号列表一次查出所有人的角色，避免每个用户查一次库（N+1）。
 **/
@Data
public class UserRoleEntry {

    private Long userId;

    private String roleCode;
}
