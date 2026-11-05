<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { allTasks, reviewTask } from '../api'
import { notify } from '../store/toast'
import type { ReviewTaskPayload, TaskStatusCode, TaskView } from '../types/api'

type Filter = 'ALL' | TaskStatusCode

const tasks = ref<TaskView[]>([])
const editing = ref<TaskView | null>(null)
const form = reactive<ReviewTaskPayload>({ score: 90, comment: '' })
const saving = ref(false)
const filter = ref<Filter>('ALL')
const keyword = ref('')

const stats = computed(() => {
  const reviewed = tasks.value.filter((task) => task.status === 'REVIEWED')
  const total = reviewed.reduce((acc, task) => acc + (task.score ?? 0), 0)
  return {
    total: tasks.value.length,
    pending: tasks.value.filter((task) => task.status === 'SUBMITTED').length,
    reviewed: reviewed.length,
    average: reviewed.length ? (total / reviewed.length).toFixed(1) : '—',
    students: new Set(tasks.value.map((task) => task.owner)).size,
  }
})

/** 筛选和搜索都在本地做，列表接口一次给全量；接分页时这两步要改成后端参数 */
const visible = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  return tasks.value.filter((task) => {
    if (filter.value !== 'ALL' && task.status !== filter.value) return false
    if (!kw) return true
    return [task.title, task.owner, task.ownerNickname].some((text) => text.toLowerCase().includes(kw))
  })
})

async function load(): Promise<void> {
  tasks.value = await allTasks()
}

onMounted(load)

function startReview(task: TaskView): void {
  editing.value = task
  form.score = task.score ?? 90
  form.comment = task.comment ?? ''
}

async function onSubmit(): Promise<void> {
  if (!editing.value) return
  saving.value = true
  try {
    await reviewTask(editing.value.id, { score: Number(form.score), comment: form.comment })
    notify('批改已保存，学生那边立刻能看到')
    editing.value = null
    await load()
  } finally {
    saving.value = false
  }
}

function formatTime(value: string | null): string {
  return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : ''
}
</script>

<template>
  <div class="container">
    <div class="stats">
      <div class="stat">
        <div class="k">全部提交</div>
        <div class="v">{{ stats.total }}<small>份</small></div>
      </div>
      <div class="stat amber">
        <div class="k">待批改</div>
        <div class="v">{{ stats.pending }}<small>份</small></div>
      </div>
      <div class="stat green">
        <div class="k">已批改</div>
        <div class="v">{{ stats.reviewed }}<small>份</small></div>
      </div>
      <div class="stat">
        <div class="k">平均分 · {{ stats.students }} 人提交</div>
        <div class="v">{{ stats.average }}<small v-if="stats.reviewed">分</small></div>
      </div>
    </div>

    <div class="card">
      <h2>全部提交</h2>
      <p class="hint">接口是 /api/tasks，需要 task:review 权限点。学生调这个接口会拿到 403</p>

      <div class="toolbar">
        <div class="tabs">
          <button :class="{ on: filter === 'ALL' }" @click="filter = 'ALL'">
            全部<span class="n">{{ stats.total }}</span>
          </button>
          <button :class="{ on: filter === 'SUBMITTED' }" @click="filter = 'SUBMITTED'">
            待批改<span class="n">{{ stats.pending }}</span>
          </button>
          <button :class="{ on: filter === 'REVIEWED' }" @click="filter = 'REVIEWED'">
            已批改<span class="n">{{ stats.reviewed }}</span>
          </button>
        </div>
        <input v-model="keyword" class="search" placeholder="按姓名、账号或标题搜" />
        <span class="count">筛出 {{ visible.length }} 条</span>
      </div>

      <div v-if="!visible.length" class="empty">
        {{ tasks.length ? '没有符合条件的记录' : '还没有人提交' }}
      </div>
      <table v-else>
        <thead>
          <tr>
            <th style="width: 46px">#</th>
            <th>标题</th>
            <th style="width: 96px">提交人</th>
            <th style="width: 90px">状态</th>
            <th style="width: 64px">分数</th>
            <th style="width: 150px">提交时间</th>
            <th style="width: 76px"></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="task in visible" :key="task.id">
            <td class="mono">{{ task.id }}</td>
            <td>
              {{ task.title }}
              <div v-if="task.comment" class="muted clip" style="font-size: 12.5px">评语：{{ task.comment }}</div>
            </td>
            <td>
              {{ task.ownerNickname }}
              <div class="muted mono" style="font-size: 12px">{{ task.owner }}</div>
            </td>
            <td>
              <span class="tag" :class="task.status === 'REVIEWED' ? 'tag-done' : 'tag-wait'">
                {{ task.statusText }}
              </span>
            </td>
            <td class="score">{{ task.score ?? '—' }}</td>
            <td class="mono">{{ formatTime(task.submittedAt) }}</td>
            <td>
              <button v-if="task.status !== 'REVIEWED'" class="btn btn-sm" @click="startReview(task)">
                批改
              </button>
              <span v-else class="muted" style="font-size: 12.5px">已批</span>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-if="editing" class="card">
      <h2>批改：{{ editing.title }}</h2>
      <p class="hint">
        {{ editing.ownerNickname }}（{{ editing.owner }}）· 提交于 {{ formatTime(editing.submittedAt) }}
        · 同一份不能批改两次，后端会返回业务码 30012
      </p>
      <p class="submitted">{{ editing.content }}</p>
      <form @submit.prevent="onSubmit">
        <div class="field" style="max-width: 160px">
          <label for="score">分数（0 到 100）</label>
          <input id="score" v-model.number="form.score" type="number" min="0" max="100" />
        </div>
        <div class="field">
          <label for="comment">评语</label>
          <textarea id="comment" v-model.trim="form.comment" placeholder="写清哪里对、哪里要改"></textarea>
        </div>
        <button class="btn btn-primary" type="submit" :disabled="saving">
          {{ saving ? '保存中…' : '保存批改' }}
        </button>
        <button class="btn" type="button" style="margin-left: 8px" @click="editing = null">取消</button>
      </form>
    </div>
  </div>
</template>

<style scoped>
.submitted {
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 10px 14px;
  font-size: 13.5px;
  color: var(--text-2);
  margin: 0 0 14px;
}
</style>
