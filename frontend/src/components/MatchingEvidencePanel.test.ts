// @vitest-environment jsdom
import { mount } from '@vue/test-utils'
import { expect, it } from 'vitest'
import MatchingEvidencePanel from './MatchingEvidencePanel.vue'
import type { MatchingStudent, MatchingWorkspace } from '@/types/api'

const student = (id: number, name: string, preview: string): MatchingStudent => ({
  id, name, revision: 1, studentNo: `TEST${id}`, grade: 2026, role: 'CLIMBER',
  statement: '合成原文', externalPreview: preview, pairingClue: false, subjects: ['线性代数'],
})
const workspace: MatchingWorkspace = {
  settings: { revision: 1, policy: { freshmanGrade: 2026, allowExternalAnalysis: true, rules: [] } },
  students: [
    student(1, '测试甲', '可以辅导编程入门。'),
    student(2, '测试乙', '可以辅导英语听说。'),
    student(3, '空预览', ' '),
    { ...student(4, '已分析', '可以辅导线性代数。'), evidence: { applicationRevision: 1, reviewed: true, facts: { needs: [], offers: [] } } },
  ],
  exclusions: [],
}
const props = { workspace, provider: { encryptionReady: true, configured: true, model: '示例' },
  editable: true, permissionEditable: true, busy: false }

it('confirms the eligible filtered batch without submitting, then sends only confirmed previews in scope', async () => {
  const wrapper = mount(MatchingEvidencePanel, { props })
  const [confirm, analyze] = wrapper.findAll('.batch-actions button')
  expect(wrapper.get('.batch-analysis').text()).toContain('当前筛选可确认 2 份')
  expect(analyze.attributes('disabled')).toBeDefined()
  await confirm.trigger('click')
  expect(wrapper.emitted('analyze')).toBeUndefined()
  expect(analyze.text()).toContain('2 份')
  const search = wrapper.get('input[type="search"]')
  await search.setValue('测试甲')
  expect(analyze.text()).toContain('1 份')
  await analyze.trigger('click')
  expect(wrapper.emitted('analyze')).toEqual([[[workspace.students[0]]]])
  await search.setValue('')
  await wrapper.get('.batch-preview input[type="checkbox"]').setValue(false)
  expect(analyze.text()).toContain('1 份')
  wrapper.unmount()
})

it('requires a new confirmation after an approved preview changes and blocks analysis without permission', async () => {
  const wrapper = mount(MatchingEvidencePanel, { props })
  const [confirm, analyze] = wrapper.findAll('.batch-actions button')
  await confirm.trigger('click')
  await wrapper.setProps({ workspace: { ...workspace, students: [{ ...workspace.students[0], externalPreview: '修改后的预览' }] } })
  expect(analyze.attributes('disabled')).toBeDefined()
  await wrapper.setProps({ provider: { ...props.provider, configured: false } })
  expect(confirm.attributes('disabled')).toBeDefined()
  expect(wrapper.emitted('analyze')).toBeUndefined()
  wrapper.unmount()
})
