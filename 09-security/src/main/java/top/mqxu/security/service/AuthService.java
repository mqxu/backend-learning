package top.mqxu.security.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import top.mqxu.security.common.BusinessException;
import top.mqxu.security.common.ErrorCode;
import top.mqxu.security.dto.LoginRequest;
import top.mqxu.security.security.JwtTokenService;
import top.mqxu.security.security.SecurityUser;
import top.mqxu.security.vo.LoginResponse;

/**
 * @author mqxu
 * @date 2026/10/28
 * @description 登录：交给 AuthenticationManager 校验，校验通过后签发令牌
 **/
@Slf4j
@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenService jwtTokenService;

    public AuthService(AuthenticationManager authenticationManager, JwtTokenService jwtTokenService) {
        this.authenticationManager = authenticationManager;
        this.jwtTokenService = jwtTokenService;
    }

    public LoginResponse login(LoginRequest request) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        } catch (DisabledException e) {
            log.warn("账号已禁用：{}", request.username());
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        } catch (BadCredentialsException e) {
            // 用户名不存在与密码错误给同一个提示，避免被用来枚举账号
            log.warn("登录失败：{}", request.username());
            throw new BusinessException(ErrorCode.BAD_CREDENTIALS);
        } catch (AuthenticationException e) {
            log.warn("登录异常：{}，原因：{}", request.username(), e.getMessage());
            throw new BusinessException(ErrorCode.BAD_CREDENTIALS);
        }

        SecurityUser user = (SecurityUser) authentication.getPrincipal();
        JwtTokenService.IssuedToken issued = jwtTokenService.issue(user);
        log.info("用户 {} 登录成功，角色 {}", user.getUsername(), user.roleNames());
        return new LoginResponse(issued.token(), "Bearer", issued.expiresAt(),
                user.getUsername(), user.roleNames());
    }
}
