// @vitest-environment jsdom
import { mount } from '@vue/test-utils'
import { expect, it } from 'vitest'
import SubjectEditor from './SubjectEditor.vue'
import { validateSubjects } from '@/utils/subjects'

it('previews a paste before replacing the row and keeps spaces within a course name', async () => {
  const wrapper = mount(SubjectEditor, { props: { title: '需要帮扶的课程', modelValue: ['原科目'] } })
  await wrapper.get('input').trigger('paste', {
    clipboardData: { getData: () => '高等数学、English Listening；数据结构\n' },
  })
  expect(wrapper.text()).toContain('拆分预览')
  expect(wrapper.emitted('update:modelValue')).toBeUndefined()
  await wrapper.findAll('button').find(button => button.text() === '确认拆分')!.trigger('click')
  const values = ['高等数学', 'English Listening', '数据结构']
  expect(wrapper.emitted('update:modelValue')).toEqual([[values]])
  await wrapper.setProps({ modelValue: values })
  await wrapper.get('button[aria-label="删除第 2 门课程"]').trigger('click')
  expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([['高等数学', '数据结构']])
})

it('reports invalid rows and accepts a single course with spaces', () => {
  expect(validateSubjects(['English Listening'])).toBe('')
  expect(validateSubjects(['线性代数', ' '])).toContain('第 2 门课程')
  expect(validateSubjects(['English Listening', ' english listening '])).toContain('重复')
  expect(validateSubjects(['线性代数，高等数学'])).toContain('拆分')
  expect(validateSubjects(['课'.repeat(81)])).toContain('80 字')
  expect(validateSubjects(Array.from({ length: 9 }, (_, i) => `课程${i}`))).toContain('8 门')
})
