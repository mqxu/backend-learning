package com.campus.activity.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campus.activity.common.BizException;
import com.campus.activity.common.ResultCode;
import com.campus.activity.config.CampusProperties;
import com.campus.activity.dto.AdminLoginRequest;
import com.campus.activity.dto.BindRequest;
import com.campus.activity.dto.LoginRequest;
import com.campus.activity.entity.Organization;
import com.campus.activity.entity.StudentProfile;
import com.campus.activity.entity.StudentWhitelist;
import com.campus.activity.entity.SysUser;
import com.campus.activity.mapper.OrganizationMapper;
import com.campus.activity.mapper.StudentProfileMapper;
import com.campus.activity.mapper.StudentWhitelistMapper;
import com.campus.activity.mapper.SysUserMapper;
import com.campus.activity.security.JwtUtil;
import com.campus.activity.security.SysUserDetails;
import com.campus.activity.security.TokenBlacklistService;
import com.campus.activity.vo.LoginVO;
import com.campus.activity.vo.UserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String WX_CODE2SESSION = "https://api.weixin.qq.com/sns/jscode2session";

    private final SysUserMapper userMapper;
    private final StudentProfileMapper profileMapper;
    private final StudentWhitelistMapper whitelistMapper;
    private final OrganizationMapper organizationMapper;
    private final JwtUtil jwtUtil;
    private final CampusProperties properties;
    private final AuthenticationManager authenticationManager;
    private final TokenBlacklistService tokenBlacklistService;

    /** 小程序登录：换取 openid，不存在则建号 */
    @Transactional
    public LoginVO login(LoginRequest request) {
        String openId = resolveOpenId(request);

        SysUser user = userMapper.selectOne(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getOpenId, openId));
        if (user == null) {
            user = new SysUser();
            user.setOpenId(openId);
            user.setNickname(request.nickname() == null || request.nickname().isBlank() ? "微信用户" : request.nickname());
            user.setAvatarUrl(request.avatarUrl());
            user.setRole("STUDENT");
            user.setStatus(1);
            user.setLastLoginTime(LocalDateTime.now());
            userMapper.insert(user);
        } else {
            if (user.getStatus() != null && user.getStatus() == 0) {
                throw new BizException(ResultCode.ACCOUNT_DISABLED);
            }
            SysUser update = new SysUser();
            update.setId(user.getId());
            update.setLastLoginTime(LocalDateTime.now());
            userMapper.updateById(update);
        }

        return buildLoginVO(user);
    }

    /**
     * 后台账号登录：交给 Spring Security 的 AuthenticationManager 完成查库与密码校验。
     * 密码错误 → LOGIN_FAILED，账号禁用 → ACCOUNT_DISABLED，角色限制沿用原逻辑。
     */
    public LoginVO adminLogin(AdminLoginRequest request) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(request.username(), request.password()));
        } catch (DisabledException e) {
            throw new BizException(ResultCode.ACCOUNT_DISABLED);
        } catch (AuthenticationException e) {
            // 用户不存在与密码错误在 DaoAuthenticationProvider 里已经被合并成 BadCredentialsException，
            // 对外统一提示“账号或密码错误”，避免暴露账号是否存在
            throw new BizException(ResultCode.LOGIN_FAILED);
        }

        SysUserDetails principal = (SysUserDetails) authentication.getPrincipal();
        SysUser user = principal.getUser();
        if (!"ADMIN".equals(user.getRole()) && !"REVIEWER".equals(user.getRole())) {
            throw new BizException(ResultCode.FORBIDDEN, "该账号没有后台登录权限");
        }
        SysUser update = new SysUser();
        update.setId(user.getId());
        update.setLastLoginTime(LocalDateTime.now());
        userMapper.updateById(update);

        return buildLoginVO(user);
    }

    /**
     * 登出：把 token 的 jti 写进 Redis 黑名单，TTL 取 token 的剩余有效期。
     * token 已经无效（过期、伪造）时无需处理，直接返回。
     */
    public void logout(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return;
        }
        JwtUtil.TokenClaims claims = jwtUtil.parse(authorization.substring("Bearer ".length()).trim());
        if (claims == null) {
            return;
        }
        Duration ttl = Duration.between(Instant.now(), claims.expiresAt());
        tokenBlacklistService.block(claims.jti(), ttl);
    }

    /** 学号绑定：与白名单比对，一个学号只能绑一个微信 */
    @Transactional
    public UserVO bind(Long userId, BindRequest request) {
        StudentWhitelist whitelist = whitelistMapper.selectOne(Wrappers.<StudentWhitelist>lambdaQuery()
                .eq(StudentWhitelist::getStudentNo, request.studentNo()));
        if (whitelist == null) {
            throw new BizException(ResultCode.STUDENT_NO_NOT_FOUND);
        }

        StudentProfile exist = profileMapper.selectOne(Wrappers.<StudentProfile>lambdaQuery()
                .eq(StudentProfile::getStudentNo, request.studentNo()));
        if (exist != null && !exist.getUserId().equals(userId)) {
            throw new BizException(ResultCode.STUDENT_NO_BOUND);
        }

        StudentProfile profile = new StudentProfile();
        profile.setUserId(userId);
        profile.setStudentNo(request.studentNo());
        profile.setRealName(whitelist.getRealName());
        profile.setCollege(request.college() == null || request.college().isBlank() ? whitelist.getCollege() : request.college());
        profile.setGrade(request.grade() == null || request.grade().isBlank() ? whitelist.getGrade() : request.grade());
        profile.setMajor(request.major() == null || request.major().isBlank() ? whitelist.getMajor() : request.major());

        if (exist != null) {
            profileMapper.updateById(profile);
        } else {
            profileMapper.insert(profile);
        }

        StudentWhitelist mark = new StudentWhitelist();
        mark.setId(whitelist.getId());
        mark.setUsedFlag(1);
        whitelistMapper.updateById(mark);

        SysUser user = userMapper.selectById(userId);
        return UserVO.of(user, profile, orgName(user.getOrgId()));
    }

    public UserVO currentUser(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        StudentProfile profile = profileMapper.selectById(userId);
        return UserVO.of(user, profile, orgName(user.getOrgId()));
    }

    private LoginVO buildLoginVO(SysUser user) {
        StudentProfile profile = profileMapper.selectById(user.getId());
        boolean needBind = profile == null && "STUDENT".equals(user.getRole());
        UserVO userVO = UserVO.of(user, profile, orgName(user.getOrgId()));
        return new LoginVO(jwtUtil.generate(user), needBind, userVO);
    }

    private String orgName(Long orgId) {
        if (orgId == null) {
            return null;
        }
        Organization organization = organizationMapper.selectById(orgId);
        return organization == null ? null : organization.getName();
    }

    /**
     * devMode 打开时直接用前端传来的 devOpenId，不请求微信服务器；
     * 生产环境走 jscode2session。
     */
    private String resolveOpenId(LoginRequest request) {
        if (properties.isDevMode()) {
            String devOpenId = request.devOpenId();
            if (devOpenId == null || devOpenId.isBlank()) {
                throw new BizException(ResultCode.PARAM_ERROR, "开发模式需要传 devOpenId");
            }
            return devOpenId;
        }

        if (request.code() == null || request.code().isBlank()) {
            throw new BizException(ResultCode.PARAM_ERROR, "缺少登录 code");
        }
        CampusProperties.Wechat wechat = properties.getWechat();
        if (wechat.getAppId() == null || wechat.getAppId().isBlank()) {
            throw new BizException(ResultCode.SYSTEM_ERROR, "未配置小程序 AppID");
        }

        Map<?, ?> body = RestClient.create()
                .get()
                .uri(uriBuilder -> uriBuilder
                        .scheme("https")
                        .host("api.weixin.qq.com")
                        .path("/sns/jscode2session")
                        .queryParam("appid", wechat.getAppId())
                        .queryParam("secret", wechat.getAppSecret())
                        .queryParam("js_code", request.code())
                        .queryParam("grant_type", "authorization_code")
                        .build())
                .retrieve()
                .body(Map.class);

        if (body == null || body.get("openid") == null) {
            log.warn("微信登录失败: {}", body);
            throw new BizException(ResultCode.UNAUTHORIZED, "微信登录失败，请重试");
        }
        return String.valueOf(body.get("openid"));
    }
}
