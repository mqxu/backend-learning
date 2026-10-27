import request from './request'

/**
 * 所有接口集中在这里，页面不直接写 URL。
 * 后端改路径时只改这个文件，页面不用动。
 */

// 认证
export const login = (data) => request.post('/auth/login', data)
export const me = () => request.get('/auth/me')

// 实训任务
export const submitTask = (data) => request.post('/api/tasks', data)
export const myTasks = () => request.get('/api/tasks/mine')
export const taskDetail = (id) => request.get(`/api/tasks/${id}`)
export const allTasks = () => request.get('/api/tasks')
export const reviewTask = (id, data) => request.post(`/api/tasks/${id}/review`, data)

// 账号管理
export const listUsers = () => request.get('/admin/users')
export const createUser = (data) => request.post('/admin/users', data)
export const deleteUser = (username) => request.delete(`/admin/users/${username}`)
