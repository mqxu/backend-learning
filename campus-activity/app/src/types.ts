/** 与后端接口一一对应的类型定义 */

export interface ApiResult<T> {
  code: number
  message: string
  data: T
}

export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  size: number
}

export interface Category {
  id: number
  name: string
  code: string
  icon?: string
  sort: number
  status: number
}

export interface ActivityItem {
  id: number
  title: string
  coverUrl?: string | null
  summary?: string | null
  categoryId: number
  categoryName?: string
  orgId: number
  orgName?: string
  startTime: string
  endTime: string
  signupStartTime: string
  signupEndTime: string
  locationName: string
  capacity: number
  signedCount: number
  status: number
  statusLabel: string
  signupOpen: boolean
  mySignupStatus?: number | null
}

export interface ActivityDetail extends ActivityItem {
  content?: string | null
  address?: string | null
  latitude?: number | null
  longitude?: number | null
  cancelDeadline?: string | null
  waitlistEnabled?: number
  auditRequired?: number
  checkinType?: number
  formFields?: string | null
  viewCount?: number
  favoriteCount?: number
  favorited?: boolean
  actionText?: string
  mySignupId?: number | null
}

export interface SignupItem {
  id: number
  activityId: number
  activityTitle: string
  coverUrl?: string | null
  startTime?: string
  endTime?: string
  locationName?: string
  status: number
  statusLabel: string
  signupNo: string
  waitlistSeq: number
  createTime: string
  checkinTime?: string | null
  cancelDeadline?: string | null
  canCancel: boolean
  canCheckin: boolean
}

export interface SignupDetail {
  id: number
  userId: number
  studentNo?: string
  realName?: string
  college?: string
  grade?: string
  status: number
  statusLabel: string
  signupNo: string
  formData?: string | null
  waitlistSeq: number
  createTime: string
  checkinTime?: string | null
}

export interface UserInfo {
  id: number
  nickname?: string
  avatarUrl?: string | null
  role: string
  orgId?: number | null
  orgName?: string | null
  phone?: string | null
  studentNo?: string
  realName?: string
  college?: string
  grade?: string
  major?: string
  bound: boolean
}

export interface LoginResult {
  token: string
  needBind: boolean
  user: UserInfo
}

export interface SysNotice {
  id: number
  type: string
  title: string
  content?: string
  bizId?: number
  readFlag: number
  createTime: string
}

export interface CheckinQr {
  activityId: number
  activityTitle: string
  token: string
  refreshSeconds: number
  signedCount: number
  checkedInCount: number
  recentCheckins: string[]
}

export interface CheckinResultData {
  success: boolean
  message: string
  activityId: number
  activityTitle: string
  checkinTime: string
  studentName?: string
}

export interface ActivityStat {
  activityId: number
  activityTitle: string
  capacity: number
  signedCount: number
  totalSignup: number
  successCount: number
  pendingCount: number
  waitlistCount: number
  checkedInCount: number
  cancelledCount: number
  noShowCount: number
  attendanceRate: number
}

export interface OrgDashboard {
  activityTotal: number
  publishedCount: number
  pendingAuditCount: number
  totalSignup: number
}

/** 活动自定义报名字段 */
export interface FormField {
  key: string
  label: string
  type?: 'TEXT' | 'SINGLE' | 'MULTI'
  required?: boolean
  options?: string[]
}

export type FormAnswers = Record<string, string | string[] | undefined>

export interface ActivityQuery {
  page?: number
  size?: number
  keyword?: string
  categoryId?: number
  timeScope?: string
  sort?: string
  status?: number
}

export interface SaveActivityPayload {
  title: string
  categoryId: number | null
  summary: string
  content: string
  locationName: string
  capacity: number
  waitlistEnabled: number
  auditRequired: number
  checkinType: number
  formFields?: string | null
  startTime: string
  endTime: string
  signupStartTime: string
  signupEndTime: string
}
