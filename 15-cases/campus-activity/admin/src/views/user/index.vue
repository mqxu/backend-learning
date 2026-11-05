<template>
  <div class="page-container">
    <div class="page-card">
      <div class="filter-bar">
        <el-input
          v-model="query.keyword"
          placeholder="按昵称 / 姓名 / 手机号搜索"
          clearable
          :prefix-icon="Search"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        />
        <el-select v-model="query.role" placeholder="全部角色" clearable @change="handleSearch">
          <el-option v-for="item in ROLE_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
        <el-button :icon="RefreshLeft" @click="handleReset">重置</el-button>
      </div>

      <el-table v-loading="loading" :data="list" border stripe empty-text="暂无用户数据">
        <el-table-column label="用户" min-width="180">
          <template #default="{ row }">
            <div class="user-cell">
              <el-avatar :size="28" :src="row.avatarUrl || undefined">
                {{ (row.nickname || 'U').slice(0, 1) }}
              </el-avatar>
              <span class="text-ellipsis">{{ row.nickname || '-' }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="角色" width="120" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.role === 'ADMIN' ? 'danger' : 'info'">
              {{ roleLabel(row.role) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="orgName" label="所属组织" min-width="150" show-overflow-tooltip />
        <el-table-column prop="realName" label="真实姓名" width="110" />
        <el-table-column prop="phone" label="手机号" width="130" />
        <el-table-column prop="studentNo" label="学号" width="130" />
        <el-table-column prop="college" label="学院" min-width="140" show-overflow-tooltip />
        <el-table-column prop="grade" label="年级" width="90" />
        <el-table-column prop="major" label="专业" min-width="140" show-overflow-tooltip />
        <el-table-column label="绑定状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.bound ? 'success' : 'info'" effect="plain">
              {{ row.bound ? '已绑定' : '未绑定' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openRoleDialog(row)">设置角色</el-button>
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

    <el-dialog v-model="roleVisible" title="设置用户角色" width="480px" @closed="resetRoleForm">
      <el-form ref="roleFormRef" :model="roleForm" :rules="roleRules" label-width="90px">
        <el-form-item label="用户">
          <span>{{ roleForm.nickname }}</span>
        </el-form-item>
        <el-form-item label="当前角色">
          <span class="text-muted">{{ roleLabel(roleForm.currentRole) }}</span>
        </el-form-item>
        <el-form-item label="新角色" prop="role">
          <el-select v-model="roleForm.role" placeholder="请选择角色" style="width: 100%" @change="handleRoleChange">
            <el-option v-for="item in ROLE_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="roleForm.role === 'ORGANIZER'" label="所属组织" prop="orgId">
          <el-select
            v-model="roleForm.orgId"
            placeholder="请选择组织"
            filterable
            style="width: 100%"
            :loading="orgLoading"
          >
            <el-option v-for="org in organizations" :key="org.id" :label="org.label" :value="org.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="roleVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitRole">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { RefreshLeft, Search } from '@element-plus/icons-vue'
import { fetchOrganizations, fetchUsers, setUserRole } from '@/api'
import { ROLE_OPTIONS, roleLabel } from '@/utils/dict'
import type { UserInfo, UserRole } from '@/types/api'

interface UserQueryForm {
  keyword: string
  role: UserRole | ''
  page: number
  size: number
}

interface RoleForm {
  id: number | null
  nickname: string
  currentRole: string
  role: UserRole | ''
  orgId: number | null
}

interface OrganizationOption {
  id: number
  label: string
}

const loading = ref(false)
const submitting = ref(false)
const list = ref<UserInfo[]>([])
const total = ref(0)
const query = reactive<UserQueryForm>({ keyword: '', role: '', page: 1, size: 10 })

const roleVisible = ref(false)
const roleFormRef = ref<FormInstance>()
const roleForm = reactive<RoleForm>({ id: null, nickname: '', currentRole: '', role: '', orgId: null })

const organizations = ref<OrganizationOption[]>([])
const orgLoading = ref(false)

const roleRules: FormRules = {
  role: [{ required: true, message: '请选择角色', trigger: 'change' }],
  orgId: [{ required: true, message: '请选择所属组织', trigger: 'change' }]
}

async function loadList(): Promise<void> {
  loading.value = true
  try {
    const data = await fetchUsers({
      keyword: query.keyword || '',
      role: query.role || '',
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

async function loadOrganizations(): Promise<void> {
  if (organizations.value.length) return
  orgLoading.value = true
  try {
    const data = await fetchOrganizations()
    organizations.value = (data || []).map((item) => ({
      id: item.id,
      label: item.name || `组织 ${item.id}`
    }))
  } catch {
    organizations.value = []
  } finally {
    orgLoading.value = false
  }
}

function handleSearch(): void {
  query.page = 1
  loadList()
}

function handleReset(): void {
  query.keyword = ''
  query.role = ''
  query.page = 1
  loadList()
}

function handleSizeChange(): void {
  query.page = 1
  loadList()
}

function openRoleDialog(row: UserInfo): void {
  roleForm.id = row.id
  roleForm.nickname = row.nickname || `用户 ${row.id}`
  roleForm.currentRole = row.role
  roleForm.role = row.role as UserRole
  roleForm.orgId = row.orgId ?? null
  roleVisible.value = true
  loadOrganizations()
}

function handleRoleChange(value: UserRole | ''): void {
  if (value !== 'ORGANIZER') {
    roleForm.orgId = null
    roleFormRef.value?.clearValidate('orgId')
  }
}

function resetRoleForm(): void {
  roleFormRef.value?.clearValidate()
  roleForm.id = null
  roleForm.nickname = ''
  roleForm.currentRole = ''
  roleForm.role = ''
  roleForm.orgId = null
}

async function submitRole(): Promise<void> {
  try {
    await roleFormRef.value?.validate()
  } catch {
    return
  }
  if (roleForm.id == null) return
  submitting.value = true
  try {
    await setUserRole(roleForm.id, {
      role: roleForm.role,
      orgId: roleForm.role === 'ORGANIZER' ? roleForm.orgId : null
    })
    ElMessage.success('角色已更新')
    roleVisible.value = false
    await loadList()
  } catch {
    // 错误提示已由请求拦截器统一处理
  } finally {
    submitting.value = false
  }
}

onMounted(loadList)
</script>

<style scoped>
.user-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}
</style>
