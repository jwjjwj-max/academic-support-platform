<script setup lang="ts">
import { privateFileBlob, uploadPrivate } from '@/services/private-files'

import { computed, onMounted, reactive, ref } from 'vue'
import { apiErrorMessage, http } from '@/services/http'
import type { AdminGrade, ApiResponse, GradeBatch, GradeCohortStat, GradePreview, Period } from '@/types/api'
import { labelOf } from '@/utils/labels'
import { validateGradeMapping } from '@/utils/grade-mapping'
import { groupAdminGrades } from '@/utils/grade-comparison'

const periods = ref<Period[]>([])
const batches = ref<GradeBatch[]>([])
const grades = ref<AdminGrade[]>([])
const cohortStats = ref<GradeCohortStat[]>([])
const preview = ref<GradePreview | null>(null)
const error = ref('')
const success = ref('')
const busy = ref(false)
const file = ref<File | null>(null)
const form = reactive({ periodId: '' as number | '', stage: 'BEFORE', label: '' })
const mapping = reactive<Record<string, string>>({})
const correction = ref<AdminGrade | null>(null)
const correctForm = reactive({ termGpa: '', termGpaRank: '', reason: '' })

const comparisonRows = computed(() => groupAdminGrades(grades.value))
const requiredFields = ['studentNo', 'grade', 'major', 'termGpa', 'termGpaRank']
const fields = [
  ['studentNo', '学号（必选）'], ['name', '姓名'], ['termGpa', '平均学分绩点（必选）'],
  ['termGpaRank', '平均学分绩点排名（必选）'], ['college', '学院'], ['grade', '年级（必选）'],
  ['major', '专业（必选）'], ['className', '班级'],
]

const cohortRows = computed(() => {
  const rows = new Map<string, { key: string; grade: string; major: string; before?: GradeCohortStat; after?: GradeCohortStat }>()
  for (const stat of cohortStats.value) {
    const key = `${stat.grade}:${stat.major}`
    const row = rows.get(key) ?? { key, grade: stat.grade, major: stat.major }
    if (stat.stage === 'BEFORE') row.before = stat
    else row.after = stat
    rows.set(key, row)
  }
  return [...rows.values()].sort((a, b) => a.grade.localeCompare(b.grade, 'zh-CN', { numeric: true }) || a.major.localeCompare(b.major, 'zh-CN'))
})

const correctionRankInvalid = computed(() => {
  if (!correction.value || !correctForm.termGpaRank || !correction.value.professionalStudentCount) return false
  return Number(correctForm.termGpaRank) > correction.value.professionalStudentCount
})

async function load() {
  error.value = ''
  try {
    if (!periods.value.length) periods.value = (await http.get<ApiResponse<Period[]>>('/periods')).data.data
    if (!form.periodId && periods.value.length) form.periodId = periods.value[0].id
    if (!form.periodId) return
    const params = { periodId: form.periodId }
    const [batchResult, gradeResult, cohortResult] = await Promise.all([
      http.get<ApiResponse<GradeBatch[]>>('/admin/grades/batches', { params }),
      http.get<ApiResponse<AdminGrade[]>>('/admin/grades', { params }),
      http.get<ApiResponse<GradeCohortStat[]>>('/admin/grades/cohort-stats', { params }),
    ])
    batches.value = batchResult.data.data
    grades.value = gradeResult.data.data
    cohortStats.value = cohortResult.data.data
  } catch (e) {
    error.value = apiErrorMessage(e)
  }
}

async function previewFile() {
  if (!file.value) return
  busy.value = true
  error.value = ''
  const data = new FormData()
  data.append('periodId', String(form.periodId))
  data.append('stage', form.stage)
  data.append('label', form.label)
  data.append('file', file.value)
  try {
    preview.value = (await uploadPrivate<ApiResponse<GradePreview>>('/admin/grades/preview', data, { kind: 'GRADE', periodId: form.periodId, stage: form.stage, label: form.label })).data.data
    Object.keys(mapping).forEach((key) => delete mapping[key])
    Object.assign(mapping, preview.value.suggestedMapping)
    success.value = '预览完成，请核对字段映射后确认导入。'
  } catch (e) {
    error.value = apiErrorMessage(e)
  } finally {
    busy.value = false
  }
}

async function importRows() {
  if (!preview.value) return
  const mappingError = validateGradeMapping(mapping)
  if (mappingError) { error.value = mappingError; return }
  busy.value = true
  try {
    await http.post(`/admin/grades/batches/${preview.value.batch.id}/import`, { mapping })
    success.value = `已保存本期 ${preview.value.expectedParticipants} 名正式参与者成绩，并完成整院年级专业人数统计。`
    preview.value = null
    await load()
  } catch (e) {
    error.value = apiErrorMessage(e)
  } finally {
    busy.value = false
  }
}

function openCorrection(row: AdminGrade) {
  correction.value = row
  correctForm.termGpa = String(row.termGpa)
  correctForm.termGpaRank = row.termGpaRank ? String(row.termGpaRank) : ''
  correctForm.reason = ''
}

async function saveCorrection() {
  if (!correction.value || correctionRankInvalid.value) return
  busy.value = true
  try {
    await http.patch(`/admin/grades/${correction.value.id}`, {
      termGpa: Number(correctForm.termGpa),
      termGpaRank: correctForm.termGpaRank ? Number(correctForm.termGpaRank) : null,
      reason: correctForm.reason.trim(),
    })
    success.value = '成绩已修正，修改前后值、原因、操作人和时间均已留痕。'
    correction.value = null
    await load()
  } catch (e) {
    error.value = apiErrorMessage(e)
  } finally {
    busy.value = false
  }
}

async function raw(batch: GradeBatch) {
  try {
    const blob = await privateFileBlob(`/api/admin/grades/batches/${batch.id}/file`)
    const url = URL.createObjectURL(blob)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = batch.originalFilename
    anchor.click()
    setTimeout(() => URL.revokeObjectURL(url), 60_000)
  } catch (e) {
    error.value = apiErrorMessage(e)
  }
}

function signed(value?: number, digits = 2) {
  if (value == null) return '—'
  return `${value > 0 ? '+' : ''}${value.toFixed(digits)}`
}

function rankLabel(row?: AdminGrade) {
  if (!row || row.termGpaRank == null) return '—'
  return row.professionalStudentCount
    ? `第 ${row.termGpaRank} / ${row.professionalStudentCount} 名`
    : `第 ${row.termGpaRank} 名（待重新导入统计人数）`
}

function importStatus(before?: GradeCohortStat, after?: GradeCohortStat) {
  if (before && after) return '帮扶前后均已导入'
  if (before) return '待导入帮扶后'
  return '待导入帮扶前'
}

onMounted(load)
</script>

<template>
  <div v-if="error" class="notice danger action-feedback" role="alert">{{ error }}</div>
  <div v-if="success" class="notice success action-feedback" role="status">{{ success }}</div>

  <article class="card">
    <header><div><h2>导入整院单学期成绩</h2><p>系统使用整院完整成绩表统计同年级同专业人数，只保存本期正式帮扶参与者的个人成绩。</p></div></header>
    <form class="card-body form-grid" @submit.prevent="previewFile">
      <label>活动期次<select v-model="form.periodId" required @change="load"><option disabled value="">请选择</option><option v-for="period in periods" :key="period.id" :value="period.id">{{ period.name }}（{{ labelOf(period.status) }}）</option></select></label>
      <label>成绩阶段<select v-model="form.stage"><option value="BEFORE">帮扶前</option><option value="AFTER">帮扶后</option></select></label>
      <label>成绩单标签<input v-model="form.label" required maxlength="100" placeholder="例如：2025—2026学年第二学期整院成绩" /></label>
      <label>选择整院成绩表<input type="file" accept=".csv,.xls,.xlsx" required @change="file = ($event.target as HTMLInputElement).files?.[0] || null" /></label>
      <div class="notice warning full-row">必须包含学号、年级、专业、平均学分绩点和平均学分绩点排名。专业人数按“年级 + 专业”统计，单个文件不超过 20MB。</div>
      <div class="form-actions full-row"><button class="button primary" :disabled="busy || !file || !form.periodId">上传并预览</button></div>
    </form>
  </article>

  <article v-if="preview" class="card">
    <header><div><h2>确认字段映射</h2><p>共 {{ preview.batch.totalRows }} 行，识别本期参与者 {{ preview.batch.participantRows }} / {{ preview.expectedParticipants }} 人。</p></div></header>
    <div class="card-body">
      <div v-for="message in preview.validationMessages" :key="message" class="notice warning">{{ message }}</div>
      <div class="mapping-grid"><label v-for="field in fields" :key="field[0]">{{ field[1] }}<select v-model="mapping[field[0]]" :required="requiredFields.includes(field[0])"><option value="">不导入</option><option v-for="header in preview.headers" :key="header" :value="header">{{ header }}</option></select></label></div>
      <div class="table-wrap preview-table"><table><thead><tr><th v-for="header in preview.headers" :key="header">{{ header }}</th></tr></thead><tbody><tr v-for="(row, index) in preview.samples" :key="index"><td v-for="header in preview.headers" :key="header">{{ row[header] }}</td></tr></tbody></table></div>
      <div class="form-actions"><button class="button ghost" @click="preview = null">取消</button><button class="button primary" :disabled="busy || requiredFields.some((key) => !mapping[key])" @click="importRows">确认并导入参与者成绩</button></div>
    </div>
  </article>

  <article class="card">
    <header><div><h2>年级专业人数统计</h2><p>人数来自最近一次成功导入的整院成绩表，帮扶前后分别保存。</p></div></header>
    <div v-if="!cohortRows.length" class="empty-state"><p>尚未导入可统计的整院成绩表。</p></div>
    <div v-else class="table-wrap cohort-stat-table"><table><thead><tr><th>年级</th><th>专业</th><th>帮扶前专业人数</th><th>帮扶后专业人数</th><th>状态</th></tr></thead><tbody><tr v-for="row in cohortRows" :key="row.key"><td>{{ row.grade }}</td><td>{{ row.major }}</td><td>{{ row.before?.studentCount ?? '—' }}</td><td>{{ row.after?.studentCount ?? '—' }}</td><td><span class="status-badge" :class="row.before && row.after ? 'approved' : 'pending'">{{ importStatus(row.before, row.after) }}</span></td></tr></tbody></table></div>
  </article>

  <article class="card">
    <header><div><h2>导入批次</h2><p>原始文件仅管理员可下载。</p></div></header>
    <div class="table-wrap"><table><thead><tr><th>期次 / 阶段</th><th>文件</th><th>状态</th><th>参与者行</th><th>操作</th></tr></thead><tbody><tr v-for="batch in batches" :key="batch.id"><td>{{ batch.periodName }}<small>{{ labelOf(batch.stage) }}</small></td><td>{{ batch.label }}<small>{{ batch.originalFilename }}</small></td><td><span class="status-badge" :class="batch.status === 'IMPORTED' ? 'approved' : 'pending'">{{ labelOf(batch.status) }}</span></td><td>{{ batch.participantRows }} / {{ batch.totalRows }}</td><td><button class="button ghost small" @click="raw(batch)">下载原始文件</button></td></tr></tbody></table></div>
  </article>

  <article class="card">
    <header><div><h2>参与者成绩对比</h2><p>专业前百分比 = 专业排名 ÷ 同年级同专业人数；排名提升为正数表示进步。排名数据仅管理员可见。</p></div></header>
    <div class="table-wrap grade-comparison-table"><table><thead><tr><th rowspan="2">学生</th><th rowspan="2">身份</th><th colspan="4" class="comparison-group before-group">帮扶前（上个学期）</th><th colspan="4" class="comparison-group after-group">帮扶后（这个学期）</th><th rowspan="2">绩点变化</th><th rowspan="2">排名提升</th></tr><tr><th>平均学分绩点</th><th>专业排名</th><th>专业前百分比</th><th>操作</th><th>平均学分绩点</th><th>专业排名</th><th>专业前百分比</th><th>操作</th></tr></thead><tbody><tr v-for="row in comparisonRows" :key="row.key"><td><strong>{{ row.name }}</strong><small>{{ row.studentNo }} · {{ row.before?.grade || row.after?.grade }}级 · {{ row.major }}</small></td><td>{{ labelOf(row.role) }}</td><td>{{ row.before ? Number(row.before.termGpa).toFixed(2) : '—' }}</td><td>{{ rankLabel(row.before) }}</td><td>{{ row.before?.professionalTopPercent != null ? `前 ${Number(row.before.professionalTopPercent).toFixed(1)}%` : '—' }}</td><td><button v-if="row.before" class="button secondary small" @click="openCorrection(row.before)">修正</button></td><td :class="{ 'grade-empty': !row.after }">{{ row.after ? Number(row.after.termGpa).toFixed(2) : '—' }}</td><td :class="{ 'grade-empty': !row.after }">{{ rankLabel(row.after) }}</td><td :class="{ 'grade-empty': !row.after }">{{ row.after?.professionalTopPercent != null ? `前 ${Number(row.after.professionalTopPercent).toFixed(1)}%` : '—' }}</td><td><button v-if="row.after" class="button secondary small" @click="openCorrection(row.after)">修正</button></td><td :class="{ 'positive-change': (row.change ?? 0) > 0, 'negative-change': (row.change ?? 0) < 0 }"><strong>{{ signed(row.change) }}</strong></td><td :class="{ 'positive-change': (row.rankImprovementPercentagePoints ?? 0) > 0, 'negative-change': (row.rankImprovementPercentagePoints ?? 0) < 0 }"><strong>{{ row.rankImprovementPercentagePoints == null ? '—' : `${signed(row.rankImprovementPercentagePoints, 1)} 个百分点` }}</strong></td></tr></tbody></table></div>
  </article>

  <dialog :open="Boolean(correction)" class="modal"><form @submit.prevent="saveCorrection"><header><h2>修正导入成绩</h2></header><div class="modal-body"><div class="notice warning">修正原因必填；系统将记录修改前后值、操作人和时间。</div><label>平均学分绩点<input v-model="correctForm.termGpa" type="number" min="0" max="5" step="0.01" required /></label><label>平均学分绩点排名<input v-model="correctForm.termGpaRank" type="number" min="1" :max="correction?.professionalStudentCount" step="1" required /><small v-if="correction?.professionalStudentCount">该年级专业共 {{ correction.professionalStudentCount }} 人</small><small v-if="correctionRankInvalid" class="text-danger">专业排名不能超过专业人数</small></label><label>修正原因<textarea v-model="correctForm.reason" maxlength="500" required /></label></div><footer><button type="button" class="button ghost" @click="correction = null">取消</button><button class="button primary" :disabled="busy || !correctForm.reason.trim() || correctionRankInvalid">确认修正</button></footer></form></dialog>
</template>
