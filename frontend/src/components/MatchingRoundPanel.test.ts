// @vitest-environment jsdom
import { mount } from '@vue/test-utils'
import { expect, it } from 'vitest'
import MatchingRoundPanel from './MatchingRoundPanel.vue'
import type { MatchCandidate, MatchingRoundView, MatchingStudent } from '@/types/api'

function pair(id: number, first: number, second: number, status: string): MatchCandidate {
  return {
    id, roundId: 10, periodId: 1, roundType: 'NORMAL', status, matchKind: 'PEER', matchedSubjects: ['线性代数'],
    navigatorApplicationId: first, navigatorUserId: first, navigatorName: `学生${first}`, navigatorMajor: '软件工程', navigatorGrade: 2026,
    climberApplicationId: second, climberUserId: second, climberName: `学生${second}`, climberMajor: '软件工程', climberGrade: 2026,
    navigatorDecision: 'PENDING', climberDecision: 'PENDING',
  }
}

const students: MatchingStudent[] = [1, 2, 3, 4].map(id => ({
  id, name: `学生${id}`, studentNo: `TEST${id}`, revision: 0, grade: 2026, role: 'CLIMBER', subjects: ['线性代数'],
  statement: '合成测试数据', pairingClue: false,
}))
const view: MatchingRoundView = {
  round: { id: 10, roundNo: 1, status: 'DRAFT', roundType: 'NORMAL', workspaceRevision: 2, draftRevision: 3, createdAt: '' },
  participantIds: students.map(student => student.id),
  policy: { freshmanGrade: 2026, allowExternalAnalysis: false, rules: [{
    kind: 'PEER', enabled: true, minimumCoverage: 1, allowOlderNavigator: false, requireHigherGpa: false,
  }] },
  candidates: [pair(1, 1, 2, 'SELECTED'), pair(2, 3, 4, 'SELECTED'), pair(3, 1, 3, 'GENERATED')],
  adjustments: [],
}

it('shows both displaced partners and sends the revision and reason for manual replacement', async () => {
  const wrapper = mount(MatchingRoundPanel, { props: { view, students, editable: true, busy: false } })
  expect(wrapper.get('table[aria-label="方案组合名单"]').text()).toContain('学生1（TEST1）')
  await wrapper.findAll('button').find(button => button.text() === '调整组合')!.trigger('click')
  await wrapper.get('dialog select').setValue('3')
  expect(wrapper.get('dialog').text()).toContain('回到待安排 · 学生2（TEST2）、学生4（TEST4）')
  expect(wrapper.get('dialog button.primary').attributes('disabled')).toBeDefined()
  await wrapper.get('dialog textarea').setValue('核实后调整空闲时间')
  await wrapper.get('dialog form').trigger('submit')
  expect(wrapper.emitted('adjust')).toEqual([[{ revision: 3, removeId: 1, selectId: 3, reason: '核实后调整空闲时间' }]])
  await wrapper.setProps({ editable: false })
  expect(wrapper.find('dialog').exists()).toBe(false)
  expect(wrapper.text()).not.toContain('调整组合')
})

it('opens on the waiting table and reads names and numbers from the round when students leave the current pool', async () => {
  const historical = {
    ...view,
    candidates: [pair(1, 1, 2, 'PUBLISHED')],
    participants: students,
  }
  const wrapper = mount(MatchingRoundPanel, { props: { view: historical, students: [], historical: true, editable: false, busy: false } })
  const table = wrapper.get('table[aria-label="待安排名单"]')
  expect(table.findAll('tbody tr').map(row => row.findAll('td').map(cell => cell.text()))).toEqual([
    ['学生3', 'TEST3', '该轮未安排'], ['学生4', 'TEST4', '该轮未安排'],
  ])
  await wrapper.get('button[aria-pressed="false"]').trigger('click')
  const pairTable = wrapper.get('table[aria-label="方案组合名单"]')
  expect(pairTable.text()).toContain('学生1（TEST1）')
  expect(pairTable.text()).toContain('学生2（TEST2）')
})
