import type { MatchCandidate, MatchKind, MatchingRoundView } from '@/types/api'

const labels: Record<MatchKind, string> = {
  PEER: '大一双向互助', REGULAR: '高年级常规帮扶', FRESHMAN_SUPPORT: '大二补位大一', LEGACY: '领航帮扶',
}

export function matchKindLabel(kind?: MatchKind) {
  return labels[kind ?? 'LEGACY']
}

export function members(pair: MatchCandidate) {
  return [pair.navigatorApplicationId, pair.climberApplicationId]
}

export function pairName(pair: MatchCandidate) {
  return `${pair.navigatorName} ${pair.matchKind === 'PEER' ? '↔' : '→'} ${pair.climberName}`
}

export function selectedPairs(view?: MatchingRoundView | null) {
  return view?.candidates.filter(pair => ['SELECTED', 'PUBLISHED', 'CONFIRMED', 'REJECTED'].includes(pair.status)) ?? []
}

export function adjustmentImpact(view: MatchingRoundView, removeId?: number, replacement?: MatchCandidate) {
  const before = selectedPairs(view)
  const conflicts = new Set(replacement ? members(replacement) : [])
  const removed = before.filter(pair => pair.id === removeId || members(pair).some(id => conflicts.has(id)))
  const after = before.filter(pair => !removed.includes(pair))
  if (replacement) after.push(replacement)
  const occupied = new Set(after.flatMap(members))
  const returned = [...new Set(removed.flatMap(members))].filter(id => !occupied.has(id))
  const valid = view.policy?.rules.every(rule => rule.maximumPairs == null ||
    after.filter(pair => pair.matchKind === rule.kind).length <= rule.maximumPairs) ?? true
  return { removed, returned, valid }
}
