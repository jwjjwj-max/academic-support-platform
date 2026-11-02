export interface ApplicationAdminPermissions {
  canReview: boolean
  canSelect: boolean
  notice: string
  noticeTone: 'success' | 'warning'
}

export function applicationAdminPermissions(status?: string): ApplicationAdminPermissions {
  if (status === 'REVIEW') {
    return {
      canReview: true,
      canSelect: true,
      notice: '当前处于报名审核阶段：可以审核报名，并为审核通过的学生设置匹配池、候补或本期未入选。',
      noticeTone: 'success',
    }
  }
  if (status === 'REGISTRATION') {
    return {
      canReview: false,
      canSelect: false,
      notice: '当前处于报名阶段：学生可以提交首次报名；管理员需推进至“报名审核阶段”后才能审核或设置入选状态。',
      noticeTone: 'warning',
    }
  }
  if (status === 'MATCHING') {
    return {
      canReview: false,
      canSelect: false,
      notice: '当前处于匹配阶段：普通报名审核和入选状态调整已关闭，请在匹配管理中处理入池学生。',
      noticeTone: 'warning',
    }
  }
  return {
    canReview: false,
    canSelect: false,
    notice: status
      ? '当前阶段不开放报名审核或普通入选状态调整。'
      : '请先选择期次，系统将按期次阶段开放相应操作。',
    noticeTone: 'warning',
  }
}
