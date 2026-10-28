<template>
  <view class="page">
    <view class="bar">
      <text class="muted">共 {{ total }} 条消息</text>
      <text class="link" @click="markAll">全部已读</text>
    </view>

    <view v-if="!list.length && !loading" class="empty">暂无消息</view>

    <view v-for="m in list" :key="m.id" class="card msg" :class="{ unread: m.readFlag === 0 }">
      <view class="between">
        <view class="row">
          <view v-if="m.readFlag === 0" class="dot"></view>
          <text class="msg-title">{{ m.title }}</text>
        </view>
        <text class="tag">{{ typeLabel(m.type) }}</text>
      </view>
      <view class="msg-content">{{ m.content }}</view>
      <text class="muted">{{ m.createTime }}</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import type { SysNotice } from '../../types'
import { ref } from 'vue'
import { onPullDownRefresh, onShow } from '@dcloudio/uni-app'
import { api } from '../../utils/api'

const list = ref<SysNotice[]>([])
const total = ref<number>(0)
const loading = ref(false)

const TYPE: Record<string, string> = {
  SIGNUP: '报名',
  AUDIT: '审核',
  WAITLIST: '候补',
  REMIND: '提醒',
  CANCEL: '取消'
}

function typeLabel(type: string): string {
  return TYPE[type] || '通知'
}

async function load(): Promise<void> {
  loading.value = true
  try {
    const data = await api.messages({ page: 1, size: 30 })
    list.value = data.records || []
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}

async function markAll(): Promise<void> {
  await api.markRead(null)
  list.value = list.value.map((m) => ({ ...m, readFlag: 1 }))
  uni.showToast({ title: '已全部标记为已读', icon: 'none' })
}

onShow(load)

onPullDownRefresh(async () => {
  await load()
  uni.stopPullDownRefresh()
})
</script>

<style scoped>
.bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 24rpx 32rpx 8rpx;
}

.link {
  font-size: 26rpx;
  color: #1f6feb;
}

.msg-title {
  font-size: 28rpx;
  font-weight: 600;
}

.msg.unread .msg-title {
  color: #16181d;
}

.dot {
  width: 12rpx;
  height: 12rpx;
  border-radius: 50%;
  background: #b3261e;
  margin-right: 10rpx;
}

.msg-content {
  font-size: 26rpx;
  color: #5b6270;
  margin: 12rpx 0 10rpx;
  line-height: 1.7;
  white-space: pre-wrap;
}
</style>
