import { http } from './request'
import type {
  ActivityDetail,
  ActivityItem,
  ActivityQuery,
  ActivityStat,
  Category,
  CheckinQr,
  CheckinResultData,
  LoginResult,
  OrgDashboard,
  PageResult,
  SaveActivityPayload,
  SignupDetail,
  SignupItem,
  SysNotice,
  UserInfo
} from '../types'

export interface BindPayload {
  studentNo: string
  realName: string
  college?: string
  grade?: string
  major?: string
}

export interface SignupAuditPayload {
  signupIds: number[]
  pass: boolean
  reason?: string | null
}

export const api = {
  // ---------- 学生端
  categories: () => http.get<Category[]>('/app/categories'),
  activities: (params?: ActivityQuery) => http.get<PageResult<ActivityItem>>('/app/activities', params as Record<string, string | number>),
  hotActivities: () => http.get<ActivityItem[]>('/app/activities/hot', { limit: 5 }),
  activityDetail: (id: number | string) => http.get<ActivityDetail>(`/app/activities/${id}`),
  toggleFavorite: (id: number | string) => http.post<{ favorited: boolean }>(`/app/activities/${id}/favorite`),

  signup: (id: number | string, data: { formData?: string }) =>
    http.post<SignupItem>(`/app/activities/${id}/signup`, data),
  mySignups: (params?: { page?: number; size?: number; status?: number }) =>
    http.get<PageResult<SignupItem>>('/app/signups', params as Record<string, string | number>),
  signupDetail: (id: number | string) => http.get<SignupItem>(`/app/signups/${id}`),
  cancelSignup: (id: number | string) => http.post<void>(`/app/signups/${id}/cancel`, {}),

  checkin: (data: { token: string; latitude?: number; longitude?: number }) =>
    http.post<CheckinResultData>('/app/checkin', data),

  messages: (params?: { page?: number; size?: number }) =>
    http.get<PageResult<SysNotice>>('/app/messages', params as Record<string, string | number>),
  unreadCount: () => http.get<{ count: number }>('/app/messages/unread-count'),
  markRead: (ids: number[] | null) => http.post<void>('/app/messages/read', { ids }),

  profile: () => http.get<UserInfo>('/app/user/profile'),
  bind: (data: BindPayload) => http.post<UserInfo>('/app/auth/bind', data),

  // ---------- 组织者
  orgDashboard: () => http.get<OrgDashboard>('/org/dashboard'),
  orgActivities: (params?: { page?: number; size?: number; status?: number }) =>
    http.get<PageResult<ActivityItem>>('/org/activities', params as Record<string, string | number>),
  createActivity: (data: SaveActivityPayload) => http.post<{ id: number }>('/org/activities', data),
  updateActivity: (id: number | string, data: SaveActivityPayload) =>
    http.put<void>(`/org/activities/${id}`, data),
  submitActivity: (id: number | string) => http.post<void>(`/org/activities/${id}/submit`, {}),
  cancelActivity: (id: number | string, reason?: string) =>
    http.post<void>(`/org/activities/${id}/cancel`, { reason }),

  orgSignups: (id: number | string, params?: { page?: number; size?: number; status?: number; keyword?: string }) =>
    http.get<PageResult<SignupDetail>>(`/org/activities/${id}/signups`, params as Record<string, string | number>),
  auditSignups: (id: number | string, data: SignupAuditPayload) =>
    http.post<{ handled: number }>(`/org/activities/${id}/signups/audit`, data),
  checkinQr: (id: number | string) => http.get<CheckinQr>(`/org/activities/${id}/checkin-qr`),
  manualCheckin: (id: number | string, data: { signupId: number; remark: string }) =>
    http.post<void>(`/org/activities/${id}/checkin/manual`, data),
  orgStat: (id: number | string) => http.get<ActivityStat>(`/org/activities/${id}/stat`)
}

/** 活动状态文案 */
export const ACTIVITY_STATUS: Record<number, string> = {
  0: '草稿',
  1: '待审核',
  2: '已驳回',
  3: '已发布',
  4: '进行中',
  5: '已结束',
  6: '已取消'
}

/** 报名状态文案 */
export const SIGNUP_STATUS: Record<number, string> = {
  0: '待审核',
  1: '报名成功',
  2: '候补中',
  3: '已签到',
  4: '已取消',
  5: '审核未通过',
  6: '未到场'
}
