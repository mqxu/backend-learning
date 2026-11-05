package top.mqxu.security.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.mqxu.security.common.BusinessException;
import top.mqxu.security.common.ErrorCode;
import top.mqxu.security.dto.CreateUserRequest;
import top.mqxu.security.entity.User;
import top.mqxu.security.mapper.RoleMapper;
import top.mqxu.security.mapper.UserMapper;
import top.mqxu.security.mapper.UserRoleMapper;
import top.mqxu.security.model.Account;
import top.mqxu.security.model.Role;
import top.mqxu.security.model.UserRoleEntry;
import top.mqxu.security.vo.UserProfile;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author mqxu
 * @date 2026/10/28
 * @description 账号的读取与维护：登录要读、管理接口要写
 * <p>
 * 认证与账号管理共用同一个读方法，避免两处各写一份“按用户名查人 + 查角色”。
 **/
@Slf4j
@Service
public class UserService {

    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserMapper userMapper, UserRoleMapper userRoleMapper,
                       RoleMapper roleMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.roleMapper = roleMapper;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 按用户名取账号与角色，登录与每个请求的令牌校验都走这里
     */
    public Optional<Account> findByUsername(String username) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (user == null) {
            return Optional.empty();
        }
        Set<Role> roles = userRoleMapper.selectRoleCodesByUserId(user.getId()).stream()
                .map(Role::valueOf)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return Optional.of(new Account(user.getId(), user.getUsername(), user.getPassword(),
                user.getNickname(), Boolean.TRUE.equals(user.getEnabled()), roles));
    }

    /**
     * 账号列表。两次查询：一次取人，一次取全部角色关联，在内存里分组
     */
    public List<UserProfile> listProfiles() {
        List<User> users = userMapper.selectList(
                new LambdaQueryWrapper<User>().orderByAsc(User::getUsername));

        Map<Long, List<String>> rolesByUser = userRoleMapper.selectAllEntries().stream()
                .collect(Collectors.groupingBy(UserRoleEntry::getUserId,
                        Collectors.mapping(UserRoleEntry::getRoleCode, Collectors.toList())));

        return users.stream()
                .map(user -> new UserProfile(user.getUsername(), user.getNickname(),
                        new LinkedHashSet<>(rolesByUser.getOrDefault(user.getId(), List.of())),
                        Role.authorities(parseRoles(rolesByUser.get(user.getId()))),
                        Boolean.TRUE.equals(user.getEnabled())))
                .toList();
    }

    /**
     * 新增账号。写两张表，任一步失败整体回滚
     */
    @Transactional(rollbackFor = Exception.class)
    public UserProfile create(CreateUserRequest request) {
        if (findByUsername(request.username()).isPresent()) {
            throw new BusinessException(ErrorCode.USERNAME_EXISTS);
        }

        User user = new User();
        user.setUsername(request.username());
        // 明文只在这里出现一次，落库前就换成密文
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setNickname(request.nickname());
        user.setEnabled(true);
        userMapper.insert(user);

        for (Role role : request.roles()) {
            Long roleId = roleMapper.selectIdByCode(role.name());
            if (roleId == null) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR);
            }
            userRoleMapper.insert(user.getId(), roleId);
        }

        log.info("新增账号 {}，角色 {}", user.getUsername(), request.roles());
        return findByUsername(user.getUsername()).map(UserProfile::of)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR));
    }

    /**
     * @param operator 当前登录用户名，禁止删自己，避免把最后一个管理员删掉后无从登录
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(String username, String operator) {
        if (username.equals(operator)) {
            throw new BusinessException(ErrorCode.CANNOT_DELETE_SELF);
        }
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        // 先删关联再删人，不然留下指向不存在用户的脏关联
        userRoleMapper.deleteByUserId(user.getId());
        userMapper.deleteById(user.getId());
        log.info("账号 {} 被 {} 删除", username, operator);
    }

    private Set<Role> parseRoles(List<String> codes) {
        if (codes == null) {
            return Set.of();
        }
        return codes.stream().map(Role::valueOf).collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
