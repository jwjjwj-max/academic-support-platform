import { describe, expect, it } from 'vitest'
import { applicationAdminPermissions } from './application-stage'

describe('报名管理阶段权限', () => {
  it.each(['REGISTRATION', 'MATCHING', 'CONFIRMATION', 'TRACKING', 'ACCEPTANCE', 'COMPLETED', 'ARCHIVED'])('%s 阶段禁止审核和入选调整', (status) => {
    expect(applicationAdminPermissions(status)).toMatchObject({ canReview: false, canSelect: false })
  })

  it('报名审核阶段开放审核和入选调整', () => {
    expect(applicationAdminPermissions('REVIEW')).toMatchObject({ canReview: true, canSelect: true })
  })

  it('报名阶段提示管理员需先推进阶段', () => {
    expect(applicationAdminPermissions('REGISTRATION').notice).toContain('推进至“报名审核阶段”')
  })
})
