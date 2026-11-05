package top.mqxu.zhihu.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import top.mqxu.zhihu.common.BusinessException;
import top.mqxu.zhihu.common.ErrorCode;
import top.mqxu.zhihu.dto.LoginRequest;
import top.mqxu.zhihu.dto.RegisterRequest;
import top.mqxu.zhihu.entity.User;
import top.mqxu.zhihu.mapper.UserMapper;
import top.mqxu.zhihu.security.JwtService;
import top.mqxu.zhihu.vo.LoginVO;
import top.mqxu.zhihu.vo.UserVO;

/**
 * 注册、登录、查当前用户。
 *
 * @author mqxu
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final JwtService jwtService;
    private final BCryptPasswordEncoder passwordEncoder;

    public LoginVO login(LoginRequest request) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, request.getUsername()));
        // 用户名不存在和密码错误返回同一句话：不要告诉攻击者"这个账号是存在的"
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }
        String token = jwtService.createToken(user.getId(), user.getUsername());
        return new LoginVO(token, UserVO.of(user));
    }

    public LoginVO register(RegisterRequest request) {
        Long exists = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, request.getUsername()));
        if (exists != null && exists > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "用户名已被占用");
        }
        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setNickname(request.getNickname());
        userMapper.insert(user);

        String token = jwtService.createToken(user.getId(), user.getUsername());
        return new LoginVO(token, UserVO.of(user));
    }

    public UserVO current(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        return UserVO.of(user);
    }
}
