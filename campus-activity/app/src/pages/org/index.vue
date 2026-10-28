<template>
  <view class="page">
    <view class="overview">
      <view class="ov-item">
        <text class="ov-num">{{ stat.activityTotal || 0 }}</text>
        <text class="ov-label">活动总数</text>
      </view>
      <view class="ov-item">
        <text class="ov-num">{{ stat.publishedCount || 0 }}</text>
        <text class="ov-label">已发布</text>
      </view>
      <view class="ov-item">
        <text class="ov-num">{{ stat.pendingAuditCount || 0 }}</text>
        <text class="ov-label">待审核</text>
      </view>
      <view class="ov-item">
        <text class="ov-num">{{ stat.totalSignup || 0 }}</text>
        <text class="ov-label">累计报名</text>
      </view>
    </view>

    <view class="tabs">
      <text
        v-for="t in tabs"
        :key="t.value === null ? 'all' : t.value"
        class="tab"
        :class="{ active: status === t.value }"
        @click="pick(t.value)"
      >{{ t.label }}</text>
    </view>

    <view v-if="!list.length && !loading" class="empty">还没有活动，点击右下角发布</view>

    <view v-for="a in list" :key="a.id" class="card">
      <view class="between">
        <text class="item-title">{{ a.title }}</text>
        <text class="tag" :class="statusClass(a.status)">{{ a.statusLabel }}</text>
      </view>
      <view class="muted" style="margin-top: 10rpx">{{ a.startTime }} · {{ a.locationName }}</view>
      <view class="muted" style="margin-top: 6rpx">
        报名 {{ a.signedCount }}/{{ a.capacity || '不限' }}
      </view>

      <view class="actions">
        <text v-if="a.status === 0 || a.status === 2" class="link" @click="submitActivity(a)">提交审核</text>
        <text class="link" @click="goSignups(a.id)">报名名单</text>
        <text v-if="[3, 4, 5].includes(a.status)" class="link" @click="goCheckin(a.id)">签到核销</text>
      </view>
    </view>

    <view class="fab" @click="goEdit">
      <text>+ 发布活动</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import type { ActivityItem, OrgDashboard } from '../../types'
import { ref } from 'vue'
import { onPullDownRefresh, onShow } from '@dcloudio/uni-app'
import { api } from '../../utils/api'

const tabs = [
  { label: '全部', value: null },
  { label: '草稿', value: 0 },
  { label: '待审核', value: 1 },
  { label: '已发布', value: 3 },
  { label: '已结束', value: 5 }
]

const list = ref<ActivityItem[]>([])
const stat = ref<OrgDashboard>({} as OrgDashboard)
const status = ref<number | null>(null)
const loading = ref(false)

function statusClass(value: number): string {
  if (value === 3 || value === 4) return 'tag-green'
  if (value === 1) return 'tag-orange'
  if (value === 2 || value === 6) return 'tag-red'
  return ''
}

async function load(): Promise<void> {
  loading.value = true
  try {
    const [dash, page] = await Promise.all([
      api.orgDashboard(),
      api.orgActivities({ status: status.value === null ? undefined : status.value, page: 1, size: 20 })
    ])
    stat.value = dash || {}
    list.value = page.records || []
  } finally {
    loading.value = false
  }
}

function pick(value: number | null): void {
  status.value = value
  load()
}

function goEdit(): void {
  uni.navigateTo({ url: '/pages/org/edit' })
}

function goSignups(id: number): void {
  uni.navigateTo({ url: `/pages/org/signups?id=${id}` })
}

function goCheckin(id: number): void {
  uni.navigateTo({ url: `/pages/org/checkin?id=${id}` })
}

function submitActivity(activity: ActivityItem): void {
  uni.showModal({
    title: '提交审核',
    content: `确认提交「${activity.title}」给管理员审核？`,
    success: async (res) => {
      if (!res.confirm) return
      await api.submitActivity(activity.id)
      uni.showToast({ title: '已提交', icon: 'success' })
      load()
    }
  })
}

onShow(load)

onPullDownRefresh(async () => {
  await load()
  uni.stopPullDownRefresh()
})
</script>

<style scoped>
.overview {
  background: linear-gradient(160deg, #1f6feb, #3b82f6);
  padding: 36rpx 24rpx;
  display: flex;
}

.ov-item {
  flex: 1;
  text-align: center;
}

.ov-num {
  color: #ffffff;
  font-size: 40rpx;
  font-weight: 600;
  display: block;
}

.ov-label {
  color: rgba(255, 255, 255, 0.8);
  font-size: 22rpx;
}

.tabs {
  display: flex;
  background: #ffffff;
  padding: 18rpx 24rpx;
}

.tab {
  font-size: 25rpx;
  color: #5b6270;
  padding: 8rpx 20rpx;
  border-radius: 999rpx;
  margin-right: 12rpx;
  background: #f5f6f8;
}

.tab.active {
  background: #eef4ff;
  color: #1f6feb;
  font-weight: 600;
}

.item-title {
  font-size: 30rpx;
  font-weight: 600;
  flex: 1;
  margin-right: 16rpx;
}

.actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 18rpx;
}

.link {
  font-size: 26rpx;
  color: #1f6feb;
  margin-left: 28rpx;
}

.fab {
  position: fixed;
  right: 32rpx;
  bottom: 60rpx;
  background: #1f6feb;
  color: #ffffff;
  font-size: 28rpx;
  padding: 22rpx 34rpx;
  border-radius: 999rpx;
  box-shadow: 0 10rpx 24rpx rgba(31, 111, 235, 0.32);
}
</style>
