<template>
  <view class="page">
    <view class="intro">
      <text class="intro-title">绑定学号</text>
      <text class="intro-sub">用于报名与签到核对，一个学号只能绑定一个微信</text>
    </view>

    <view class="card">
      <view class="field">
        <text class="label">学号 *</text>
        <input v-model="form.studentNo" class="input" placeholder="例如 2023001" />
      </view>
      <view class="field">
        <text class="label">姓名 *</text>
        <input v-model="form.realName" class="input" placeholder="与学籍一致" />
      </view>
      <view class="field">
        <text class="label">学院</text>
        <input v-model="form.college" class="input" placeholder="例如 计算机学院" />
      </view>
      <view class="field">
        <text class="label">年级</text>
        <input v-model="form.grade" class="input" placeholder="例如 2023" />
      </view>
      <view class="field">
        <text class="label">专业</text>
        <input v-model="form.major" class="input" placeholder="例如 软件工程" />
      </view>
    </view>

    <view class="hint">
      演示环境已导入学号：2023001 张小明、2023002 李小红、2023003 王大力、2024001 赵静、2024002 陈晨。
      学院、年级留空时按学籍名单自动填充。
    </view>

    <view class="actions">
      <view class="btn-primary" @click="submit">{{ submitting ? '提交中…' : '完成绑定' }}</view>
    </view>
  </view>
</template>

<script setup lang="ts">
import type { UserInfo } from '../../types'
import { reactive, ref } from 'vue'
import { api } from '../../utils/api'
import { updateLocalUser } from '../../utils/auth'

const form = reactive({
  studentNo: '',
  realName: '',
  college: '',
  grade: '',
  major: ''
})
const submitting = ref(false)

async function submit(): Promise<void> {
  if (!form.studentNo || !form.realName) {
    uni.showToast({ title: '学号与姓名必填', icon: 'none' })
    return
  }
  submitting.value = true
  try {
    const user = await api.bind(form) as UserInfo
    updateLocalUser(user)
    uni.showToast({ title: '绑定成功', icon: 'success' })
    setTimeout(() => {
      uni.switchTab({ url: '/pages/index/index' })
    }, 800)
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.intro {
  padding: 40rpx 32rpx 8rpx;
}

.intro-title {
  font-size: 40rpx;
  font-weight: 600;
  display: block;
}

.intro-sub {
  font-size: 26rpx;
  color: #8a8f99;
  margin-top: 8rpx;
  display: block;
}

.field {
  margin-bottom: 24rpx;
}

.label {
  font-size: 26rpx;
  color: #3d424d;
  display: block;
  margin-bottom: 10rpx;
}

.input {
  height: 80rpx;
  background: #f7f8fa;
  border-radius: 10rpx;
  padding: 0 20rpx;
  font-size: 26rpx;
}

.hint {
  padding: 0 40rpx;
  font-size: 22rpx;
  color: #9aa0aa;
  line-height: 1.8;
}

.actions {
  padding: 32rpx 24rpx;
}
</style>
