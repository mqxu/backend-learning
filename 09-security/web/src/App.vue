<script setup>
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { me } from './api'
import { auth, clearLogin, hasRole, saveUser } from './store/auth'
import { toasts } from './store/toast'
import { homeOf } from './router'

const router = useRouter()
const route = useRoute()

const loggedIn = computed(() => !!auth.token && route.path !== '/login')

/**
 * 刷新页面后用令牌换回用户信息。
 * 之所以要多这一步：localStorage 里的用户信息可能过期（角色被管理员改了），
 * 以接口返回的为准才靠得住。
 */
onMounted(async () => {
  if (!auth.token) return
  try {
    saveUser(await me())
  } catch (e) {
    // 401 已在拦截器里清登录态并跳转，这里不用重复处理
  }
})

function logout() {
  clearLogin()
  router.push('/login')
}
</script>

<template>
  <div v-if="loggedIn" class="topbar">
    <div class="brand">实训任务提交与批改<span>09 · 认证与授权</span></div>
    <nav class="nav">
      <router-link v-if="hasRole('STUDENT')" to="/tasks">我的提交</router-link>
      <router-link v-if="hasRole('TEACHER') || hasRole('ADMIN')" to="/review">待批改</router-link>
      <router-link v-if="hasRole('ADMIN')" to="/users">账号管理</router-link>
    </nav>
    <div class="spacer"></div>
    <div class="who">
      <b>{{ auth.user?.nickname || auth.user?.username }}</b>
      <span class="role-tag">{{ auth.user?.roles?.join(' / ') }}</span>
    </div>
    <button class="btn btn-sm" @click="logout">退出</button>
  </div>

  <router-view />

  <div class="toast-wrap">
    <div v-for="item in toasts" :key="item.id" class="toast">{{ item.message }}</div>
  </div>
</template>
