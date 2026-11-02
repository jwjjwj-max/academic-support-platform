import type { MatchCandidate } from '@/types/api'

export function canGenerateMatching(status?: string) {
  return status === 'MATCHING'
}

export function canGenerateLocalRematch(status?: string) {
  return status === 'CONFIRMATION'
}

export function canOperateRound(status: string | undefined, roundType: MatchCandidate['roundType'] | undefined) {
  return status === 'MATCHING' && roundType === 'NORMAL'
    || status === 'CONFIRMATION' && roundType === 'LOCAL_REMATCH'
}

export function canStudentDecide(status?: string) {
  return status === 'CONFIRMATION'
}
