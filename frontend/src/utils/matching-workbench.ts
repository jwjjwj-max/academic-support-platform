import type { MatchCandidate, MatchKind, MatchingRoundView } from '@/types/api'

const labels: Record<MatchKind, string> = {
  PEER: '大一结对学习', REGULAR: '高年级常规帮扶', FRESHMAN_SUPPORT: '高年级领航大一', LEGACY: '领航帮扶',
}

export function matchKindLabel(kind?: MatchKind) {
  return labels[kind ?? 'LEGACY']
}

type PairMembers = Pick<MatchCandidate, 'navigatorApplicationId' | 'climberApplicationId'>
type PairAssignment = PairMembers & Pick<MatchCandidate, 'matchKind'>

export function members(pair: PairMembers) {
  return [pair.navigatorApplicationId, pair.climberApplicationId]
}

export function selectedPairs(view?: MatchingRoundView | null) {
  return view?.candidates.filter(pair => ['SELECTED', 'PUBLISHED', 'CONFIRMED', 'REJECTED'].includes(pair.status)) ?? []
}

export function adjustmentImpact(view: MatchingRoundView, removeId?: number, replacement?: PairAssignment, extraSlot = false) {
  const before = selectedPairs(view)
  const conflicts = new Set(replacement && !extraSlot ? members(replacement) : [])
  const removed = before.filter(pair => pair.id === removeId || members(pair).some(id => conflicts.has(id)))
  const after: PairAssignment[] = before.filter(pair => !removed.includes(pair))
  if (replacement) after.push(replacement)
  const occupied = new Set(after.flatMap(members))
  const returned = [...new Set(removed.flatMap(members))].filter(id => !occupied.has(id))
  const valid = view.policy?.rules.every(rule => rule.maximumPairs == null ||
    after.filter(pair => pair.matchKind === rule.kind).length <= rule.maximumPairs) ?? true
  return { removed, returned, valid }
}
