<template>
  <div class="home-container">
    <el-container>
      <el-header class="header">
        <div class="header-content">
          <h2 class="title">欢迎来到首页</h2>
          <div class="user-info">
            <el-button type="primary" @click="logout">
              <el-icon>
                <SwitchButton/>
              </el-icon>
              退出登录
            </el-button>
          </div>
        </div>
      </el-header>

      <el-main class="main-content">
        <el-card class="welcome-card">
          <template #header>
            <div class="card-header">
              <el-icon>
                <User/>
              </el-icon>
              <span>用户信息</span>
            </div>
          </template>
          <div class="user-profile">
            <el-descriptions :column="1" size="large">
              <el-descriptions-item label="手机号">
                {{ userPhone }}
              </el-descriptions-item>
              <el-descriptions-item label="登录时间">
                {{ loginTime }}
              </el-descriptions-item>
              <el-descriptions-item label="Token状态">
                <el-tag :type="verified ? 'success' : 'info'">
                  {{ verified ? "已认证" : "校验中" }}
                </el-tag>
              </el-descriptions-item>
            </el-descriptions>
          </div>
        </el-card>

        <el-card class="feature-card">
          <template #header>
            <div class="card-header">
              <el-icon>
                <Setting/>
              </el-icon>
              <span>功能区域</span>
            </div>
          </template>
          <el-empty description="功能开发中..."/>
        </el-card>
      </el-main>
    </el-container>
  </div>
</template>

<script setup lang="ts">
import {onMounted, ref} from "vue";
import {useRouter} from "vue-router";
import {ElMessage} from "element-plus";
import {Setting, SwitchButton, User} from "@element-plus/icons-vue";
import {authApi} from "@/api/auth";
import {clearToken} from "@/utils/auth";

const router = useRouter();
const userPhone = ref("");
const loginTime = ref("");
const verified = ref(false);

onMounted(async () => {
  try {
    // 手机号与登录时间都取自 token 对应的服务端数据，不再读 localStorage
    const {data} = await authApi.me();
    userPhone.value = data.phone;
    loginTime.value = data.loginTime;
    verified.value = true;
  } catch {
    // token 缺失、被改过或者已经过期，清掉本地存储回登录页
    clearToken();
    router.replace("/login");
  }
});

const logout = () => {
  clearToken();
  ElMessage.success("退出登录成功");
  // 跳转到登录页
  router.push("/login");
};
</script>

<style scoped>
.home-container {
  width: 100%;
  height: 100vh;
  background: linear-gradient(135deg, #f5f7fa 0%, #c3cfe2 100%);
}

.header {
  background: linear-gradient(90deg, #60cddb 0%, #cbe9ed 100%);
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.1);
}

.header-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
  height: 100%;
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 20px;
}

.title {
  color: white;
  margin: 0;
  font-size: 24px;
  font-weight: 600;
}

.user-info {
  display: flex;
  align-items: center;
}

.main-content {
  width: 100%;
  padding: 20px;
}

.welcome-card,
.feature-card {
  margin-bottom: 20px;
  border-radius: 12px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.1);
  width: 100%;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 18px;
  font-weight: 600;
  color: #409eff;
}

.card-header .el-icon {
  font-size: 20px;
}

.user-profile {
  padding: 20px 0;
}

:deep(.el-descriptions__label) {
  font-weight: 600;
  color: #606266;
}

:deep(.el-descriptions__content) {
  color: #303133;
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

:deep(.el-card__header) {
  background: linear-gradient(135deg, #f8f9fa 0%, #e9ecef 100%);
  border-bottom: 1px solid #e4e7ed;
}
</style>
