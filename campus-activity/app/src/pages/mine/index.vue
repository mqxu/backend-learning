<template>
  <view class="page">
    <view class="profile">
      <view class="avatar">{{ initial }}</view>
      <view class="info">
        <text class="name">{{ user.realName || user.nickname || '微信用户' }}</text>
        <text class="sub" v-if="user.bound">{{ user.college }} · {{ user.grade }} 级 · {{ user.studentNo }}</text>
        <text class="sub" v-else>尚未绑定学号</text>
      </view>
      <text class="role-tag">{{ roleLabel }}</text>
    </view>

    <view class="card" v-if="!user.bound">
      <view class="between">
        <view>
          <text class="bind-title">绑定学号后才能报名</text>
          <view class="muted" style="margin-top: 6rpx">需要与学校学籍名单一致</view>
        </view>
        <view class="btn-ghost mini" @click="go('/pages/auth/bind')">去绑定</view>
      </view>
    </view>

    <view class="card menu">
      <view class="menu-item" @click="go('/pages/mine/signups')">
        <text>我的报名</text>
        <text class="arrow">›</text>
      </view>
      <view class="menu-item" @click="go('/pages/checkin/scan')">
        <text>扫码签到</text>
        <text class="arrow">›</text>
      </view>
      <view class="menu-item" @click="go('/pages/message/list', true)">
        <text>消息通知</text>
        <text class="arrow">›</text>
      </view>
      <view class="menu-item" v-if="isOrganizer" @click="go('/pages/org/index')">
        <text>组织者工作台</text>
        <text class="arrow">›</text>
      </view>
    </view>

    <view class="card menu">
      <view class="menu-item" @click="resetStudent">
        <text>切换测试身份</text>
        <text class="muted">当前：{{ devOpenId }}</text>
      </view>
      <view class="menu-item" @click="doLogout">
        <text style="color: #b3261e">退出登录</text>
      </view>
    </view>

    <view class="version">校园活动 V1.0 · 教学演示版</view>
  </view>
</template>

<script setup lang="ts">
import type { UserInfo } from '../../types'
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { api } from '../../utils/api'
import { currentUser, updateLocalUser, logout } from '../../utils/auth'

const user = ref<UserInfo>(currentUser() || ({} as UserInfo))
const devOpenId = ref<string>(uni.getStorageSync('campus_dev_openid') || 'mock-student-1')

const initial = computed(() => {
  const name = user.value.realName || user.value.nickname || '同'
  return name.slice(0, 1)
})

const roleLabel = computed(() => {
  const map: Record<string, string> = { STUDENT: '学生', ORGANIZER: '组织者', REVIEWER: '审核员', ADMIN: '管理员' }
  return map[user.value.role] || '学生'
})

const isOrganizer = computed(() => ['ORGANIZER', 'ADMIN'].includes(user.value.role))

onShow(async () => {
  try {
    const data = await api.profile()
    user.value = data
    updateLocalUser(data)
  } catch (e) {
    user.value = currentUser() || ({} as UserInfo)
  }
})

function go(url: string, switchTab = false): void {
  if (switchTab) {
    uni.switchTab({ url })
  } else {
    uni.navigateTo({ url })
  }
}

function resetStudent(): void {
  uni.showActionSheet({
    itemList: ['张小明（mock-student-1）', '李小红（mock-student-2）', '组织者（mock-organizer-1）'],
    success: (res) => {
      const openIds = ['mock-student-1', 'mock-student-2', 'mock-organizer-1']
      uni.setStorageSync('campus_dev_openid', openIds[res.tapIndex])
      uni.removeStorageSync('campus_token')
      uni.removeStorageSync('campus_user')
      uni.reLaunch({ url: '/pages/index/index' })
    }
  })
}

function doLogout(): void {
  logout()
}
</script>

<style scoped>
.profile {
  background: linear-gradient(160deg, #1f6feb, #3b82f6);
  padding: 48rpx 32rpx;
  display: flex;
  align-items: center;
}

.avatar {
  width: 96rpx;
  height: 96rpx;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.24);
  color: #ffffff;
  font-size: 38rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.info {
  flex: 1;
  margin-left: 22rpx;
}

.name {
  color: #ffffff;
  font-size: 34rpx;
  font-weight: 600;
  display: block;
}

.sub {
  color: rgba(255, 255, 255, 0.82);
  font-size: 24rpx;
  margin-top: 6rpx;
  display: block;
}

.role-tag {
  font-size: 22rpx;
  color: #ffffff;
  background: rgba(255, 255, 255, 0.2);
  padding: 6rpx 16rpx;
  border-radius: 999rpx;
}

.bind-title {
  font-size: 28rpx;
  font-weight: 600;
}

.mini {
  width: 140rpx;
  height: 64rpx;
  line-height: 62rpx;
  font-size: 24rpx;
}

.menu {
  padding: 0 28rpx;
}

.menu-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 28rpx 0;
  border-bottom: 1rpx solid #f0f2f5;
  font-size: 28rpx;
}

.menu-item:last-child {
  border-bottom: none;
}

.arrow {
  color: #c4c9d1;
  font-size: 32rpx;
}

.version {
  text-align: center;
  color: #b0b6bf;
  font-size: 22rpx;
  margin-top: 40rpx;
}
</style>
