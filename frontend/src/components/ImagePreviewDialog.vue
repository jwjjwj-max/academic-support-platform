<script setup lang="ts">
import { isAxiosError, type AxiosProgressEvent } from 'axios'
import { onBeforeUnmount, ref, watch } from 'vue'
import { apiErrorMessage } from '@/services/http'
import { privateFileBlob } from '@/services/private-files'
import type { PairPhoto } from '@/types/api'

const props = defineProps<{ photo: PairPhoto | null }>()
const emit = defineEmits<{ close: [] }>()

const previewUrl = ref('')
const loading = ref(false)
const progress = ref<number | null>(null)
const error = ref('')
const previewCache = new Map<string, string>()
let activeRequest: AbortController | undefined

function cacheKey(photo: PairPhoto) {
  return `${photo.id}:${photo.revisionNo}`
}

function remember(key: string, url: string) {
  if (previewCache.size >= 8) {
    const oldest = previewCache.entries().next().value as [string, string] | undefined
    if (oldest) {
      URL.revokeObjectURL(oldest[1])
      previewCache.delete(oldest[0])
    }
  }
  previewCache.set(key, url)
}

function cancelPreview() {
  const request = activeRequest
  activeRequest = undefined
  request?.abort()
}

function retryable(cause: unknown) {
  if (!isAxiosError(cause)) return cause instanceof TypeError
  return cause.code === 'ERR_NETWORK' || [502, 503, 504].includes(cause.response?.status ?? 0)
}

async function loadPreview(photo: PairPhoto | null) {
  cancelPreview()
  previewUrl.value = ''
  error.value = ''
  progress.value = null
  if (!photo) {
    loading.value = false
    return
  }

  const key = cacheKey(photo)
  const cached = previewCache.get(key)
  if (cached) {
    previewCache.delete(key)
    previewCache.set(key, cached)
    previewUrl.value = cached
    loading.value = false
    return
  }

  const request = new AbortController()
  activeRequest = request
  const timeout = setTimeout(() => request.abort(), 60_000)
  loading.value = true
  const options = {
    signal: request.signal,
    onDownloadProgress: ({ loaded, total }: AxiosProgressEvent) => {
      const size = total || photo.fileSize
      if (activeRequest === request && size > 0) {
        progress.value = Math.min(99, Math.floor(100 * loaded / size))
      }
    },
  }
  const fetchPreview = () => privateFileBlob(photo.downloadUrl, options)
  try {
    const blob = await fetchPreview().catch((cause: unknown) => {
      if (request.signal.aborted || !retryable(cause)) throw cause
      progress.value = null
      return fetchPreview()
    })
    if (activeRequest !== request) return
    if (!blob.type.startsWith('image/')) {
      error.value = '该材料不是可预览的图片。'
      return
    }
    const url = URL.createObjectURL(blob)
    remember(key, url)
    previewUrl.value = url
  } catch (cause) {
    if (activeRequest === request) {
      error.value = request.signal.aborted ? '图片加载超时，请重试。' : apiErrorMessage(cause)
    }
  } finally {
    clearTimeout(timeout)
    if (activeRequest === request) {
      activeRequest = undefined
      loading.value = false
    }
  }
}

function handleDecodeError(event: Event) {
  const photo = props.photo
  const url = (event.currentTarget as HTMLImageElement).getAttribute('src')
  if (!photo || !url || url !== previewUrl.value) return
  previewCache.delete(cacheKey(photo))
  URL.revokeObjectURL(url)
  previewUrl.value = ''
  error.value = '图片已下载，但浏览器无法打开。'
}

watch(() => props.photo, loadPreview, { immediate: true })

onBeforeUnmount(() => {
  cancelPreview()
  previewCache.forEach((url) => URL.revokeObjectURL(url))
  previewCache.clear()
})
</script>

<template>
  <dialog
    :open="Boolean(photo)"
    class="modal image-preview-modal"
    aria-label="站内图片预览"
    @click.self="emit('close')"
    @keydown.esc.window="emit('close')"
  >
    <header>
      <div>
        <span class="eyebrow">过程照片</span>
        <h2>{{ photo?.originalFilename }}</h2>
      </div>
      <button type="button" class="icon-ghost modal-close" aria-label="关闭图片预览" @click="emit('close')">×</button>
    </header>
    <div class="modal-body image-preview-body">
      <div v-if="loading" class="image-preview-state">
        <div role="status">正在从服务器加载图片…</div>
        <progress v-if="progress !== null" :value="progress" max="100" aria-label="图片下载进度" />
      </div>
      <div v-else-if="error" class="image-preview-state image-preview-error">
        <div role="alert">{{ error }}</div>
        <button type="button" class="button secondary small" @click="loadPreview(photo)">重试加载</button>
      </div>
      <img v-else-if="previewUrl" class="image-preview-image" :src="previewUrl" :alt="photo?.originalFilename || '过程照片'" @error="handleDecodeError" />
    </div>
    <footer>
      <span v-if="photo" class="image-preview-caption">{{ photo.uploaderName }}提交 · 第{{ photo.revisionNo }}版</span>
      <button type="button" class="button primary" @click="emit('close')">关闭</button>
    </footer>
  </dialog>
</template>

<style scoped>
.image-preview-state progress {
  width: min(220px, 100%);
  margin-top: 14px;
  accent-color: #b2dcfb;
}

.image-preview-error button {
  margin-top: 14px;
}
</style>
