package top.mqxu.security.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import top.mqxu.security.common.BusinessException;
import top.mqxu.security.common.ErrorCode;
import top.mqxu.security.dto.CreateUserRequest;
import top.mqxu.security.model.UserAccount;
import top.mqxu.security.repository.InMemoryUserRepository;
import top.mqxu.security.vo.UserProfile;

import java.util.List;

/**
 * @author mqxu
 * @date 2026/10/28
 * @description 账号管理，仅 ADMIN 可调用
 **/
@Slf4j
@Service
public class UserAdminService {

    private final InMemoryUserRepository repository;
    private final PasswordEncoder passwordEncoder;

    public UserAdminService(InMemoryUserRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<UserProfile> list() {
        return repository.findAll().stream().map(UserProfile::of).toList();
    }

    public UserProfile create(CreateUserRequest request) {
        if (repository.exists(request.username())) {
            throw new BusinessException(ErrorCode.USERNAME_EXISTS);
        }
        UserAccount account = new UserAccount(request.username(),
                passwordEncoder.encode(request.password()),
                request.nickname(),
                request.roles(),
                true);
        repository.save(account);
        log.info("新增用户 {}，角色 {}", account.getUsername(), account.getRoles());
        return UserProfile.of(account);
    }

    /**
     * @param operator 当前登录用户名，禁止删自己，避免把最后一个管理员删掉后无从登录
     */
    public void delete(String username, String operator) {
        if (username.equals(operator)) {
            throw new BusinessException(ErrorCode.CANNOT_DELETE_SELF);
        }
        if (!repository.deleteByUsername(username)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        log.info("用户 {} 被 {} 删除", username, operator);
    }
}
