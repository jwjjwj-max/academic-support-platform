<script setup lang="ts">
import { computed } from 'vue'
const props = defineProps<{ page: number; size: number; total: number; loading?: boolean }>()
const emit = defineEmits<{ 'update:page': [value: number]; 'update:size': [value: number] }>()
const pages = computed(() => Math.max(1, Math.ceil(props.total / props.size)))
</script>
<template>
  <nav class="pagination-bar" aria-label="列表分页">
    <span role="status">当前筛选共 {{ total }} 人<span v-if="total">，显示第 {{ (page - 1) * size + 1 }}—{{ Math.min(page * size, total) }} 人</span></span>
    <label>每页人数<select :value="size" :disabled="loading" @change="emit('update:size', Number(($event.target as HTMLSelectElement).value))"><option :value="20">20</option><option :value="50">50</option><option :value="100">100</option></select></label>
    <div class="pagination-buttons">
      <button class="button ghost small" :disabled="loading || page <= 1" @click="emit('update:page', page - 1)">上一页</button>
      <span>第 {{ page }} / {{ pages }} 页</span>
      <button class="button ghost small" :disabled="loading || page >= pages" @click="emit('update:page', page + 1)">下一页</button>
    </div>
  </nav>
</template>
<style scoped>
.pagination-bar{display:flex;align-items:center;justify-content:space-between;gap:12px;flex-wrap:wrap;padding:16px 20px;border-top:1px solid var(--line);font-size:13px;color:var(--muted)}
.pagination-bar label,.pagination-buttons{display:flex;align-items:center;gap:10px}.pagination-bar select{width:auto;min-height:34px}.pagination-buttons{flex-wrap:wrap}
@media(max-width:600px){.pagination-bar{align-items:flex-start;flex-direction:column}.pagination-buttons{width:100%;justify-content:space-between}}
</style>
