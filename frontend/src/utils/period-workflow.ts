import type { ParticipationApplication, Period } from '@/types/api'
import { labelOf } from './labels'

export type RegistrationWindowState = 'NOT_STARTED' | 'OPEN' | 'ENDED' | 'CLOSED_BY_STAGE'

export function registrationWindowState(period: Period, now = new Date()): RegistrationWindowState {
  if (period.status !== 'REGISTRATION') return 'CLOSED_BY_STAGE'
  if (now < new Date(period.registrationStart)) return 'NOT_STARTED'
  if (now >= new Date(period.registrationEnd)) return 'ENDED'
  return 'OPEN'
}

export function registrationWindowMessage(period: Period, now = new Date()): string {
  const state = registrationWindowState(period, now)
  if (state === 'NOT_STARTED') return `报名尚未开始，将于 ${formatTime(period.registrationStart)} 开放。`
  if (state === 'ENDED') return '报名已截止，等待学院开始报名审核。'
  if (state === 'OPEN') return `报名正在开放，将于 ${formatTime(period.registrationEnd)} 截止。`
  if (period.status === 'REVIEW') return '首次报名已关闭，学院正在审核报名结果。'
  return `当前活动阶段：${studentPeriodLabel(period.status)}。`
}

export function studentPeriodLabel(status: string): string {
  const labels: Record<string, string> = {
    ANNOUNCED: '活动预告中',
    REGISTRATION: '报名进行中',
    REVIEW: '报名结果审核中',
    MATCHING: '结对匹配中',
    CONFIRMATION: '匹配结果确认中',
    TRACKING: '帮扶活动进行中',
    ACCEPTANCE: '活动验收中',
    ENDED: '本期活动已结束',
    ARCHIVED: '本期记录已归档',
  }
  return labels[status] || labelOf(status)
}

export function studentApplicationStatus(application: ParticipationApplication): string {
  if (application.applicationStatus === 'REJECTED') return '报名被驳回，请按原因修改重提'
  if (application.applicationStatus === 'SUBMITTED' || application.applicationStatus === 'UNDER_REVIEW') return '报名已提交，等待学院审核'
  if (application.selectionStatus === 'MATCHING_POOL') return '本期已入选，等待学院安排结对'
  if (application.selectionStatus === 'WAITLIST') return '本期候补'
  if (application.selectionStatus === 'NOT_SELECTED') return '本期未入选'
  if (application.applicationStatus === 'APPROVED') return '审核通过，等待学院确定本期入选结果'
  return labelOf(application.applicationStatus)
}

export function formatTime(value: string): string {
  return new Date(value).toLocaleString('zh-CN', { hour12: false })
}
