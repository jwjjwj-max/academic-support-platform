import { describe, expect, it } from 'vitest'
import { canGenerateLocalRematch, canGenerateMatching, canOperateRound, canStudentDecide } from './matching-stage'

describe('匹配阶段权限', () => {
  it('普通匹配只允许匹配阶段', () => {
    expect(canGenerateMatching('MATCHING')).toBe(true)
    expect(canGenerateMatching('CONFIRMATION')).toBe(false)
    expect(canGenerateMatching('REGISTRATION')).toBe(false)
  })

  it('局部重匹配只允许双方确认阶段', () => {
    expect(canGenerateLocalRematch('CONFIRMATION')).toBe(true)
    expect(canGenerateLocalRematch('MATCHING')).toBe(false)
  })

  it('轮次调整和发布必须同时匹配阶段与轮次类型', () => {
    expect(canOperateRound('MATCHING', 'NORMAL')).toBe(true)
    expect(canOperateRound('CONFIRMATION', 'LOCAL_REMATCH')).toBe(true)
    expect(canOperateRound('CONFIRMATION', 'NORMAL')).toBe(false)
    expect(canOperateRound('MATCHING', 'LOCAL_REMATCH')).toBe(false)
  })

  it('学生只在双方确认阶段显示确认和拒绝操作', () => {
    expect(canStudentDecide('CONFIRMATION')).toBe(true)
    expect(canStudentDecide('MATCHING')).toBe(false)
    expect(canStudentDecide('TRACKING')).toBe(false)
  })
})
