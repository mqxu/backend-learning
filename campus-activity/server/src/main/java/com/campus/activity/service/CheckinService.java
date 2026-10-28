package com.campus.activity.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campus.activity.common.BizException;
import com.campus.activity.common.DateTimes;
import com.campus.activity.common.LoginUser;
import com.campus.activity.common.ResultCode;
import com.campus.activity.dto.CheckinRequest;
import com.campus.activity.dto.ManualCheckinRequest;
import com.campus.activity.entity.Activity;
import com.campus.activity.entity.ActivityCheckin;
import com.campus.activity.entity.ActivitySignup;
import com.campus.activity.entity.AuditRecord;
import com.campus.activity.entity.StudentProfile;
import com.campus.activity.enums.SignupStatus;
import com.campus.activity.mapper.ActivityCheckinMapper;
import com.campus.activity.mapper.ActivityMapper;
import com.campus.activity.mapper.ActivitySignupMapper;
import com.campus.activity.mapper.AuditRecordMapper;
import com.campus.activity.mapper.StudentProfileMapper;
import com.campus.activity.vo.ActivityStatVO;
import com.campus.activity.vo.CheckinQrVO;
import com.campus.activity.vo.CheckinResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class CheckinService {

    private static final double EARTH_RADIUS_METERS = 6_371_000;
    private static final double MAX_DISTANCE_METERS = 200;

    private final ActivityMapper activityMapper;
    private final ActivitySignupMapper signupMapper;
    private final ActivityCheckinMapper checkinMapper;
    private final StudentProfileMapper profileMapper;
    private final AuditRecordMapper auditRecordMapper;
    private final CheckinTokenService tokenService;
    private final com.campus.activity.config.CampusProperties properties;

    /** 组织者核销台：返回动态签到码与到场概览 */
    public CheckinQrVO qr(LoginUser user, Long activityId) {
        Activity activity = requireManage(user, activityId);
        CheckinQrVO vo = new CheckinQrVO();
        vo.setActivityId(activityId);
        vo.setActivityTitle(activity.getTitle());
        vo.setToken(tokenService.generate(activityId));
        vo.setRefreshSeconds(tokenService.refreshSeconds());
        vo.setSignedCount(signupMapper.countByStatus(activityId, SignupStatus.SUCCESS.getCode()));
        vo.setCheckedInCount(signupMapper.countByStatus(activityId, SignupStatus.CHECKED_IN.getCode()));

        List<ActivityCheckin> recent = checkinMapper.selectList(Wrappers.<ActivityCheckin>lambdaQuery()
                .eq(ActivityCheckin::getActivityId, activityId)
                .orderByDesc(ActivityCheckin::getCheckinTime)
                .last("LIMIT 5"));
        vo.setRecentCheckins(recent.stream().map(c -> {
            StudentProfile profile = profileMapper.selectById(c.getUserId());
            return profile == null ? "同学" : profile.getRealName();
        }).toList());
        return vo;
    }

    /** 学生扫码签到 */
    @Transactional
    public CheckinResultVO checkin(LoginUser user, CheckinRequest request) {
        Long activityId = tokenService.verify(request.token());
        Activity activity = activityMapper.selectById(activityId);
        if (activity == null) {
            throw new BizException(ResultCode.ACTIVITY_NOT_FOUND);
        }
        if (activity.getCheckinType() != null && activity.getCheckinType() == 3) {
            throw new BizException(ResultCode.CHECKIN_CLOSED, "该活动由组织者统一签到，无需扫码");
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime open = activity.getStartTime().minusMinutes(properties.getCheckin().getOpenBeforeMinutes());
        LocalDateTime close = activity.getEndTime().plusMinutes(properties.getCheckin().getCloseAfterMinutes());
        if (now.isBefore(open) || now.isAfter(close)) {
            throw new BizException(ResultCode.CHECKIN_CLOSED);
        }

        ActivitySignup signup = signupMapper.selectOne(Wrappers.<ActivitySignup>lambdaQuery()
                .eq(ActivitySignup::getActivityId, activityId)
                .eq(ActivitySignup::getUserId, user.id()));
        if (signup == null) {
            throw new BizException(ResultCode.CHECKIN_NOT_SIGNED_UP);
        }
        if (Objects.equals(signup.getStatus(), SignupStatus.CHECKED_IN.getCode())) {
            throw new BizException(ResultCode.CHECKIN_REPEATED);
        }
        if (!Objects.equals(signup.getStatus(), SignupStatus.SUCCESS.getCode())) {
            throw new BizException(ResultCode.CHECKIN_NOT_SIGNED_UP, "报名未成功，无法签到");
        }

        if (activity.getCheckinType() != null && activity.getCheckinType() == 2) {
            double distance = distanceMeters(activity.getLatitude(), activity.getLongitude(),
                    request.latitude(), request.longitude());
            if (distance > MAX_DISTANCE_METERS) {
                throw new BizException(ResultCode.CHECKIN_OUT_OF_RANGE);
            }
        }

        ActivityCheckin checkin = new ActivityCheckin();
        checkin.setActivityId(activityId);
        checkin.setSignupId(signup.getId());
        checkin.setUserId(user.id());
        checkin.setCheckinType(activity.getCheckinType() != null && activity.getCheckinType() == 2 ? 2 : 1);
        checkin.setLatitude(request.latitude());
        checkin.setLongitude(request.longitude());
        checkin.setCheckinTime(now);
        try {
            checkinMapper.insert(checkin);
        } catch (DuplicateKeyException e) {
            throw new BizException(ResultCode.CHECKIN_REPEATED);
        }

        signup.setStatus(SignupStatus.CHECKED_IN.getCode());
        signup.setCheckinTime(now);
        signupMapper.updateById(signup);

        StudentProfile profile = profileMapper.selectById(user.id());
        return CheckinResultVO.ok(activityId, activity.getTitle(), DateTimes.format(now),
                profile == null ? null : profile.getRealName());
    }

    /** 组织者手动补签 */
    @Transactional
    public void manualCheckin(LoginUser user, Long activityId, ManualCheckinRequest request) {
        Activity activity = requireManage(user, activityId);
        ActivitySignup signup = signupMapper.selectById(request.signupId());
        if (signup == null || !Objects.equals(signup.getActivityId(), activityId)) {
            throw new BizException(ResultCode.SIGNUP_NOT_FOUND);
        }
        if (Objects.equals(signup.getStatus(), SignupStatus.CHECKED_IN.getCode())) {
            throw new BizException(ResultCode.CHECKIN_REPEATED);
        }

        LocalDateTime now = LocalDateTime.now();
        ActivityCheckin checkin = new ActivityCheckin();
        checkin.setActivityId(activityId);
        checkin.setSignupId(signup.getId());
        checkin.setUserId(signup.getUserId());
        checkin.setCheckinType(3);
        checkin.setOperatorId(user.id());
        checkin.setRemark(request.remark());
        checkin.setCheckinTime(now);
        try {
            checkinMapper.insert(checkin);
        } catch (DuplicateKeyException e) {
            throw new BizException(ResultCode.CHECKIN_REPEATED);
        }

        signup.setStatus(SignupStatus.CHECKED_IN.getCode());
        signup.setCheckinTime(now);
        signupMapper.updateById(signup);

        AuditRecord record = new AuditRecord();
        record.setBizType("CHECKIN");
        record.setBizId(signup.getId());
        record.setAction("MANUAL_CHECKIN");
        record.setOperatorId(user.id());
        record.setOperatorName(user.nickname());
        record.setRemark(request.remark());
        auditRecordMapper.insert(record);
        log.info("手动补签: activity={}, signup={}, operator={}, reason={}",
                activityId, signup.getId(), user.id(), request.remark());
    }

    public ActivityStatVO stat(LoginUser user, Long activityId) {
        Activity activity = requireManage(user, activityId);
        ActivityStatVO vo = new ActivityStatVO();
        vo.setActivityId(activityId);
        vo.setActivityTitle(activity.getTitle());
        vo.setCapacity(activity.getCapacity());
        vo.setSignedCount(activity.getSignedCount());

        vo.setPendingCount(signupMapper.countByStatus(activityId, SignupStatus.PENDING.getCode()));
        vo.setWaitlistCount(signupMapper.countByStatus(activityId, SignupStatus.WAITLIST.getCode()));
        vo.setCheckedInCount(signupMapper.countByStatus(activityId, SignupStatus.CHECKED_IN.getCode()));
        vo.setCancelledCount(signupMapper.countByStatus(activityId, SignupStatus.CANCELLED.getCode()));
        vo.setNoShowCount(signupMapper.countByStatus(activityId, SignupStatus.NO_SHOW.getCode()));
        vo.setSuccessCount(signupMapper.countByStatus(activityId, SignupStatus.SUCCESS.getCode()));

        int total = vo.getPendingCount() + vo.getSuccessCount() + vo.getCheckedInCount()
                + vo.getCancelledCount() + vo.getNoShowCount() + vo.getWaitlistCount();
        vo.setTotalSignup(total);

        int attended = vo.getCheckedInCount();
        int shouldAttend = vo.getCheckedInCount() + vo.getNoShowCount() + vo.getSuccessCount();
        vo.setAttendanceRate(shouldAttend == 0 ? 0 : Math.round(attended * 1000.0 / shouldAttend) / 10.0);
        return vo;
    }

    private Activity requireManage(LoginUser user, Long activityId) {
        Activity activity = activityMapper.selectById(activityId);
        if (activity == null) {
            throw new BizException(ResultCode.ACTIVITY_NOT_FOUND);
        }
        if (!Objects.equals(activity.getCreatorId(), user.id()) && !user.isAdmin()) {
            throw new BizException(ResultCode.ACTIVITY_NOT_OWNER);
        }
        return activity;
    }

    /** Haversine 距离，单位米 */
    private double distanceMeters(BigDecimal lat1, BigDecimal lon1, BigDecimal lat2, BigDecimal lon2) {
        if (lat1 == null || lon1 == null || lat2 == null || lon2 == null) {
            return 0;
        }
        double radLat1 = Math.toRadians(lat1.doubleValue());
        double radLat2 = Math.toRadians(lat2.doubleValue());
        double deltaLat = radLat2 - radLat1;
        double deltaLon = Math.toRadians(lon2.doubleValue() - lon1.doubleValue());
        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(radLat1) * Math.cos(radLat2) * Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);
        return 2 * EARTH_RADIUS_METERS * Math.asin(Math.sqrt(a));
    }
}
