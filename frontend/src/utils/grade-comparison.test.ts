import { describe, expect, it } from 'vitest'
import type { AdminGrade } from '@/types/api'
import { groupAdminGrades } from './grade-comparison'

const grade = (stage: 'BEFORE' | 'AFTER', termGpa: number, id: number): AdminGrade => ({
  id, periodId: 1, periodName: '测试期次', studentUserId: 10, studentNo: 'climber01', name: '攀登者01',
  role: 'CLIMBER', stage, termGpa, termGpaRank: stage === 'BEFORE' ? 20 : 10, professionalStudentCount: 100,
  professionalTopPercent: stage === 'BEFORE' ? 20 : 10, grade: '2024', major: '计算机科学与技术',
  importBatchId: stage === 'BEFORE' ? 1 : 2, updatedAt: '2026-08-13T00:00:00',
})

describe('管理员成绩横向对比', () => {
  it('只有帮扶前成绩时仍生成一行，帮扶后保持空缺', () => {
    const rows = groupAdminGrades([grade('BEFORE', 3.04, 1)])
    expect(rows).toHaveLength(1)
    expect(rows[0].before?.termGpa).toBe(3.04)
    expect(rows[0].after).toBeUndefined()
    expect(rows[0].change).toBeUndefined()
  })

  it('第二次导入后补到同一行并计算变化', () => {
    const rows = groupAdminGrades([grade('AFTER', 3.32, 2), grade('BEFORE', 3.04, 1)])
    expect(rows).toHaveLength(1)
    expect(rows[0].after?.termGpa).toBe(3.32)
    expect(rows[0].change).toBe(0.28)
    expect(rows[0].rankImprovementPercentagePoints).toBe(10)
  })
})
