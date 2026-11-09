// @vitest-environment jsdom
import { mount, flushPromises } from '@vue/test-utils'
import { beforeEach, expect, it, vi } from 'vitest'
import { http } from '@/services/http'
import type { StudentProfile } from '@/types/api'
import StudentReviewView from './StudentReviewView.vue'
vi.mock('@/services/http', () => ({ http: { get: vi.fn(), post: vi.fn() }, apiErrorMessage: () => '加载失败' }))
const student = (id: number) => ({ userId: id, realName: `学生${id}`, studentNo: `S${id}`, college: '信息技术与人工智能学院', major: '软件工程', grade: 2026, wechat: 'test', reviewStatus: 'PENDING' })
beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(http.get).mockImplementation(async (_url, config) => {
    const page = (config?.params as { page?: number })?.page || 1
    return { data: { data: { records: page === 1 ? Array.from({length: 20}, (_, i) => student(i + 1)) : [student(21)], total: 21 } } }
  })
})
it('shows total count and fetches the next server page instead of hiding student 21', async () => {
  const wrapper = mount(StudentReviewView); await flushPromises()
  expect(wrapper.text()).toContain('当前筛选共 21 人')
  const next = wrapper.findAll('button').find(b => b.text() === '下一页')!
  await next.trigger('click'); await flushPromises()
  expect(http.get).toHaveBeenLastCalledWith('/admin/students', { params: { status: 'PENDING', page: 2, size: 20 } })
  expect(wrapper.text()).toContain('学生21')
  expect(wrapper.text()).toContain('第 2 / 2 页')
  expect(next.attributes('disabled')).toBeDefined()
  await wrapper.get('select[aria-label="学生审核状态"]').setValue('')
  await flushPromises()
  expect(http.get).toHaveBeenLastCalledWith('/admin/students', { params: { status: undefined, page: 1, size: 20 } })
})
it('returns to the preceding page after reviewing the final item on a filtered page', async () => {
  const wrapper = mount(StudentReviewView); await flushPromises()
  await wrapper.findAll('button').find(b => b.text() === '下一页')!.trigger('click'); await flushPromises()
  vi.mocked(http.get).mockResolvedValue({ data: { data: { records: [student(1)], total: 20 } } })
  await wrapper.findAll('button').find(b => b.text() === '审核')!.trigger('click')
  await wrapper.get('dialog form').trigger('submit'); await flushPromises()
  expect(http.get).toHaveBeenLastCalledWith('/admin/students', { params: { status: 'PENDING', page: 1, size: 20 } })
  expect(wrapper.text()).toContain('第 1 / 1 页')
})

it('ignores an older response after a new search and preserves the keyword when expanding status', async () => {
  vi.useFakeTimers()
  const searchResponse = (records: StudentProfile[]) => ({ data: { data: { records, total: records.length } } })
  let deliverOld: (value: ReturnType<typeof searchResponse>) => void = () => {}
  vi.mocked(http.get).mockImplementationOnce(() => new Promise(resolve => { deliverOld = resolve }))
  vi.mocked(http.get).mockResolvedValueOnce(searchResponse([]))
  vi.mocked(http.get).mockResolvedValueOnce(searchResponse([student(21) as StudentProfile]))
  const wrapper = mount(StudentReviewView)
  try {
    await wrapper.get('input[type=search]').setValue('21')
    await vi.advanceTimersByTimeAsync(250)
    expect(http.get).toHaveBeenLastCalledWith('/admin/students', { params: { status: 'PENDING', page: 1, size: 20, keyword: '21' } })
    deliverOld(searchResponse([student(1) as StudentProfile]))
    await flushPromises()
    expect(wrapper.text()).not.toContain('学生1')
    expect(wrapper.text()).toContain('没有符合条件的学生')
    await wrapper.findAll('button').find(b => b.text() === '查看全部审核状态')!.trigger('click')
    await flushPromises()
    expect(http.get).toHaveBeenLastCalledWith('/admin/students', { params: { status: undefined, page: 1, size: 20, keyword: '21' } })
    expect(wrapper.text()).toContain('学生21')
  } finally {
    wrapper.unmount()
    vi.useRealTimers()
  }
})
