<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { myTasks, submitTask, taskDetail } from '../api'
import type { SubmitTaskPayload, TaskStatusCode, TaskView } from '../types/api'

type Filter = 'ALL' | TaskStatusCode

const tasks = ref<TaskView[]>([])
const form = reactive<SubmitTaskPayload>({ title: '', content: '' })
const submitting = ref(false)
const detail = ref<TaskView | null>(null)
const filter = ref<Filter>('ALL')

/**
 * 统计都在前端算。列表接口本来就返回全量，再单独做一个统计接口没有意义；
 * 等真上了分页，统计就得换后端聚合，这里写注释是提醒这一点。
 */
const stats = computed(() => {
  const reviewed = tasks.value.filter((task) => task.status === 'REVIEWED')
  const total = reviewed.reduce((acc, task) => acc + (task.score ?? 0), 0)
  return {
    total: tasks.value.length,
    pending: tasks.value.filter((task) => task.status === 'SUBMITTED').length,
    reviewed: reviewed.length,
    average: reviewed.length ? (total / reviewed.length).toFixed(1) : '—',
  }
})

const visible = computed(() =>
  filter.value === 'ALL' ? tasks.value : tasks.value.filter((task) => task.status === filter.value),
)

async function load(): Promise<void> {
  tasks.value = await myTasks()
}

onMounted(load)

async function onSubmit(): Promise<void> {
  if (!form.title || !form.content) return
  submitting.value = true
  try {
    await submitTask({ title: form.title, content: form.content })
    form.title = ''
    form.content = ''
    await load()
  } finally {
    submitting.value = false
  }
}

async function openDetail(task: TaskView): Promise<void> {
  detail.value = await taskDetail(task.id)
}

function formatTime(value: string | null): string {
  return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : ''
}
</script>

<template>
  <div class="container">
    <div class="stats">
      <div class="stat">
        <div class="k">已提交</div>
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
        <div class="k">平均分</div>
        <div class="v">{{ stats.average }}<small v-if="stats.reviewed">分</small></div>
      </div>
    </div>

    <div class="grid">
      <div>
        <div class="card">
          <h2>提交实训任务</h2>
          <p class="hint">提交人由后端按令牌写入，请求里没有用户名字段</p>
          <form @submit.prevent="onSubmit">
            <div class="field">
              <label for="title">任务标题</label>
              <input id="title" v-model.trim="form.title" placeholder="实训六：分页查询与条件筛选" />
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
            <span class="count">共 {{ visible.length }} 条</span>
          </div>

          <div v-if="!visible.length" class="empty">
            {{ tasks.length ? '这个状态下没有记录' : '还没有提交过任务' }}
          </div>
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
              <tr v-for="task in visible" :key="task.id">
                <td>
                  {{ task.title }}
                  <div v-if="task.comment" class="muted clip" style="font-size: 12.5px">
                    评语：{{ task.comment }}
                  </div>
                </td>
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
          <p class="hint">
            {{ detail.ownerNickname }} · 提交于 {{ formatTime(detail.submittedAt) }}
            <template v-if="detail.reviewedAt"> · 批改于 {{ formatTime(detail.reviewedAt) }}</template>
          </p>
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
.sub-card div + div {
  margin-top: 4px;
}
</style>
