import { describe, expect, it } from 'vitest'
import type { ParticipationApplication, Period } from '@/types/api'
import { registrationWindowState, studentApplicationStatus, studentPeriodLabel } from './period-workflow'

const period = {
  status: 'REGISTRATION', registrationStart: '2026-09-01T00:00:00', registrationEnd: '2026-09-15T00:00:00',
} as Period

describe('期次阶段、时间窗口与个人状态', () => {
  it('报名开始前不开放', () => expect(registrationWindowState(period, new Date('2026-08-31T23:59:59'))).toBe('NOT_STARTED'))
  it('报名时间内开放', () => expect(registrationWindowState(period, new Date('2026-09-01T00:00:00'))).toBe('OPEN'))
  it('报名截止时立即关闭', () => expect(registrationWindowState(period, new Date('2026-09-15T00:00:00'))).toBe('ENDED'))
  it('阶段不是报名时关闭首次报名', () => expect(registrationWindowState({ ...period, status: 'REVIEW' }, new Date('2026-09-02'))).toBe('CLOSED_BY_STAGE'))
  it('学生端使用业务语言展示阶段', () => expect(studentPeriodLabel('REVIEW')).toBe('报名结果审核中'))
  it('审核通过但未确定入选时给出完整个人状态', () => expect(studentApplicationStatus({ applicationStatus: 'APPROVED', selectionStatus: 'UNDECIDED' } as ParticipationApplication)).toContain('等待学院确定'))
})
