import type { AdminGrade } from '@/types/api'

export interface AdminGradeComparison {
  key: string
  periodId: number
  studentUserId: number
  studentNo: string
  name: string
  role: AdminGrade['role']
  major?: string
  before?: AdminGrade
  after?: AdminGrade
  change?: number
  rankImprovementPercentagePoints?: number
}

export function groupAdminGrades(grades: AdminGrade[]): AdminGradeComparison[] {
  const rows = new Map<string, AdminGradeComparison>()
  for (const grade of grades) {
    const key = `${grade.periodId}:${grade.studentUserId}`
    const row = rows.get(key) ?? {
      key,
      periodId: grade.periodId,
      studentUserId: grade.studentUserId,
      studentNo: grade.studentNo,
      name: grade.name,
      role: grade.role,
      major: grade.major,
    }
    if (grade.stage === 'BEFORE') row.before = grade
    else row.after = grade
    if (row.before && row.after) {
      row.change = Number((Number(row.after.termGpa) - Number(row.before.termGpa)).toFixed(2))
      if (row.before.professionalTopPercent != null && row.after.professionalTopPercent != null)
        row.rankImprovementPercentagePoints = Number((Number(row.before.professionalTopPercent) - Number(row.after.professionalTopPercent)).toFixed(1))
    }
    rows.set(key, row)
  }
  return [...rows.values()].sort((a, b) => a.studentNo.localeCompare(b.studentNo, 'zh-CN', { numeric: true }))
}
