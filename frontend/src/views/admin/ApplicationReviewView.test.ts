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
  await wrapper.get('input[type=search]').setValue(' S21 ')
  expect(wrapper.text()).toContain('当前筛选共 1 人')
  expect(wrapper.text()).toContain('报名学生21')
  expect(wrapper.text()).toContain('第 1 / 1 页')
  expect(wrapper.get('[data-testid="summary-total"]').text()).toBe('21')
  await wrapper.findAll('button').find(b => b.text() === '清空搜索')!.trigger('click')
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
  await wrapper.get('input[type=search]').setValue('待审核')
  expect(wrapper.text()).toContain('没有符合条件的报名')
  expect(wrapper.text()).toContain('已通过')
  await wrapper.findAll('button').find(b => b.text() === '查看全部审核状态')!.trigger('click')
  expect(wrapper.findAll('section.card:last-of-type tbody tr')).toHaveLength(1)
  expect(wrapper.get('section.card:last-of-type tbody').text()).toContain('待审核学生')
  await wrapper.get('select[aria-label="期次"]').setValue(2); await flushPromises()
  expect(wrapper.get('[data-testid="summary-total"]').text()).toBe('0')
  expect(http.get).toHaveBeenLastCalledWith('/admin/applications', { params: { periodId: 2 } })
})

it('allows changing an approved student’s role before publication and requires courses for the new role', async () => {
  vi.mocked(http.post).mockReset().mockResolvedValue({})
  vi.mocked(http.get).mockImplementation(async url => ({ data: { data: url === '/periods'
    ? [{ id: 1, name: '本期', shortName: '本期', status: 'MATCHING' }]
    : [{ id: 11, realName: '测试学生', studentNo: 'TEST11', grade: 2025, participantRole: 'CLIMBER',
      cumulativeGpa: 4.3, subjects: ['高等数学'], applicationStatus: 'APPROVED', selectionStatus: 'MATCHING_POOL',
      revision: 5, roleChangeAllowed: true }],
  } }))
  const wrapper = mount(ApplicationReviewView)
  await flushPromises()
  await wrapper.get('select[aria-label="审核状态"]').setValue('APPROVED')
  await wrapper.findAll('button').find(button => button.text() === '改报名身份')!.trigger('click')
  const dialog = wrapper.get('dialog[aria-label="更正报名身份"]')
  await dialog.get('select').setValue('NAVIGATOR')
  expect(dialog.text()).toContain('新身份可提供帮扶的课程')
  await dialog.get('textarea').setValue('核验领航资格')
  await dialog.get('form').trigger('submit')
  expect(vi.mocked(http.post)).not.toHaveBeenCalled()
  await dialog.findAll('button').find(button => button.text() === '确认原科目适用于新身份')!.trigger('click')
  await dialog.get('form').trigger('submit')
  await flushPromises()
  expect(http.post).toHaveBeenLastCalledWith('/admin/applications/11/role-correction', {
    revision: 5, participantRole: 'NAVIGATOR', subjects: ['高等数学'], reason: '核验领航资格',
  })
  expect(wrapper.text()).toContain('请重新试算')
  wrapper.unmount()
})
