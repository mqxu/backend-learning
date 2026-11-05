package com.campus.activity.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.activity.common.BizException;
import com.campus.activity.common.DateTimes;
import com.campus.activity.common.LoginUser;
import com.campus.activity.common.PageResult;
import com.campus.activity.common.ResultCode;
import com.campus.activity.dto.ActivitySaveRequest;
import com.campus.activity.entity.Activity;
import com.campus.activity.entity.ActivityCategory;
import com.campus.activity.entity.ActivityFavorite;
import com.campus.activity.entity.ActivitySignup;
import com.campus.activity.entity.AuditRecord;
import com.campus.activity.entity.Organization;
import com.campus.activity.enums.ActivityStatus;
import com.campus.activity.enums.SignupStatus;
import com.campus.activity.mapper.ActivityCategoryMapper;
import com.campus.activity.mapper.ActivityFavoriteMapper;
import com.campus.activity.mapper.ActivityMapper;
import com.campus.activity.mapper.ActivitySignupMapper;
import com.campus.activity.mapper.AuditRecordMapper;
import com.campus.activity.mapper.OrganizationMapper;
import com.campus.activity.vo.ActivityDetailVO;
import com.campus.activity.vo.ActivityItemVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ActivityService {

    private final ActivityMapper activityMapper;
    private final ActivityCategoryMapper categoryMapper;
    private final OrganizationMapper organizationMapper;
    private final ActivitySignupMapper signupMapper;
    private final ActivityFavoriteMapper favoriteMapper;
    private final AuditRecordMapper auditRecordMapper;

    // ---------------------------------------------------------------- 学生端查询

    public List<ActivityCategory> categories() {
        return categoryMapper.selectList(Wrappers.<ActivityCategory>lambdaQuery()
                .eq(ActivityCategory::getStatus, 1)
                .orderByAsc(ActivityCategory::getSort));
    }

    /**
     * @param timeScope 可选值 TODAY / WEEK / MONTH / ENDED
     * @param sort      可选值 HOT / LATEST，默认按开始时间升序
     */
    public PageResult<ActivityItemVO> pageForStudent(Long categoryId, String keyword, String timeScope,
                                                    String sort, long page, long size) {
        LambdaQueryWrapper<Activity> wrapper = Wrappers.<Activity>lambdaQuery();
        wrapper.in(Activity::getStatus, ActivityStatus.PUBLISHED.getCode(), ActivityStatus.ONGOING.getCode());

        if ("ENDED".equals(timeScope)) {
            wrapper.eq(Activity::getStatus, ActivityStatus.FINISHED.getCode());
            wrapper.orderByDesc(Activity::getStartTime);
        } else if (timeScope != null && !timeScope.isBlank()) {
            LocalDateTime from = LocalDate.now().atStartOfDay();
            LocalDateTime to = switch (timeScope) {
                case "TODAY" -> from.plusDays(1);
                case "WEEK" -> from.plusDays(7);
                case "MONTH" -> from.plusDays(30);
                default -> null;
            };
            wrapper.ge(Activity::getStartTime, from);
            if (to != null) {
                wrapper.lt(Activity::getStartTime, to);
            }
            wrapper.orderByAsc(Activity::getStartTime);
        } else if ("HOT".equals(sort)) {
            wrapper.orderByDesc(Activity::getSignupCount);
        } else {
            wrapper.orderByAsc(Activity::getStartTime);
        }

        if (categoryId != null) {
            wrapper.eq(Activity::getCategoryId, categoryId);
        }
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(Activity::getTitle, kw).or().like(Activity::getSummary, kw));
        }

        Page<Activity> result = activityMapper.selectPage(new Page<>(page, size), wrapper);
        return PageResult.of(result, this::toItemVO);
    }

    public List<ActivityItemVO> hot(int limit) {
        List<Activity> list = activityMapper.selectList(Wrappers.<Activity>lambdaQuery()
                .in(Activity::getStatus, ActivityStatus.PUBLISHED.getCode(), ActivityStatus.ONGOING.getCode())
                .orderByDesc(Activity::getSignupCount)
                .last("LIMIT " + Math.min(limit, 20)));
        return list.stream().map(this::toItemVO).toList();
    }

    @Transactional
    public ActivityDetailVO detail(Long activityId, Long userId) {
        Activity activity = activityMapper.selectById(activityId);
        if (activity == null) {
            throw new BizException(ResultCode.ACTIVITY_NOT_FOUND);
        }
        ActivityDetailVO vo = ActivityDetailVO.of(activity, categoryName(activity.getCategoryId()), orgName(activity.getOrgId()));
        activityMapper.increaseView(activityId);

        if (userId != null) {
            ActivitySignup signup = signupMapper.selectOne(Wrappers.<ActivitySignup>lambdaQuery()
                    .eq(ActivitySignup::getActivityId, activityId)
                    .eq(ActivitySignup::getUserId, userId));
            if (signup != null) {
                vo.setMySignupStatus(signup.getStatus());
                vo.setMySignupId(signup.getId());
            }
            Long favoriteCount = favoriteMapper.selectCount(Wrappers.<ActivityFavorite>lambdaQuery()
                    .eq(ActivityFavorite::getActivityId, activityId)
                    .eq(ActivityFavorite::getUserId, userId));
            vo.setFavorited(favoriteCount > 0);
        }
        vo.setActionText(actionText(activity, vo.getMySignupStatus()));
        return vo;
    }

    // ---------------------------------------------------------------- 组织者端

    public PageResult<ActivityItemVO> pageForOrg(LoginUser user, Integer status, long page, long size) {
        LambdaQueryWrapper<Activity> wrapper = Wrappers.<Activity>lambdaQuery()
                .eq(Activity::getCreatorId, user.id())
                .orderByDesc(Activity::getCreateTime);
        if (status != null) {
            wrapper.eq(Activity::getStatus, status);
        }
        Page<Activity> result = activityMapper.selectPage(new Page<>(page, size), wrapper);
        return PageResult.of(result, this::toItemVO);
    }

    @Transactional
    public Long create(LoginUser user, ActivitySaveRequest request) {
        if (user.orgId() == null) {
            throw new BizException(ResultCode.FORBIDDEN, "当前账号未绑定组织，无法发布活动");
        }
        Activity activity = new Activity();
        applyRequest(activity, request);
        activity.setCreatorId(user.id());
        activity.setOrgId(user.orgId());
        activity.setStatus(ActivityStatus.DRAFT.getCode());
        activity.setSignedCount(0);
        activity.setViewCount(0);
        activity.setFavoriteCount(0);
        activity.setSignupCount(0);
        activityMapper.insert(activity);
        return activity.getId();
    }

    @Transactional
    public void update(LoginUser user, Long activityId, ActivitySaveRequest request) {
        Activity activity = requireOwnActivity(user, activityId);
        if (activity.getStatus() == ActivityStatus.FINISHED.getCode()
                || activity.getStatus() == ActivityStatus.CANCELLED.getCode()) {
            throw new BizException(ResultCode.ACTIVITY_STATUS_INVALID);
        }
        applyRequest(activity, request);
        // 已发布活动改动后需要重新审核，避免绕过审核修改时间地点
        if (activity.getStatus() == ActivityStatus.PUBLISHED.getCode()
                || activity.getStatus() == ActivityStatus.ONGOING.getCode()) {
            activity.setStatus(ActivityStatus.PENDING.getCode());
        }
        activityMapper.updateById(activity);
    }

    @Transactional
    public void submit(LoginUser user, Long activityId) {
        Activity activity = requireOwnActivity(user, activityId);
        if (activity.getStatus() != ActivityStatus.DRAFT.getCode()
                && activity.getStatus() != ActivityStatus.REJECTED.getCode()) {
            throw new BizException(ResultCode.ACTIVITY_STATUS_INVALID);
        }
        activity.setStatus(ActivityStatus.PENDING.getCode());
        activity.setRejectReason(null);
        activityMapper.updateById(activity);
    }

    @Transactional
    public void cancelByOrg(LoginUser user, Long activityId, String reason) {
        Activity activity = requireOwnActivity(user, activityId);
        cancelInternal(activity, user, reason);
    }

    @Transactional
    public void offline(LoginUser operator, Long activityId, String reason) {
        Activity activity = activityMapper.selectById(activityId);
        if (activity == null) {
            throw new BizException(ResultCode.ACTIVITY_NOT_FOUND);
        }
        cancelInternal(activity, operator, reason);
    }

    // ---------------------------------------------------------------- 审核

    public PageResult<ActivityItemVO> auditList(long page, long size) {
        Page<Activity> result = activityMapper.selectPage(new Page<>(page, size),
                Wrappers.<Activity>lambdaQuery()
                        .eq(Activity::getStatus, ActivityStatus.PENDING.getCode())
                        .orderByAsc(Activity::getCreateTime));
        return PageResult.of(result, this::toItemVO);
    }

    public PageResult<ActivityItemVO> adminPage(String keyword, Integer status, long page, long size) {
        LambdaQueryWrapper<Activity> wrapper = Wrappers.<Activity>lambdaQuery().orderByDesc(Activity::getCreateTime);
        if (status != null) {
            wrapper.eq(Activity::getStatus, status);
        }
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(Activity::getTitle, keyword.trim());
        }
        Page<Activity> result = activityMapper.selectPage(new Page<>(page, size), wrapper);
        return PageResult.of(result, this::toItemVO);
    }

    @Transactional
    public void audit(LoginUser operator, Long activityId, boolean pass, String reason) {
        Activity activity = activityMapper.selectById(activityId);
        if (activity == null) {
            throw new BizException(ResultCode.ACTIVITY_NOT_FOUND);
        }
        if (activity.getStatus() != ActivityStatus.PENDING.getCode()) {
            throw new BizException(ResultCode.ACTIVITY_STATUS_INVALID, "该活动不在待审核状态");
        }
        int before = activity.getStatus();
        activity.setStatus(pass ? ActivityStatus.PUBLISHED.getCode() : ActivityStatus.REJECTED.getCode());
        activity.setRejectReason(pass ? null : reason);
        if (pass && activity.getPublishTime() == null) {
            activity.setPublishTime(LocalDateTime.now());
        }
        activityMapper.updateById(activity);

        AuditRecord record = new AuditRecord();
        record.setBizType("ACTIVITY");
        record.setBizId(activityId);
        record.setAction(pass ? "APPROVE" : "REJECT");
        record.setOperatorId(operator.id());
        record.setOperatorName(operator.nickname());
        record.setBeforeStatus(String.valueOf(before));
        record.setAfterStatus(String.valueOf(activity.getStatus()));
        record.setRemark(reason);
        auditRecordMapper.insert(record);
    }

    // ---------------------------------------------------------------- 收藏

    @Transactional
    public boolean toggleFavorite(Long userId, Long activityId) {
        Activity activity = activityMapper.selectById(activityId);
        if (activity == null) {
            throw new BizException(ResultCode.ACTIVITY_NOT_FOUND);
        }
        ActivityFavorite exist = favoriteMapper.selectOne(Wrappers.<ActivityFavorite>lambdaQuery()
                .eq(ActivityFavorite::getActivityId, activityId)
                .eq(ActivityFavorite::getUserId, userId));
        if (exist != null) {
            favoriteMapper.deleteById(exist.getId());
            activityMapper.decreaseFavorite(activityId);
            return false;
        }
        ActivityFavorite favorite = new ActivityFavorite();
        favorite.setActivityId(activityId);
        favorite.setUserId(userId);
        try {
            favoriteMapper.insert(favorite);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            return true;
        }
        activityMapper.increaseFavorite(activityId);
        return true;
    }

    // ---------------------------------------------------------------- 内部工具

    private Activity requireOwnActivity(LoginUser user, Long activityId) {
        Activity activity = activityMapper.selectById(activityId);
        if (activity == null) {
            throw new BizException(ResultCode.ACTIVITY_NOT_FOUND);
        }
        if (!Objects.equals(activity.getCreatorId(), user.id()) && !user.isAdmin()) {
            throw new BizException(ResultCode.ACTIVITY_NOT_OWNER);
        }
        return activity;
    }

    private void cancelInternal(Activity activity, LoginUser operator, String reason) {
        if (activity.getStatus() == ActivityStatus.CANCELLED.getCode()) {
            throw new BizException(ResultCode.ACTIVITY_STATUS_INVALID, "活动已取消");
        }
        int before = activity.getStatus();
        activity.setStatus(ActivityStatus.CANCELLED.getCode());
        activity.setRejectReason(reason);
        activityMapper.updateById(activity);

        AuditRecord record = new AuditRecord();
        record.setBizType("ACTIVITY");
        record.setBizId(activity.getId());
        record.setAction("CANCEL");
        record.setOperatorId(operator.id());
        record.setOperatorName(operator.nickname());
        record.setBeforeStatus(String.valueOf(before));
        record.setAfterStatus(String.valueOf(ActivityStatus.CANCELLED.getCode()));
        record.setRemark(reason);
        auditRecordMapper.insert(record);
    }

    private void applyRequest(Activity activity, ActivitySaveRequest request) {
        LocalDateTime start = DateTimes.parse(request.startTime());
        LocalDateTime end = DateTimes.parse(request.endTime());
        LocalDateTime signupStart = DateTimes.parse(request.signupStartTime());
        LocalDateTime signupEnd = DateTimes.parse(request.signupEndTime());
        LocalDateTime cancelDeadline = DateTimes.parse(request.cancelDeadline());

        if (start == null || end == null || signupStart == null || signupEnd == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "活动与报名时间不能为空");
        }
        if (!start.isBefore(end)) {
            throw new BizException(ResultCode.ACTIVITY_TIME_INVALID);
        }
        if (signupStart.isAfter(signupEnd)) {
            throw new BizException(ResultCode.ACTIVITY_TIME_INVALID);
        }
        if (signupEnd.isAfter(start)) {
            throw new BizException(ResultCode.ACTIVITY_TIME_INVALID, "报名截止时间不能晚于活动开始时间");
        }
        if (cancelDeadline == null) {
            cancelDeadline = start.minusHours(2);
        }
        if (cancelDeadline.isAfter(start)) {
            throw new BizException(ResultCode.ACTIVITY_TIME_INVALID, "取消报名截止时间不能晚于活动开始时间");
        }

        activity.setTitle(request.title().trim());
        activity.setCoverUrl(request.coverUrl());
        activity.setCategoryId(request.categoryId() == null ? 1001L : request.categoryId());
        activity.setSummary(request.summary());
        activity.setContent(request.content());
        activity.setStartTime(start);
        activity.setEndTime(end);
        activity.setSignupStartTime(signupStart);
        activity.setSignupEndTime(signupEnd);
        activity.setCancelDeadline(cancelDeadline);
        activity.setLocationName(request.locationName());
        activity.setAddress(request.address());
        activity.setLatitude(request.latitude());
        activity.setLongitude(request.longitude());
        activity.setCapacity(request.capacity() == null ? 0 : request.capacity());
        activity.setWaitlistEnabled(request.waitlistEnabled() == null ? 0 : request.waitlistEnabled());
        activity.setAuditRequired(request.auditRequired() == null ? 0 : request.auditRequired());
        activity.setCheckinType(request.checkinType() == null ? 1 : request.checkinType());
        activity.setFormFields(request.formFields());
    }

    private ActivityItemVO toItemVO(Activity activity) {
        return ActivityItemVO.of(activity, categoryName(activity.getCategoryId()), orgName(activity.getOrgId()));
    }

    private String categoryName(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        ActivityCategory category = categoryMapper.selectById(categoryId);
        return category == null ? null : category.getName();
    }

    private String orgName(Long orgId) {
        if (orgId == null) {
            return null;
        }
        Organization organization = organizationMapper.selectById(orgId);
        return organization == null ? null : organization.getName();
    }

    private String actionText(Activity activity, Integer myStatus) {
        ActivityStatus status = ActivityStatus.of(activity.getStatus());
        if (status == ActivityStatus.CANCELLED) {
            return "活动已取消";
        }
        if (status == ActivityStatus.FINISHED) {
            return "活动已结束";
        }
        if (myStatus != null) {
            SignupStatus signupStatus = SignupStatus.of(myStatus);
            return switch (signupStatus) {
                case PENDING -> "报名待审核";
                case SUCCESS -> "已报名";
                case WAITLIST -> "候补中";
                case CHECKED_IN -> "已签到";
                default -> ActivityItemVO.isSignupOpen(activity) ? "立即报名" : "不可报名";
            };
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(activity.getSignupStartTime())) {
            return "报名未开始";
        }
        if (now.isAfter(activity.getSignupEndTime())) {
            return "报名已结束";
        }
        if (!activity.hasSeat()) {
            return activity.waitlistOn() ? "名额已满，可候补" : "名额已满";
        }
        return "立即报名";
    }
}
