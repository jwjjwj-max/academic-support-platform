<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { apiErrorMessage } from '@/services/http'
import { privateFileBlob } from '@/services/private-files'
import type { PairPhoto } from '@/types/api'

const props = defineProps<{ photo: PairPhoto | null }>()
const emit = defineEmits<{ close: [] }>()

const previewUrl = ref('')
const loading = ref(false)
const error = ref('')
const previewCache = new Map<string, string>()
let requestVersion = 0

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

watch(() => props.photo, async (photo) => {
  const currentRequest = ++requestVersion
  previewUrl.value = ''
  error.value = ''
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

  loading.value = true
  try {
    const blob = await privateFileBlob(photo.downloadUrl)
    if (currentRequest !== requestVersion) return
    if (!blob.type.startsWith('image/')) {
      error.value = '该材料不是可预览的图片。'
      return
    }
    const url = URL.createObjectURL(blob)
    remember(key, url)
    previewUrl.value = url
  } catch (cause) {
    if (currentRequest === requestVersion) error.value = apiErrorMessage(cause)
  } finally {
    if (currentRequest === requestVersion) loading.value = false
  }
}, { immediate: true })

onBeforeUnmount(() => {
  requestVersion++
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
      <div v-if="loading" class="image-preview-state">正在从服务器加载图片…</div>
      <div v-else-if="error" class="image-preview-state image-preview-error" role="alert">{{ error }}</div>
      <img v-else-if="previewUrl" class="image-preview-image" :src="previewUrl" :alt="photo?.originalFilename || '过程照片'" />
    </div>
    <footer>
      <span v-if="photo" class="image-preview-caption">{{ photo.uploaderName }}提交 · 第{{ photo.revisionNo }}版</span>
      <button type="button" class="button primary" @click="emit('close')">关闭</button>
    </footer>
  </dialog>
</template>
