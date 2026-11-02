export const REQUIRED_GRADE_FIELDS = ['studentNo', 'termGpa'] as const

export function validateGradeMapping(mapping: Record<string, string>): string | null {
  if (!mapping.studentNo || !mapping.grade || !mapping.major || !mapping.termGpa || !mapping.termGpaRank)
    return '必须映射学号、年级、专业、平均学分绩点和平均学分绩点排名。'
  const selected = Object.values(mapping).filter(Boolean)
  if (new Set(selected).size !== selected.length) return '同一成绩单列不能映射到多个字段。'
  if (mapping.termGpa === '平均绩点' || mapping.termGpaRank === '平均绩点排名')
    return '不得将“平均绩点”及其排名映射为“平均学分绩点”。'
  return null
}
