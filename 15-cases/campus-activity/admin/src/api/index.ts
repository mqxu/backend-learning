import request from './request'
import type {
  ActivityDetail,
  ActivityItem,
  ActivityQuery,
  AuditParams,
  AuditRecord,
  DashboardData,
  FileUploadResult,
  LoginParams,
  LoginResult,
  OfflineParams,
  Organization,
  PageQuery,
  PageResult,
  SetRoleParams,
  StorageInfo,
  UserInfo,
  UserQuery
} from '@/types/api'

/** 管理员登录 */
export function login(data: LoginParams): Promise<LoginResult> {
  return request.post<LoginResult>('/admin/auth/login', data)
}

/** 管理员登出：后端把当前 token 的 jti 写入黑名单，已失效的 token 也会返回成功 */
export function logout(): Promise<void> {
  return request.post<void>('/admin/auth/logout')
}

/** 工作台统计 */
export function fetchDashboard(): Promise<DashboardData> {
  return request.get<DashboardData>('/admin/dashboard')
}

/** 待审核活动列表 */
export function fetchAuditList(params: PageQuery): Promise<PageResult<ActivityItem>> {
  return request.get<PageResult<ActivityItem>>('/admin/activities/audit-list', { params })
}

/** 审核活动 */
export function auditActivity(id: number, data: AuditParams): Promise<void> {
  return request.post<void>(`/admin/activities/${id}/audit`, data)
}

/** 活动列表 */
export function fetchActivities(params: ActivityQuery): Promise<PageResult<ActivityItem>> {
  return request.get<PageResult<ActivityItem>>('/admin/activities', { params })
}

/** 下架活动 */
export function offlineActivity(id: number, data: OfflineParams): Promise<void> {
  return request.post<void>(`/admin/activities/${id}/offline`, data)
}

/** 用户列表 */
export function fetchUsers(params: UserQuery): Promise<PageResult<UserInfo>> {
  return request.get<PageResult<UserInfo>>('/admin/users', { params })
}

/** 设置用户角色 */
export function setUserRole(id: number, data: SetRoleParams): Promise<void> {
  return request.post<void>(`/admin/users/${id}/roles`, data)
}

/** 组织列表 */
export function fetchOrganizations(): Promise<Organization[]> {
  return request.get<Organization[]>('/admin/organizations')
}

/** 操作日志 */
export function fetchLogs(params: PageQuery): Promise<PageResult<AuditRecord>> {
  return request.get<PageResult<AuditRecord>>('/admin/logs', { params })
}

/** 活动详情 */
export function fetchActivityDetail(id: number | string): Promise<ActivityDetail> {
  return request.get<ActivityDetail>(`/app/activities/${id}`)
}

/**
 * 上传文件（管理后台上传活动封面、组织者 logo、头像共用）。
 * multipart 字段名固定为 file；不手写 Content-Type，交给 axios 自动生成 boundary。
 *
 * @param file    待上传文件
 * @param bizType 业务类型：activity-cover / org-logo / avatar / common
 */
export function uploadFile(file: File, bizType: string): Promise<FileUploadResult> {
  const formData = new FormData()
  formData.append('file', file)
  return request.post<FileUploadResult>('/files/upload', formData, {
    params: { bizType }
  })
}

/** 当前生效的文件存储方式与上传约束 */
export function fetchStorageInfo(): Promise<StorageInfo> {
  return request.get<StorageInfo>('/files/storage')
}
