import { describe, expect, it } from 'vitest'
import { validateGradeMapping } from './grade-mapping'

describe('成绩字段映射校验', () => {
  const requiredMapping = {
    studentNo: '学号',
    grade: '年级',
    major: '专业',
    termGpa: '平均学分绩点',
    termGpaRank: '平均学分绩点排名',
  }

  it('接受整院成绩表的完整精确映射', () => expect(validateGradeMapping(requiredMapping)).toBeNull())
  it('拒绝把平均绩点当成平均学分绩点', () => expect(validateGradeMapping({ ...requiredMapping, termGpa: '平均绩点' })).toContain('不得'))
  it('拒绝缺少必选列', () => expect(validateGradeMapping({ studentNo: '学号', termGpa: '平均学分绩点' })).toContain('必须'))
  it('拒绝重复映射同一列', () => expect(validateGradeMapping({ ...requiredMapping, name: '学号' })).toContain('不能'))
})
