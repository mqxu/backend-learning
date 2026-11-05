<template>
  <view class="page">
    <view class="hero">
      <view class="between">
        <view>
          <view class="hi">你好，{{ user.realName || user.nickname || '同学' }}</view>
          <view class="hi-sub">今天想参加点什么</view>
        </view>
        <view class="avatar">{{ initial }}</view>
      </view>

      <view class="search" @click="goList()">
        <text class="search-text">搜索活动名称、社团</text>
      </view>
    </view>

    <scroll-view class="cats" scroll-x :show-scrollbar="false">
      <view class="cats-inner">
        <view v-for="c in categories" :key="c.id" class="cat" @click="goList(c.id)">
          <view class="cat-dot" :style="{ background: c.icon || '#1f6feb' }"></view>
          <text class="cat-name">{{ c.name }}</text>
        </view>
      </view>
    </scroll-view>

    <view v-if="hot.length" class="section">
      <view class="between section-head">
        <text class="title">热门活动</text>
        <text class="more" @click="goList()">全部</text>
      </view>
      <scroll-view scroll-x :show-scrollbar="false">
        <view class="hot-list">
          <view v-for="a in hot" :key="a.id" class="hot-card" @click="goDetail(a.id)">
            <view class="hot-cover">
              <image v-if="a.coverUrl" :src="a.coverUrl" mode="aspectFill" class="cover-img" />
              <text v-else class="cover-text">{{ a.categoryName }}</text>
            </view>
            <view class="hot-body">
              <text class="hot-title">{{ a.title }}</text>
              <text class="muted">{{ shortTime(a.startTime) }} · {{ a.locationName }}</text>
              <view class="row" style="margin-top: 10rpx">
                <text class="tag tag-blue">{{ a.categoryName }}</text>
                <text class="muted" style="margin-left: auto">{{ a.signedCount }}/{{ a.capacity || '不限' }}</text>
              </view>
            </view>
          </view>
        </view>
      </scroll-view>
    </view>

    <view class="section">
      <view class="between section-head">
        <text class="title">即将开始</text>
        <text class="more" @click="goList()">更多</text>
      </view>
      <view v-if="loading" class="empty">加载中…</view>
      <view v-else-if="!upcoming.length" class="empty">暂时没有可报名的活动</view>
      <view v-for="a in upcoming" :key="a.id" class="card item" @click="goDetail(a.id)">
        <view class="between">
          <text class="item-title">{{ a.title }}</text>
          <text class="tag" :class="a.signupOpen ? 'tag-green' : 'tag-orange'">
            {{ a.signupOpen ? '报名中' : a.statusLabel }}
          </text>
        </view>
        <view class="muted" style="margin-top: 8rpx">{{ a.summary || '暂无简介' }}</view>
        <view class="item-meta">
          <text class="muted">{{ shortTime(a.startTime) }} 开始</text>
          <text class="muted">{{ a.locationName }}</text>
        </view>
        <view class="between" style="margin-top: 12rpx">
          <text class="muted">{{ a.orgName || '校园组织' }}</text>
          <text class="muted">已报名 {{ a.signedCount }} 人</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import type { ActivityItem, Category, UserInfo } from '../../types'
import { computed, ref } from 'vue'
import { onPullDownRefresh, onShow } from '@dcloudio/uni-app'
import { api } from '../../utils/api'
import { getToken } from '../../utils/request'
import { login, currentUser } from '../../utils/auth'

const categories = ref<Category[]>([])
const hot = ref<ActivityItem[]>([])
const upcoming = ref<ActivityItem[]>([])
const loading = ref(true)
const user = ref<UserInfo>(currentUser() || ({} as UserInfo))

const initial = computed(() => {
  const name = user.value.realName || user.value.nickname || '同'
  return name.slice(0, 1)
})

function shortTime(value?: string): string {
  return value ? value.slice(5, 16) : ''
}

async function loadData() {
  loading.value = true
  try {
    const [cats, hotList, page] = await Promise.all([
      api.categories(),
      api.hotActivities(),
      api.activities({ page: 1, size: 10 })
    ])
    categories.value = cats || []
    hot.value = hotList || []
    upcoming.value = page.records || []
  } catch (e) {
    upcoming.value = []
  } finally {
    loading.value = false
  }
}

async function silentLogin() {
  if (getToken()) {
    user.value = currentUser() || ({} as UserInfo)
    return
  }
  try {
    const data = await login()
    user.value = data.user || ({} as UserInfo)
  } catch (e) {
    // 登录失败不影响浏览，报名时再提示
  }
}

onShow(async () => {
  await silentLogin()
  await loadData()
})

onPullDownRefresh(async () => {
  await loadData()
  uni.stopPullDownRefresh()
})

function goDetail(id: number): void {
  uni.navigateTo({ url: `/pages/activity/detail?id=${id}` })
}

function goList(categoryId?: number): void {
  if (categoryId) {
    uni.setStorageSync('filter_category', categoryId)
  } else {
    uni.removeStorageSync('filter_category')
  }
  uni.switchTab({ url: '/pages/activity/list' })
}
</script>

<style scoped>
.hero {
  background: linear-gradient(160deg, #1f6feb 0%, #3b82f6 60%, #60a5fa 100%);
  padding: 40rpx 32rpx 56rpx;
  border-radius: 0 0 28rpx 28rpx;
}

.hi {
  color: #ffffff;
  font-size: 40rpx;
  font-weight: 600;
}

.hi-sub {
  color: rgba(255, 255, 255, 0.82);
  font-size: 26rpx;
  margin-top: 6rpx;
}

.avatar {
  width: 76rpx;
  height: 76rpx;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.22);
  color: #ffffff;
  font-size: 30rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.search {
  margin-top: 32rpx;
  height: 80rpx;
  background: #ffffff;
  border-radius: 14rpx;
  display: flex;
  align-items: center;
  padding: 0 24rpx;
}

.search-text {
  font-size: 26rpx;
  color: #9aa0aa;
}

.cats {
  margin-top: -28rpx;
}

.cats-inner {
  display: flex;
  padding: 0 24rpx;
}

.cat {
  background: #ffffff;
  border-radius: 16rpx;
  padding: 18rpx 22rpx;
  margin-right: 16rpx;
  display: flex;
  align-items: center;
  flex-shrink: 0;
  box-shadow: 0 4rpx 16rpx rgba(22, 24, 29, 0.06);
  white-space: nowrap;
}

.cat-dot {
  width: 14rpx;
  height: 14rpx;
  border-radius: 50%;
  margin-right: 12rpx;
}

.cat-name {
  font-size: 26rpx;
  color: #16181d;
}

.section {
  margin-top: 16rpx;
}

.section-head {
  padding: 12rpx 28rpx;
}

.more {
  font-size: 24rpx;
  color: #8a8f99;
}

.hot-list {
  display: flex;
  padding: 8rpx 24rpx 8rpx;
}

.hot-card {
  width: 380rpx;
  background: #ffffff;
  border-radius: 18rpx;
  margin-right: 20rpx;
  overflow: hidden;
  flex-shrink: 0;
}

.hot-cover {
  height: 160rpx;
  background: linear-gradient(135deg, #dbeafe, #eff6ff);
  display: flex;
  align-items: center;
  justify-content: center;
}

.cover-img {
  width: 100%;
  height: 160rpx;
}

.cover-text {
  color: #1f6feb;
  font-size: 28rpx;
  font-weight: 600;
}

.hot-body {
  padding: 18rpx 20rpx 22rpx;
}

.hot-title {
  font-size: 28rpx;
  font-weight: 600;
  display: block;
  margin-bottom: 6rpx;
}

.item {
  padding: 24rpx 26rpx;
}

.item-title {
  font-size: 30rpx;
  font-weight: 600;
  flex: 1;
  margin-right: 16rpx;
}

.item-meta {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: 8rpx;
  margin-top: 14rpx;
}
</style>
