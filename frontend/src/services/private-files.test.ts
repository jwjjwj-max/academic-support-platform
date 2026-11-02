// @vitest-environment jsdom
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { http } from './http'
import { privateFileBlob, uploadPrivate } from './private-files'
vi.mock('./http', () => ({ http: { get: vi.fn(), post: vi.fn() } }))
beforeEach(() => { vi.resetAllMocks(); vi.stubGlobal('fetch', vi.fn()) })
describe('private files', () => {
  it('sends application authorization only to the API and uses an anonymous fetch for the signed object', async () => {
    vi.mocked(http.get).mockResolvedValue({ data: { data: 'https://storage.example/file' } })
    const blob = new Blob(['image'], { type: 'image/png' })
    vi.mocked(fetch).mockResolvedValue({ ok: true, blob: async () => blob } as Response)
    expect(await privateFileBlob('/api/materials/photos/1/file')).toBe(blob)
    expect(fetch).toHaveBeenCalledWith('https://storage.example/file', { credentials: 'omit', referrerPolicy: 'no-referrer', cache: 'no-store' })
  })
  it('uses the authorized same-origin path when direct reads are disabled', async () => {
    const blob = new Blob(['x'])
    vi.mocked(http.get).mockResolvedValueOnce({ data: { data: '/api/moral-files/1/file' } }).mockResolvedValueOnce({ data: blob })
    expect(await privateFileBlob('/api/moral-files/1/file')).toBe(blob)
    expect(fetch).not.toHaveBeenCalled()
  })
  it('reports an expired object signature', async () => {
    vi.mocked(http.get).mockResolvedValue({ data: { data: 'https://storage.example/file' } })
    vi.mocked(fetch).mockResolvedValue({ ok: false, status: 403 } as Response)
    await expect(privateFileBlob('/api/materials/photos/1/file')).rejects.toThrow('expired')
  })
  it('keeps multipart upload available when bucket CORS is unavailable', async () => {
    vi.mocked(http.get).mockResolvedValue({ data: { data: { directUpload: false } } })
    const form = new FormData(); form.set('file', new File(['x'], 'x.csv'))
    await uploadPrivate('/admin/grades/preview', form, { kind: 'GRADE' })
    expect(http.post).toHaveBeenCalledWith('/admin/grades/preview', form, { timeout: 120_000 })
    expect(fetch).not.toHaveBeenCalled()
  })
  it('retries confirmation using the same upload session after a lost response', async () => {
    vi.mocked(http.get).mockResolvedValue({ data: { data: { directUpload: true } } })
    vi.mocked(http.post).mockResolvedValueOnce({ data: { data: { id: 'session-1', url: 'https://storage.example/upload' } } }).mockRejectedValueOnce(new Error('network')).mockResolvedValueOnce({ data: { data: 'saved' } })
    vi.mocked(fetch).mockResolvedValue({ ok: true } as Response)
    const form = new FormData(); form.set('file', new File(['x'], 'x.csv', { type: 'text/csv' }))
    await expect(uploadPrivate('/admin/grades/preview', form, { kind: 'GRADE' })).rejects.toThrow('network')
    await uploadPrivate('/admin/grades/preview', form, { kind: 'GRADE' })
    expect(fetch).toHaveBeenCalledTimes(1)
    expect(http.post).toHaveBeenLastCalledWith('/storage/uploads/session-1/confirm', {}, { timeout: 120_000 })
  })
})
