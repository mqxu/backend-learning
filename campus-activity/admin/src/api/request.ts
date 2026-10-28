import axios, { type AxiosError, type AxiosInstance, type AxiosRequestConfig, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import type { ApiResult, UserInfo } from '@/types/api'

const TOKEN_KEY = 'campus_admin_token'
const USER_KEY = 'campus_admin_user'

export function getToken(): string {
  return localStorage.getItem(TOKEN_KEY) || ''
}

export function getCurrentUser(): UserInfo | null {
  const raw = localStorage.getItem(USER_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw) as UserInfo
  } catch {
    return null
  }
}

export function setAuth(token: string, user?: UserInfo | null): void {
  localStorage.setItem(TOKEN_KEY, token)
  localStorage.setItem(USER_KEY, JSON.stringify(user || {}))
}

export function setCurrentUser(user?: UserInfo | null): void {
  localStorage.setItem(USER_KEY, JSON.stringify(user || {}))
}

export function clearAuth(): void {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}

function redirectToLogin(): void {
  clearAuth()
  if (!window.location.hash.startsWith('#/login')) {
    window.location.hash = '#/login'
  }
}

/**
 * 拆包统一响应体：code === 0 时返回 data，否则提示并抛出错误。
 * 非统一响应体（无 code 字段）原样返回。
 */
function unwrapResponse(response: AxiosResponse): unknown {
  const body: unknown = response.data
  if (body && typeof body === 'object' && 'code' in body) {
    const result = body as ApiResult<unknown>
    if (result.code === 0) {
      return result.data
    }
    const message = result.message || '请求失败'
    ElMessage.error(message)
    throw new Error(message)
  }
  return body
}

const service: AxiosInstance = axios.create({
  baseURL: '/api',
  timeout: 15000
})

service.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

service.interceptors.response.use(
  // 拦截器已把响应体拆包成业务数据，因此这里与 AxiosResponse 的签名不符，做一次显式转换
  (response: AxiosResponse) => unwrapResponse(response) as unknown as AxiosResponse,
  (error: AxiosError<ApiResult<unknown>>) => {
    const status = error.response?.status
    if (status === 401) {
      ElMessage.error('登录状态已失效，请重新登录')
      redirectToLogin()
      return Promise.reject(new Error('未登录或登录已过期'))
    }
    if (status === 403) {
      ElMessage.error('没有权限执行该操作')
      return Promise.reject(new Error('没有权限执行该操作'))
    }
    const message = error.response?.data?.message || error.message || '网络异常，请稍后重试'
    ElMessage.error(message)
    return Promise.reject(new Error(message))
  }
)

/** 已拆包的请求客户端：返回的 Promise 直接是业务数据 */
export interface HttpClient {
  get<T = unknown>(url: string, config?: AxiosRequestConfig): Promise<T>
  post<T = unknown>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T>
  put<T = unknown>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T>
  delete<T = unknown>(url: string, config?: AxiosRequestConfig): Promise<T>
}

export default service as unknown as HttpClient
