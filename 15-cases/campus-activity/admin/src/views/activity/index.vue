<template>
  <div class="page-container">
    <div class="page-card">
      <div class="filter-bar">
        <el-input
          v-model="query.keyword"
          placeholder="按活动标题搜索"
          clearable
          :prefix-icon="Search"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        />
        <el-select v-model="query.status" placeholder="全部状态" clearable @change="handleSearch">
          <el-option
            v-for="item in ACTIVITY_STATUS_OPTIONS"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
        <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
        <el-button :icon="RefreshLeft" @click="handleReset">重置</el-button>
      </div>

      <el-table v-loading="loading" :data="list" border stripe empty-text="暂无活动数据">
        <el-table-column prop="title" label="活动标题" min-width="220" show-overflow-tooltip />
        <el-table-column prop="orgName" label="所属组织" min-width="150" show-overflow-tooltip />
        <el-table-column prop="categoryName" label="分类" width="120" show-overflow-tooltip />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="activityStatusType(row.status)" size="small">
              {{ activityStatusLabel(row.status, row.statusLabel) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="活动时间" min-width="200">
          <template #default="{ row }">
            <div>{{ row.startTime || '-' }}</div>
            <div class="text-muted">至 {{ row.endTime || '-' }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="locationName" label="活动地点" min-width="140" show-overflow-tooltip />
        <el-table-column label="报名 / 容量" width="120" align="center">
          <template #default="{ row }">{{ row.signedCount ?? 0 }} / {{ row.capacity ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">查看</el-button>
            <el-button
              link
              type="danger"
              :disabled="row.status === 6"
              @click="openOffline(row)"
            >
              下架
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="table-pager">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.size"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @current-change="loadList"
          @size-change="handleSizeChange"
        />
      </div>
    </div>

    <ActivityDetailDrawer v-model="detailVisible" :activity-id="currentId" />

    <el-dialog v-model="offlineVisible" title="下架活动" width="480px" @closed="resetOffline">
      <el-form ref="offlineFormRef" :model="offlineForm" :rules="offlineRules" label-width="80px">
        <el-form-item label="活动">
          <span class="text-ellipsis">{{ offlineForm.title }}</span>
        </el-form-item>
        <el-form-item label="下架理由" prop="reason">
          <el-input
            v-model="offlineForm.reason"
            type="textarea"
            :rows="4"
            maxlength="200"
            show-word-limit
            placeholder="请填写下架理由"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="offlineVisible = false">取消</el-button>
        <el-button type="danger" :loading="submitting" @click="submitOffline">确认下架</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { RefreshLeft, Search } from '@element-plus/icons-vue'
import { fetchActivities, offlineActivity } from '@/api'
import { ACTIVITY_STATUS_OPTIONS, activityStatusLabel, activityStatusType } from '@/utils/dict'
import ActivityDetailDrawer from '@/components/ActivityDetailDrawer.vue'
import type { ActivityItem, ActivityStatus } from '@/types/api'

interface ActivityQueryForm {
  keyword: string
  status: ActivityStatus | ''
  page: number
  size: number
}

interface OfflineForm {
  id: number | null
  title: string
  reason: string
}

const loading = ref(false)
const submitting = ref(false)
const list = ref<ActivityItem[]>([])
const total = ref(0)
const query = reactive<ActivityQueryForm>({ keyword: '', status: '', page: 1, size: 10 })

const detailVisible = ref(false)
const currentId = ref<number | null>(null)

const offlineVisible = ref(false)
const offlineFormRef = ref<FormInstance>()
const offlineForm = reactive<OfflineForm>({ id: null, title: '', reason: '' })
const offlineRules: FormRules = {
  reason: [{ required: true, message: '请填写下架理由', trigger: 'blur' }]
}

async function loadList(): Promise<void> {
  loading.value = true
  try {
    const data = await fetchActivities({
      keyword: query.keyword || '',
      status: query.status === '' ? '' : query.status,
      page: query.page,
      size: query.size
    })
    list.value = data?.records || []
    total.value = data?.total || 0
  } catch {
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function handleSearch(): void {
  query.page = 1
  loadList()
}

function handleReset(): void {
  query.keyword = ''
  query.status = ''
  query.page = 1
  loadList()
}

function handleSizeChange(): void {
  query.page = 1
  loadList()
}

function openDetail(row: ActivityItem): void {
  currentId.value = row.id
  detailVisible.value = true
}

function openOffline(row: ActivityItem): void {
  offlineForm.id = row.id
  offlineForm.title = row.title
  offlineForm.reason = ''
  offlineVisible.value = true
}

function resetOffline(): void {
  offlineFormRef.value?.clearValidate()
  offlineForm.id = null
  offlineForm.title = ''
  offlineForm.reason = ''
}

async function submitOffline(): Promise<void> {
  try {
    await offlineFormRef.value?.validate()
  } catch {
    return
  }
  if (offlineForm.id == null) return
  submitting.value = true
  try {
    await offlineActivity(offlineForm.id, { reason: offlineForm.reason.trim() })
    ElMessage.success('活动已下架')
    offlineVisible.value = false
    await loadList()
  } catch {
    // 错误提示已由请求拦截器统一处理
  } finally {
    submitting.value = false
  }
}

onMounted(loadList)
</script>
