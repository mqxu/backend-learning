<template>
  <div class="page-container">
    <div class="page-card">
      <div class="card-head">
        <span class="card-title">待审核活动</span>
        <el-tag type="warning" effect="plain">共 {{ total }} 条</el-tag>
        <el-button class="refresh-btn" :icon="Refresh" size="small" @click="loadList">刷新</el-button>
      </div>

      <el-table v-loading="loading" :data="list" border stripe empty-text="暂无待审核活动">
        <el-table-column prop="title" label="活动标题" min-width="220" show-overflow-tooltip />
        <el-table-column prop="orgName" label="所属组织" min-width="160" show-overflow-tooltip />
        <el-table-column label="活动时间" min-width="210">
          <template #default="{ row }">
            <div>{{ row.startTime || '-' }}</div>
            <div class="text-muted">至 {{ row.endTime || '-' }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="locationName" label="活动地点" min-width="150" show-overflow-tooltip />
        <el-table-column label="容量" width="90" align="center">
          <template #default="{ row }">{{ row.capacity ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">查看</el-button>
            <el-button link type="success" @click="handlePass(row)">通过</el-button>
            <el-button link type="danger" @click="openReject(row)">驳回</el-button>
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

    <el-dialog v-model="rejectVisible" title="驳回活动" width="480px" @closed="resetReject">
      <el-form ref="rejectFormRef" :model="rejectForm" :rules="rejectRules" label-width="80px">
        <el-form-item label="活动">
          <span class="text-ellipsis">{{ rejectForm.title }}</span>
        </el-form-item>
        <el-form-item label="驳回理由" prop="reason">
          <el-input
            v-model="rejectForm.reason"
            type="textarea"
            :rows="4"
            maxlength="200"
            show-word-limit
            placeholder="请填写驳回理由，将展示给活动组织者"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rejectVisible = false">取消</el-button>
        <el-button type="danger" :loading="submitting" @click="submitReject">确认驳回</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { auditActivity, fetchAuditList } from '@/api'
import ActivityDetailDrawer from '@/components/ActivityDetailDrawer.vue'
import type { ActivityItem } from '@/types/api'

interface RejectForm {
  id: number | null
  title: string
  reason: string
}

const loading = ref(false)
const submitting = ref(false)
const list = ref<ActivityItem[]>([])
const total = ref(0)
const query = reactive<{ page: number; size: number }>({ page: 1, size: 10 })

const detailVisible = ref(false)
const currentId = ref<number | null>(null)

const rejectVisible = ref(false)
const rejectFormRef = ref<FormInstance>()
const rejectForm = reactive<RejectForm>({ id: null, title: '', reason: '' })
const rejectRules: FormRules = {
  reason: [{ required: true, message: '请填写驳回理由', trigger: 'blur' }]
}

async function loadList(): Promise<void> {
  loading.value = true
  try {
    const data = await fetchAuditList({ page: query.page, size: query.size })
    list.value = data?.records || []
    total.value = data?.total || 0
  } catch {
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function handleSizeChange(): void {
  query.page = 1
  loadList()
}

async function reloadAfterHandled(): Promise<void> {
  if (list.value.length <= 1 && query.page > 1) {
    query.page -= 1
  }
  await loadList()
}

function openDetail(row: ActivityItem): void {
  currentId.value = row.id
  detailVisible.value = true
}

async function handlePass(row: ActivityItem): Promise<void> {
  try {
    await ElMessageBox.confirm(`确认通过「${row.title}」的审核吗？通过后活动将对外发布。`, '审核确认', {
      confirmButtonText: '通过',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }
  try {
    await auditActivity(row.id, { pass: true })
    ElMessage.success('审核已通过')
    await reloadAfterHandled()
  } catch {
    // 错误提示已由请求拦截器统一处理
  }
}

function openReject(row: ActivityItem): void {
  rejectForm.id = row.id
  rejectForm.title = row.title
  rejectForm.reason = ''
  rejectVisible.value = true
}

function resetReject(): void {
  rejectFormRef.value?.clearValidate()
  rejectForm.id = null
  rejectForm.title = ''
  rejectForm.reason = ''
}

async function submitReject(): Promise<void> {
  try {
    await rejectFormRef.value?.validate()
  } catch {
    return
  }
  if (rejectForm.id == null) return
  submitting.value = true
  try {
    await auditActivity(rejectForm.id, { pass: false, reason: rejectForm.reason.trim() })
    ElMessage.success('已驳回该活动')
    rejectVisible.value = false
    await reloadAfterHandled()
  } catch {
    // 错误提示已由请求拦截器统一处理
  } finally {
    submitting.value = false
  }
}

onMounted(loadList)
</script>

<style scoped>
.card-head {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.card-title {
  font-size: 15px;
  font-weight: 600;
}

.refresh-btn {
  margin-left: auto;
}
</style>
