import request from './request'
import type {
  ActivityDetail,
  ActivityItem,
  ActivityQuery,
  AuditParams,
  AuditRecord,
  DashboardData,
  LoginParams,
  LoginResult,
  OfflineParams,
  Organization,
  PageQuery,
  PageResult,
  SetRoleParams,
  UserInfo,
  UserQuery
} from '@/types/api'

/** 管理员登录 */
export function login(data: LoginParams): Promise<LoginResult> {
  return request.post<LoginResult>('/admin/auth/login', data)
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
