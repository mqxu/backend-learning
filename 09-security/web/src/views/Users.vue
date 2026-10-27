<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { createUser, deleteUser, listUsers } from '../api'
import { notify } from '../store/toast'
import type { CreateUserPayload, RoleCode, UserProfile } from '../types/api'

/** 表单只让选一个角色，提交时再包成数组，所以不用 CreateUserPayload 的 roles 字段 */
interface UserForm {
  username: string
  password: string
  nickname: string
  role: RoleCode
}

type Filter = 'ALL' | RoleCode

const users = ref<UserProfile[]>([])
const form = reactive<UserForm>({
  username: '',
  password: 'Student#2026',
  nickname: '',
  role: 'STUDENT',
})
const creating = ref(false)
const filter = ref<Filter>('ALL')
const keyword = ref('')

const stats = computed(() => ({
  total: users.value.length,
  students: users.value.filter((user) => user.roles.includes('STUDENT')).length,
  teachers: users.value.filter((user) => user.roles.includes('TEACHER')).length,
  disabled: users.value.filter((user) => !user.enabled).length,
}))

const visible = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  return users.value.filter((user) => {
    if (filter.value !== 'ALL' && !user.roles.includes(filter.value)) return false
    if (!kw) return true
    return [user.username, user.nickname].some((text) => text.toLowerCase().includes(kw))
  })
})

async function load(): Promise<void> {
  users.value = await listUsers()
}

onMounted(load)

async function onCreate(): Promise<void> {
  creating.value = true
  try {
    const payload: CreateUserPayload = {
      username: form.username,
      password: form.password,
      nickname: form.nickname,
      roles: [form.role],
    }
    await createUser(payload)
    notify('账号已创建')
    form.username = ''
    form.nickname = ''
    form.password = 'Student#2026'
    form.role = 'STUDENT'
    await load()
  } finally {
    creating.value = false
  }
}

async function onDelete(user: UserProfile): Promise<void> {
  if (!window.confirm(`确定删除 ${user.username}？`)) return
  await deleteUser(user.username)
  notify('已删除')
  await load()
}
</script>

<template>
  <div class="container">
    <div class="stats">
      <div class="stat">
        <div class="k">账号总数</div>
        <div class="v">{{ stats.total }}<small>个</small></div>
      </div>
      <div class="stat">
        <div class="k">学生</div>
        <div class="v">{{ stats.students }}<small>个</small></div>
      </div>
      <div class="stat">
        <div class="k">教师</div>
        <div class="v">{{ stats.teachers }}<small>个</small></div>
      </div>
      <div class="stat amber">
        <div class="k">停用</div>
        <div class="v">{{ stats.disabled }}<small>个</small></div>
      </div>
    </div>

    <div class="grid">
      <div class="card">
        <h2>新增账号</h2>
        <p class="hint">只有 ADMIN 能调，教师访问 /admin/users 会拿到 403</p>
        <form @submit.prevent="onCreate">
          <div class="field">
            <label for="username">用户名</label>
            <input id="username" v-model.trim="form.username" placeholder="student11" />
          </div>
          <div class="field">
            <label for="nickname">姓名</label>
            <input id="nickname" v-model.trim="form.nickname" placeholder="马嘉懿" />
          </div>
          <div class="field">
            <label for="password">初始密码</label>
            <input id="password" v-model.trim="form.password" />
          </div>
          <div class="field">
            <label for="role">角色</label>
            <select id="role" v-model="form.role">
              <option value="STUDENT">学生 STUDENT</option>
              <option value="TEACHER">教师 TEACHER</option>
              <option value="ADMIN">管理员 ADMIN</option>
            </select>
          </div>
          <button class="btn btn-primary" type="submit" :disabled="creating">
            {{ creating ? '创建中…' : '创建' }}
          </button>
        </form>
      </div>

      <div class="card">
        <h2>账号列表</h2>
        <p class="hint">响应里没有密码字段，出参对象压根不带它</p>

        <div class="toolbar">
          <div class="tabs">
            <button :class="{ on: filter === 'ALL' }" @click="filter = 'ALL'">
              全部<span class="n">{{ stats.total }}</span>
            </button>
            <button :class="{ on: filter === 'STUDENT' }" @click="filter = 'STUDENT'">
              学生<span class="n">{{ stats.students }}</span>
            </button>
            <button :class="{ on: filter === 'TEACHER' }" @click="filter = 'TEACHER'">
              教师<span class="n">{{ stats.teachers }}</span>
            </button>
            <button :class="{ on: filter === 'ADMIN' }" @click="filter = 'ADMIN'">管理员</button>
          </div>
          <input v-model="keyword" class="search" placeholder="按用户名或姓名搜" />
          <span class="count">筛出 {{ visible.length }} 个</span>
        </div>

        <div v-if="!visible.length" class="empty">没有符合条件的账号</div>
        <table v-else>
          <thead>
            <tr>
              <th>用户名</th>
              <th>姓名</th>
              <th>角色</th>
              <th>状态</th>
              <th style="width: 64px"></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="user in visible" :key="user.username">
              <td class="mono">{{ user.username }}</td>
              <td>{{ user.nickname }}</td>
              <td>{{ user.roles.join(' / ') }}</td>
              <td>
                <span class="tag" :class="user.enabled ? 'tag-done' : 'tag-wait'">
                  {{ user.enabled ? '启用' : '停用' }}
                </span>
              </td>
              <td><button class="btn btn-sm btn-danger" @click="onDelete(user)">删除</button></td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>

<style scoped>
select {
  width: 100%;
  padding: 8px 11px;
  border: 1px solid var(--border);
  border-radius: 8px;
  background: #fff;
  color: var(--text);
  font-family: inherit;
  font-size: inherit;
  outline: none;
}
select:focus {
  border-color: var(--primary);
  box-shadow: 0 0 0 3px rgba(47, 92, 246, 0.1);
}
</style>
