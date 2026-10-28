<template>
  <view class="page">
    <view class="card qr-card">
      <text class="qr-title">{{ data.activityTitle }}</text>
      <text class="muted">请让同学用小程序扫码或粘贴下方签到码</text>

      <view class="token-box">
        <text class="token-text">{{ data.token }}</text>
      </view>

      <view class="row" style="justify-content: center; margin-top: 20rpx">
        <text class="refresh">{{ countdown }} 秒后自动刷新</text>
        <text class="link" @click="refresh">立即刷新</text>
      </view>

      <view class="copy" @click="copyToken">复制签到码</view>
    </view>

    <view class="card">
      <view class="row">
        <view class="stat">
          <text class="stat-num">{{ data.signedCount || 0 }}</text>
          <text class="stat-label">待签到</text>
        </view>
        <view class="stat">
          <text class="stat-num">{{ data.checkedInCount || 0 }}</text>
          <text class="stat-label">已签到</text>
        </view>
      </view>
      <view class="divider"></view>
      <text class="muted">最近签到</text>
      <view v-if="!data.recentCheckins || !data.recentCheckins.length" class="muted" style="margin-top: 10rpx">
        还没有人签到
      </view>
      <view v-for="(name, i) in data.recentCheckins || []" :key="i" class="recent">{{ name }}</view>
    </view>

    <view class="tip">
      签到码每 30 秒轮换一次，过期后需要重新获取。网络不稳定时可直接刷新本页。
    </view>
  </view>
</template>

<script setup lang="ts">
import type { CheckinQr } from '../../types'
import { onUnload } from '@dcloudio/uni-app'
import { onLoad } from '@dcloudio/uni-app'
import { reactive, ref } from 'vue'
import { api } from '../../utils/api'

const activityId = ref<number | null>(null)
const data = reactive<Partial<CheckinQr>>({})
const countdown = ref<number>(30)
let timer: ReturnType<typeof setInterval> | undefined

async function refresh(): Promise<void> {
  const res = await api.checkinQr(Number(activityId.value))
  Object.assign(data, res)
  countdown.value = res.refreshSeconds || 30
}

function startTimer(): void {
  if (timer) clearInterval(timer)
  timer = setInterval(() => {
    countdown.value -= 1
    if (countdown.value <= 0) {
      refresh()
    }
  }, 1000)
}

function copyToken(): void {
  uni.setClipboardData({
    data: data.token || '',
    success: () => uni.showToast({ title: '已复制', icon: 'none' })
  })
}

onLoad(async (options) => {
  activityId.value = Number(options?.id)
  await refresh()
  startTimer()
})

onUnload(() => {
  if (timer) {
    clearInterval(timer)
  }
})
</script>

<style scoped>
.qr-card {
  text-align: center;
  padding: 40rpx 28rpx;
}

.qr-title {
  font-size: 32rpx;
  font-weight: 600;
  display: block;
  margin-bottom: 8rpx;
}

.token-box {
  margin: 28rpx 0 8rpx;
  background: #f7f8fa;
  border-radius: 14rpx;
  padding: 28rpx 20rpx;
}

.token-text {
  font-size: 24rpx;
  color: #16181d;
  word-break: break-all;
  line-height: 1.7;
  letter-spacing: 1rpx;
}

.refresh {
  font-size: 24rpx;
  color: #8a8f99;
  margin-right: 20rpx;
}

.link {
  font-size: 24rpx;
  color: #1f6feb;
}

.copy {
  margin-top: 26rpx;
  height: 76rpx;
  line-height: 76rpx;
  border-radius: 12rpx;
  background: #eef4ff;
  color: #1f6feb;
  font-size: 28rpx;
}

.stat {
  flex: 1;
  text-align: center;
}

.stat-num {
  font-size: 44rpx;
  font-weight: 600;
  display: block;
}

.stat-label {
  font-size: 24rpx;
  color: #8a8f99;
}

.recent {
  font-size: 26rpx;
  color: #3d424d;
  padding: 10rpx 0;
  border-bottom: 1rpx solid #f4f5f7;
}

.tip {
  padding: 0 40rpx;
  font-size: 22rpx;
  color: #9aa0aa;
  line-height: 1.7;
}
</style>
