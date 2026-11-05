<template>
  <view class="page">
    <view class="tabs">
      <text
        v-for="t in tabs"
        :key="t.label"
        class="tab"
        :class="{ active: status === t.value }"
        @click="pick(t.value)"
      >{{ t.label }}</text>
    </view>

    <view v-if="!list.length && !loading" class="empty">暂无报名记录</view>

    <view v-for="s in list" :key="s.id" class="card">
      <view class="between">
        <view class="row">
          <text class="name">{{ s.realName || '未绑定' }}</text>
          <text class="muted" style="margin-left: 12rpx">{{ s.studentNo }}</text>
        </view>
        <text class="tag" :class="statusClass(s.status)">{{ s.statusLabel }}</text>
      </view>
      <view class="muted" style="margin-top: 8rpx">{{ s.college }} · 报名于 {{ fmtTime(s.createTime) }}</view>
      <view v-if="formEntries(s).length" class="form-data">
        <view v-for="e in formEntries(s)" :key="e.key" class="form-row">
          <text class="form-key">{{ e.label }}</text>
          <text class="form-val">{{ e.value }}</text>
        </view>
      </view>

      <view class="actions">
        <template v-if="s.status === 0">
          <text class="link" @click="audit(s, true)">通过</text>
          <text class="link danger" @click="audit(s, false)">驳回</text>
        </template>
        <text v-if="[1, 6].includes(s.status)" class="link" @click="manual(s)">补签</text>
      </view>
    </view>

    <view v-if="list.length" class="footer-tip">{{ hasMore ? '上拉加载更多' : '共 ' + total + ' 条' }}</view>
  </view>
</template>

<script setup lang="ts">
import type { SignupDetail } from '../../types'
import { ref } from 'vue'
import { onLoad, onPullDownRefresh, onReachBottom } from '@dcloudio/uni-app'
import { api } from '../../utils/api'

const activityId = ref<number | null>(null)
const list = ref<SignupDetail[]>([])
const total = ref<number>(0)
const status = ref<number | null>(null)
const page = ref(1)
const size = 20
const hasMore = ref(true)
const loading = ref(false)

const fieldLabels = ref<Record<string, string>>({})

/** 后端时间形如 2026-10-28 10:06:11，这里去掉秒 */
function fmtTime(value?: string | null): string {
  return value ? value.slice(0, 16) : ''
}

/** 把报名表单的 JSON 字符串拆成「字段名：值」逐行展示，字段名优先用活动里配置的中文标签 */
function formEntries(s: SignupDetail): Array<{ key: string; label: string; value: string }> {
  if (!s.formData) return []
  let raw: Record<string, unknown>
  try {
    raw = (typeof s.formData === 'string' ? JSON.parse(s.formData) : s.formData) as Record<string, unknown>
  } catch {
    return []
  }
  return Object.entries(raw).map(([key, value]) => ({
    key,
    label: fieldLabels.value[key] || key,
    value: Array.isArray(value) ? value.join('、') : String(value ?? '')
  }))
}

const tabs = [
  { label: '全部', value: null },
  { label: '待审核', value: 0 },
  { label: '报名成功', value: 1 },
  { label: '已签到', value: 3 },
  { label: '候补中', value: 2 }
]

function statusClass(value: number): string {
  if (value === 1) return 'tag-green'
  if (value === 3) return 'tag-blue'
  if (value === 2) return 'tag-orange'
  if (value === 5 || value === 6) return 'tag-red'
  return ''
}

async function load(reset = false): Promise<void> {
  if (loading.value) return
  if (reset) {
    page.value = 1
    hasMore.value = true
    list.value = []
  }
  if (!hasMore.value) return

  loading.value = true
  try {
    const data = await api.orgSignups(Number(activityId.value), {
      page: page.value,
      size,
      status: status.value === null ? undefined : status.value
    })
    const records = data.records || []
    list.value = reset ? records : list.value.concat(records)
    total.value = data.total || 0
    hasMore.value = list.value.length < total.value
    if (hasMore.value) page.value += 1
  } finally {
    loading.value = false
  }
}

function pick(value: number | null): void {
  status.value = value
  load(true)
}

function audit(signup: SignupDetail, pass: boolean): void {
  const doAudit = async () => {
    await api.auditSignups(Number(activityId.value), {
      signupIds: [signup.id],
      pass,
      reason: pass ? null : '信息不完整'
    })
    uni.showToast({ title: pass ? '已通过' : '已驳回', icon: 'success' })
    load(true)
  }

  if (pass) {
    doAudit()
    return
  }
  uni.showModal({
    title: '驳回报名',
    content: `确认驳回 ${signup.realName || '该同学'} 的报名？`,
    success: (res) => {
      if (res.confirm) doAudit()
    }
  })
}

function manual(signup: SignupDetail): void {
  uni.showModal({
    title: '手动补签',
    content: `确认为 ${signup.realName || '该同学'} 补签？将记录操作日志。`,
    success: async (res) => {
      if (!res.confirm) return
      await api.manualCheckin(Number(activityId.value), {
        signupId: signup.id,
        remark: '组织者现场补签'
      })
      uni.showToast({ title: '补签成功', icon: 'success' })
      load(true)
    }
  })
}

onLoad((options) => {
  activityId.value = Number(options?.id)
  load(true)
  loadFieldLabels()
})

async function loadFieldLabels(): Promise<void> {
  try {
    const detail = await api.activityDetail(Number(activityId.value))
    const fields = detail.formFields ? (JSON.parse(detail.formFields) as Array<{ key: string; label: string }>) : []
    fieldLabels.value = Object.fromEntries(fields.map((f) => [f.key, f.label]))
  } catch {
    fieldLabels.value = {}
  }
}

onPullDownRefresh(async () => {
  await load(true)
  uni.stopPullDownRefresh()
})

onReachBottom(() => load(false))
</script>

<style scoped>
.tabs {
  display: flex;
  background: #ffffff;
  padding: 18rpx 24rpx;
}

.tab {
  font-size: 25rpx;
  color: #5b6270;
  padding: 8rpx 20rpx;
  border-radius: 999rpx;
  margin-right: 12rpx;
  background: #f5f6f8;
}

.tab.active {
  background: #eef4ff;
  color: #1f6feb;
  font-weight: 600;
}

.name {
  font-size: 30rpx;
  font-weight: 600;
}

.form-data {
  margin-top: 12rpx;
  background: #f7f8fa;
  border-radius: 10rpx;
  padding: 14rpx 18rpx;
  font-size: 24rpx;
  color: #5b6270;
}

.form-row {
  display: flex;
  align-items: flex-start;
  gap: 16rpx;
  line-height: 1.6;
}

.form-key {
  color: #8a8f99;
  flex-shrink: 0;
}

.form-val {
  color: #1f2430;
  word-break: break-all;
}

.actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 16rpx;
}

.link {
  font-size: 26rpx;
  color: #1f6feb;
  margin-left: 28rpx;
}

.link.danger {
  color: #b3261e;
}

.footer-tip {
  text-align: center;
  color: #9aa0aa;
  font-size: 24rpx;
  padding: 24rpx 0 40rpx;
}
</style>
