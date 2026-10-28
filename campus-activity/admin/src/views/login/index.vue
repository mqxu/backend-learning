<template>
  <div class="login-page">
    <div class="login-card">
      <div class="login-head">
        <el-icon :size="28" color="#409eff"><Calendar /></el-icon>
        <h1>校园活动管理后台</h1>
        <p>请使用管理员账号登录</p>
      </div>

      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent>
        <el-form-item label="账号" prop="username">
          <el-input
            v-model="form.username"
            placeholder="请输入账号"
            size="large"
            clearable
            :prefix-icon="User"
            @keyup.enter="handleLogin"
          />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="请输入密码"
            size="large"
            show-password
            :prefix-icon="Lock"
            @keyup.enter="handleLogin"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" size="large" class="login-btn" :loading="loading" @click="handleLogin">
            登录
          </el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Lock, User } from '@element-plus/icons-vue'
import { login } from '@/api'
import { setAuth } from '@/api/request'

const route = useRoute()
const router = useRouter()
const formRef = ref<FormInstance>()
const loading = ref(false)

const form = reactive<{ username: string; password: string }>({
  username: 'admin',
  password: 'Admin@123'
})

const rules: FormRules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function handleLogin(): Promise<void> {
  if (loading.value) return
  try {
    await formRef.value?.validate()
  } catch {
    return
  }
  loading.value = true
  try {
    const data = await login({ username: form.username, password: form.password })
    setAuth(data.token, data.user)
    ElMessage.success('登录成功')
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/dashboard'
    router.replace(redirect)
  } catch {
    // 错误提示已由请求拦截器统一处理
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #eef2f8 0%, #dbe6f6 100%);
}

.login-card {
  width: 380px;
  padding: 32px 32px 12px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 8px 30px rgba(31, 45, 61, 0.12);
}

.login-head {
  text-align: center;
  margin-bottom: 24px;
}

.login-head h1 {
  margin: 12px 0 6px;
  font-size: 20px;
  color: #303133;
}

.login-head p {
  margin: 0;
  font-size: 13px;
  color: #909399;
}

.login-btn {
  width: 100%;
}
</style>
