<template>
  <el-drawer
    :model-value="modelValue"
    title="活动详情"
    size="720px"
    destroy-on-close
    @update:model-value="(val: boolean) => emit('update:modelValue', val)"
    @open="loadDetail"
  >
    <div v-loading="loading" class="detail-body">
      <el-empty v-if="!loading && !detail" description="暂无活动详情" />
      <template v-else-if="detail">
        <div class="detail-title-row">
          <h3 class="detail-title">{{ detail.title }}</h3>
          <el-tag :type="activityStatusType(detail.status)" size="small">
            {{ activityStatusLabel(detail.status, detail.statusLabel) }}
          </el-tag>
        </div>

        <el-image
          v-if="detail.coverUrl"
          :src="detail.coverUrl"
          fit="cover"
          class="detail-cover"
          :preview-src-list="[detail.coverUrl]"
          preview-teleported
        />

        <el-descriptions :column="2" border size="small" class="detail-desc">
          <el-descriptions-item label="所属组织">{{ detail.orgName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="活动分类">{{ detail.categoryName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="活动开始">{{ detail.startTime || '-' }}</el-descriptions-item>
          <el-descriptions-item label="活动结束">{{ detail.endTime || '-' }}</el-descriptions-item>
          <el-descriptions-item label="报名开始">{{ detail.signupStartTime || '-' }}</el-descriptions-item>
          <el-descriptions-item label="报名结束">{{ detail.signupEndTime || '-' }}</el-descriptions-item>
          <el-descriptions-item label="取消截止">{{ detail.cancelDeadline || '-' }}</el-descriptions-item>
          <el-descriptions-item label="活动地点">{{ detail.locationName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="详细地址" :span="2">{{ detail.address || '-' }}</el-descriptions-item>
          <el-descriptions-item label="容量 / 已报名">
            {{ detail.capacity ?? '-' }} / {{ detail.signedCount ?? 0 }}
          </el-descriptions-item>
          <el-descriptions-item label="签到方式">{{ detail.checkinType ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="需要审核">
            {{ detail.auditRequired ? '是' : '否' }}
          </el-descriptions-item>
          <el-descriptions-item label="开启候补">
            {{ detail.waitlistEnabled ? '是' : '否' }}
          </el-descriptions-item>
          <el-descriptions-item label="浏览量">{{ detail.viewCount ?? 0 }}</el-descriptions-item>
          <el-descriptions-item label="收藏数">{{ detail.favoriteCount ?? 0 }}</el-descriptions-item>
          <el-descriptions-item label="经度">{{ detail.longitude ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="纬度">{{ detail.latitude ?? '-' }}</el-descriptions-item>
        </el-descriptions>

        <div class="detail-section">
          <div class="section-title">活动简介</div>
          <div class="section-text pre-wrap">{{ detail.summary || '暂无简介' }}</div>
        </div>

        <div class="detail-section">
          <div class="section-title">活动内容</div>
          <div class="section-text pre-wrap">{{ detail.content || '暂无内容' }}</div>
        </div>

        <div v-if="formFieldText" class="detail-section">
          <div class="section-title">报名表单</div>
          <div class="section-text pre-wrap">{{ formFieldText }}</div>
        </div>
      </template>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { fetchActivityDetail } from '@/api'
import { activityStatusLabel, activityStatusType } from '@/utils/dict'
import type { ActivityDetail, FormFieldItem } from '@/types/api'

const props = withDefaults(
  defineProps<{
    modelValue?: boolean
    activityId?: number | string | null
  }>(),
  {
    modelValue: false,
    activityId: null
  }
)

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
}>()

const loading = ref(false)
const detail = ref<ActivityDetail | null>(null)

function describeFields(fields: Array<FormFieldItem | string>): string {
  return fields
    .map((item, index) => {
      if (typeof item === 'string') return `${index + 1}. ${item}`
      const name = item?.label || item?.name || item?.fieldName || `字段${index + 1}`
      const required = item?.required ? '（必填）' : ''
      return `${index + 1}. ${name}${required}`
    })
    .join('\n')
}

const formFieldText = computed<string>(() => {
  const fields = detail.value?.formFields
  if (!fields) return ''
  if (Array.isArray(fields)) {
    return describeFields(fields)
  }
  if (typeof fields === 'string') {
    try {
      const parsed: unknown = JSON.parse(fields)
      if (Array.isArray(parsed)) {
        return describeFields(parsed as Array<FormFieldItem | string>)
      }
    } catch {
      return fields
    }
    return fields
  }
  return ''
})

async function loadDetail(): Promise<void> {
  if (props.activityId == null || props.activityId === '') return
  loading.value = true
  detail.value = null
  try {
    detail.value = await fetchActivityDetail(props.activityId)
  } catch {
    detail.value = null
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.detail-body {
  min-height: 200px;
}

.detail-title-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
}

.detail-title {
  margin: 0;
  font-size: 17px;
  font-weight: 600;
}

.detail-cover {
  width: 100%;
  height: 200px;
  border-radius: 4px;
  margin-bottom: 14px;
  background: #f5f7fa;
}

.detail-desc {
  margin-bottom: 8px;
}

.detail-section {
  margin-top: 18px;
}

.section-title {
  font-size: 14px;
  font-weight: 600;
  padding-left: 8px;
  border-left: 3px solid #409eff;
  margin-bottom: 10px;
}

.section-text {
  font-size: 14px;
  line-height: 1.8;
  color: #606266;
  background: #fafafa;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  padding: 12px;
}

.pre-wrap {
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
