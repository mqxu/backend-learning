import type { ActivityStatus, AuditAction, TagType, UserRole } from '@/types/api'

export interface ActivityStatusOption {
  value: ActivityStatus
  label: string
  type: TagType
}

export const ACTIVITY_STATUS_OPTIONS: ActivityStatusOption[] = [
  { value: 0, label: '草稿', type: 'info' },
  { value: 1, label: '待审核', type: 'warning' },
  { value: 2, label: '已驳回', type: 'danger' },
  { value: 3, label: '已发布', type: 'success' },
  { value: 4, label: '进行中', type: 'primary' },
  { value: 5, label: '已结束', type: 'info' },
  { value: 6, label: '已取消', type: 'danger' }
]

const STATUS_MAP: Record<number, ActivityStatusOption> = ACTIVITY_STATUS_OPTIONS.reduce<
  Record<number, ActivityStatusOption>
>((acc, item) => {
  acc[item.value] = item
  return acc
}, {})

export function activityStatusLabel(status?: number | null, fallback?: string | null): string {
  if (fallback) return fallback
  if (status == null) return '未知'
  return STATUS_MAP[status]?.label || '未知'
}

export function activityStatusType(status?: number | null): TagType {
  if (status == null) return 'info'
  return STATUS_MAP[status]?.type || 'info'
}

export interface RoleOption {
  value: UserRole
  label: string
}

export const ROLE_OPTIONS: RoleOption[] = [
  { value: 'STUDENT', label: '学生' },
  { value: 'ORGANIZER', label: '活动组织者' },
  { value: 'REVIEWER', label: '审核员' },
  { value: 'ADMIN', label: '管理员' }
]

const ROLE_MAP: Record<string, string> = ROLE_OPTIONS.reduce<Record<string, string>>((acc, item) => {
  acc[item.value] = item.label
  return acc
}, {})

export function roleLabel(role?: string | null): string {
  if (!role) return '-'
  return ROLE_MAP[role] || role
}

export const AUDIT_ACTION_LABELS: Record<string, string> = {
  APPROVE: '通过',
  REJECT: '驳回',
  CANCEL: '取消',
  MANUAL_CHECKIN: '补签',
  ROLE_CHANGE: '角色变更'
}

export function auditActionLabel(action?: AuditAction | string | null): string {
  if (!action) return '-'
  return AUDIT_ACTION_LABELS[action] || action
}
