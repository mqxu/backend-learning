<template>
  <view class="page">
    <view class="intro">
      <text class="intro-title">手机号登录</text>
      <text class="intro-sub">未注册的手机号验证通过后会自动创建账号</text>
    </view>

    <view class="card">
      <view class="field">
        <text class="label">手机号</text>
        <input
          v-model="phone"
          class="input"
          type="number"
          maxlength="11"
          placeholder="请输入 11 位手机号"
        />
      </view>

      <view class="field">
        <text class="label">验证码</text>
        <view class="code-row">
          <input
            v-model="code"
            class="input code-input"
            type="number"
            maxlength="6"
            placeholder="6 位验证码"
          />
          <view class="code-btn" :class="{ disabled: secondsLeft > 0 || sending }" @click="sendCode">
            {{ secondsLeft > 0 ? secondsLeft + 's 后重发' : '获取验证码' }}
          </view>
        </view>
      </view>

      <view v-if="mockTip" class="mock-tip">{{ mockTip }}</view>
    </view>

    <view class="actions">
      <view class="btn-primary" :class="{ disabled: submitting }" @click="submit">
        {{ submitting ? '登录中…' : '登录' }}
      </view>
    </view>

    <view class="hint">
      演示环境使用 Mock 短信通道，不会真实发送短信；验证码会随接口返回并自动填入。
      登录后学生仍需绑定学号才能报名。
    </view>
  </view>
</template>

<script setup lang="ts">
import { onUnmounted, ref } from 'vue'
import { api } from '../../utils/api'
import { loginBySms } from '../../utils/auth'

const phone = ref('')
const code = ref('')
const secondsLeft = ref(0)
const sending = ref(false)
const submitting = ref(false)
const mockTip = ref('')

let timer: ReturnType<typeof setInterval> | null = null

onUnmounted(clearTimer)

function clearTimer(): void {
  if (timer !== null) {
    clearInterval(timer)
    timer = null
  }
}

function startCountdown(seconds: number): void {
  clearTimer()
  secondsLeft.value = seconds
  timer = setInterval(() => {
    secondsLeft.value -= 1
    if (secondsLeft.value <= 0) {
      clearTimer()
    }
  }, 1000)
}

function validPhone(): boolean {
  return /^1[3-9]\d{9}$/.test(phone.value)
}

async function sendCode(): Promise<void> {
  if (secondsLeft.value > 0 || sending.value) {
    return
  }
  if (!validPhone()) {
    uni.showToast({ title: '请输入正确的手机号', icon: 'none' })
    return
  }

  sending.value = true
  try {
    const data = await api.sendSmsCode(phone.value)
    startCountdown(data.intervalSeconds || 60)
    if (data.mockCode) {
      // Mock 通道会把验证码带回来，联调时直接填入，省去翻后端日志
      code.value = data.mockCode
      mockTip.value = `演示环境验证码：${data.mockCode}（真实短信不会返回）`
    } else {
      mockTip.value = ''
      uni.showToast({ title: '验证码已发送', icon: 'success' })
    }
  } catch (e) {
    // request 层已统一 toast 业务错误（如 60002 发送太频繁）
  } finally {
    sending.value = false
  }
}

async function submit(): Promise<void> {
  if (submitting.value) {
    return
  }
  if (!validPhone()) {
    uni.showToast({ title: '请输入正确的手机号', icon: 'none' })
    return
  }
  if (!/^\d{6}$/.test(code.value)) {
    uni.showToast({ title: '请输入 6 位验证码', icon: 'none' })
    return
  }

  submitting.value = true
  try {
    const data = await loginBySms(phone.value, code.value)
    uni.showToast({ title: '登录成功', icon: 'success' })
    setTimeout(() => {
      if (data.needBind) {
        // 登录页用 redirectTo 替换掉自己，绑定完成后直接回首页
        uni.redirectTo({ url: '/pages/auth/bind' })
      } else {
        uni.switchTab({ url: '/pages/index/index' })
      }
    }, 600)
  } catch (e) {
    // request 层已统一 toast（如 60005 验证码错误）
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

.code-row {
  display: flex;
  align-items: center;
}

.code-input {
  flex: 1;
}

.code-btn {
  width: 220rpx;
  height: 80rpx;
  line-height: 78rpx;
  margin-left: 16rpx;
  text-align: center;
  font-size: 24rpx;
  color: #1f6feb;
  background: #eef4ff;
  border-radius: 10rpx;
}

.code-btn.disabled {
  color: #9aa0aa;
  background: #f2f4f7;
}

.mock-tip {
  margin-top: 8rpx;
  padding: 16rpx 20rpx;
  background: #e9f7f1;
  color: #0f8a5f;
  border-radius: 10rpx;
  font-size: 24rpx;
  line-height: 1.6;
}

.actions {
  padding: 32rpx 24rpx 0;
}

.btn-primary.disabled {
  background: #c9d3e0;
}

.hint {
  padding: 28rpx 40rpx 40rpx;
  font-size: 22rpx;
  color: #9aa0aa;
  line-height: 1.8;
}
</style>
