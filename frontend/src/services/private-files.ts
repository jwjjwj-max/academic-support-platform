import { http } from './http'

/** The application token is sent only to our API, never to object storage. */
export async function privateFileBlob(path: string): Promise<Blob> {
  const endpoint = path.replace(/^\/api/, '')
  const access = await http.get<{ data: string }>(`${endpoint}/access`)
  const url = access.data.data
  if (url.startsWith('/api/')) {
    return (await http.get<Blob>(url.replace(/^\/api/, ''), { responseType: 'blob', timeout: 60_000 })).data
  }
  if (new URL(url).protocol !== 'https:') throw new Error('Invalid private file URL')
  const response = await fetch(url, { credentials: 'omit', referrerPolicy: 'no-referrer', cache: 'no-store' })
  if (!response.ok) throw new Error('Private file access expired or unavailable')
  return response.blob()
}

// Retain the session after a network error so a retry confirms the same upload.
const pending = new WeakMap<File, { fingerprint: string; id: string; uploaded: boolean }>()
export async function uploadPrivate<T>(path: string, form: FormData, metadata: Record<string, unknown>) {
  const file = form.get('file') as File
  const fingerprint = JSON.stringify(metadata)
  let session = pending.get(file)
  if (!session || session.fingerprint !== fingerprint) {
    const capabilities = await http.get<{ data: { directUpload: boolean } }>('/storage/uploads/capabilities')
    if (!capabilities.data.data.directUpload) return http.post<T>(path, form, { timeout: 120_000 })
    const request = { ...metadata, filename: file.name, mimeType: file.type || 'application/octet-stream', size: file.size }
    const signed = await http.post<{ data: { id: string; url: string } }>('/storage/uploads', request)
    session = { fingerprint, id: signed.data.data.id, uploaded: false }
    pending.set(file, session)
    const response = await fetch(signed.data.data.url, {
      method: 'PUT', body: file, headers: { 'Content-Type': request.mimeType },
      credentials: 'omit', referrerPolicy: 'no-referrer',
    })
    if (!response.ok) { pending.delete(file); throw new Error('文件上传失败，请重试') }
    session.uploaded = true
  }
  if (!session.uploaded) { pending.delete(file); throw new Error('文件上传未完成，请重试') }
  const response = await http.post<T>(`/storage/uploads/${session.id}/confirm`, {}, { timeout: 120_000 })
  pending.delete(file)
  return response
}
