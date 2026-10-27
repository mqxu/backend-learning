import { reactive } from 'vue'

/**
 * 登录态只做三件事：存令牌、存用户信息、清空。
 *
 * 存 localStorage 而不是内存，是因为刷新页面后内存变量会丢，
 * 刷新就掉登录。作业里可以讨论为什么不存 cookie：令牌走请求头，
 * 天然不受 CSRF 影响，代价是脚本能读到它，所以要防 XSS。
 */
const TOKEN_KEY = 'demo_token'
const USER_KEY = 'demo_user'

export const auth = reactive({
  token: localStorage.getItem(TOKEN_KEY) || '',
  user: JSON.parse(localStorage.getItem(USER_KEY) || 'null'),
})

export function saveLogin(data) {
  auth.token = data.token
  auth.user = { username: data.username, nickname: data.nickname, roles: data.roles }
  localStorage.setItem(TOKEN_KEY, data.token)
  localStorage.setItem(USER_KEY, JSON.stringify(auth.user))
}

export function saveUser(user) {
  auth.user = { username: user.username, nickname: user.nickname, roles: user.roles }
  localStorage.setItem(USER_KEY, JSON.stringify(auth.user))
}

export function clearLogin() {
  auth.token = ''
  auth.user = null
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}

export function hasRole(role) {
  return auth.user?.roles?.includes(role) ?? false
}

export function hasAnyRole(roles) {
  return roles.some(hasRole)
}
