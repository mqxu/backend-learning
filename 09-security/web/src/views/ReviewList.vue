<script setup>
import { onMounted, ref } from 'vue'
import { allTasks, reviewTask } from '../api'
import { notify } from '../store/toast'

const tasks = ref([])
const editing = ref(null)
const form = ref({ score: 90, comment: '' })
const saving = ref(false)

async function load() {
  tasks.value = await allTasks()
}

onMounted(load)

function startReview(task) {
  editing.value = task
  form.value = { score: task.score ?? 90, comment: task.comment ?? '' }
}

async function onSubmit() {
  saving.value = true
  try {
    await reviewTask(editing.value.id, {
      score: Number(form.value.score),
      comment: form.value.comment,
    })
    notify('批改已保存，学生那边立刻能看到')
    editing.value = null
    await load()
  } finally {
    saving.value = false
  }
}

function formatTime(value) {
  return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : ''
}
</script>

<template>
  <div class="container">
    <div class="card">
      <h2>全部提交</h2>
      <p class="hint">接口是 /api/tasks，需要 task:review 权限点。学生调这个接口会拿到 403</p>

      <div v-if="!tasks.length" class="empty">还没有人提交</div>
      <table v-else>
        <thead>
          <tr>
            <th style="width: 46px">#</th>
            <th>标题</th>
            <th style="width: 96px">提交人</th>
            <th style="width: 90px">状态</th>
            <th style="width: 64px">分数</th>
            <th style="width: 150px">提交时间</th>
            <th style="width: 72px"></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="task in tasks" :key="task.id">
            <td class="mono">{{ task.id }}</td>
            <td>
              {{ task.title }}
              <div v-if="task.comment" class="muted" style="font-size: 12.5px">评语：{{ task.comment }}</div>
            </td>
            <td>{{ task.ownerNickname }}<div class="muted mono" style="font-size: 12px">{{ task.owner }}</div></td>
            <td>
              <span class="tag" :class="task.status === 'REVIEWED' ? 'tag-done' : 'tag-wait'">
                {{ task.statusText }}
              </span>
            </td>
            <td class="score">{{ task.score ?? '—' }}</td>
            <td class="mono">{{ formatTime(task.submittedAt) }}</td>
            <td>
              <button v-if="task.status !== 'REVIEWED'" class="btn btn-sm" @click="startReview(task)">批改</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-if="editing" class="card">
      <h2>批改：{{ editing.title }}</h2>
      <p class="hint">同一份不能批改两次，后端会返回业务码 30012</p>
      <form @submit.prevent="onSubmit">
        <div class="field" style="max-width: 160px">
          <label for="score">分数（0 到 100）</label>
          <input id="score" v-model="form.score" type="number" min="0" max="100" />
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
