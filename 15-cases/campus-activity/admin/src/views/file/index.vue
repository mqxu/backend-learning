<template>
  <div class="page-container">
    <div class="page-card storage-card">
      <div class="card-head">
        <span class="card-title">当前存储方式</span>
        <el-button class="refresh-btn" :icon="Refresh" size="small" :loading="storageLoading" @click="loadStorage">
          刷新
        </el-button>
      </div>

      <el-descriptions v-loading="storageLoading" :column="2" border>
        <el-descriptions-item label="存储方式">
          <el-tag size="small" :type="isLocal ? 'success' : 'warning'">{{ storageLabel }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="单文件上限">{{ storage?.maxFileSize || '-' }}</el-descriptions-item>
        <el-descriptions-item label="允许类型" :span="2">
          <div v-if="allowedExtensions.length" class="ext-list">
            <el-tag v-for="ext in allowedExtensions" :key="ext" size="small" type="info" effect="plain">
              {{ ext }}
            </el-tag>
          </div>
          <span v-else class="text-muted">-</span>
        </el-descriptions-item>
        <template v-if="isLocal">
          <el-descriptions-item label="落盘目录">{{ storage?.baseDir || '-' }}</el-descriptions-item>
          <el-descriptions-item label="访问前缀">{{ storage?.urlPrefix || '-' }}</el-descriptions-item>
        </template>
        <template v-else-if="storage">
          <el-descriptions-item label="Bucket">{{ storage.bucket || '-' }}</el-descriptions-item>
          <el-descriptions-item label="目录">{{ storage.dir || '-' }}</el-descriptions-item>
        </template>
      </el-descriptions>

      <el-alert
        v-if="isLocal"
        class="storage-tip"
        type="info"
        :closable="false"
        show-icon
        title="本地存储返回 /uploads/... 相对地址，开发环境由 Vite 的 /uploads 代理转发到后端"
      />
    </div>

    <div class="page-card upload-card">
      <div class="card-head">
        <span class="card-title">素材上传</span>
        <div class="biz-selector">
          <span class="text-muted">业务类型</span>
          <el-select v-model="bizType" size="small" style="width: 150px" :disabled="uploading">
            <el-option v-for="item in BIZ_TYPE_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </div>
      </div>

      <el-upload
        drag
        class="uploader"
        :show-file-list="false"
        :accept="accept"
        :disabled="uploading"
        :before-upload="beforeUpload"
        :http-request="handleUpload"
      >
        <el-icon class="uploader-icon" :size="44"><UploadFilled /></el-icon>
        <div class="uploader-text">将文件拖到此处，或<em>点击选择</em></div>
        <div class="uploader-tip">单文件上传，大小不超过 {{ storage?.maxFileSize || '10MB' }}</div>
      </el-upload>

      <div v-if="uploading" class="upload-status">
        <el-icon class="is-loading"><Loading /></el-icon>
        <span>正在上传…</span>
      </div>

      <div v-else-if="result" class="result-card">
        <div class="result-preview">
          <el-image
            v-if="isImage"
            :src="result.url"
            :preview-src-list="[result.url]"
            fit="cover"
            class="result-image"
            preview-teleported
          >
            <template #error>
              <div class="image-error">图片加载失败</div>
            </template>
          </el-image>
          <div v-else class="file-placeholder">
            <el-icon :size="34"><Document /></el-icon>
          </div>
        </div>

        <div class="result-info">
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="文件名">{{ result.originalName }}</el-descriptions-item>
            <el-descriptions-item label="大小">{{ fmtSize(result.size) }}</el-descriptions-item>
            <el-descriptions-item label="类型">{{ result.contentType || '-' }}</el-descriptions-item>
            <el-descriptions-item label="存储方式">
              <el-tag size="small" type="info" effect="plain">{{ storageTypeLabel(result.storage) }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="访问地址">
              <span class="result-url">{{ result.url }}</span>
            </el-descriptions-item>
          </el-descriptions>

          <div class="result-actions">
            <el-button type="primary" size="small" :icon="CopyDocument" @click="copyUrl">复制地址</el-button>
            <el-link type="primary" :href="result.url" target="_blank">在新窗口打开</el-link>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { UploadRawFile, UploadRequestOptions } from 'element-plus'
import { CopyDocument, Document, Loading, Refresh, UploadFilled } from '@element-plus/icons-vue'
import { fetchStorageInfo, uploadFile } from '@/api'
import type { FileUploadResult, StorageInfo } from '@/types/api'

interface BizTypeOption {
  value: string
  label: string
}

const BIZ_TYPE_OPTIONS: BizTypeOption[] = [
  { value: 'common', label: '通用素材' },
  { value: 'activity-cover', label: '活动封面' },
  { value: 'org-logo', label: '组织 Logo' },
  { value: 'avatar', label: '用户头像' }
]

const storage = ref<StorageInfo | null>(null)
const storageLoading = ref(false)
const uploading = ref(false)
const bizType = ref('common')
const result = ref<FileUploadResult | null>(null)

const allowedExtensions = computed<string[]>(() => storage.value?.allowedExtensions ?? [])
const isLocal = computed<boolean>(() => storage.value?.storage === 'local')
const storageLabel = computed<string>(() => storageTypeLabel(storage.value?.storage))
const accept = computed<string>(() => allowedExtensions.value.map((ext) => `.${ext}`).join(','))
const isImage = computed<boolean>(() => {
  if (!result.value) return false
  if (result.value.contentType?.startsWith('image/')) return true
  return /\.(png|jpe?g|gif|webp|bmp|svg)$/i.test(result.value.url)
})

function storageTypeLabel(type?: string | null): string {
  if (type === 'local') return '本地磁盘 (local)'
  if (type === 'oss') return '阿里云 OSS (oss)'
  return type || '-'
}

/** 把后端的 "10MB" 这类上限换算成字节数，解析失败返回 0 表示不校验 */
function maxSizeBytes(limit?: string | null): number {
  if (!limit) return 0
  const matched = /^([\d.]+)\s*(B|KB|MB|GB)$/i.exec(limit.trim())
  if (!matched) return 0
  const value = Number(matched[1])
  if (!Number.isFinite(value)) return 0
  const unit = matched[2].toUpperCase()
  const factor = unit === 'GB' ? 1024 ** 3 : unit === 'MB' ? 1024 ** 2 : unit === 'KB' ? 1024 : 1
  return value * factor
}

function fmtSize(size: number): string {
  if (!size || size < 0) return '-'
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(2)} MB`
}

async function loadStorage(): Promise<void> {
  storageLoading.value = true
  try {
    storage.value = await fetchStorageInfo()
  } catch {
    // 错误提示已由请求拦截器统一处理
    storage.value = null
  } finally {
    storageLoading.value = false
  }
}

/** 选择文件后先按后端的约束做一次本地校验，避免白传一次 */
function beforeUpload(rawFile: UploadRawFile): boolean {
  const limit = maxSizeBytes(storage.value?.maxFileSize)
  if (limit > 0 && rawFile.size > limit) {
    ElMessage.error(`文件大小不能超过 ${storage.value?.maxFileSize || ''}`)
    return false
  }
  if (allowedExtensions.value.length) {
    const ext = rawFile.name.split('.').pop()?.toLowerCase() || ''
    if (!allowedExtensions.value.includes(ext)) {
      ElMessage.error(`仅支持 ${allowedExtensions.value.join(' / ')} 格式`)
      return false
    }
  }
  return true
}

/** el-upload 自定义上传：FormData 由 api/uploadFile 组装，axios 自动生成 multipart boundary */
async function handleUpload(options: UploadRequestOptions): Promise<void> {
  uploading.value = true
  result.value = null
  try {
    result.value = await uploadFile(options.file, bizType.value)
    ElMessage.success('上传成功')
  } catch {
    // 失败提示已由请求拦截器统一处理，这里不再重复提示
  } finally {
    uploading.value = false
  }
}

/** clipboard 不可用时（非安全上下文）降级到 execCommand */
function fallbackCopy(text: string): boolean {
  const textarea = document.createElement('textarea')
  textarea.value = text
  textarea.style.position = 'fixed'
  textarea.style.opacity = '0'
  document.body.appendChild(textarea)
  textarea.select()
  let copied = false
  try {
    copied = document.execCommand('copy')
  } catch {
    copied = false
  }
  document.body.removeChild(textarea)
  return copied
}

async function copyUrl(): Promise<void> {
  const url = result.value?.url
  if (!url) return
  try {
    if (!navigator.clipboard) throw new Error('clipboard unavailable')
    await navigator.clipboard.writeText(url)
    ElMessage.success('地址已复制')
  } catch {
    if (fallbackCopy(url)) {
      ElMessage.success('地址已复制')
    } else {
      ElMessage.error('复制失败，请手动复制地址')
    }
  }
}

onMounted(loadStorage)
</script>

<style scoped>
.storage-card {
  margin-bottom: 16px;
}

.card-head {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.card-title {
  font-size: 15px;
  font-weight: 600;
}

.refresh-btn {
  margin-left: auto;
}

.ext-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.storage-tip {
  margin-top: 12px;
}

.biz-selector {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-left: auto;
}

.uploader {
  max-width: 560px;
}

.uploader :deep(.el-upload-dragger) {
  padding: 32px 20px;
}

.uploader-icon {
  color: #c0c4cc;
}

.uploader-text {
  margin-top: 8px;
  font-size: 14px;
  color: #606266;
}

.uploader-text em {
  color: #409eff;
  font-style: normal;
}

.uploader-tip {
  margin-top: 6px;
  font-size: 12px;
  color: #909399;
}

.upload-status {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 18px;
  color: #909399;
}

.result-card {
  display: flex;
  align-items: flex-start;
  gap: 20px;
  margin-top: 20px;
  padding-top: 20px;
  border-top: 1px dashed #e4e7ed;
}

.result-preview {
  flex: none;
}

.result-image,
.file-placeholder {
  width: 140px;
  height: 140px;
  border: 1px solid #ebeef5;
  border-radius: 4px;
}

.image-error,
.file-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  color: #c0c4cc;
  background: #f5f7fa;
  font-size: 12px;
}

.image-error {
  height: 100%;
}

.result-info {
  flex: 1;
  min-width: 0;
}

.result-url {
  word-break: break-all;
}

.result-actions {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-top: 12px;
}
</style>
