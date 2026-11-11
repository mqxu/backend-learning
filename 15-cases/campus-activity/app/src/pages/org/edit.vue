<template>
  <view class="page">
    <view class="card">
      <view class="field">
        <text class="label">活动封面</text>
        <view v-if="form.coverUrl" class="cover-box">
          <image :src="resolveFileUrl(form.coverUrl)" mode="aspectFill" class="cover-preview" />
          <view class="cover-actions">
            <text class="cover-link" @click="chooseCover">重新选择</text>
            <text class="cover-link danger" @click="removeCover">移除</text>
          </view>
        </view>
        <view v-else class="cover-empty" :class="{ disabled: uploading }" @click="chooseCover">
          <text class="cover-plus">+</text>
          <text class="muted">上传活动封面（建议 16:9 横图）</text>
        </view>
      </view>

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
import { resolveFileUrl } from '../../utils/request'

const categories = ref<Category[]>([])
const categoryIndex = ref(0)
const checkinLabels = ['扫码签到', '扫码 + 定位签到', '仅组织者手动签到']
const checkinIndex = ref(0)
const capacityText = ref('0')
const saving = ref(false)
const uploading = ref(false)

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
  coverUrl: string | null
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
  coverUrl: null,
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

/** 选择并上传封面：uni.chooseImage 单张压缩图 → uni.uploadFile → 存相对地址 */
function chooseCover(): void {
  if (uploading.value) {
    return
  }
  uni.chooseImage({
    count: 1,
    sizeType: ['compressed'],
    sourceType: ['album', 'camera'],
    success: async (res) => {
      const tempFilePath = res.tempFilePaths[0]
      if (!tempFilePath) {
        return
      }
      uploading.value = true
      uni.showLoading({ title: '上传中', mask: true })
      try {
        const data = await api.uploadFile(tempFilePath, 'activity-cover')
        uni.hideLoading()
        form.coverUrl = data.url
        uni.showToast({ title: '封面已上传', icon: 'success' })
      } catch (e) {
        // uploadFile 已 toast 失败原因
        uni.hideLoading()
      } finally {
        uploading.value = false
      }
    }
  })
}

function removeCover(): void {
  form.coverUrl = null
}

async function save(submitAfter: boolean): Promise<void> {
  if (uploading.value) {
    uni.showToast({ title: '封面正在上传，请稍候', icon: 'none' })
    return
  }
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

.cover-box {
  background: #f7f8fa;
  border-radius: 10rpx;
  padding: 16rpx;
}

.cover-preview {
  width: 100%;
  height: 320rpx;
  border-radius: 10rpx;
  background: #eef0f3;
}

.cover-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 14rpx;
}

.cover-link {
  font-size: 24rpx;
  color: #1f6feb;
  margin-left: 28rpx;
}

.cover-link.danger {
  color: #b3261e;
}

.cover-empty {
  height: 200rpx;
  background: #f7f8fa;
  border: 1rpx dashed #d3d8e0;
  border-radius: 10rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.cover-empty.disabled {
  opacity: 0.6;
}

.cover-plus {
  font-size: 48rpx;
  color: #9aa0aa;
  line-height: 1.2;
  margin-bottom: 10rpx;
}
</style>
