import { http, setToken, clearToken, getToken } from './request'
import { api } from './api'
import type { LoginResult, UserInfo } from '../types'

const USER_KEY = 'campus_user'

/** 微信登录：小程序走 uni.login 换 code，H5 用 devOpenId 调试 */
export async function login(): Promise<LoginResult> {
  let payload: { code?: string; devOpenId?: string; nickname?: string } = {}

  // #ifdef MP-WEIXIN
  const code = await new Promise<string>((resolve, reject) => {
    uni.login({
      provider: 'weixin',
      success: (res) => resolve(res.code),
      fail: reject
    })
  })
  payload = { code }
  // #endif

  // #ifndef MP-WEIXIN
  const devOpenId = uni.getStorageSync('campus_dev_openid') || 'mock-student-1'
  payload = { devOpenId, nickname: '张小明' }
  // #endif

  const data = await http.post<LoginResult>('/app/auth/login', payload, { auth: false })
  setToken(data.token)
  uni.setStorageSync(USER_KEY, data.user)
  return data
}

export async function ensureLogin(): Promise<UserInfo> {
  if (getToken()) {
    const cached = currentUser()
    if (cached) {
      return cached
    }
  }
  const data = await login()
  if (data.needBind) {
    uni.navigateTo({ url: '/pages/auth/bind' })
  }
  return data.user
}

/** 手机号 + 短信验证码登录：拿到 token 后与微信登录一样落本地 */
export async function loginBySms(phone: string, code: string): Promise<LoginResult> {
  const data = await api.smsLogin(phone, code)
  setToken(data.token)
  uni.setStorageSync(USER_KEY, data.user)
  return data
}

/** 登出：先尽力通知后端把 token 拉黑（失败忽略），再清本地并回首页 */
export async function logout(): Promise<void> {
  try {
    if (getToken()) {
      await api.logout()
    }
  } catch (e) {
    // 后端不可用或 token 已过期时忽略，保证本地仍能退出
    console.warn('调用登出接口失败，已忽略', e)
  } finally {
    clearToken()
    uni.reLaunch({ url: '/pages/index/index' })
  }
}

export function currentUser(): UserInfo | null {
  const user = uni.getStorageSync(USER_KEY)
  return user ? (user as UserInfo) : null
}

export function updateLocalUser(user: UserInfo): void {
  uni.setStorageSync(USER_KEY, user)
}
