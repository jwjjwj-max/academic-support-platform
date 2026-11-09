// @vitest-environment jsdom
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, expect, it, vi } from 'vitest'
import { http } from '@/services/http'
import type { MatchingPolicy, MatchingRound, MatchingWorkspace } from '@/types/api'
import MatchingManageView from './MatchingManageView.vue'

vi.mock('@/services/http', () => ({
  http: { get: vi.fn(), put: vi.fn() },
  apiErrorMessage: () => '请求失败',
}))

const base = '/admin/matching/periods/1/workspace'
const policy: MatchingPolicy = {
  freshmanGrade: 2026, allowExternalAnalysis: false, rules: [{
    kind: 'PEER', enabled: true, minimumCoverage: 1, allowOlderNavigator: false, requireHigherGpa: false,
  }],
}
const makeRound = (roundNo: number, status: MatchingRound['status']): MatchingRound => ({
  id: roundNo, roundNo, status, roundType: 'NORMAL', workspaceRevision: 1, draftRevision: 0, createdAt: '',
})
let workspace: MatchingWorkspace
let rounds: MatchingRound[]

beforeEach(() => {
  workspace = { settings: { revision: 1, policy: structuredClone(policy) }, students: [], exclusions: [] }
  rounds = [makeRound(2, 'DRAFT'), makeRound(1, 'SUPERSEDED')]
  vi.mocked(http.get).mockImplementation(async url => {
    const data = url === '/periods' ? [{ id: 1, name: '本期', status: 'MATCHING' }]
      : url === '/admin/matching/deepseek' ? { encryptionReady: true, configured: false, model: '示例' }
      : url === base ? workspace
      : url === `${base}/rounds` ? rounds
      : { round: rounds.find(round => url.endsWith(`/${round.id}`)), policy, participantIds: [], candidates: [], adjustments: [] }
    return { data: { data } }
  })
  vi.mocked(http.put).mockImplementation(async (_url, body) => {
    workspace = { ...workspace, settings: { revision: workspace.settings.revision + 1, policy: (body as { policy: MatchingPolicy }).policy } }
    return { data: { data: workspace.settings } }
  })
})

it('shows only the selected panel and keeps editing controls out of round history', async () => {
  const wrapper = mount(MatchingManageView, { attachTo: document.body })
  await flushPromises()
  try {
    const panels = () => wrapper.findAll('[data-panel]').filter(panel => panel.isVisible()).map(panel => panel.attributes('data-panel'))
    const button = (label: string) => wrapper.findAll('button').find(item => item.text() === label)!
    expect(panels()).toEqual(['trial'])
    await button('规则配置').trigger('click')
    expect(panels()).toEqual(['rules'])
    expect(wrapper.get('[data-panel="rules"]').text()).toContain('禁止组合')
    await button('DeepSeek 分析').trigger('click')
    expect(panels()).toEqual(['evidence'])
    expect(wrapper.get('[data-panel="evidence"]').text()).not.toContain('禁止组合')
    await button('轮次记录').trigger('click')
    expect(panels()).toEqual(['history'])
    const history = wrapper.get('[data-panel="history"]')
    expect(history.text()).not.toContain('第 2 轮')
    await button('查看方案').trigger('click')
    await flushPromises()
    expect(history.text()).toContain('第 1 轮规则快照')
    expect(history.text()).not.toContain('与上轮对比')
    expect(history.find('input').exists()).toBe(false)
  } finally {
    wrapper.unmount()
  }
})

it('saves outbound permission independently of an unfinished matching rule edit', async () => {
  const wrapper = mount(MatchingManageView)
  await flushPromises()
  const button = (label: string) => wrapper.findAll('button').find(item => item.text() === label)!
  await button('规则配置').trigger('click')
  const year = wrapper.get('[data-panel="rules"] input[type="number"]')
  await year.setValue('2027')
  await button('DeepSeek 分析').trigger('click')
  await wrapper.get('[data-panel="evidence"] .permission-actions input').setValue(true)
  await wrapper.get('[data-panel="evidence"] .permission-actions').trigger('submit')
  await flushPromises()
  expect(http.put).toHaveBeenLastCalledWith(`${base}/policy`, { revision: 1, policy: { ...policy, allowExternalAnalysis: true } })
  await button('规则配置').trigger('click')
  expect((year.element as HTMLInputElement).value).toBe('2027')
  await wrapper.get('[data-panel="rules"] form').trigger('submit')
  await flushPromises()
  expect(http.put).toHaveBeenLastCalledWith(`${base}/policy`, {
    revision: 2, policy: { ...policy, freshmanGrade: 2027, allowExternalAnalysis: true, rules: policy.rules.map(rule => ({ ...rule, maximumPairs: null })) },
  })
  wrapper.unmount()
})
