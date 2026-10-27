<script setup>
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { login } from '../api'
import { saveLogin } from '../store/auth'
import { homeOf } from '../router'

const router = useRouter()
const route = useRoute()

const form = ref({ username: 'student01', password: 'student123' })
const loading = ref(false)
const error = ref('')

const demoAccounts = [
  { label: '学生 student01', username: 'student01', password: 'student123' },
  { label: '教师 teacher01', username: 'teacher01', password: 'teacher123' },
  { label: '管理员 admin', username: 'admin', password: 'admin123' },
  { label: '停用 guest', username: 'guest', password: 'guest123' },
]

function fill(account) {
  form.value = { username: account.username, password: account.password }
  error.value = ''
}

async function onSubmit() {
  error.value = ''
  loading.value = true
  try {
    const data = await login(form.value)
    // 令牌和身份落库，后面每个请求由拦截器自动带上
    saveLogin(data)
    router.replace(route.query.redirect || homeOf())
  } catch (e) {
    // 密码错、账号停用这类属于业务失败，后端返回 200 + 非 200 的 code，
    // 拦截器已经提示过一次，这里把原因显示在表单上方
    error.value = e.message || '登录失败'
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
        <input id="password" v-model="form.password" type="password" autocomplete="current-password" placeholder="student123" />
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
