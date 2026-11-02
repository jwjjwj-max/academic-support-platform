// @vitest-environment jsdom
import { mount, flushPromises } from '@vue/test-utils'
import { expect, it, vi } from 'vitest'
import { http } from '@/services/http'
import ApplicationReviewView from './ApplicationReviewView.vue'
vi.mock('@/services/http', () => ({ http: { get: vi.fn(), post: vi.fn() }, apiErrorMessage: () => '加载失败' }))
it('paginates every matching application and resets the page when a filter changes', async () => {
  vi.mocked(http.get).mockImplementation(async url => ({ data: { data: url === '/periods' ? [{ id: 1, shortName: '本期', name: '本期', status: 'APPLICATION_REVIEW' }] : Array.from({ length: 21 }, (_, i) => ({ id: i+1, realName: `报名学生${i+1}`, studentNo: `S${i+1}`, participantRole: 'NAVIGATOR', cumulativeGpa: 3, subjects: [], applicationStatus: 'SUBMITTED', selectionStatus: 'PENDING' })) } }))
  const wrapper = mount(ApplicationReviewView); await flushPromises()
  expect(wrapper.text()).toContain('当前筛选共 21 人')
  expect(wrapper.findAll('section.card:last-of-type tbody tr')).toHaveLength(20)
  await wrapper.findAll('button').find(b => b.text() === '下一页')!.trigger('click')
  expect(wrapper.findAll('section.card:last-of-type tbody tr')).toHaveLength(1)
  expect(wrapper.text()).toContain('报名学生21')
  await wrapper.get('select[aria-label="审核状态"]').setValue(''); await flushPromises()
  expect(wrapper.text()).toContain('第 1 / 2 页')
  expect(http.get).toHaveBeenLastCalledWith('/admin/applications', { params: { periodId: 1 } })
})

it('keeps the period totals independent of the list status and refreshes them when the period changes', async () => {
  vi.clearAllMocks()
  vi.mocked(http.get).mockImplementation(async (url, config) => ({ data: { data: url === '/periods' ? [{ id: 1, shortName: '一期', name: '一期', status: 'REVIEW' }, { id: 2, shortName: '二期', name: '二期', status: 'REGISTRATION' }] : (config?.params as { periodId?: number })?.periodId === 2 ? [] : [
    { id: 1, grade: 2024, participantRole: 'NAVIGATOR', realName: '已通过学生', cumulativeGpa: 3, subjects: [], applicationStatus: 'APPROVED', selectionStatus: 'MATCHING_POOL' },
    { id: 2, grade: 2025, participantRole: 'CLIMBER', realName: '待审核学生', cumulativeGpa: 3, subjects: [], applicationStatus: 'SUBMITTED', selectionStatus: 'UNDECIDED' },
  ] } }))
  const wrapper = mount(ApplicationReviewView); await flushPromises()
  expect(wrapper.get('[data-testid="summary-total"]').text()).toBe('2')
  const calls = vi.mocked(http.get).mock.calls.length
  await wrapper.get('select[aria-label="审核状态"]').setValue('APPROVED')
  expect(wrapper.get('[data-testid="summary-total"]').text()).toBe('2')
  expect(vi.mocked(http.get).mock.calls).toHaveLength(calls)
  expect(wrapper.findAll('section.card:last-of-type tbody tr')).toHaveLength(1)
  await wrapper.get('select[aria-label="期次"]').setValue(2); await flushPromises()
  expect(wrapper.get('[data-testid="summary-total"]').text()).toBe('0')
  expect(http.get).toHaveBeenLastCalledWith('/admin/applications', { params: { periodId: 2 } })
})
