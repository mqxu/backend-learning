import type { ApiResult, FileUploadResult } from '../types'

/**
 * H5 走 vite 代理（/api），小程序需要完整地址，并在开发者工具里勾选「不校验合法域名」。
 */
let BASE_URL = '/api'
// #ifndef H5
BASE_URL = 'http://127.0.0.1:8016/api'
// #endif

/** 后端静态资源（本地存储）的源地址，小程序端拼图片完整地址用 */
const FILE_ORIGIN = 'http://127.0.0.1:8016'

/** H5 下 /uploads 交给 vite 代理，相对地址即可；小程序需要拼后端完整地址 */
let IS_H5 = false
// #ifdef H5
IS_H5 = true
// #endif

const TOKEN_KEY = 'campus_token'
const USER_KEY = 'campus_user'

/**
 * 解析后端返回的文件地址。
 * 绝对地址原样返回；H5 返回相对路径（vite 已代理 /uploads）；小程序拼后端源地址。
 */
export function resolveFileUrl(url?: string | null): string {
  if (!url) {
    return ''
  }
  if (/^https?:\/\//i.test(url)) {
    return url
  }
  const path = url.startsWith('/') ? url : `/${url}`
  return IS_H5 ? path : FILE_ORIGIN + path
}

export function getToken(): string {
  return uni.getStorageSync(TOKEN_KEY) || ''
}

export function setToken(token: string): void {
  uni.setStorageSync(TOKEN_KEY, token)
}

export function clearToken(): void {
  uni.removeStorageSync(TOKEN_KEY)
  uni.removeStorageSync(USER_KEY)
}

export interface RequestOptions {
  url: string
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE'
  data?: unknown
  auth?: boolean
  loading?: boolean
}

type QueryParams = Record<string, string | number | boolean | undefined | null>

export function request<T>(options: RequestOptions): Promise<T> {
  const { url, method = 'GET', data, auth = true, loading = false } = options

  if (loading) {
    uni.showLoading({ title: '加载中', mask: true })
  }

  return new Promise<T>((resolve, reject) => {
    const header: Record<string, string> = { 'Content-Type': 'application/json' }
    const token = getToken()
    if (auth && token) {
      header.Authorization = `Bearer ${token}`
    }

    uni.request({
      url: BASE_URL + url,
      method,
      data: data as string | Record<string, unknown> | ArrayBuffer | undefined,
      header,
      success: (res) => {
        if (res.statusCode === 401) {
          clearToken()
          uni.showToast({ title: '登录已过期，请重新登录', icon: 'none' })
          reject(new Error('unauthorized'))
          return
        }
        if (res.statusCode === 403) {
          uni.showToast({ title: '没有操作权限', icon: 'none' })
          reject(new Error('forbidden'))
          return
        }
        const body = res.data as ApiResult<T>
        if (body && body.code === 0) {
          resolve(body.data)
          return
        }
        const message = body?.message || '请求失败'
        uni.showToast({ title: message, icon: 'none' })
        reject(new Error(message))
      },
      fail: (err) => {
        uni.showToast({ title: '网络异常，请检查后端是否启动', icon: 'none' })
        reject(err instanceof Error ? err : new Error('network error'))
      },
      complete: () => {
        if (loading) {
          uni.hideLoading()
        }
      }
    })
  })
}

/**
 * 上传单个文件（uni.uploadFile）。
 * multipart 字段名固定 file，bizType 既走 query 也放 formData；需要登录态。
 * 返回的 url 是相对地址，页面渲染前用 resolveFileUrl 处理。
 */
export function uploadFile(filePath: string, bizType: string): Promise<FileUploadResult> {
  return new Promise<FileUploadResult>((resolve, reject) => {
    const header: Record<string, string> = {}
    const token = getToken()
    if (token) {
      header.Authorization = `Bearer ${token}`
    }

    uni.uploadFile({
      url: `${BASE_URL}/files/upload?bizType=${encodeURIComponent(bizType)}`,
      filePath,
      name: 'file',
      // bizType 只放 query：同时放 formData 时后端 @RequestParam 会收到
      // "activity-cover,activity-cover" 这样的逗号拼接值，被判为非法业务类型
      header,
      success: (res) => {
        if (res.statusCode === 401) {
          clearToken()
          uni.showToast({ title: '登录已过期，请重新登录', icon: 'none' })
          reject(new Error('unauthorized'))
          return
        }
        let body: ApiResult<FileUploadResult> | null = null
        try {
          body = JSON.parse(res.data) as ApiResult<FileUploadResult>
        } catch (e) {
          body = null
        }
        if (body && body.code === 0) {
          resolve(body.data)
          return
        }
        const message = body?.message || '上传失败'
        uni.showToast({ title: message, icon: 'none' })
        reject(new Error(message))
      },
      fail: (err) => {
        uni.showToast({ title: '上传失败，请检查网络', icon: 'none' })
        reject(err instanceof Error ? err : new Error('upload failed'))
      }
    })
  })
}

function buildQuery(params?: QueryParams): string {
  if (!params) {
    return ''
  }
  const query = Object.entries(params)
    .filter(([, value]) => value !== undefined && value !== null && value !== '')
    .map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(String(value))}`)
    .join('&')
  return query ? `?${query}` : ''
}

export const http = {
  get<T>(url: string, params?: QueryParams, options: Partial<RequestOptions> = {}): Promise<T> {
    return request<T>({ url: url + buildQuery(params), method: 'GET', ...options })
  },
  post<T>(url: string, data?: unknown, options: Partial<RequestOptions> = {}): Promise<T> {
    return request<T>({ url, method: 'POST', data, ...options })
  },
  put<T>(url: string, data?: unknown, options: Partial<RequestOptions> = {}): Promise<T> {
    return request<T>({ url, method: 'PUT', data, ...options })
  }
}
