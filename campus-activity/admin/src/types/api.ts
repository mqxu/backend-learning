/**
 * 后端接口数据结构定义。
 * 统一响应：{ code, message, data }，code === 0 表示成功。
 * 时间字段统一为字符串（yyyy-MM-dd HH:mm 或 yyyy-MM-dd HH:mm:ss），不要使用 Date。
 */

/** 统一响应体 */
export interface ApiResult<T> {
  code: number
  message: string
  data: T
}

/** 分页响应体 */
export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  size: number
}

/** 活动状态：0 草稿 1 待审核 2 已驳回 3 已发布 4 进行中 5 已结束 6 已取消 */
export type ActivityStatus = 0 | 1 | 2 | 3 | 4 | 5 | 6

/** 用户角色 */
export type UserRole = 'STUDENT' | 'ORGANIZER' | 'REVIEWER' | 'ADMIN'

/** 审计动作 */
export type AuditAction = 'APPROVE' | 'REJECT' | 'CANCEL' | 'MANUAL_CHECKIN' | 'ROLE_CHANGE'

/** Element Plus 标签类型 */
export type TagType = 'primary' | 'success' | 'info' | 'warning' | 'danger'

/** 报名字段定义（活动 formFields 反序列化后的元素） */
export interface FormFieldItem {
  label?: string
  name?: string
  fieldName?: string
  required?: boolean
}

/** 活动列表项 */
export interface ActivityItem {
  id: number
  title: string
  coverUrl?: string | null
  summary?: string | null
  categoryId?: number | null
  categoryName?: string | null
  orgId?: number | null
  orgName?: string | null
  startTime?: string | null
  endTime?: string | null
  signupStartTime?: string | null
  signupEndTime?: string | null
  locationName?: string | null
  /** 0 表示不限人数 */
  capacity?: number | null
  signedCount?: number | null
  signupCount?: number | null
  status: ActivityStatus
  statusLabel?: string | null
  signupOpen?: boolean
  mySignupStatus?: number | null
}

/** 活动详情 */
export interface ActivityDetail extends ActivityItem {
  content?: string | null
  address?: string | null
  latitude?: number | null
  longitude?: number | null
  cancelDeadline?: string | null
  /** 1 开启 0 关闭 */
  waitlistEnabled?: number | null
  /** 1 需要 0 不需要 */
  auditRequired?: number | null
  /** 1 扫码 2 扫码+定位 3 仅手动 */
  checkinType?: number | null
  /** 自定义报名字段定义，JSON 字符串 */
  formFields?: string | FormFieldItem[] | null
  viewCount?: number | null
  favoriteCount?: number | null
  favorited?: boolean
  actionText?: string | null
  mySignupId?: number | null
}

/** 用户信息 */
export interface UserInfo {
  id: number
  nickname?: string | null
  avatarUrl?: string | null
  role: UserRole | string
  orgId?: number | null
  orgName?: string | null
  phone?: string | null
  studentNo?: string | null
  realName?: string | null
  college?: string | null
  grade?: string | null
  major?: string | null
  /** 是否已完成学号绑定 */
  bound?: boolean
}

/** 组织 */
export interface Organization {
  id: number
  name?: string | null
  /** 1 社团 2 院系 3 部门 */
  type?: number | null
  logoUrl?: string | null
  description?: string | null
  contact?: string | null
  status?: number | null
  createTime?: string | null
  updateTime?: string | null
}

/** 操作日志 / 审计记录 */
export interface AuditRecord {
  id: number
  /** ACTIVITY / SIGNUP / CHECKIN / USER */
  bizType?: string | null
  bizId?: number | null
  action: AuditAction | string
  operatorId?: number | null
  operatorName?: string | null
  beforeStatus?: string | null
  afterStatus?: string | null
  remark?: string | null
  createTime?: string | null
}

/** 工作台统计（不含热门活动） */
export interface DashboardSummary {
  userTotal: number
  studentTotal: number
  activityTotal: number
  publishedCount: number
  pendingAuditCount: number
  signupTotal: number
  checkinTotal: number
}

/** 工作台热门活动 */
export interface DashboardTopActivity {
  id: number
  title: string
  signupCount?: number | null
  capacity?: number | null
}

/** 工作台数据 */
export interface DashboardData extends DashboardSummary {
  topActivities: DashboardTopActivity[]
}

/** 登录结果 */
export interface LoginResult {
  token: string
  needBind?: boolean
  user: UserInfo
}

/** 登录入参 */
export interface LoginParams {
  username: string
  password: string
}

/** 通用分页入参 */
export interface PageQuery {
  page: number
  size: number
}

/** 活动列表入参 */
export interface ActivityQuery extends PageQuery {
  keyword?: string
  /** 空字符串表示不限状态 */
  status?: ActivityStatus | ''
}

/** 用户列表入参 */
export interface UserQuery extends PageQuery {
  keyword?: string
  /** 空字符串表示不限角色 */
  role?: UserRole | ''
}

/** 活动审核入参 */
export interface AuditParams {
  pass: boolean
  reason?: string
}

/** 活动下架入参 */
export interface OfflineParams {
  reason?: string
}

/** 设置用户角色入参 */
export interface SetRoleParams {
  role: UserRole | string
  orgId: number | null
}
