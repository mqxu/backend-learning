<template>
  <el-container class="layout">
    <el-aside width="210px" class="layout-aside">
      <div class="logo">
        <el-icon :size="20"><Calendar /></el-icon>
        <span>校园活动管理</span>
      </div>
      <el-menu :default-active="activeMenu" router class="layout-menu">
        <el-menu-item index="/dashboard">
          <el-icon><Odometer /></el-icon>
          <span>工作台</span>
        </el-menu-item>
        <el-menu-item index="/audit">
          <el-icon><Stamp /></el-icon>
          <span>活动审核</span>
        </el-menu-item>
        <el-menu-item index="/activity">
          <el-icon><Tickets /></el-icon>
          <span>活动管理</span>
        </el-menu-item>
        <el-menu-item index="/user">
          <el-icon><User /></el-icon>
          <span>用户管理</span>
        </el-menu-item>
        <el-menu-item index="/log">
          <el-icon><Document /></el-icon>
          <span>操作日志</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="layout-header">
        <div class="header-title">{{ pageTitle }}</div>
        <el-dropdown trigger="click" @command="handleCommand">
          <span class="header-user">
            <el-avatar :size="28" :src="user.avatarUrl || undefined">
              {{ (user.nickname || 'A').slice(0, 1) }}
            </el-avatar>
            <span class="header-user-name">{{ user.nickname || '管理员' }}</span>
            <el-tag size="small" type="info">{{ roleLabel(user.role) }}</el-tag>
            <el-icon><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </el-header>

      <el-main class="layout-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { clearAuth, getCurrentUser } from '@/api/request'
import { roleLabel } from '@/utils/dict'
import type { UserInfo } from '@/types/api'

const route = useRoute()
const router = useRouter()
const user = ref<Partial<UserInfo>>(getCurrentUser() ?? {})

const activeMenu = computed<string>(() => route.path)
const pageTitle = computed<string>(() => route.meta.title || '')

async function handleCommand(command: string | number | Record<string, unknown>): Promise<void> {
  if (command !== 'logout') return
  try {
    await ElMessageBox.confirm('确定退出登录吗？', '提示', {
      confirmButtonText: '退出',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }
  clearAuth()
  user.value = {}
  ElMessage.success('已退出登录')
  router.replace('/login')
}
</script>

<style scoped>
.layout {
  height: 100%;
}

.layout-aside {
  background: #1f2d3d;
  display: flex;
  flex-direction: column;
}

.logo {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: #fff;
  font-size: 16px;
  font-weight: 600;
  letter-spacing: 1px;
  background: #1a2532;
}

.layout-menu {
  border-right: none;
  background: #1f2d3d;
  flex: 1;
}

.layout-menu :deep(.el-menu-item) {
  color: #c0c4cc;
}

.layout-menu :deep(.el-menu-item:hover) {
  background: #2a3a4d;
  color: #fff;
}

.layout-menu :deep(.el-menu-item.is-active) {
  background: #2a3a4d;
  color: #409eff;
}

.layout-header {
  height: 60px;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.header-title {
  font-size: 16px;
  font-weight: 600;
}

.header-user {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  outline: none;
}

.header-user-name {
  font-size: 14px;
  color: #303133;
}

.layout-main {
  padding: 0;
  background: #f5f7fa;
  overflow: auto;
}
</style>
