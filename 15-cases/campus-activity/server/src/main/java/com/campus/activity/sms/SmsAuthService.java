package com.campus.activity.sms;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campus.activity.common.BizException;
import com.campus.activity.common.ResultCode;
import com.campus.activity.config.SmsProperties;
import com.campus.activity.entity.Organization;
import com.campus.activity.entity.StudentProfile;
import com.campus.activity.entity.SysUser;
import com.campus.activity.mapper.OrganizationMapper;
import com.campus.activity.mapper.StudentProfileMapper;
import com.campus.activity.mapper.SysUserMapper;
import com.campus.activity.security.JwtUtil;
import com.campus.activity.sms.dto.SmsLoginRequest;
import com.campus.activity.vo.LoginVO;
import com.campus.activity.vo.UserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 手机号 + 短信验证码登录。
 *
 * <p>与微信登录共用 sys_user 表与 JWT 签发逻辑：
 * 手机号首次登录自动建号（openId 用 {@code phone:<手机号>} 占位，唯一且可追溯），
 * 学号绑定仍然走原有的绑定接口，needBind 含义保持一致。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SmsAuthService {

    private final SmsCodeService smsCodeService;
    private final SmsProperties properties;
    private final SysUserMapper userMapper;
    private final StudentProfileMapper profileMapper;
    private final OrganizationMapper organizationMapper;
    private final JwtUtil jwtUtil;

    /**
     * 发送验证码。Mock 通道且 expose-code=true 时把验证码回传给前端，便于本地联调。
     */
    public SmsSendVO sendCode(String phone) {
        String code = smsCodeService.sendCode(phone);
        String mockCode = smsCodeService.isMock() && properties.isExposeCode() ? code : null;
        return new SmsSendVO(phone, properties.getCodeExpireSeconds(),
                properties.getSendIntervalSeconds(), mockCode);
    }

    /**
     * 验证码登录：校验验证码 → 按手机号找用户（没有就自动建号）→ 签发 JWT。
     */
    @Transactional
    public LoginVO login(SmsLoginRequest request) {
        String phone = request.phone();
        smsCodeService.verify(phone, request.code());

        SysUser user = userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getPhone, phone));
        if (user == null) {
            user = createPhoneUser(phone);
        } else {
            if (user.getStatus() != null && user.getStatus() == 0) {
                throw new BizException(ResultCode.ACCOUNT_DISABLED);
            }
            SysUser update = new SysUser();
            update.setId(user.getId());
            update.setLastLoginTime(LocalDateTime.now());
            userMapper.updateById(update);
        }

        log.info("手机号 {} 登录成功，userId={}", mask(phone), user.getId());
        return buildLoginVO(user);
    }

    /** 手机号首次登录自动建号：STUDENT 角色，昵称取手机号后 4 位 */
    private SysUser createPhoneUser(String phone) {
        SysUser user = new SysUser();
        user.setOpenId("phone:" + phone);
        user.setPhone(phone);
        user.setNickname("手机用户" + phone.substring(phone.length() - 4));
        user.setRole("STUDENT");
        user.setStatus(1);
        user.setLastLoginTime(LocalDateTime.now());
        try {
            userMapper.insert(user);
            log.info("手机号 {} 首次登录，已自动创建账号 userId={}", mask(phone), user.getId());
            return user;
        } catch (DuplicateKeyException e) {
            // 并发首次登录：另一个请求刚好建了号，重新查一次即可
            SysUser exist = userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                    .eq(SysUser::getPhone, phone));
            if (exist != null) {
                return exist;
            }
            throw new BizException(ResultCode.SYSTEM_ERROR, "手机号账号创建失败，请重试");
        }
    }

    private LoginVO buildLoginVO(SysUser user) {
        StudentProfile profile = profileMapper.selectById(user.getId());
        // 学生且尚未绑定学号时需要引导去绑定；组织者/管理员不要求
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

    private String mask(String phone) {
        return phone == null || phone.length() < 7
                ? phone
                : phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
