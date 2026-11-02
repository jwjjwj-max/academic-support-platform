// @vitest-environment jsdom
import { mount } from '@vue/test-utils'
import { expect, it } from 'vitest'
import ApplicationStatistics from './ApplicationStatistics.vue'
import type { ParticipationApplication } from '@/types/api'
const applications = [
  { id: 1, grade: 2024, participantRole: 'NAVIGATOR', applicationStatus: 'APPROVED', selectionStatus: 'MATCHING_POOL' },
  { id: 2, grade: 2024, participantRole: 'CLIMBER', applicationStatus: 'APPROVED', selectionStatus: 'WAITLIST' },
  { id: 3, grade: 2025, participantRole: 'CLIMBER', applicationStatus: 'SUBMITTED', selectionStatus: 'UNDECIDED' },
  { id: 4, grade: 2026, participantRole: 'NAVIGATOR', applicationStatus: 'UNDER_REVIEW', selectionStatus: 'UNDECIDED' },
  { id: 5, grade: 2026, participantRole: 'CLIMBER', applicationStatus: 'REJECTED', selectionStatus: 'MATCHING_POOL' },
] as ParticipationApplication[]
it('summarizes every review status but defaults the grade breakdown to approved applications', () => {
  const wrapper = mount(ApplicationStatistics, { props: { applications, periodName: '本期', loading: false } })
  expect(wrapper.get('[data-testid="summary-total"]').text()).toBe('5')
  expect(wrapper.get('[data-testid="summary-navigators"]').text()).toBe('2')
  expect(wrapper.get('[data-testid="summary-climbers"]').text()).toBe('3')
  expect(wrapper.get('[data-testid="summary-pending"]').text()).toBe('2')
  expect(wrapper.get('select').element.value).toBe('APPROVED')
  expect(wrapper.findAll('tbody tr').map(row => row.text())).toEqual(['2024级112'])
  expect(wrapper.get('tfoot').text()).toContain('合计112')
})
it('switches between all, approved and matching-pool counts without changing the overall cards', async () => {
  const wrapper = mount(ApplicationStatistics, { props: { applications, periodName: '本期', loading: false } })
  await wrapper.get('select').setValue('ALL')
  expect(wrapper.findAll('tbody tr').map(row => row.text())).toEqual(['2024级112','2025级011','2026级112'])
  await wrapper.get('select').setValue('POOL')
  expect(wrapper.findAll('tbody tr').map(row => row.text())).toEqual(['2024级101'])
  expect(wrapper.get('[data-testid="summary-total"]').text()).toBe('5')
})
it('updates for another period and handles zero approvals without misleading chart scales', async () => {
  const wrapper = mount(ApplicationStatistics, { props: { applications, periodName: '本期', loading: false } })
  await wrapper.setProps({ applications: [applications[2]], periodName: '下一期' })
  expect(wrapper.text()).toContain('下一期')
  expect(wrapper.text()).toContain('当前口径暂无报名')
  expect(wrapper.find('table').exists()).toBe(false)
  await wrapper.get('select').setValue('ALL')
  expect(wrapper.findAll('tbody tr').map(row => row.text())).toEqual(['2025级011'])
})
it('shows loading or failure instead of presenting stale counts as current results', async () => {
  const wrapper = mount(ApplicationStatistics, { props: { applications, periodName: '本期', loading: true } })
  expect(wrapper.find('[data-testid="summary-total"]').exists()).toBe(false)
  await wrapper.setProps({ loading: false, unavailable: true })
  expect(wrapper.text()).toContain('统计暂不可用')
  expect(wrapper.find('table').exists()).toBe(false)
})
