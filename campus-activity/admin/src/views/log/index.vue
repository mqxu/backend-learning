<template>
  <div class="page-container">
    <div class="page-card">
      <div class="card-head">
        <span class="card-title">操作日志</span>
        <el-button class="refresh-btn" :icon="Refresh" size="small" @click="loadList">刷新</el-button>
      </div>

      <el-table v-loading="loading" :data="list" border stripe empty-text="暂无操作日志">
        <el-table-column prop="createTime" label="操作时间" width="180" />
        <el-table-column prop="bizType" label="业务类型" width="130" show-overflow-tooltip />
        <el-table-column prop="bizId" label="业务 ID" width="100" align="center" />
        <el-table-column label="操作" width="110" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="actionTagType(row.action)" effect="plain">
              {{ auditActionLabel(row.action) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作人" width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.operatorName || row.operatorId || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态变更" width="150" align="center">
          <template #default="{ row }">
            <span class="text-muted">{{ row.beforeStatus ?? '-' }}</span>
            <el-icon class="arrow-icon"><Right /></el-icon>
            <span>{{ row.afterStatus ?? '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="240" show-overflow-tooltip />
      </el-table>

      <div class="table-pager">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.size"
          :total="total"
          :page-sizes="[20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @current-change="loadList"
          @size-change="handleSizeChange"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { Refresh, Right } from '@element-plus/icons-vue'
import { fetchLogs } from '@/api'
import { auditActionLabel } from '@/utils/dict'
import type { AuditRecord, TagType } from '@/types/api'

const loading = ref(false)
const list = ref<AuditRecord[]>([])
const total = ref(0)
const query = reactive<{ page: number; size: number }>({ page: 1, size: 20 })

const ACTION_TAG_TYPES: Record<string, TagType> = {
  APPROVE: 'success',
  REJECT: 'danger',
  CANCEL: 'info',
  MANUAL_CHECKIN: 'warning',
  ROLE_CHANGE: 'primary'
}

function actionTagType(action?: string | null): TagType {
  if (!action) return 'info'
  return ACTION_TAG_TYPES[action] || 'info'
}

async function loadList(): Promise<void> {
  loading.value = true
  try {
    const data = await fetchLogs({ page: query.page, size: query.size })
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

.arrow-icon {
  margin: 0 6px;
  color: #c0c4cc;
  vertical-align: middle;
}
</style>
