<template>
  <div class="login-container">
    <el-card class="login-card">
      <template #header>
        <div class="card-header">
          <el-icon>
            <Iphone/>
          </el-icon>
          <span class="title">短信登录</span>
        </div>
      </template>

      <el-form
          @submit.prevent="login"
          :model="formData"
          ref="loginFormRef"
          :rules="rules"
      >
        <el-form-item prop="phone">
          <el-input
              v-model="phone"
              placeholder="请输入手机号"
              size="large"
              :prefix-icon="Iphone"
              maxlength="11"
              show-word-limit
          />
        </el-form-item>

        <el-form-item>
          <el-button
              type="primary"
              @click="sendSms"
              :disabled="isCountingDown"
              :loading="isSendingSms"
              size="large"
              style="width: 100%"
          >
            <el-icon>
              <Message/>
            </el-icon>
            {{ isCountingDown ? `${countdown}秒后重发` : '发送验证码' }}
          </el-button>
        </el-form-item>

        <div v-show="isCodeSent">
          <el-form-item prop="code">
            <el-input
                v-model="code"
                placeholder="请输入验证码"
                size="large"
                :prefix-icon="Lock"
                maxlength="4"
                show-word-limit
            />
          </el-form-item>

          <el-form-item>
            <el-button
                type="success"
                @click="login"
                :loading="isLoggingIn"
                size="large"
                style="width: 100%"
            >
              <el-icon>
                <Check/>
              </el-icon>
              登录
            </el-button>
          </el-form-item>
        </div>

        <el-alert
            v-if="errorMessage"
            :title="errorMessage"
            type="error"
            :closable="false"
            show-icon
        />

        <el-alert
            v-if="successMessage"
            :title="successMessage"
            type="success"
            :closable="false"
            show-icon
        />
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import {onUnmounted, reactive, ref} from 'vue';
import {useRouter} from 'vue-router';
import {ElMessage, type FormInstance, type FormRules} from 'element-plus';
import {Check, Iphone, Lock, Message} from '@element-plus/icons-vue';
import {CODE_TTL_MINUTES, SEND_INTERVAL_SECONDS} from '@/config';
import {saveToken} from '@/utils/auth';
import type {LoginRequest} from '@/types/api';
import {authApi} from '@/api/auth';

const router = useRouter();

const phone = ref("13951905171");
const code = ref("");
const errorMessage = ref("");
const successMessage = ref("");
const isCountingDown = ref(false);
const countdown = ref(SEND_INTERVAL_SECONDS);
const isSendingSms = ref(false);
const isLoggingIn = ref(false);
const isCodeSent = ref(false);
let timer: ReturnType<typeof setInterval> | undefined;

const loginFormRef = ref<FormInstance>();

const formData = reactive({
  phone,
  code,
});

const rules: FormRules = {
  phone: [
    {required: true, message: "请输入手机号", trigger: "blur"},
    {
      pattern: /^1[3-9]\d{9}$/,
      message: "请输入正确的手机号",
      trigger: "blur",
    },
  ],
  code: [
    {required: true, message: "请输入验证码", trigger: "blur"},
    {len: 4, message: "验证码长度为4位", trigger: "blur"},
  ],
};

const sendSms = async () => {
  if (!loginFormRef.value || isCountingDown.value) return;

  const valid = await loginFormRef.value
      .validateField("phone")
      .catch(() => false);
  if (!valid) return;

  errorMessage.value = "";
  successMessage.value = "";
  isSendingSms.value = true;

  try {
    await authApi.sendSms(phone.value);
    successMessage.value = `验证码已发送，${CODE_TTL_MINUTES} 分钟内有效`;
    ElMessage.success("验证码已发送，请查收！");
    isCodeSent.value = true;
    startCountdown();
  } catch (error) {
    // 响应拦截器把后端的 message 包成了 Error，这里直接用
    errorMessage.value = (error as Error).message || "发送失败，请重试。";
  } finally {
    isSendingSms.value = false;
  }
};

const login = async () => {
  if (!loginFormRef.value) return;

  const valid = await loginFormRef.value.validate().catch(() => false);
  if (!valid) return;

  errorMessage.value = "";
  successMessage.value = "";
  isLoggingIn.value = true;

  try {
    const loginData: LoginRequest = {
      phone: phone.value,
      code: code.value,
    };
    const {data} = await authApi.login(loginData);

    saveToken(data.token);
    successMessage.value = data.message;
    ElMessage.success(data.message);
    // 跳转到首页
    router.push("/home");
  } catch (error) {
    errorMessage.value = (error as Error).message || "登录失败，请重试。";
  } finally {
    isLoggingIn.value = false;
  }
};

const startCountdown = () => {
  clearInterval(timer);
  isCountingDown.value = true;
  countdown.value = SEND_INTERVAL_SECONDS;
  timer = setInterval(() => {
    countdown.value -= 1;
    if (countdown.value <= 0) {
      clearInterval(timer);
      timer = undefined;
      isCountingDown.value = false;
    }
  }, 1000);
};

// 用户中途离开页面时把定时器清掉，避免它继续跑
onUnmounted(() => {
  clearInterval(timer);
});
</script>

<style scoped>
.login-container {
  display: flex;
  min-height: 100vh;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #cbe9ed 0%, #60cddb 100%);
  padding: 20px;
}

.login-card {
  width: 100%;
  max-width: 400px;
  border-radius: 15px;
  box-shadow: 0 15px 35px rgba(0, 0, 0, 0.1);
  overflow: hidden;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  font-size: 20px;
  font-weight: 600;
  color: #409eff;
}

.card-header .el-icon {
  font-size: 24px;
}

.title {
  background: linear-gradient(45deg, #409eff, #67c23a);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

:deep(.el-form-item) {
  margin-bottom: 20px;
}

:deep(.el-input__wrapper) {
  border-radius: 8px;
}

:deep(.el-button) {
  border-radius: 8px;
  font-weight: 500;
  transition: all 0.3s ease;
}

:deep(.el-button:hover) {
  transform: translateY(-1px);
  box-shadow: 0 6px 20px rgba(0, 0, 0, 0.15);
}

:deep(.el-alert) {
  border-radius: 8px;
  margin-top: 10px;
}

:deep(.el-card__header) {
  background: linear-gradient(135deg, #f5f7fa 0%, #c3cfe2 100%);
  border-bottom: 1px solid #e4e7ed;
  padding: 20px;
}

:deep(.el-card__body) {
  padding: 30px;
}
</style>
