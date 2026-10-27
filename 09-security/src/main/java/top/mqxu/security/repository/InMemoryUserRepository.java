package top.mqxu.security.repository;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Repository;
import top.mqxu.security.model.Role;
import top.mqxu.security.model.UserAccount;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author mqxu
 * @date 2026/10/28
 * @description 用户仓库的内存实现，免去本模块对外部数据库的依赖
 * <p>
 * 换成数据库时，把这里的方法体换成 Mapper 调用即可，上层 UserDetailsService 不用改。
 **/
@Repository
public class InMemoryUserRepository {

    private final Map<String, UserAccount> accounts = new ConcurrentHashMap<>();

    public InMemoryUserRepository(PasswordEncoder passwordEncoder) {
        seed(passwordEncoder);
    }

    /**
     * 初始账号。密码在这里即时加密，库里从不出现明文
     */
    private void seed(PasswordEncoder passwordEncoder) {
        save(new UserAccount("admin", passwordEncoder.encode("admin123"), "系统管理员", Set.of(Role.ADMIN), true));
        save(new UserAccount("teacher", passwordEncoder.encode("teacher123"), "陶老师", Set.of(Role.USER), true));
        save(new UserAccount("guest", passwordEncoder.encode("guest123"), "访客", Set.of(Role.USER), false));
    }

    public Optional<UserAccount> findByUsername(String username) {
        return Optional.ofNullable(accounts.get(username));
    }

    public List<UserAccount> findAll() {
        return accounts.values().stream()
                .sorted(Comparator.comparing(UserAccount::getUsername))
                .toList();
    }

    public boolean exists(String username) {
        return accounts.containsKey(username);
    }

    public void save(UserAccount account) {
        accounts.put(account.getUsername(), account);
    }

    public boolean deleteByUsername(String username) {
        return accounts.remove(username) != null;
    }
}
