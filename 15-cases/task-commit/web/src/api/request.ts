import axios, { type AxiosError } from 'axios'
import router from '../router'
import { auth, clearLogin } from '../store/auth'
import { notify } from '../store/toast'
import type { ApiResult } from '../types/api'

/**
 * 前后端分离里，这一个文件承担三条约定：
 *
 * 1. 每个请求自动带上 Authorization: Bearer <token>
 * 2. HTTP 401 说明没登录或令牌失效，清登录态回登录页
 * 3. HTTP 403 说明登录了但没权限，只提示，不能把用户踢去重新登录
 *
 * 页面里都不写这几段逻辑，只写业务，这样才不会每个页面各写一套。
 */
const request = axios.create({ timeout: 10000 })

request.interceptors.request.use((config) => {
  if (auth.token) {
    config.headers.Authorization = `Bearer ${auth.token}`
  }
  return config
})

request.interceptors.response.use(
  (response) => {
    // 后端的约定：成功和数据都在 Result 里，业务失败也是 HTTP 200
    const body = response.data as ApiResult<unknown>
    if (body.code === 200) {
      return body.data as never
    }
    notify(body.message || '操作失败')
    return Promise.reject(new Error(body.message || '操作失败'))
  },
  (error: AxiosError<ApiResult<unknown>>) => {
    const status = error.response?.status
    const message = error.response?.data?.message

    if (status === 401) {
      clearLogin()
      const from = router.currentRoute.value.fullPath
      router.push({ path: '/login', query: from === '/login' ? {} : { redirect: from } })
      notify(message || '登录已失效，请重新登录')
    } else if (status === 403) {
      // 只提示，不动登录态
      notify(message || '你没有权限做这个操作')
    } else if (status) {
      notify(message || `请求失败（${status}）`)
    } else {
      notify('连不上后端，确认 8009 是否已启动')
    }
    return Promise.reject(error)
  }
)

/**
 * 拦截器已经把 Result 拆成 data 了，但 axios 的类型仍认为是完整响应，
 * 所以在这里统一校正一次：调用方拿到的就是 T，不用再 .data.data。
 * 只写这一处断言，页面里全是实打实的类型。
 */
async function unwrap<T>(promise: Promise<unknown>): Promise<T> {
  return (await promise) as T
}

export const http = {
  get: <T>(url: string) => unwrap<T>(request.get(url)),
  post: <T>(url: string, data?: unknown) => unwrap<T>(request.post(url, data)),
  del: <T>(url: string) => unwrap<T>(request.delete(url)),
}

export default request
