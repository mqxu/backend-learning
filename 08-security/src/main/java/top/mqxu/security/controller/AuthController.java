package top.mqxu.security.controller;

import jakarta.validation.Valid;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.mqxu.security.common.Result;
import top.mqxu.security.dto.LoginRequest;
import top.mqxu.security.dto.LoginResponse;
import top.mqxu.security.dto.UserInfo;
import top.mqxu.security.security.JwtService;

import java.util.List;

/**
 * 密码登录。
 * <p>
 * 关掉 formLogin 之后，POST 登录不再被 Security 接管，得自己把 AuthenticationManager 请出来用。
 * 好处是登录接口终于可以直接返回 JSON + token，而不是 302 跳页面。
 *
 * @author mqxu
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthController(AuthenticationManager authenticationManager, JwtService jwtService) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        // 交给 ProviderManager：密码错抛 BadCredentialsException，
        // 账号禁用/锁定抛 DisabledException / LockedException，都由 GlobalExceptionHandler 转成 JSON
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(
                        request.username(), request.password()));

        List<String> authorities = authoritiesOf(authentication);
        String token = jwtService.generate(authentication.getName(), authorities);
        return Result.ok(new LoginResponse(token, "Bearer",
                jwtService.getExpireSeconds(), authentication.getName(), authorities));
    }

    /**
     * 当前登录人。任何“权限不对”的排查都从这一条开始。
     */
    @GetMapping("/me")
    public Result<UserInfo> me(Authentication authentication) {
        return Result.ok(new UserInfo(authentication.getName(), authoritiesOf(authentication)));
    }

    private static List<String> authoritiesOf(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
    }
}
