<template>
  <view class="page">
    <view class="card">
      <view class="field">
        <text class="label">活动标题 *</text>
        <input v-model="form.title" class="input" placeholder="不超过 30 字" maxlength="60" />
      </view>

      <view class="field">
        <text class="label">活动分类</text>
        <picker :range="categoryNames" :value="categoryIndex" @change="onCategory">
          <view class="picker">{{ categoryNames[categoryIndex] || '请选择分类' }}</view>
        </picker>
      </view>

      <view class="field">
        <text class="label">一句话简介</text>
        <input v-model="form.summary" class="input" placeholder="展示在列表中的简短说明" maxlength="100" />
      </view>

      <view class="field">
        <text class="label">活动详情</text>
        <textarea v-model="form.content" class="textarea" placeholder="流程、注意事项、参与要求" maxlength="5000" />
      </view>
    </view>

    <view class="card">
      <text class="section-title">时间安排</text>

      <view class="field">
        <text class="label">活动开始 *</text>
        <view class="row">
          <picker mode="date" :value="startDate" @change="onStartDateChange">
            <view class="picker half">{{ startDate }}</view>
          </picker>
          <picker mode="time" :value="startClock" @change="onStartClockChange">
            <view class="picker half">{{ startClock }}</view>
          </picker>
        </view>
      </view>

      <view class="field">
        <text class="label">活动结束 *</text>
        <view class="row">
          <picker mode="date" :value="endDate" @change="onEndDateChange">
            <view class="picker half">{{ endDate }}</view>
          </picker>
          <picker mode="time" :value="endClock" @change="onEndClockChange">
            <view class="picker half">{{ endClock }}</view>
          </picker>
        </view>
      </view>

      <view class="field">
        <text class="label">报名截止 *</text>
        <view class="row">
          <picker mode="date" :value="signupEndDate" @change="onSignupEndDateChange">
            <view class="picker half">{{ signupEndDate }}</view>
          </picker>
          <picker mode="time" :value="signupEndClock" @change="onSignupEndClockChange">
            <view class="picker half">{{ signupEndClock }}</view>
          </picker>
        </view>
      </view>

      <view class="muted">报名开始时间默认立即开始；取消报名截止默认活动开始前 2 小时。</view>
    </view>

    <view class="card">
      <text class="section-title">地点与名额</text>

      <view class="field">
        <text class="label">活动地点 *</text>
        <input v-model="form.locationName" class="input" placeholder="例如 大学生活动中心 报告厅" />
      </view>

      <view class="field">
        <text class="label">人数上限（0 表示不限）</text>
        <input v-model="capacityText" class="input" type="number" placeholder="0" />
      </view>

      <view class="switch-row">
        <text class="label">名额满后开启候补</text>
        <switch :checked="form.waitlistEnabled === 1" color="#1f6feb" @change="onWaitlistChange" />
      </view>

      <view class="switch-row">
        <text class="label">报名需要组织者审核</text>
        <switch :checked="form.auditRequired === 1" color="#1f6feb" @change="onAuditRequiredChange" />
      </view>

      <view class="field">
        <text class="label">签到方式</text>
        <picker :range="checkinLabels" :value="checkinIndex" @change="onCheckinTypeChange">
          <view class="picker">{{ checkinLabels[checkinIndex] }}</view>
        </picker>
      </view>
    </view>

    <view class="actions">
      <view class="btn-ghost" @click="save(false)">保存草稿</view>
      <view class="btn-primary" @click="save(true)">保存并提交审核</view>
    </view>
  </view>
</template>

<script setup lang="ts">
import type { Category } from '../../types'
import { computed, reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { api } from '../../utils/api'

const categories = ref<Category[]>([])
const categoryIndex = ref(0)
const checkinLabels = ['扫码签到', '扫码 + 定位签到', '仅组织者手动签到']
const checkinIndex = ref(0)
const capacityText = ref('0')
const saving = ref(false)

const today = new Date()
const pad = (n: number): string => String(n).padStart(2, '0')
const dateStr = (d: Date): string => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`

const startDate = ref(dateStr(new Date(today.getTime() + 86400000)))
const startClock = ref('14:00')
const endDate = ref(dateStr(new Date(today.getTime() + 86400000)))
const endClock = ref('17:00')
const signupEndDate = ref(dateStr(new Date(today.getTime() + 86400000)))
const signupEndClock = ref('13:00')

const form = reactive<{
  title: string
  categoryId: number | null
  summary: string
  content: string
  locationName: string
  capacity: number
  waitlistEnabled: number
  auditRequired: number
  checkinType: number
  formFields: string | null
}>({
  title: '',
  categoryId: null,
  summary: '',
  content: '',
  locationName: '',
  capacity: 0,
  waitlistEnabled: 1,
  auditRequired: 0,
  checkinType: 1,
  formFields: null
})

const categoryNames = computed(() => categories.value.map((c) => c.name))

onLoad(async () => {
  categories.value = (await api.categories()) || []
  if (categories.value.length) {
    form.categoryId = categories.value[0].id
  }
})

function onCategory(e: { detail: { value: string | number } }): void {
  categoryIndex.value = Number(e.detail.value)
  form.categoryId = categories.value[categoryIndex.value].id
}

type PickerEvent = { detail: { value: string | number } }

function onStartDateChange(e: PickerEvent): void {
  startDate.value = String(e.detail.value)
}

function onStartClockChange(e: PickerEvent): void {
  startClock.value = String(e.detail.value)
}

function onEndDateChange(e: PickerEvent): void {
  endDate.value = String(e.detail.value)
}

function onEndClockChange(e: PickerEvent): void {
  endClock.value = String(e.detail.value)
}

function onSignupEndDateChange(e: PickerEvent): void {
  signupEndDate.value = String(e.detail.value)
}

function onSignupEndClockChange(e: PickerEvent): void {
  signupEndClock.value = String(e.detail.value)
}

function onWaitlistChange(e: any): void {
  form.waitlistEnabled = e.detail.value ? 1 : 0
}

function onAuditRequiredChange(e: any): void {
  form.auditRequired = e.detail.value ? 1 : 0
}

function onCheckinTypeChange(e: PickerEvent): void {
  checkinIndex.value = Number(e.detail.value)
}

async function save(submitAfter: boolean): Promise<void> {
  if (!form.title.trim()) {
    uni.showToast({ title: '请填写活动标题', icon: 'none' })
    return
  }
  if (!form.locationName.trim()) {
    uni.showToast({ title: '请填写活动地点', icon: 'none' })
    return
  }

  const payload = {
    ...form,
    capacity: Number(capacityText.value) || 0,
    checkinType: checkinIndex.value + 1,
    startTime: `${startDate.value} ${startClock.value}:00`,
    endTime: `${endDate.value} ${endClock.value}:00`,
    signupStartTime: nowString(),
    signupEndTime: `${signupEndDate.value} ${signupEndClock.value}:00`
  }

  saving.value = true
  try {
    const data = await api.createActivity(payload)
    if (submitAfter) {
      await api.submitActivity(data.id)
    }
    uni.showToast({ title: submitAfter ? '已提交审核' : '已保存草稿', icon: 'success' })
    setTimeout(() => uni.navigateBack(), 800)
  } finally {
    saving.value = false
  }
}

function nowString(): string {
  const d = new Date()
  return `${dateStr(d)} ${pad(d.getHours())}:${pad(d.getMinutes())}:00`
}
</script>

<style scoped>
.field {
  margin-bottom: 26rpx;
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

.textarea {
  width: 100%;
  height: 220rpx;
  background: #f7f8fa;
  border-radius: 10rpx;
  padding: 18rpx 20rpx;
  font-size: 26rpx;
}

.picker {
  height: 80rpx;
  line-height: 80rpx;
  background: #f7f8fa;
  border-radius: 10rpx;
  padding: 0 20rpx;
  font-size: 26rpx;
  color: #16181d;
}

.picker.half {
  width: 260rpx;
  margin-right: 20rpx;
}

.section-title {
  font-size: 30rpx;
  font-weight: 600;
  display: block;
  margin-bottom: 20rpx;
}

.switch-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16rpx 0;
}

.actions {
  padding: 10rpx 24rpx 40rpx;
}

.actions .btn-ghost {
  margin-bottom: 18rpx;
}
</style>
