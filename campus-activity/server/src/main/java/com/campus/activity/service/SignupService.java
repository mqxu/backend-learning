package com.campus.activity.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.activity.common.BizException;
import com.campus.activity.common.LoginUser;
import com.campus.activity.common.PageResult;
import com.campus.activity.common.ResultCode;
import com.campus.activity.dto.SignupAuditRequest;
import com.campus.activity.dto.SignupRequest;
import com.campus.activity.entity.Activity;
import com.campus.activity.entity.ActivitySignup;
import com.campus.activity.entity.StudentProfile;
import com.campus.activity.enums.ActivityStatus;
import com.campus.activity.enums.SignupStatus;
import com.campus.activity.mapper.ActivityMapper;
import com.campus.activity.mapper.ActivitySignupMapper;
import com.campus.activity.mapper.StudentProfileMapper;
import com.campus.activity.vo.SignupDetailVO;
import com.campus.activity.vo.SignupVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class SignupService {

    private final ActivityMapper activityMapper;
    private final ActivitySignupMapper signupMapper;
    private final StudentProfileMapper profileMapper;
    private final NoticeService noticeService;

    /**
     * 提交报名。
     * 名额占用依赖单条条件 UPDATE，不做“先查后写”；唯一索引 uk_activity_user 兜底重复提交。
     */
    @Transactional
    public SignupVO signup(LoginUser user, Long activityId, SignupRequest request) {
        if (profileMapper.selectById(user.id()) == null) {
            throw new BizException(ResultCode.NEED_BIND);
        }
        Activity activity = activityMapper.selectById(activityId);
        if (activity == null) {
            throw new BizException(ResultCode.ACTIVITY_NOT_FOUND);
        }
        if (!Objects.equals(activity.getStatus(), ActivityStatus.PUBLISHED.getCode())) {
            throw new BizException(ResultCode.ACTIVITY_NOT_OPEN);
        }

        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(activity.getSignupStartTime())) {
            throw new BizException(ResultCode.ACTIVITY_NOT_OPEN, "报名尚未开始");
        }
        if (now.isAfter(activity.getSignupEndTime())) {
            throw new BizException(ResultCode.ACTIVITY_NOT_OPEN, "报名已结束");
        }

        ActivitySignup exist = signupMapper.selectOne(Wrappers.<ActivitySignup>lambdaQuery()
                .eq(ActivitySignup::getActivityId, activityId)
                .eq(ActivitySignup::getUserId, user.id()));
        if (exist != null) {
            SignupStatus existStatus = SignupStatus.of(exist.getStatus());
            if (existStatus != SignupStatus.CANCELLED && existStatus != SignupStatus.REJECTED) {
                throw new BizException(ResultCode.SIGNUP_REPEATED);
            }
        }

        SignupStatus target;
        if (activity.needAudit()) {
            target = SignupStatus.PENDING;
        } else if (activityMapper.occupySeat(activityId) == 1) {
            target = SignupStatus.SUCCESS;
        } else if (activity.waitlistOn()) {
            target = SignupStatus.WAITLIST;
        } else {
            throw new BizException(ResultCode.SIGNUP_FULL);
        }

        ActivitySignup signup = exist == null ? new ActivitySignup() : exist;
        signup.setActivityId(activityId);
        signup.setUserId(user.id());
        signup.setStatus(target.getCode());
        signup.setSignupNo(nextSignupNo());
        signup.setFormData(request == null ? null : request.formData());
        signup.setWaitlistSeq(target == SignupStatus.WAITLIST
                ? activityMapper.maxWaitlistSeq(activityId) + 1 : 0);
        signup.setAuditUserId(null);
        signup.setAuditTime(null);
        signup.setCancelTime(null);
        signup.setCheckinTime(null);

        try {
            if (exist == null) {
                signupMapper.insert(signup);
            } else {
                signupMapper.updateById(signup);
            }
        } catch (DuplicateKeyException e) {
            // 同一用户并发提交，唯一索引拦下后按已报名处理
            throw new BizException(ResultCode.SIGNUP_REPEATED);
        }

        activityMapper.update(null, Wrappers.<Activity>lambdaUpdate()
                .eq(Activity::getId, activityId)
                .setSql("signup_count = signup_count + 1"));

        if (target == SignupStatus.SUCCESS) {
            noticeService.send(user.id(), "SIGNUP", "报名成功",
                    "你已成功报名「" + activity.getTitle() + "」，请按时到场并扫码签到。", activityId);
        } else if (target == SignupStatus.PENDING) {
            noticeService.send(user.id(), "SIGNUP", "报名已提交",
                    "「" + activity.getTitle() + "」的报名需要组织者审核，请留意审核结果。", activityId);
        } else if (target == SignupStatus.WAITLIST) {
            noticeService.send(user.id(), "WAITLIST", "已加入候补",
                    "「" + activity.getTitle() + "」名额已满，你已进入候补队列，有人取消时会按顺序转正。", activityId);
        }

        return SignupVO.of(signupMapper.selectById(signup.getId()), activity);
    }

    @Transactional
    public void cancel(LoginUser user, Long signupId) {
        ActivitySignup signup = signupMapper.selectById(signupId);
        if (signup == null || !Objects.equals(signup.getUserId(), user.id())) {
            throw new BizException(ResultCode.SIGNUP_NOT_FOUND);
        }
        SignupStatus status = SignupStatus.of(signup.getStatus());
        if (!status.cancellable()) {
            throw new BizException(ResultCode.SIGNUP_STATUS_INVALID, "当前状态不支持取消报名");
        }

        Activity activity = activityMapper.selectById(signup.getActivityId());
        LocalDateTime now = LocalDateTime.now();
        if (activity != null && activity.getCancelDeadline() != null && now.isAfter(activity.getCancelDeadline())) {
            throw new BizException(ResultCode.SIGNUP_CANNOT_CANCEL);
        }

        signup.setStatus(SignupStatus.CANCELLED.getCode());
        signup.setCancelTime(now);
        signupMapper.updateById(signup);

        if (status.occupiesSeat()) {
            activityMapper.releaseSeat(signup.getActivityId());
            promoteWaitlist(signup.getActivityId());
        }
        if (activity != null) {
            noticeService.send(user.id(), "SIGNUP", "已取消报名",
                    "你已取消「" + activity.getTitle() + "」的报名。", activity.getId());
        }
    }

    /** 候补队列转正：按序号取队首，占位成功才转正 */
    @Transactional
    public void promoteWaitlist(Long activityId) {
        ActivitySignup next = signupMapper.findFirstWaitlist(activityId);
        if (next == null) {
            return;
        }
        if (activityMapper.occupySeat(activityId) != 1) {
            return;
        }
        next.setStatus(SignupStatus.SUCCESS.getCode());
        signupMapper.updateById(next);

        Activity activity = activityMapper.selectById(activityId);
        noticeService.send(next.getUserId(), "WAITLIST", "候补转正成功",
                "「" + (activity == null ? "活动" : activity.getTitle()) + "」已为你转正，请按时到场。", activityId);
    }

    public PageResult<SignupVO> pageMine(Long userId, Integer status, long page, long size) {
        Page<ActivitySignup> result = signupMapper.selectPage(new Page<>(page, size),
                Wrappers.<ActivitySignup>lambdaQuery()
                        .eq(ActivitySignup::getUserId, userId)
                        .eq(status != null, ActivitySignup::getStatus, status)
                        .orderByDesc(ActivitySignup::getCreateTime));
        Map<Long, Activity> activityMap = loadActivities(result.getRecords().stream()
                .map(ActivitySignup::getActivityId).toList());
        return PageResult.of(result, s -> SignupVO.of(s, activityMap.get(s.getActivityId())));
    }

    public SignupVO signupDetail(Long userId, Long signupId) {
        ActivitySignup signup = signupMapper.selectById(signupId);
        if (signup == null || !Objects.equals(signup.getUserId(), userId)) {
            throw new BizException(ResultCode.SIGNUP_NOT_FOUND);
        }
        return SignupVO.of(signup, activityMapper.selectById(signup.getActivityId()));
    }

    // ---------------------------------------------------------------- 组织者

    public PageResult<SignupDetailVO> pageByActivity(LoginUser user, Long activityId, Integer status,
                                                     String keyword, long page, long size) {
        requireManage(user, activityId);

        LambdaQueryWrapper<ActivitySignup> wrapper = Wrappers.<ActivitySignup>lambdaQuery()
                .eq(ActivitySignup::getActivityId, activityId)
                .eq(status != null, ActivitySignup::getStatus, status)
                .orderByAsc(ActivitySignup::getCreateTime);
        Page<ActivitySignup> result = signupMapper.selectPage(new Page<>(page, size), wrapper);

        Map<Long, StudentProfile> profiles = loadProfiles(result.getRecords().stream()
                .map(ActivitySignup::getUserId).toList());

        List<SignupDetailVO> records = result.getRecords().stream()
                .map(s -> SignupDetailVO.of(s, profiles.get(s.getUserId())))
                .filter(v -> keyword == null || keyword.isBlank()
                        || (v.getRealName() != null && v.getRealName().contains(keyword))
                        || (v.getStudentNo() != null && v.getStudentNo().contains(keyword)))
                .toList();

        return new PageResult<>(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Transactional
    public int auditByOrg(LoginUser user, Long activityId, SignupAuditRequest request) {
        Activity activity = requireManage(user, activityId);
        int handled = 0;
        for (Long signupId : request.signupIds()) {
            ActivitySignup signup = signupMapper.selectById(signupId);
            if (signup == null || !Objects.equals(signup.getActivityId(), activityId)) {
                continue;
            }
            if (!Objects.equals(signup.getStatus(), SignupStatus.PENDING.getCode())) {
                continue;
            }
            if (Boolean.TRUE.equals(request.pass())) {
                if (activityMapper.occupySeat(activityId) != 1) {
                    throw new BizException(ResultCode.SIGNUP_FULL, "名额不足，无法通过全部报名");
                }
                signup.setStatus(SignupStatus.SUCCESS.getCode());
                noticeService.send(signup.getUserId(), "AUDIT", "报名审核通过",
                        "「" + activity.getTitle() + "」的报名已通过审核。", activityId);
            } else {
                signup.setStatus(SignupStatus.REJECTED.getCode());
                noticeService.send(signup.getUserId(), "AUDIT", "报名审核未通过",
                        "「" + activity.getTitle() + "」的报名未通过" +
                                (request.reason() == null ? "" : "，原因：" + request.reason()), activityId);
            }
            signup.setAuditUserId(user.id());
            signup.setAuditTime(LocalDateTime.now());
            signupMapper.updateById(signup);
            handled++;
        }
        return handled;
    }

    Activity requireManage(LoginUser user, Long activityId) {
        Activity activity = activityMapper.selectById(activityId);
        if (activity == null) {
            throw new BizException(ResultCode.ACTIVITY_NOT_FOUND);
        }
        if (!Objects.equals(activity.getCreatorId(), user.id()) && !user.isAdmin()) {
            throw new BizException(ResultCode.ACTIVITY_NOT_OWNER);
        }
        return activity;
    }

    private Map<Long, Activity> loadActivities(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, Activity> map = new HashMap<>();
        activityMapper.selectByIds(ids).forEach(a -> map.put(a.getId(), a));
        return map;
    }

    private Map<Long, StudentProfile> loadProfiles(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, StudentProfile> map = new HashMap<>();
        profileMapper.selectByIds(userIds).forEach(p -> map.put(p.getUserId(), p));
        return map;
    }

    private String nextSignupNo() {
        return "C" + System.currentTimeMillis() + ThreadLocalRandom.current().nextInt(100, 999);
    }
}
