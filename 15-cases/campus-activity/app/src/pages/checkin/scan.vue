<template>
  <view class="page">
    <view class="card" v-if="!result">
      <text class="title">扫码签到</text>
      <view class="muted" style="margin-top: 10rpx">
        在活动现场扫描组织者展示的签到二维码，签到码每 30 秒刷新。
      </view>

      <view class="scan-btn" @click="scan">扫描签到码</view>

      <view class="divider"></view>

      <text class="muted">扫码不可用时，可粘贴签到码内容</text>
      <input v-model="token" class="input" placeholder="粘贴签到码" />
      <view class="btn-primary" style="margin-top: 20rpx" @click="submit(token)">提交签到</view>
    </view>

    <view class="card result" v-else>
      <view class="ok-icon">✓</view>
      <text class="ok-title">签到成功</text>
      <text class="ok-sub">{{ result.activityTitle }}</text>
      <view class="result-row">
        <text class="muted">签到时间</text>
        <text>{{ result.checkinTime }}</text>
      </view>
      <view class="result-row">
        <text class="muted">签到人</text>
        <text>{{ result.studentName }}</text>
      </view>
      <view class="btn-ghost" style="margin-top: 24rpx" @click="back">返回</view>
    </view>
  </view>
</template>

<script setup lang="ts">
import type { CheckinResultData } from '../../types'
import { ref } from 'vue'
import { api } from '../../utils/api'
import { ensureLogin } from '../../utils/auth'

const token = ref<string>('')
const result = ref<CheckinResultData | null>(null)

async function scan(): Promise<void> {
  // #ifdef MP-WEIXIN
  uni.scanCode({
    onlyFromCamera: false,
    success: async (res) => {
      await submit(res.result)
    },
    fail: () => {
      uni.showToast({ title: '已取消扫码', icon: 'none' })
    }
  })
  // #endif

  // #ifndef MP-WEIXIN
  uni.showToast({ title: 'H5 不支持扫码，请粘贴签到码', icon: 'none' })
  // #endif
}

async function submit(value: string): Promise<void> {
  if (!value) {
    uni.showToast({ title: '请先获取签到码', icon: 'none' })
    return
  }
  await ensureLogin()

  let position: { latitude?: number; longitude?: number } = {}
  try {
    const res = await new Promise<{ latitude: number; longitude: number }>((resolve, reject) => {
      uni.getLocation({ type: 'gcj02', success: resolve, fail: reject })
    })
    position = { latitude: res.latitude, longitude: res.longitude }
  } catch (e) {
    // 定位失败也允许提交，服务端按活动配置决定是否拒绝
  }

  const data = await api.checkin({ token: value, ...position })
  result.value = data
}

function back(): void {
  uni.switchTab({ url: '/pages/index/index' })
}
</script>

<style scoped>
.scan-btn {
  margin-top: 32rpx;
  height: 220rpx;
  border: 2rpx dashed #cfe0fd;
  border-radius: 16rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #1f6feb;
  font-size: 30rpx;
  background: #f7faff;
}

.input {
  height: 80rpx;
  background: #f7f8fa;
  border-radius: 10rpx;
  padding: 0 20rpx;
  font-size: 24rpx;
  margin-top: 16rpx;
}

.result {
  text-align: center;
  padding: 48rpx 32rpx;
}

.ok-icon {
  width: 108rpx;
  height: 108rpx;
  border-radius: 50%;
  background: #e9f7f1;
  color: #0f8a5f;
  font-size: 52rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto 20rpx;
}

.ok-title {
  font-size: 36rpx;
  font-weight: 600;
  display: block;
}

.ok-sub {
  font-size: 26rpx;
  color: #8a8f99;
  display: block;
  margin-top: 8rpx;
}

.result-row {
  display: flex;
  justify-content: space-between;
  padding: 16rpx 0;
  border-bottom: 1rpx solid #f0f2f5;
  font-size: 26rpx;
  margin-top: 10rpx;
}
</style>
