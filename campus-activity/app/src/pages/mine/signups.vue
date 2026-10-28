<template>
  <view class="page">
    <view class="tabs">
      <text
        v-for="t in tabs"
        :key="t.label"
        class="tab"
        :class="{ active: status === t.value }"
        @click="pick(t.value)"
      >{{ t.label }}</text>
    </view>

    <view v-if="!list.length && !loading" class="empty">暂无报名记录</view>

    <view v-for="s in list" :key="s.id" class="card">
      <view class="between">
        <text class="item-title" @click="goDetail(s.activityId)">{{ s.activityTitle }}</text>
        <text class="tag" :class="statusClass(s.status)">{{ s.statusLabel }}</text>
      </view>
      <view class="muted" style="margin-top: 10rpx">{{ s.startTime }} · {{ s.locationName }}</view>

      <view v-if="s.status === 1" class="ticket">
        <text class="ticket-label">签到凭证</text>
        <text class="ticket-no">{{ s.signupNo }}</text>
      </view>

      <view v-if="s.status === 2" class="muted" style="margin-top: 10rpx">
        候补序号 {{ s.waitlistSeq }}，有人取消时按顺序转正
      </view>

      <view class="actions">
        <text class="link" @click="goDetail(s.activityId)">活动详情</text>
        <text v-if="s.canCancel" class="link danger" @click="cancel(s)">取消报名</text>
        <text v-if="s.status === 1" class="link" @click="goScan">去签到</text>
      </view>
    </view>

    <view v-if="list.length" class="footer-tip">{{ hasMore ? '上拉加载更多' : '没有更多了' }}</view>
  </view>
</template>

<script setup lang="ts">
import type { SignupItem } from '../../types'
import { ref } from 'vue'
import { onPullDownRefresh, onReachBottom, onShow } from '@dcloudio/uni-app'
import { api } from '../../utils/api'

const tabs = [
  { label: '全部', value: null },
  { label: '报名成功', value: 1 },
  { label: '待审核', value: 0 },
  { label: '候补中', value: 2 },
  { label: '已签到', value: 3 }
]

const list = ref<SignupItem[]>([])
const status = ref<number | null>(null)
const page = ref(1)
const size = 10
const hasMore = ref(true)
const loading = ref(false)

async function loadPage(reset = false): Promise<void> {
  if (loading.value) return
  if (reset) {
    page.value = 1
    hasMore.value = true
    list.value = []
  }
  if (!hasMore.value) return

  loading.value = true
  try {
    const data = await api.mySignups({
      page: page.value,
      size,
      status: status.value === null ? undefined : status.value
    })
    const records = data.records || []
    list.value = reset ? records : list.value.concat(records)
    hasMore.value = list.value.length < data.total
    if (hasMore.value) page.value += 1
  } finally {
    loading.value = false
  }
}

function pick(value: number | null): void {
  status.value = value
  loadPage(true)
}

function statusClass(value: number): string {
  if (value === 1) return 'tag-green'
  if (value === 2) return 'tag-orange'
  if (value === 3) return 'tag-blue'
  if (value === 5 || value === 6) return 'tag-red'
  return ''
}

function goDetail(id: number): void {
  uni.navigateTo({ url: `/pages/activity/detail?id=${id}` })
}

function goScan(): void {
  uni.navigateTo({ url: '/pages/checkin/scan' })
}

function cancel(signup: SignupItem): void {
  uni.showModal({
    title: '取消报名',
    content: `确认取消「${signup.activityTitle}」的报名？名额将释放给候补同学。`,
    success: async (res) => {
      if (!res.confirm) return
      await api.cancelSignup(signup.id)
      uni.showToast({ title: '已取消', icon: 'success' })
      loadPage(true)
    }
  })
}

onShow(() => loadPage(true))

onPullDownRefresh(async () => {
  await loadPage(true)
  uni.stopPullDownRefresh()
})

onReachBottom(() => loadPage(false))
</script>

<style scoped>
.tabs {
  display: flex;
  background: #ffffff;
  padding: 20rpx 24rpx;
}

.tab {
  font-size: 26rpx;
  color: #5b6270;
  padding: 10rpx 22rpx;
  border-radius: 999rpx;
  margin-right: 14rpx;
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

.ticket {
  margin-top: 18rpx;
  background: #f7f8fa;
  border-radius: 12rpx;
  padding: 16rpx 20rpx;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.ticket-label {
  font-size: 24rpx;
  color: #8a8f99;
}

.ticket-no {
  font-size: 26rpx;
  color: #16181d;
  letter-spacing: 1rpx;
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

.link.danger {
  color: #b3261e;
}

.footer-tip {
  text-align: center;
  color: #9aa0aa;
  font-size: 24rpx;
  padding: 24rpx 0 40rpx;
}
</style>
