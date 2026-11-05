<template>
  <div class="page-container" v-loading="loading">
    <div class="metric-grid">
      <div v-for="item in metrics" :key="item.label" class="metric-card">
        <div class="metric-icon" :style="{ background: item.bg, color: item.color }">
          <el-badge :is-dot="item.badge" :offset="[2, 2]">
            <el-icon :size="22"><component :is="item.icon" /></el-icon>
          </el-badge>
        </div>
        <div class="metric-body">
          <div class="metric-label">{{ item.label }}</div>
          <div class="metric-value">{{ item.value }}</div>
        </div>
      </div>
    </div>

    <div class="page-card">
      <div class="card-head">
        <span class="card-title">热门活动</span>
        <span class="text-muted">按报名人数排序</span>
      </div>
      <el-table :data="topActivities" border stripe empty-text="暂无数据">
        <el-table-column type="index" label="#" width="60" align="center" />
        <el-table-column prop="title" label="活动名称" min-width="220" show-overflow-tooltip />
        <el-table-column prop="signupCount" label="报名人数" width="110" align="center" />
        <el-table-column prop="capacity" label="容量" width="100" align="center" />
        <el-table-column label="报名进度" min-width="200">
          <template #default="{ row }">
            <el-progress
              :percentage="signupRate(row)"
              :stroke-width="10"
              :color="signupRate(row) >= 100 ? '#f56c6c' : '#409eff'"
            />
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, type Component } from 'vue'
import { ElMessage } from 'element-plus'
import { Tickets, User, UserFilled, Checked, Bell, Calendar } from '@element-plus/icons-vue'
import { fetchDashboard } from '@/api'
import type { DashboardSummary, DashboardTopActivity } from '@/types/api'

interface MetricItem {
  label: string
  value: number
  icon: Component
  color: string
  bg: string
  badge?: boolean
}

const loading = ref(false)
const summary = ref<DashboardSummary>({
  userTotal: 0,
  studentTotal: 0,
  activityTotal: 0,
  publishedCount: 0,
  pendingAuditCount: 0,
  signupTotal: 0,
  checkinTotal: 0
})
const topActivities = ref<DashboardTopActivity[]>([])

const metrics = computed<MetricItem[]>(() => [
  { label: '用户数', value: summary.value.userTotal, icon: User, color: '#409eff', bg: '#ecf5ff' },
  { label: '活动总数', value: summary.value.activityTotal, icon: Tickets, color: '#67c23a', bg: '#f0f9eb' },
  { label: '已发布', value: summary.value.publishedCount, icon: Checked, color: '#e6a23c', bg: '#fdf6ec' },
  {
    label: '待审核',
    value: summary.value.pendingAuditCount,
    icon: Bell,
    color: '#f56c6c',
    bg: '#fef0f0',
    badge: summary.value.pendingAuditCount > 0
  },
  { label: '报名总数', value: summary.value.signupTotal, icon: UserFilled, color: '#909399', bg: '#f4f4f5' },
  { label: '签到总数', value: summary.value.checkinTotal, icon: Calendar, color: '#7c4dff', bg: '#f3f0ff' }
])

function signupRate(row: DashboardTopActivity): number {
  const capacity = Number(row.capacity) || 0
  const signed = Number(row.signupCount) || 0
  if (capacity <= 0) return 0
  return Math.min(100, Math.round((signed / capacity) * 100))
}

async function loadData(): Promise<void> {
  loading.value = true
  try {
    const data = await fetchDashboard()
    summary.value = { ...summary.value, ...data }
    topActivities.value = data?.topActivities || []
  } catch {
    ElMessage.warning('工作台数据加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(loadData)
</script>

<style scoped>
.metric-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 16px;
}

@media (max-width: 1280px) {
  .metric-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

.metric-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 18px 16px;
  background: #fff;
  border-radius: 4px;
  border: 1px solid #ebeef5;
}

.metric-icon {
  width: 44px;
  height: 44px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.metric-label {
  font-size: 13px;
  color: #909399;
  margin-bottom: 6px;
}

.metric-value {
  font-size: 22px;
  font-weight: 600;
  line-height: 1;
  color: #303133;
}

.card-head {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin-bottom: 14px;
}

.card-title {
  font-size: 15px;
  font-weight: 600;
}
</style>
