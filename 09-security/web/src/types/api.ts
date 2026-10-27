/**
 * 与后端约定的类型，一个文件写完。
 *
 * 这些接口不是手抄的，是对着后端 vo 与 dto 一个个核过的：
 * 改后端字段时先改这里，`npm run typecheck` 会把用到的地方全指出来。
 */

/** 角色编码，与后端 Role 枚举同名 */
export type RoleCode = 'STUDENT' | 'TEACHER' | 'ADMIN'

/** 任务状态，与后端 TaskStatus 枚举同名 */
export type TaskStatusCode = 'SUBMITTED' | 'REVIEWED'

/**
 * 后端统一响应体。业务失败也是 HTTP 200，靠 code 区分
 */
export interface ApiResult<T> {
  code: number
  message: string
  data: T
}

export interface LoginPayload {
  username: string
  password: string
}

export interface LoginResponse {
  token: string
  tokenType: string
  expiresAt: string
  username: string
  nickname: string
  roles: RoleCode[]
}

/** 当前登录人，只存页面要用的字段，别把整份 UserProfile 塞进 localStorage */
export interface CurrentUser {
  username: string
  nickname: string
  roles: RoleCode[]
}

export interface UserProfile {
  username: string
  nickname: string
  roles: RoleCode[]
  authorities: string[]
  enabled: boolean
}

export interface TaskView {
  id: number
  title: string
  content: string
  ownerId: number
  owner: string
  ownerNickname: string
  status: TaskStatusCode
  /** 后端给的中文，前端不再自己映射 */
  statusText: string
  score: number | null
  comment: string | null
  submittedAt: string
  reviewedAt: string | null
}

export interface SubmitTaskPayload {
  title: string
  content: string
}

export interface ReviewTaskPayload {
  score: number
  comment: string
}

export interface CreateUserPayload {
  username: string
  password: string
  nickname: string
  roles: RoleCode[]
}
