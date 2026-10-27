<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { login } from '../api'
import { saveLogin } from '../store/auth'
import { homeOf } from '../router'
import type { LoginPayload } from '../types/api'

const router = useRouter()
const route = useRoute()

const form = reactive<LoginPayload>({ username: 'student01', password: 'Student#2026' })
const loading = ref(false)
const error = ref('')

interface DemoAccount {
  label: string
  username: string
  password: string
}

const demoAccounts: DemoAccount[] = [
  { label: '学生 student01', username: 'student01', password: 'Student#2026' },
  { label: '教师 teacher01', username: 'teacher01', password: 'Teacher#2026' },
  { label: '管理员 admin', username: 'admin', password: 'Admin#2026' },
  { label: '停用 guest', username: 'guest', password: 'Guest#2026' },
]

function fill(account: DemoAccount): void {
  form.username = account.username
  form.password = account.password
  error.value = ''
}

async function onSubmit(): Promise<void> {
  error.value = ''
  loading.value = true
  try {
    const data = await login(form)
    // 令牌和身份落库，后面每个请求由拦截器自动带上
    saveLogin(data)
    // query 的类型是 string | string[] | null，直接给 router 会报错，这里显式收敛
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : ''
    await router.replace(redirect || homeOf())
  } catch (e) {
    // 密码错、账号停用这类属于业务失败，后端返回 200 + 非 200 的 code，
    // 拦截器已经提示过一次，这里把原因显示在表单上方
    error.value = e instanceof Error ? e.message : '登录失败'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <form class="login-card" @submit.prevent="onSubmit">
      <h1>实训任务提交与批改</h1>
      <p class="sub">登录后获取令牌，之后每个请求都会自动带上</p>

      <div v-if="error" class="err">{{ error }}</div>

      <div class="field">
        <label for="username">用户名</label>
        <input id="username" v-model.trim="form.username" autocomplete="username" placeholder="student01" />
      </div>
      <div class="field">
        <label for="password">密码</label>
        <input
          id="password"
          v-model="form.password"
          type="password"
          autocomplete="current-password"
          placeholder="Student#2026"
        />
      </div>

      <button class="btn btn-primary" type="submit" :disabled="loading">
        {{ loading ? '登录中…' : '登录' }}
      </button>

      <div class="accounts">
        <div class="t">点一下直接填入，不用手打</div>
        <div class="row">
          <button
            v-for="account in demoAccounts"
            :key="account.username"
            type="button"
            class="chip"
            @click="fill(account)"
          >
            {{ account.label }}
          </button>
        </div>
      </div>
    </form>
  </div>
</template>
