<template>
  <view class="page">
    <view class="toolbar">
      <view class="search">
        <input
          v-model="keyword"
          class="search-input"
          placeholder="搜索活动名称"
          confirm-type="search"
          @confirm="reload"
        />
        <text class="search-btn" @click="reload">搜索</text>
      </view>

      <scroll-view scroll-x :show-scrollbar="false" class="tabs">
        <view class="tabs-inner">
          <text
            class="tab"
            :class="{ active: !categoryId }"
            @click="pickCategory(null)"
          >全部</text>
          <text
            v-for="c in categories"
            :key="c.id"
            class="tab"
            :class="{ active: categoryId === c.id }"
            @click="pickCategory(c.id)"
          >{{ c.name }}</text>
        </view>
      </scroll-view>

      <view class="scopes">
        <text
          v-for="s in scopes"
          :key="s.value"
          class="scope"
          :class="{ active: timeScope === s.value }"
          @click="pickScope(s.value)"
        >{{ s.label }}</text>
      </view>
    </view>

    <view v-if="!list.length && !loading" class="empty">没有找到活动</view>

    <view v-for="a in list" :key="a.id" class="card item" @click="goDetail(a.id)">
      <view class="between">
        <text class="item-title">{{ a.title }}</text>
        <text class="tag" :class="a.signupOpen ? 'tag-green' : 'tag-orange'">
          {{ a.signupOpen ? '报名中' : a.statusLabel }}
        </text>
      </view>
      <view class="muted" style="margin-top: 8rpx">{{ a.summary || '暂无简介' }}</view>
      <view class="item-meta">
        <text class="muted">{{ a.startTime }}</text>
        <text class="muted">{{ a.locationName }}</text>
      </view>
      <view class="between" style="margin-top: 10rpx">
        <view class="row">
          <text class="tag tag-blue">{{ a.categoryName }}</text>
          <text class="muted" style="margin-left: 12rpx">{{ a.orgName }}</text>
        </view>
        <text class="muted">{{ a.signedCount }}/{{ a.capacity || '不限' }}</text>
      </view>
    </view>

    <view v-if="list.length" class="footer-tip">
      {{ loading ? '加载中…' : (hasMore ? '上拉加载更多' : '没有更多了') }}
    </view>
  </view>
</template>

<script setup lang="ts">
import type { ActivityItem, Category } from '../../types'
import { ref } from 'vue'
import { onPullDownRefresh, onReachBottom, onShow } from '@dcloudio/uni-app'
import { api } from '../../utils/api'

const categories = ref<Category[]>([])
const list = ref<ActivityItem[]>([])
const keyword = ref('')
const categoryId = ref<number | null>(null)
const timeScope = ref('')
const page = ref(1)
const size = 10
const hasMore = ref(true)
const loading = ref(false)

const scopes = [
  { label: '全部时间', value: '' },
  { label: '今天', value: 'TODAY' },
  { label: '本周', value: 'WEEK' },
  { label: '已结束', value: 'ENDED' }
]

async function loadCategories() {
  categories.value = (await api.categories()) || []
}

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
    const data = await api.activities({
      page: page.value,
      size,
      keyword: keyword.value || undefined,
      categoryId: categoryId.value || undefined,
      timeScope: timeScope.value || undefined
    })
    const records = data.records || []
    list.value = reset ? records : list.value.concat(records)
    hasMore.value = list.value.length < data.total
    if (hasMore.value) page.value += 1
  } finally {
    loading.value = false
  }
}

function reload() {
  loadPage(true)
}

function pickCategory(id: number | null): void {
  categoryId.value = id
  reload()
}

function pickScope(value: string): void {
  timeScope.value = value
  reload()
}

function goDetail(id: number): void {
  uni.navigateTo({ url: `/pages/activity/detail?id=${id}` })
}

onShow(async () => {
  if (!categories.value.length) {
    await loadCategories()
  }
  const stored = uni.getStorageSync('filter_category')
  if (stored) {
    categoryId.value = Number(stored)
    uni.removeStorageSync('filter_category')
  }
  await loadPage(true)
})

onPullDownRefresh(async () => {
  await loadPage(true)
  uni.stopPullDownRefresh()
})

onReachBottom(() => {
  loadPage(false)
})
</script>

<style scoped>
.toolbar {
  background: #ffffff;
  padding: 20rpx 24rpx 16rpx;
}

.search {
  display: flex;
  align-items: center;
  background: #f5f6f8;
  border-radius: 12rpx;
  padding: 0 20rpx;
  height: 76rpx;
}

.search-input {
  flex: 1;
  font-size: 26rpx;
}

.search-btn {
  font-size: 26rpx;
  color: #1f6feb;
  padding-left: 16rpx;
}

.tabs {
  margin-top: 18rpx;
}

.tabs-inner {
  display: flex;
}

.tab {
  font-size: 26rpx;
  color: #5b6270;
  padding: 10rpx 22rpx;
  background: #f5f6f8;
  border-radius: 999rpx;
  margin-right: 14rpx;
  white-space: nowrap;
}

.tab.active {
  background: #eef4ff;
  color: #1f6feb;
  font-weight: 600;
}

.scopes {
  display: flex;
  margin-top: 16rpx;
}

.scope {
  font-size: 24rpx;
  color: #8a8f99;
  margin-right: 28rpx;
}

.scope.active {
  color: #1f6feb;
  font-weight: 600;
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
  justify-content: space-between;
  margin-top: 14rpx;
}

.footer-tip {
  text-align: center;
  color: #9aa0aa;
  font-size: 24rpx;
  padding: 24rpx 0 40rpx;
}
</style>
