<template>
  <view class="page">
    <view class="card">
      <text class="title">{{ activity.title }}</text>
      <view class="muted" style="margin-top: 10rpx">
        {{ activity.startTime }} · {{ activity.locationName }}
      </view>
    </view>

    <view class="card">
      <text class="section-title">报名信息</text>
      <view v-for="f in fields" :key="f.key" class="field">
        <text class="label">{{ f.label }}<text v-if="f.required" class="required"> *</text></text>

        <input
          v-if="!f.type || f.type === 'TEXT'"
          v-model="answers[f.key]"
          class="input"
          :placeholder="`请输入${f.label}`"
        />

        <radio-group
          v-else-if="f.type === 'SINGLE'"
          class="options"
          @change="onFieldChange(f.key, $event)"
        >
          <label v-for="opt in f.options || []" :key="opt" class="option">
            <radio :value="opt" :checked="answers[f.key] === opt" color="#1f6feb" />
            <text class="option-text">{{ opt }}</text>
          </label>
        </radio-group>

        <checkbox-group
          v-else-if="f.type === 'MULTI'"
          class="options"
          @change="onFieldChange(f.key, $event)"
        >
          <label v-for="opt in f.options || []" :key="opt" class="option">
            <checkbox :value="opt" :checked="(answers[f.key] || []).includes(opt)" color="#1f6feb" />
            <text class="option-text">{{ opt }}</text>
          </label>
        </checkbox-group>
      </view>

      <view v-if="!fields.length" class="muted">该活动无需填写额外信息</view>
    </view>

    <view class="tip">
      提交即表示同意组织者使用以上信息用于本次活动组织，活动结束后可申请删除。
    </view>

    <view class="bottom-bar">
      <view class="btn-primary" @click="submit">{{ submitting ? '提交中…' : '确认报名' }}</view>
    </view>
  </view>
</template>

<script setup lang="ts">
import type { ActivityDetail, FormAnswers, FormField } from '../../types'
import { reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { api } from '../../utils/api'

const activityId = ref<number | null>(null)
const activity = ref({} as ActivityDetail)
const fields = ref<FormField[]>([])
const answers = reactive<FormAnswers>({})
const submitting = ref(false)

onLoad(async (options) => {
  activityId.value = Number(options?.id)
  const data = await api.activityDetail(Number(activityId.value))
  activity.value = data
  try {
    fields.value = data.formFields ? JSON.parse(data.formFields) : []
  } catch (e) {
    fields.value = []
  }
})

function onFieldChange(key: string, event: any): void {
  answers[key] = event.detail.value
}

async function submit(): Promise<void> {
  if (submitting.value) return

  for (const f of fields.value) {
    if (!f.required) continue
    const value = answers[f.key]
    if (value === undefined || value === '' || (Array.isArray(value) && !value.length)) {
      uni.showToast({ title: `请填写${f.label}`, icon: 'none' })
      return
    }
  }

  submitting.value = true
  try {
    await api.signup(Number(activityId.value), { formData: JSON.stringify(answers) })
    uni.showToast({ title: '报名成功', icon: 'success' })
    setTimeout(() => {
      uni.redirectTo({ url: '/pages/mine/signups' })
    }, 800)
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.page {
  padding-bottom: 160rpx;
}

.section-title {
  font-size: 30rpx;
  font-weight: 600;
  display: block;
  margin-bottom: 20rpx;
}

.field {
  margin-bottom: 28rpx;
}

.label {
  font-size: 26rpx;
  color: #3d424d;
  display: block;
  margin-bottom: 12rpx;
}

.required {
  color: #b3261e;
}

.input {
  height: 80rpx;
  background: #f7f8fa;
  border-radius: 10rpx;
  padding: 0 20rpx;
  font-size: 26rpx;
}

.options {
  display: flex;
  flex-direction: column;
}

.option {
  display: flex;
  align-items: center;
  padding: 10rpx 0;
}

.option-text {
  font-size: 26rpx;
  color: #3d424d;
  margin-left: 8rpx;
}

.tip {
  padding: 0 40rpx;
  font-size: 22rpx;
  color: #9aa0aa;
  line-height: 1.7;
}

.bottom-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  background: #ffffff;
  padding: 18rpx 24rpx calc(18rpx + env(safe-area-inset-bottom));
  box-shadow: 0 -4rpx 20rpx rgba(22, 24, 29, 0.06);
}
</style>
