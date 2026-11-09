// @vitest-environment jsdom
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, expect, it, vi } from 'vitest'
import { http } from '@/services/http'
import type { MatchingPolicy, MatchingRound, MatchingStudent, MatchingWorkspace } from '@/types/api'
import MatchingManageView from './MatchingManageView.vue'

vi.mock('@/services/http', () => ({
  http: { get: vi.fn(), put: vi.fn(), post: vi.fn() },
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
let providerConfigured: boolean

beforeEach(() => {
  workspace = { settings: { revision: 1, policy: structuredClone(policy) }, students: [], exclusions: [] }
  rounds = [makeRound(2, 'DRAFT'), makeRound(1, 'SUPERSEDED')]
  providerConfigured = false
  vi.mocked(http.get).mockImplementation(async url => {
    const data = url === '/periods' ? [{ id: 1, name: '本期', status: 'MATCHING' }]
      : url === '/admin/matching/deepseek' ? { encryptionReady: true, configured: providerConfigured, model: '示例' }
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

it('continues a batch past a changed preview and reports which item needs attention', async () => {
  providerConfigured = true
  workspace.settings.policy.allowExternalAnalysis = true
  workspace.students = [1, 2].map((id): MatchingStudent => ({
    id, revision: 1, grade: 2026, role: 'CLIMBER', name: `测试学生${id}`, studentNo: `TEST${id}`,
    statement: '合成原文', externalPreview: `脱敏预览${id}`, subjects: [], pairingClue: false,
  }))
  vi.mocked(http.post).mockReset()
  vi.mocked(http.post).mockRejectedValueOnce({ isAxiosError: true, response: { data: { code: 'MATCHING_PREVIEW_CHANGED' } } })
  vi.mocked(http.post).mockResolvedValueOnce({})
  const wrapper = mount(MatchingManageView)
  await flushPromises()
  await wrapper.findAll('button').find(item => item.text() === 'DeepSeek 分析')!.trigger('click')
  const [confirm, analyze] = wrapper.findAll('.batch-actions button')
  await confirm.trigger('click')
  expect(http.post).not.toHaveBeenCalled()
  await analyze.trigger('click')
  await flushPromises()
  expect(http.post).toHaveBeenCalledTimes(2)
  expect(http.post).toHaveBeenLastCalledWith(`${base}/students/2/analyze`, {
    applicationRevision: 1, confirmedPreview: '脱敏预览2',
  }, { timeout: 32_000 })
  expect(wrapper.get('[role="alert"]').text()).toContain('1 份分析未完成')
  expect(wrapper.get('.evidence-row').text()).toContain('分析未完成')
  wrapper.unmount()
})
