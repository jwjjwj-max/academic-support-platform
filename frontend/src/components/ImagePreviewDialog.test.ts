// @vitest-environment jsdom
import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { privateFileBlob } from '@/services/private-files'
import type { PairPhoto } from '@/types/api'
import ImagePreviewDialog from './ImagePreviewDialog.vue'

vi.mock('@/services/private-files', () => ({ privateFileBlob: vi.fn() }))

const photo: PairPhoto = {
  id: 7,
  uploaderId: 2,
  uploaderName: '攀登者01',
  originalFilename: '过程照片.jpg',
  mimeType: 'image/jpeg',
  fileSize: 128,
  status: 'ADMIN_PENDING',
  revisionNo: 1,
  submittedAt: '2026-09-07T16:00:00',
  canPeerConfirm: false,
  canResubmit: false,
  downloadUrl: '/api/materials/photos/7/file',
}

describe('站内图片预览', () => {
  beforeEach(() => {
    vi.mocked(privateFileBlob).mockResolvedValue(new Blob(['image'], { type: 'image/jpeg' }))
    Object.defineProperty(URL, 'createObjectURL', { configurable: true, value: vi.fn(() => 'blob:preview') })
    Object.defineProperty(URL, 'revokeObjectURL', { configurable: true, value: vi.fn() })
  })

  afterEach(() => vi.clearAllMocks())

  it('在当前页面显示图片，并在重复打开时复用已加载内容', async () => {
    const wrapper = mount(ImagePreviewDialog, { props: { photo } })
    await flushPromises()

    expect(privateFileBlob).toHaveBeenCalledWith('/api/materials/photos/7/file')
    expect(wrapper.get('img').attributes('src')).toBe('blob:preview')
    expect(wrapper.text()).toContain('过程照片.jpg')

    await wrapper.setProps({ photo: null })
    await wrapper.setProps({ photo })
    await flushPromises()

    expect(privateFileBlob).toHaveBeenCalledTimes(1)
    await wrapper.get('button[aria-label="关闭图片预览"]').trigger('click')
    expect(wrapper.emitted('close')).toHaveLength(1)
  })
})
