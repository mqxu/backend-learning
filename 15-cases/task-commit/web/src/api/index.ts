import { http } from './request'
import type {
  CreateUserPayload,
  LoginPayload,
  LoginResponse,
  ReviewTaskPayload,
  SubmitTaskPayload,
  TaskView,
  UserProfile,
} from '../types/api'

/**
 * 所有接口集中在这里，页面不直接写 URL。
 * 后端改路径时只改这个文件，页面不用动；每个方法都标了返回类型，
 * 页面里忘了处理 null 或写错字段，typecheck 会直接报出来。
 */

// 认证
export const login = (data: LoginPayload) => http.post<LoginResponse>('/auth/login', data)
export const me = () => http.get<UserProfile>('/auth/me')

// 实训任务
export const submitTask = (data: SubmitTaskPayload) => http.post<TaskView>('/api/tasks', data)
export const myTasks = () => http.get<TaskView[]>('/api/tasks/mine')
export const taskDetail = (id: number) => http.get<TaskView>(`/api/tasks/${id}`)
export const allTasks = () => http.get<TaskView[]>('/api/tasks')
export const reviewTask = (id: number, data: ReviewTaskPayload) =>
  http.post<TaskView>(`/api/tasks/${id}/review`, data)

// 账号管理
export const listUsers = () => http.get<UserProfile[]>('/admin/users')
export const createUser = (data: CreateUserPayload) => http.post<UserProfile>('/admin/users', data)
export const deleteUser = (username: string) => http.del<void>(`/admin/users/${username}`)
