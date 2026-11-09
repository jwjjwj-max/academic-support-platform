<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { hasSubjectSeparator, MAX_SUBJECTS, splitSubjects, subjectErrors } from '@/utils/subjects'

const subjects = defineModel<string[]>({ required: true })
const splitPending = defineModel<boolean>('splitPending')
defineProps<{ title: string; showErrors?: boolean }>()

const commonSubjects = [
  '高等数学', '线性代数', '概率论与数理统计', '离散数学', '大学英语', '英语听说', '英语读写',
  'C语言程序设计', 'Python程序设计', 'Java程序设计', '数据结构', '数据库原理',
  '计算机网络', '操作系统', 'Web前端开发', '人工智能导论',
]
const activeRow = ref<number | null>(null)
const preview = ref<{ index: number; parts: string[] } | null>(null)
watch(preview, value => { splitPending.value = Boolean(value) })
const errors = computed(() => subjectErrors(subjects.value))
const exceedsLimit = computed(() => Boolean(preview.value &&
  subjects.value.length - 1 + preview.value.parts.length > MAX_SUBJECTS))

function suggestions(value: string) {
  const query = value.trim().toLowerCase()
  if (hasSubjectSeparator(value)) return []
  return commonSubjects.filter(course => course !== value && course.toLowerCase().includes(query)).slice(0, 5)
}

function mayContainMultiple(value: string) {
  if (hasSubjectSeparator(value)) return false
  const text = value.toLowerCase()
  const hits = commonSubjects.filter(course => text.includes(course.toLowerCase()))
  return hits.filter(course => !hits.some(other => other !== course && other.includes(course))).length > 1
}

function updateRow(index: number, value: string) {
  preview.value = null
  subjects.value = subjects.value.map((item, row) => row === index ? value : item)
}

function removeRow(index: number) {
  preview.value = null
  activeRow.value = null
  subjects.value = subjects.value.filter((_, row) => row !== index)
}

function addRow() {
  preview.value = null
  subjects.value = [...subjects.value, '']
}

function paste(index: number, event: ClipboardEvent) {
  const text = event.clipboardData?.getData('text') ?? ''
  const parts = splitSubjects(text)
  if (parts.length < 2) return
  event.preventDefault()
  preview.value = { index, parts }
}

function confirmSplit() {
  if (!preview.value || exceedsLimit.value) return
  const { index, parts } = preview.value
  subjects.value = [...subjects.value.slice(0, index), ...parts, ...subjects.value.slice(index + 1)]
  preview.value = null
  activeRow.value = null
}
</script>

<template>
  <fieldset class="subjects">
    <legend>{{ title }}</legend>
    <p class="muted">每行填写一门，最多 8 门。可以选用课程建议，也可以自行输入。</p>
    <div v-for="(subject, index) in subjects" :key="index" class="subject">
      <div class="subject-row">
        <input
          :value="subject"
          :aria-label="`第 ${index + 1} 门课程`"
          :aria-invalid="Boolean(errors[index] && (subject || showErrors))"
          placeholder="输入或搜索课程"
          @focus="activeRow = index"
          @input="updateRow(index, ($event.target as HTMLInputElement).value)"
          @paste="paste(index, $event)"
        />
        <button type="button" class="button ghost small" :disabled="subjects.length === 1" :aria-label="`删除第 ${index + 1} 门课程`" @click="removeRow(index)">删除</button>
      </div>
      <small v-if="errors[index] && (subject || showErrors)" class="text-danger">{{ errors[index] }}</small>
      <small v-else-if="mayContainMultiple(subject)" class="muted">这行可能包含多门课程，请检查是否需要分行。</small>
      <button v-if="splitSubjects(subject).length > 1" type="button" class="button ghost small" @click="preview = { index, parts: splitSubjects(subject) }">预览拆分</button>
      <div v-if="activeRow === index && suggestions(subject).length" class="subject-suggestions" aria-label="课程建议">
        <button v-for="course in suggestions(subject)" :key="course" type="button" class="button ghost small" @click="updateRow(index, course); activeRow = null">{{ course }}</button>
      </div>
    </div>
    <div v-if="preview" class="notice" role="status">
      <strong>拆分预览</strong>
      <p>确认后，下列课程将替换第 {{ preview.index + 1 }} 行。</p>
      <ol><li v-for="(part, index) in preview.parts" :key="index">{{ part }}</li></ol>
      <p v-if="exceedsLimit" class="text-danger">拆分后超过 8 门，请先减少其他课程，再重新拆分。</p>
      <div class="subject-suggestions">
        <button type="button" class="button secondary small" :disabled="exceedsLimit" @click="confirmSplit">确认拆分</button>
        <button type="button" class="button ghost small" @click="preview = null">取消拆分</button>
      </div>
    </div>
    <button type="button" class="button secondary small" :disabled="subjects.length >= MAX_SUBJECTS" @click="addRow">＋添加科目</button>
    <div class="subject-summary"><strong>当前课程列表</strong><ol v-if="subjects.some(value => value.trim())"><li v-for="(subject, index) in subjects.filter(value => value.trim())" :key="index">{{ subject.trim() }}</li></ol><p v-else class="muted">尚未填写课程</p></div>
  </fieldset>
</template>

<style scoped>
.subjects { min-width: 0; margin: 0; padding: 12px; border: 1px solid var(--line); border-radius: 8px; }
.subjects legend { font-size: 13px; font-weight: 650; }
.subjects p { margin: 0 0 8px; font-size: 13px; }
.subject { display: grid; justify-items: start; gap: 5px; margin-bottom: 9px; }
.subject-row { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: 8px; width: 100%; }
.subject-suggestions { display: flex; flex-wrap: wrap; gap: 6px; }
.subject-summary { margin-top: 10px; font-size: 13px; }
.subjects ol { margin: 6px 0; padding-left: 24px; overflow-wrap: anywhere; }
</style>
