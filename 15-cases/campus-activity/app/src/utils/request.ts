import type { ApiResult } from '../types'

/**
 * H5 走 vite 代理（/api），小程序需要完整地址，并在开发者工具里勾选「不校验合法域名」。
 */
let BASE_URL = '/api'
// #ifndef H5
BASE_URL = 'http://127.0.0.1:8080/api'
// #endif

const TOKEN_KEY = 'campus_token'
const USER_KEY = 'campus_user'

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
