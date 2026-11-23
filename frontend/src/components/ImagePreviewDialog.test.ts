// @vitest-environment jsdom
import { flushPromises, mount } from '@vue/test-utils'
import { AxiosError } from 'axios'
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

    expect(privateFileBlob).toHaveBeenCalledWith('/api/materials/photos/7/file', {
      signal: expect.any(AbortSignal), onDownloadProgress: expect.any(Function),
    })
    expect(wrapper.get('img').attributes('src')).toBe('blob:preview')
    expect(wrapper.text()).toContain('过程照片.jpg')

    await wrapper.setProps({ photo: null })
    await wrapper.setProps({ photo })
    await flushPromises()

    expect(privateFileBlob).toHaveBeenCalledTimes(1)
    await wrapper.get('button[aria-label="关闭图片预览"]').trigger('click')
    expect(wrapper.emitted('close')).toHaveLength(1)
  })

  it('在下载连接中断时自动重试一次', async () => {
    vi.mocked(privateFileBlob).mockRejectedValueOnce(new AxiosError('Connection closed', 'ERR_NETWORK'))
    const wrapper = mount(ImagePreviewDialog, { props: { photo } })
    await flushPromises()

    expect(privateFileBlob).toHaveBeenCalledTimes(2)
    expect(wrapper.get('img').attributes('src')).toBe('blob:preview')
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
  })

  it('持续中断后允许手动重试', async () => {
    const interrupted = new AxiosError('Connection closed', 'ERR_NETWORK')
    vi.mocked(privateFileBlob).mockRejectedValueOnce(interrupted).mockRejectedValueOnce(interrupted)
    const wrapper = mount(ImagePreviewDialog, { props: { photo } })
    await flushPromises()

    expect(privateFileBlob).toHaveBeenCalledTimes(2)
    expect(wrapper.get('[role="alert"]').text()).toContain('请求失败')
    await wrapper.get('.image-preview-error button').trigger('click')
    await flushPromises()

    expect(privateFileBlob).toHaveBeenCalledTimes(3)
    expect(wrapper.get('img').attributes('src')).toBe('blob:preview')
  })

  it('清除无法解码的缓存，以便重新下载', async () => {
    const wrapper = mount(ImagePreviewDialog, { props: { photo } })
    await flushPromises()
    await wrapper.get('img').trigger('error')

    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:preview')
    expect(wrapper.get('[role="alert"]').text()).toContain('浏览器无法打开')
    await wrapper.get('.image-preview-error button').trigger('click')
    await flushPromises()
    expect(privateFileBlob).toHaveBeenCalledTimes(2)
  })

  it('关闭预览时取消下载并丢弃迟到的响应', async () => {
    let finish!: (blob: Blob) => void
    vi.mocked(privateFileBlob).mockReturnValueOnce(new Promise(resolve => { finish = resolve }))
    const wrapper = mount(ImagePreviewDialog, { props: { photo } })
    const signal = vi.mocked(privateFileBlob).mock.calls[0][1]?.signal
    await wrapper.setProps({ photo: null })
    finish(new Blob(['image'], { type: 'image/jpeg' }))
    await flushPromises()

    expect(signal?.aborted).toBe(true)
    expect(URL.createObjectURL).not.toHaveBeenCalled()
  })
})
