package com.campus.activity.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.activity.common.BizException;
import com.campus.activity.common.LoginUser;
import com.campus.activity.common.PageResult;
import com.campus.activity.common.ResultCode;
import com.campus.activity.entity.Activity;
import com.campus.activity.entity.AuditRecord;
import com.campus.activity.entity.Organization;
import com.campus.activity.entity.StudentProfile;
import com.campus.activity.entity.SysUser;
import com.campus.activity.enums.ActivityStatus;
import com.campus.activity.mapper.ActivityCheckinMapper;
import com.campus.activity.mapper.ActivityMapper;
import com.campus.activity.mapper.ActivitySignupMapper;
import com.campus.activity.mapper.AuditRecordMapper;
import com.campus.activity.mapper.OrganizationMapper;
import com.campus.activity.mapper.StudentProfileMapper;
import com.campus.activity.mapper.SysUserMapper;
import com.campus.activity.vo.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AdminService {

    private static final Set<String> ROLES = Set.of("STUDENT", "ORGANIZER", "REVIEWER", "ADMIN");

    private final SysUserMapper userMapper;
    private final StudentProfileMapper profileMapper;
    private final OrganizationMapper organizationMapper;
    private final ActivityMapper activityMapper;
    private final ActivitySignupMapper signupMapper;
    private final ActivityCheckinMapper checkinMapper;
    private final AuditRecordMapper auditRecordMapper;

    public PageResult<UserVO> pageUsers(String keyword, String role, long page, long size) {
        LambdaQueryWrapper<SysUser> wrapper = Wrappers.<SysUser>lambdaQuery()
                .eq(role != null && !role.isBlank(), SysUser::getRole, role)
                .orderByDesc(SysUser::getCreateTime);
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(SysUser::getNickname, kw).or().like(SysUser::getUsername, kw));
        }
        Page<SysUser> result = userMapper.selectPage(new Page<>(page, size), wrapper);

        List<UserVO> records = result.getRecords().stream().map(user -> {
            StudentProfile profile = profileMapper.selectById(user.getId());
            Organization org = user.getOrgId() == null ? null : organizationMapper.selectById(user.getOrgId());
            return UserVO.of(user, profile, org == null ? null : org.getName());
        }).toList();
        return new PageResult<>(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Transactional
    public void changeRole(LoginUser operator, Long userId, String role, Long orgId) {
        if (!ROLES.contains(role)) {
            throw new BizException(ResultCode.PARAM_ERROR, "未知角色: " + role);
        }
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "用户不存在");
        }
        String before = user.getRole();
        user.setRole(role);
        user.setOrgId("ORGANIZER".equals(role) ? orgId : null);
        userMapper.updateById(user);

        AuditRecord record = new AuditRecord();
        record.setBizType("USER");
        record.setBizId(userId);
        record.setAction("ROLE_CHANGE");
        record.setOperatorId(operator.id());
        record.setOperatorName(operator.nickname());
        record.setBeforeStatus(before);
        record.setAfterStatus(role);
        auditRecordMapper.insert(record);
    }

    public Map<String, Object> dashboard() {
        long userTotal = userMapper.selectCount(null);
        long studentTotal = profileMapper.selectCount(null);
        long activityTotal = activityMapper.selectCount(null);
        long published = activityMapper.selectCount(Wrappers.<Activity>lambdaQuery()
                .eq(Activity::getStatus, ActivityStatus.PUBLISHED.getCode()));
        long pending = activityMapper.selectCount(Wrappers.<Activity>lambdaQuery()
                .eq(Activity::getStatus, ActivityStatus.PENDING.getCode()));
        long signupTotal = signupMapper.selectCount(null);
        long checkinTotal = checkinMapper.selectCount(null);

        List<Activity> top = activityMapper.selectList(Wrappers.<Activity>lambdaQuery()
                .in(Activity::getStatus, ActivityStatus.PUBLISHED.getCode(), ActivityStatus.FINISHED.getCode())
                .orderByDesc(Activity::getSignupCount)
                .last("LIMIT 5"));
        List<Map<String, Object>> topActivities = top.stream().map(a -> {
            Map<String, Object> item = new HashMap<>();
            item.put("id", a.getId());
            item.put("title", a.getTitle());
            item.put("signupCount", a.getSignupCount());
            item.put("capacity", a.getCapacity());
            return item;
        }).toList();

        Map<String, Object> data = new HashMap<>();
        data.put("userTotal", userTotal);
        data.put("studentTotal", studentTotal);
        data.put("activityTotal", activityTotal);
        data.put("publishedCount", published);
        data.put("pendingAuditCount", pending);
        data.put("signupTotal", signupTotal);
        data.put("checkinTotal", checkinTotal);
        data.put("topActivities", topActivities);
        return data;
    }

    public PageResult<AuditRecord> pageLogs(long page, long size) {
        Page<AuditRecord> result = auditRecordMapper.selectPage(new Page<>(page, size),
                Wrappers.<AuditRecord>lambdaQuery().orderByDesc(AuditRecord::getCreateTime));
        return PageResult.of(result);
    }
}
