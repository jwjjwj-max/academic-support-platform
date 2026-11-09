<script setup lang="ts">
import { ref, watch } from 'vue'
import type { MatchingExclusion, MatchingStudent } from '@/types/api'

const props = defineProps<{ students: MatchingStudent[]; exclusions: MatchingExclusion[]; editable: boolean; busy: boolean }>()
const emit = defineEmits<{ save: [request: MatchingExclusion & { excluded: boolean }] }>()
const form = ref<{ firstApplicationId: number | ''; secondApplicationId: number | ''; reason: string; excluded: boolean }>()

// Successful saves refresh the list. Keep the reason available for retry after a failed save.
watch(() => props.exclusions, () => { form.value = undefined })

function save() {
  const draft = form.value
  if (!draft || !draft.firstApplicationId || !draft.secondApplicationId ||
      draft.firstApplicationId === draft.secondApplicationId || !draft.reason.trim()) return
  emit('save', { ...draft, firstApplicationId: draft.firstApplicationId, secondApplicationId: draft.secondApplicationId })
}

function name(id: number) {
  return props.students.find(student => student.id === id)?.name ?? `报名 #${id}`
}
</script>

<template>
  <section class="card">
    <header>
      <div><h2>禁止组合</h2><p>核实后的私下约组在试算、人工调整和重匹配时均被排除。</p></div>
      <button v-if="editable" class="button secondary small" :disabled="busy || students.length < 2"
        @click="form = { firstApplicationId: '', secondApplicationId: '', reason: '', excluded: true }">登记排除</button>
    </header>
    <div class="card-body">
      <ul v-if="exclusions.length" class="compact-list">
        <li v-for="item in exclusions" :key="`${item.firstApplicationId}-${item.secondApplicationId}`">
          <strong>{{ name(item.firstApplicationId) }} / {{ name(item.secondApplicationId) }}</strong> · {{ item.reason }}
          <button v-if="editable" class="button ghost small" :disabled="busy" @click="form = { ...item, reason: '', excluded: false }">解除排除</button>
        </li>
      </ul>
      <p v-else class="muted">暂无已核实的禁止组合。</p>
      <form v-if="form && editable" class="form-grid" @submit.prevent="save">
        <label v-for="side in (['firstApplicationId', 'secondApplicationId'] as const)" :key="side">
          {{ side === 'firstApplicationId' ? '学生一' : '学生二' }}
          <select v-if="form.excluded" v-model="form[side]" required :disabled="busy">
            <option value="" disabled>选择入池学生</option>
            <option v-for="student in students" :key="student.id" :value="student.id">{{ student.name }} · {{ student.studentNo }}</option>
          </select>
          <span v-else>{{ name(Number(form[side])) }}</span>
        </label>
        <label class="full-row">核实或解除原因<textarea v-model.trim="form.reason" maxlength="500" required :disabled="busy" /></label>
        <div class="form-actions full-row">
          <button type="button" class="button ghost" :disabled="busy" @click="form = undefined">取消</button>
          <button class="button primary" :disabled="busy || form.firstApplicationId === form.secondApplicationId">确认{{ form.excluded ? '排除' : '解除' }}</button>
        </div>
      </form>
    </div>
  </section>
</template>
