// @vitest-environment jsdom
import { mount, flushPromises } from '@vue/test-utils'
import { expect, it, vi } from 'vitest'
import RegisterView from './RegisterView.vue'
import ProfileView from './ProfileView.vue'
import { COLLEGE, MAJORS } from '@/constants/academic'
vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }) }))
vi.mock('@/stores/auth', () => ({ useAuthStore: () => ({ loadUser: vi.fn() }) }))
vi.mock('@/services/http', () => ({ http: { get: vi.fn(async () => ({ data: { data: { college: '旧学院', major: '24软工', reviewStatus: 'APPROVED' } } })) }, apiErrorMessage: () => '失败' }))
it('registration fixes the college and allows exactly the three majors', () => {
  const wrapper = mount(RegisterView, { global: { stubs: ['RouterLink'] } })
  expect((wrapper.get('input[readonly]').element as HTMLInputElement).value).toBe(COLLEGE)
  expect(wrapper.get('select').findAll('option').slice(1).map(x => x.text())).toEqual([...MAJORS])
})
it('keeps legacy profile text visible and requires the student to choose a canonical major', async () => {
  const wrapper = mount(ProfileView); await flushPromises()
  expect(wrapper.text()).toContain('旧学院 · 24软工')
  expect(wrapper.get('select').element.value).toBe('')
  expect((wrapper.get('input[readonly]').element as HTMLInputElement).value).toBe(COLLEGE)
  expect(wrapper.text()).toContain('需重新审核')
})
