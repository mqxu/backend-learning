<script setup>
import { onMounted, ref } from 'vue'
import { myTasks, submitTask, taskDetail } from '../api'

const tasks = ref([])
const form = ref({ title: '', content: '' })
const submitting = ref(false)
const detail = ref(null)

async function load() {
  tasks.value = await myTasks()
}

onMounted(load)

async function onSubmit() {
  if (!form.value.title || !form.value.content) return
  submitting.value = true
  try {
    await submitTask(form.value)
    form.value = { title: '', content: '' }
    await load()
  } finally {
    submitting.value = false
  }
}

async function openDetail(task) {
  detail.value = await taskDetail(task.id)
}

function formatTime(value) {
  return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : ''
}
</script>

<template>
  <div class="container">
    <div class="grid">
      <div>
        <div class="card">
          <h2>提交实训任务</h2>
          <p class="hint">提交人由后端按令牌写入，请求里没有用户名字段</p>
          <form @submit.prevent="onSubmit">
            <div class="field">
              <label for="title">任务标题</label>
              <input id="title" v-model.trim="form.title" placeholder="实训三：接口鉴权改造" />
            </div>
            <div class="field">
              <label for="content">完成情况</label>
              <textarea id="content" v-model.trim="form.content" placeholder="做了哪些、卡在哪里"></textarea>
            </div>
            <button class="btn btn-primary" type="submit" :disabled="submitting">
              {{ submitting ? '提交中…' : '提交' }}
            </button>
          </form>
        </div>
      </div>

      <div>
        <div class="card">
          <h2>我的提交</h2>
          <p class="hint">接口是 /api/tasks/mine，URL 里没有用户名，改也改不出别人的数据</p>

          <div v-if="!tasks.length" class="empty">还没有提交过任务</div>
          <table v-else>
            <thead>
              <tr>
                <th>标题</th>
                <th style="width: 90px">状态</th>
                <th style="width: 64px">分数</th>
                <th style="width: 150px">提交时间</th>
                <th style="width: 60px"></th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="task in tasks" :key="task.id">
                <td>{{ task.title }}</td>
                <td>
                  <span class="tag" :class="task.status === 'REVIEWED' ? 'tag-done' : 'tag-wait'">
                    {{ task.statusText }}
                  </span>
                </td>
                <td class="score">{{ task.score ?? '—' }}</td>
                <td class="mono">{{ formatTime(task.submittedAt) }}</td>
                <td><button class="btn btn-sm" @click="openDetail(task)">查看</button></td>
              </tr>
            </tbody>
          </table>
        </div>

        <div v-if="detail" class="card">
          <h2>{{ detail.title }}</h2>
          <p class="hint">{{ detail.ownerNickname }} · {{ formatTime(detail.submittedAt) }}</p>
          <p>{{ detail.content }}</p>
          <div v-if="detail.status === 'REVIEWED'" class="sub-card">
            <div><b>得分</b> <span class="score">{{ detail.score }}</span></div>
            <div><b>评语</b> {{ detail.comment || '教师未填写评语' }}</div>
          </div>
          <p v-else class="muted">教师还没批改</p>
          <button class="btn btn-sm" @click="detail = null">收起</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.sub-card {
  border: 1px solid var(--green-border);
  background: var(--green-soft);
  border-radius: 8px;
  padding: 10px 14px;
  margin-bottom: 12px;
  font-size: 13.5px;
}
.sub-card div + div { margin-top: 4px; }
</style>
