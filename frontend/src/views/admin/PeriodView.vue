<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { http, apiErrorMessage } from '@/services/http'
import type { ApiResponse, Period, PeriodTransitionPreview } from '@/types/api'
import { labelOf } from '@/utils/labels'

const periods = ref<Period[]>([]), loading = ref(true), error = ref(''), success = ref(''), showForm = ref(false), editingId = ref<number | null>(null), submitting = ref(false)
const phaseTarget = ref<Period | null>(null), phaseReason = ref(''), transitionPreview = ref<PeriodTransitionPreview | null>(null), previewLoading = ref(false), riskConfirmed = ref(false)
const extensionTarget = ref<Period | null>(null), extensionEnd = ref(''), extensionReason = ref('')
const statusOrder = ['ANNOUNCED', 'REGISTRATION', 'REVIEW', 'MATCHING', 'CONFIRMATION', 'TRACKING', 'ACCEPTANCE', 'ENDED', 'ARCHIVED']
const impactLabels: Record<string, string> = { totalApplications: '本期报名', submittedApplications: '当前待审核', pendingApplications: '待审核或处理中', undecidedApprovedApplications: '已通过但未定入选', matchingPoolApplications: '已入匹配池', publishedMatches: '已发布匹配', pendingConfirmations: '未完成双方确认', activeSupportPairs: '有效帮扶对', unfinishedSupportPairs: '未验收帮扶对' }
const blank = () => ({ name: '', shortName: '', status: 'ANNOUNCED', registrationStart: '', registrationEnd: '', confirmationDeadline: '', trackingStart: '', trackingEnd: '', acceptanceStart: '', acceptanceEnd: '', photoMin: 4, photoMax: 8, subjectWeight: 40, majorWeight: 25, gradeWeight: 15, learningStyleWeight: 10, personalityWeight: 5, gpaGapWeight: 5 })
const form = reactive(blank())
const total = () => form.subjectWeight + form.majorWeight + form.gradeWeight + form.learningStyleWeight + form.personalityWeight + form.gpaGapWeight
function openCreate() { Object.assign(form, blank()); editingId.value = null; showForm.value = true }
function openEdit(item: Period) { Object.assign(form, item); editingId.value = item.id; showForm.value = true }
async function load() { loading.value = true; try { periods.value = (await http.get<ApiResponse<Period[]>>('/periods')).data.data } catch (e) { error.value = apiErrorMessage(e) } finally { loading.value = false } }
async function save() { error.value = ''; if (total() !== 100) { error.value = '六项匹配权重合计必须为 100'; return } submitting.value = true; try { if (editingId.value) await http.put(`/periods/${editingId.value}`, form); else await http.post('/periods', form); showForm.value = false; success.value = '期次配置已保存'; await load() } catch (e) { error.value = apiErrorMessage(e) } finally { submitting.value = false } }
function datetime(value: string) { return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '—' }
function nextStatus(item: Period) { return statusOrder[statusOrder.indexOf(item.status) + 1] }
async function openAdvance(item: Period) { phaseTarget.value = item; phaseReason.value = ''; transitionPreview.value = null; riskConfirmed.value = false; previewLoading.value = true; try { transitionPreview.value = (await http.get<ApiResponse<PeriodTransitionPreview>>(`/periods/${item.id}/transition-preview`)).data.data } catch (e) { error.value = apiErrorMessage(e); phaseTarget.value = null } finally { previewLoading.value = false } }
function closeAdvance() { phaseTarget.value = null; transitionPreview.value = null; phaseReason.value = ''; riskConfirmed.value = false }
function openExtension(item: Period) { extensionTarget.value = item; extensionEnd.value = item.acceptanceEnd ? item.acceptanceEnd.slice(0, 16) : ''; extensionReason.value = '' }
function closeExtension() { extensionTarget.value = null; extensionEnd.value = ''; extensionReason.value = '' }
async function extendAcceptanceWindow() {
  if (!extensionTarget.value || !extensionEnd.value || !extensionReason.value.trim()) return
  submitting.value = true
  try {
    await http.post(`/periods/${extensionTarget.value.id}/acceptance-extension`, { newAcceptanceEnd: extensionEnd.value, reason: extensionReason.value.trim() })
    success.value = '验收材料提交窗口已延长'
    closeExtension(); await load()
  } catch (e) { error.value = apiErrorMessage(e) } finally { submitting.value = false }
}
async function advancePhase() {
  if (!phaseTarget.value || !phaseReason.value.trim() || !transitionPreview.value?.allowed) return
  if (transitionPreview.value.requiresConfirmation && !riskConfirmed.value) return
  submitting.value = true
  try {
    await http.post(`/periods/${phaseTarget.value.id}/status`, { status: nextStatus(phaseTarget.value), reason: phaseReason.value.trim(), confirmedRisk: riskConfirmed.value })
    success.value = `已推进至${labelOf(nextStatus(phaseTarget.value))}`
    closeAdvance(); await load()
  } catch (e) { error.value = apiErrorMessage(e) } finally { submitting.value = false }
}
onMounted(load)
</script>

<template>
  <div class="page-actions"><button class="button primary" @click="openCreate">创建期次</button></div>
  <div v-if="error" class="notice danger" role="alert">{{ error }}</div><div v-if="success" class="notice success" role="status">{{ success }}</div>
  <div v-if="loading" class="loading-state">正在加载期次…</div>
  <div v-else-if="!periods.length" class="empty-state card"><h3>尚未创建活动期次</h3><p>创建后可配置时间节点、照片范围和六项匹配权重。</p><button class="button primary" @click="openCreate">创建首个期次</button></div>
  <div v-else class="grid two"><article v-for="item in periods" :key="item.id" class="card period-card"><header><div><span class="tag">{{ labelOf(item.status) }}</span><h2>{{ item.name }}</h2><p>{{ item.shortName }}</p></div><div class="table-actions"><button class="button small secondary" :disabled="item.status === 'ARCHIVED'" @click="openEdit(item)">编辑</button><button v-if="item.status === 'ACCEPTANCE'" class="button small secondary" @click="openExtension(item)">延长提交窗口</button><button v-if="nextStatus(item)" class="button small primary" @click="openAdvance(item)">推进至{{ labelOf(nextStatus(item)) }}</button></div></header><div class="card-body"><dl><div><dt>报名时间</dt><dd>{{ datetime(item.registrationStart) }}<br />至 {{ datetime(item.registrationEnd) }}</dd></div><div><dt>确认截止</dt><dd>{{ datetime(item.confirmationDeadline) }}</dd></div><div><dt>帮扶开展</dt><dd>{{ datetime(item.trackingStart) }}<br />至 {{ datetime(item.trackingEnd) }}</dd></div><div><dt>验收时间</dt><dd>{{ datetime(item.acceptanceStart) }}<br />至 {{ datetime(item.acceptanceEnd) }}</dd></div><div><dt>照片范围</dt><dd>{{ item.photoMin }}—{{ item.photoMax }} 张</dd></div><div><dt>匹配权重</dt><dd>科目 {{ item.subjectWeight }} · 专业 {{ item.majorWeight }} · 年级 {{ item.gradeWeight }} · 风格 {{ item.learningStyleWeight }} · 性格 {{ item.personalityWeight }} · 绩点 {{ item.gpaGapWeight }}</dd></div></dl></div></article></div>

  <dialog :open="showForm" class="modal wide-modal"><form @submit.prevent="save"><header><h2>{{ editingId ? '编辑期次' : '创建期次' }}</h2></header><div class="modal-body form-grid"><label>期次名称<input v-model.trim="form.name" required /></label><label>期次简称<input v-model.trim="form.shortName" required /></label><label>当前阶段<input :value="labelOf(form.status)" disabled /><small>阶段需通过带原因的阶段变更操作调整</small></label><span />
    <label>报名开始<input v-model="form.registrationStart" type="datetime-local" required /></label><label>报名结束<input v-model="form.registrationEnd" type="datetime-local" required /></label><label>匹配确认截止<input v-model="form.confirmationDeadline" type="datetime-local" required /></label><label>帮扶开展开始<input v-model="form.trackingStart" type="datetime-local" required /></label><label>帮扶开展结束<input v-model="form.trackingEnd" type="datetime-local" required /></label><label>验收开始<input v-model="form.acceptanceStart" type="datetime-local" required /></label><label>验收结束<input v-model="form.acceptanceEnd" type="datetime-local" required :disabled="form.status === 'ACCEPTANCE'" /><small v-if="form.status === 'ACCEPTANCE'">当前验收阶段请使用“延长提交窗口”并填写原因。</small></label><span />
    <label>照片最少张数<input v-model.number="form.photoMin" type="number" min="0" max="100" required /></label><label>照片最多张数<input v-model.number="form.photoMax" type="number" min="0" max="100" required /></label>
    <div class="full-row weights"><strong>匹配权重 <span :class="{ 'text-danger': total() !== 100 }">合计 {{ total() }}</span></strong><label>帮扶科目<input v-model.number="form.subjectWeight" type="number" min="0" max="100" /></label><label>专业<input v-model.number="form.majorWeight" type="number" min="0" max="100" /></label><label>年级<input v-model.number="form.gradeWeight" type="number" min="0" max="100" /></label><label>学习风格<input v-model.number="form.learningStyleWeight" type="number" min="0" max="100" /></label><label>性格特点<input v-model.number="form.personalityWeight" type="number" min="0" max="100" /></label><label>绩点差距<input v-model.number="form.gpaGapWeight" type="number" min="0" max="100" /></label></div>
  </div><footer><button type="button" class="button ghost" @click="showForm = false">取消</button><button class="button primary" :disabled="submitting || total() !== 100">{{ submitting ? '保存中…' : '保存期次' }}</button></footer></form></dialog>

  <dialog :open="Boolean(phaseTarget)" class="modal"><form @submit.prevent="advancePhase"><header><h2>推进活动阶段</h2></header><div class="modal-body"><div class="notice warning"><strong>{{ labelOf(phaseTarget?.status) }} → {{ labelOf(phaseTarget ? nextStatus(phaseTarget) : '') }}</strong><p>阶段只能按顺序推进，操作会记录原因且不能自行回退。</p></div>
    <div v-if="previewLoading" class="loading-state">正在检查推进条件…</div>
    <template v-else-if="transitionPreview">
      <div v-if="Object.keys(transitionPreview.impact).length" class="transition-impact"><strong>当前影响范围</strong><span v-for="(count, key) in transitionPreview.impact" :key="key" class="tag">{{ impactLabels[String(key)] || key }}：{{ count }}</span></div>
      <div v-if="transitionPreview.blockers.length" class="notice danger"><strong>暂时不能推进</strong><p v-for="item in transitionPreview.blockers" :key="item">{{ item }}</p></div>
      <div v-if="transitionPreview.warnings.length" class="notice warning"><strong>风险提醒</strong><p v-for="item in transitionPreview.warnings" :key="item">{{ item }}</p></div>
      <label v-if="transitionPreview.requiresConfirmation" class="risk-confirm"><input v-model="riskConfirmed" type="checkbox" />我已阅读上述影响，确认继续推进且知晓阶段不能回退</label>
      <label>变更原因<textarea v-model="phaseReason" required maxlength="500" placeholder="说明本次推进依据和异常情况处置安排" /></label>
    </template>
  </div><footer><button type="button" class="button ghost" @click="closeAdvance">取消</button><button class="button primary" :disabled="submitting || previewLoading || !transitionPreview?.allowed || !phaseReason.trim() || (transitionPreview.requiresConfirmation && !riskConfirmed)">{{ submitting ? '推进中…' : '确认推进' }}</button></footer></form></dialog>

  <dialog :open="Boolean(extensionTarget)" class="modal"><form @submit.prevent="extendAcceptanceWindow"><header><h2>延长验收提交窗口</h2></header><div class="modal-body"><div class="notice warning"><strong>仅延长学生材料提交时间</strong><p>管理员审核和人工验收流程不变，系统会记录新的截止时间和延长原因。</p></div><label>新的提交截止时间<input v-model="extensionEnd" type="datetime-local" required /></label><label>延长原因<textarea v-model="extensionReason" maxlength="500" required placeholder="说明漏交、驳回重提或其他需要延长的原因" /></label></div><footer><button type="button" class="button ghost" @click="closeExtension">取消</button><button class="button primary" :disabled="submitting || !extensionEnd || !extensionReason.trim()">{{ submitting ? '保存中…' : '确认延长' }}</button></footer></form></dialog>
</template>
