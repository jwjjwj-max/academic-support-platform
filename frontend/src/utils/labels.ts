const labels: Record<string, string> = {
  ANNOUNCED: '已发布预告',
  REGISTRATION: '报名阶段',
  REVIEW: '报名审核阶段',
  MATCHING: '匹配阶段',
  CONFIRMATION: '双方确认阶段',
  TRACKING: '帮扶开展阶段',
  ACCEPTANCE: '验收阶段',
  ENDED: '已结束',
  ARCHIVED: '已归档',
  SUBMITTED: '待审核',
  UNDER_REVIEW: '审核中',
  APPROVED: '已通过',
  REJECTED: '已驳回',
  LOCKED: '已锁定',
  UNDECIDED: '待选择',
  MATCHING_POOL: '匹配池',
  WAITLIST: '候补',
  NOT_SELECTED: '本期未入选',
  REMOVED_FROM_POOL: '已撤出匹配池',
  GENERATED: '候选组合',
  SELECTED: '拟发布',
  PUBLISHED: '已发布',
  CONFIRMED: '已确认',
  PENDING: '待确认',
  ACTIVE: '帮扶进行中',
  COMPLETED: '验收完成',
  PEER_PENDING: '待同伴确认真实性',
  ADMIN_PENDING: '待管理员审核',
  BEFORE: '帮扶前',
  AFTER: '帮扶后',
  PREVIEWED: '待确认导入',
  IMPORTED: '已导入',
  DRAFT: '草稿',
  WITHDRAWN: '已撤回',
  CLIMBER: '学业攀登者',
  NAVIGATOR: '学业领航者',
  subject: '帮扶科目',
  major: '专业',
  grade: '年级',
  learningStyle: '学习风格',
  personality: '性格特点',
  gpaGap: '绩点差距',
}

export function labelOf(value?: string | null): string {
  if (!value) return '—'
  return labels[value] || value
}

export function reflectionStatusLabel(value?: string | null): string {
  if (value === 'SUBMITTED' || value === 'ADMIN_PENDING') return '管理员可查看'
  if (value === 'APPROVED') return '已提交'
  return labelOf(value)
}
