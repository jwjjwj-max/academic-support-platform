export const MAX_SUBJECTS = 8
export const MAX_SUBJECT_LENGTH = 80
const subjectSeparators = /[、,，;；\r\n]+/

export function splitSubjects(value: string) {
  return value.split(subjectSeparators).map(part => part.trim()).filter(Boolean)
}

export function hasSubjectSeparator(value: string) {
  return subjectSeparators.test(value)
}

export function subjectErrors(subjects: string[]) {
  const seen = new Set<string>()
  return subjects.map(raw => {
    const subject = raw.trim()
    const key = subject.toLowerCase()
    if (!subject) return '请输入课程名称'
    if (hasSubjectSeparator(raw)) return '每行只填一门课程，请先拆分'
    if (subject.length > MAX_SUBJECT_LENGTH) return '课程名称不能超过 80 字'
    if (seen.has(key)) return '与前面填写的课程重复'
    seen.add(key)
    return ''
  })
}

export function validateSubjects(subjects: string[]) {
  if (subjects.length < 1 || subjects.length > MAX_SUBJECTS) return '请填写 1 至 8 门课程'
  const errors = subjectErrors(subjects)
  const first = errors.findIndex(Boolean)
  return first < 0 ? '' : `第 ${first + 1} 门课程：${errors[first]}`
}
