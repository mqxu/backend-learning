<template>
  <view class="page" v-if="activity.id">
    <view class="cover">
      <image v-if="activity.coverUrl" :src="activity.coverUrl" mode="aspectFill" class="cover-img" />
      <view v-else class="cover-fallback">{{ activity.categoryName }}</view>
    </view>

    <view class="card head">
      <view class="between">
        <text class="act-title">{{ activity.title }}</text>
        <text class="tag" :class="activity.signupOpen ? 'tag-green' : 'tag-orange'">
          {{ activity.signupOpen ? '报名中' : activity.statusLabel }}
        </text>
      </view>
      <view class="muted" style="margin-top: 12rpx">{{ activity.summary }}</view>

      <view class="info">
        <view class="info-row">
          <text class="info-label">时间</text>
          <text class="info-value">{{ activity.startTime }} — {{ activity.endTime?.slice(11) }}</text>
        </view>
        <view class="info-row">
          <text class="info-label">地点</text>
          <text class="info-value">{{ activity.locationName }}</text>
        </view>
        <view class="info-row">
          <text class="info-label">主办</text>
          <text class="info-value">{{ activity.orgName }}</text>
        </view>
        <view class="info-row">
          <text class="info-label">报名</text>
          <text class="info-value">
            {{ activity.signedCount }} 人已报名{{ activity.capacity ? ` / 限 ${activity.capacity} 人` : '（不限人数）' }}
          </text>
        </view>
        <view class="info-row">
          <text class="info-label">截止</text>
          <text class="info-value">{{ activity.signupEndTime }}</text>
        </view>
      </view>

      <view class="progress">
        <view class="progress-bar">
          <view class="progress-inner" :style="{ width: progressPercent + '%' }"></view>
        </view>
        <text class="muted">{{ progressText }}</text>
      </view>
    </view>

    <view class="card">
      <text class="section-title">活动介绍</text>
      <text class="content">{{ activity.content || '组织者暂未填写详细介绍' }}</text>
    </view>

    <view class="card" v-if="formFields.length">
      <text class="section-title">报名需要填写</text>
      <view v-for="f in formFields" :key="f.key" class="field-line">
        <text class="muted">{{ f.label }}{{ f.required ? '（必填）' : '' }}</text>
      </view>
    </view>

    <view class="bottom-bar">
      <view class="fav" @click="toggleFav">
        <text :class="activity.favorited ? 'fav-on' : 'fav-off'">{{ activity.favorited ? '已收藏' : '收藏' }}</text>
      </view>
      <view
        class="btn-primary action"
        :class="{ disabled: !canSignup }"
        @click="handleAction"
      >{{ activity.actionText || '不可报名' }}</view>
    </view>
  </view>
</template>

<script setup lang="ts">
import type { ActivityDetail, FormField } from '../../types'
import { computed, ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { api } from '../../utils/api'
import { ensureLogin } from '../../utils/auth'

const activityId = ref<number | null>(null)
const activity = ref({} as ActivityDetail)
const formFields = ref<FormField[]>([])

const progressPercent = computed(() => {
  const { capacity, signedCount } = activity.value
  if (!capacity || capacity <= 0) return 8
  return Math.min(100, Math.round(((signedCount || 0) / capacity) * 100))
})

const progressText = computed(() => {
  const { capacity, signedCount = 0 } = activity.value
  if (!capacity || capacity <= 0) return '不限人数'
  const left = Math.max(0, capacity - signedCount)
  return left > 0 ? `还剩 ${left} 个名额` : '名额已满'
})

const canSignup = computed(() => activity.value.actionText === '立即报名')

async function loadDetail() {
  const data = await api.activityDetail(Number(activityId.value))
  activity.value = data
  try {
    formFields.value = data.formFields ? JSON.parse(data.formFields) : []
  } catch (e) {
    formFields.value = []
  }
}

onLoad((options) => {
  activityId.value = Number(options?.id)
})

onShow(() => {
  if (activityId.value) {
    loadDetail()
  }
})

async function handleAction(): Promise<void> {
  const text = activity.value.actionText
  if (['已报名', '已签到', '候补中', '报名待审核'].includes(text || '')) {
    uni.navigateTo({ url: '/pages/mine/signups' })
    return
  }
  if (text !== '立即报名') {
    uni.showToast({ title: text || '当前不可报名', icon: 'none' })
    return
  }

  await ensureLogin()
  if (formFields.value.length) {
    uni.navigateTo({ url: `/pages/activity/signup?id=${activityId.value}` })
    return
  }

  uni.showModal({
    title: '确认报名',
    content: `确认报名「${activity.value.title}」？`,
    success: async (res) => {
      if (!res.confirm) return
      await api.signup(Number(activityId.value), {})
      uni.showToast({ title: '报名成功', icon: 'success' })
      await loadDetail()
    }
  })
}

async function toggleFav(): Promise<void> {
  await ensureLogin()
  const data = await api.toggleFavorite(Number(activityId.value))
  activity.value.favorited = data.favorited
}
</script>

<style scoped>
.page {
  padding-bottom: 140rpx;
}

.cover {
  height: 360rpx;
  background: linear-gradient(135deg, #dbeafe, #eff6ff);
  display: flex;
  align-items: center;
  justify-content: center;
}

.cover-img {
  width: 100%;
  height: 360rpx;
}

.cover-fallback {
  color: #1f6feb;
  font-size: 40rpx;
  font-weight: 600;
}

.head {
  margin-top: -40rpx;
}

.act-title {
  flex: 1;
  min-width: 0;
  margin-right: 16rpx;
  font-size: 32rpx;
  font-weight: 600;
  word-break: break-all;
}

.info {
  margin-top: 24rpx;
}

.info-row {
  display: flex;
  padding: 10rpx 0;
}

.info-label {
  width: 84rpx;
  color: #8a8f99;
  font-size: 26rpx;
}

.info-value {
  flex: 1;
  min-width: 0;
  font-size: 26rpx;
  color: #3d424d;
  word-break: break-word;
}

.progress {
  margin-top: 20rpx;
}

.progress-bar {
  height: 10rpx;
  background: #eef0f3;
  border-radius: 999rpx;
  overflow: hidden;
  margin-bottom: 10rpx;
}

.progress-inner {
  height: 10rpx;
  background: #1f6feb;
  border-radius: 999rpx;
}

.section-title {
  font-size: 30rpx;
  font-weight: 600;
  display: block;
  margin-bottom: 14rpx;
}

.content {
  font-size: 28rpx;
  line-height: 1.8;
  color: #3d424d;
  white-space: pre-wrap;
}

.field-line {
  padding: 8rpx 0;
}

.bottom-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  background: #ffffff;
  padding: 18rpx 24rpx calc(18rpx + env(safe-area-inset-bottom));
  display: flex;
  align-items: center;
  box-shadow: 0 -4rpx 20rpx rgba(22, 24, 29, 0.06);
}

.fav {
  width: 140rpx;
  text-align: center;
  font-size: 24rpx;
}

.fav-on {
  color: #1f6feb;
}

.fav-off {
  color: #8a8f99;
}

.action {
  flex: 1;
}

.action.disabled {
  background: #dfe4ea;
  color: #ffffff;
}
</style>
