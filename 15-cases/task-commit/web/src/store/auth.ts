import { reactive } from 'vue'
import type { CurrentUser, LoginResponse, RoleCode, UserProfile } from '../types/api'

/**
 * 登录态只做三件事：存令牌、存当前用户、清空。
 *
 * 存 localStorage 而不是内存，是因为刷新页面后内存变量会丢，刷新就掉登录。
 * 为什么不存 cookie：令牌走请求头，浏览器不会自动带上，天然不受 CSRF 影响；
 * 代价是脚本能读到它，所以要防 XSS。
 */
const TOKEN_KEY = 'demo_token'
const USER_KEY = 'demo_user'

function readUser(): CurrentUser | null {
  const raw = localStorage.getItem(USER_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw) as CurrentUser
  } catch {
    // 存进去的东西被人改坏了就当没登录，别让页面崩在这里
    localStorage.removeItem(USER_KEY)
    return null
  }
}

export const auth = reactive({
  token: localStorage.getItem(TOKEN_KEY) ?? '',
  user: readUser() as CurrentUser | null,
})

export function saveLogin(data: LoginResponse): void {
  auth.token = data.token
  auth.user = { username: data.username, nickname: data.nickname, roles: data.roles }
  localStorage.setItem(TOKEN_KEY, data.token)
  localStorage.setItem(USER_KEY, JSON.stringify(auth.user))
}

/** 刷新页面后用 /auth/me 换回来的最新身份，角色可能已被管理员改过 */
export function saveUser(user: UserProfile): void {
  auth.user = { username: user.username, nickname: user.nickname, roles: user.roles }
  localStorage.setItem(USER_KEY, JSON.stringify(auth.user))
}

export function clearLogin(): void {
  auth.token = ''
  auth.user = null
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}

export function hasRole(role: RoleCode): boolean {
  return auth.user?.roles.includes(role) ?? false
}

export function hasAnyRole(roles: readonly RoleCode[]): boolean {
  return roles.some((role) => hasRole(role))
}
