<script setup lang="ts">
import { onMounted, ref } from 'vue'
import ImagePreviewDialog from '@/components/ImagePreviewDialog.vue'
import { apiErrorMessage, http } from '@/services/http'
import type { ApiResponse, PairPhoto, PairReflection, Period, SupportPairDetail } from '@/types/api'
import { labelOf, reflectionStatusLabel } from '@/utils/labels'

const pairs = ref<SupportPairDetail[]>([])
const periods = ref<Period[]>([])
const periodId = ref<number | ''>('')
const error = ref('')
const success = ref('')
const busy = ref(false)
const action = ref<{ kind: 'photo' | 'accept' | 'end'; id: number; decision?: 'APPROVED' | 'REJECTED' } | null>(null)
const reflectionDetail = ref<PairReflection | null>(null)
const previewPhoto = ref<PairPhoto | null>(null)
const reason = ref('')

async function load() {
  try {
    const [pairResult, periodResult] = await Promise.all([
      http.get<ApiResponse<SupportPairDetail[]>>('/admin/materials/pairs', { params: { periodId: periodId.value || undefined } }),
      http.get<ApiResponse<Period[]>>('/periods'),
    ])
    pairs.value = pairResult.data.data
    periods.value = periodResult.data.data
  } catch (e) {
    error.value = apiErrorMessage(e)
  }
}

function open(kind: 'photo' | 'accept' | 'end', id: number, decision?: 'APPROVED' | 'REJECTED') {
  action.value = { kind, id, decision }
  reason.value = ''
}

function openReflection(reflection: PairReflection) {
  reflectionDetail.value = reflection
}

function acceptanceState(pair: SupportPairDetail) {
  const approvedPhotos = pair.photos.filter((photo) => photo.status === 'APPROVED').length
  const submittedReflections = pair.reflections.filter((reflection) =>
    ['SUBMITTED', 'ADMIN_PENDING', 'APPROVED'].includes(reflection.status),
  ).length
  const blockers: string[] = []
  if (approvedPhotos < pair.photoMin) blockers.push(`还缺少 ${pair.photoMin - approvedPhotos} 张审核通过照片`)
  if (approvedPhotos > pair.photoMax) blockers.push(`审核通过照片超过上限 ${pair.photoMax} 张`)
  if (submittedReflections < 2) blockers.push(`还缺少 ${2 - submittedReflections} 份已提交活动感悟`)
  if (submittedReflections > 2) blockers.push('活动感悟提交数量异常，请检查数据')
  return { approvedPhotos, submittedReflections, blockers, ready: blockers.length === 0 }
}

async function submit() {
  if (!action.value) return
  busy.value = true
  const current = action.value
  try {
    if (current.kind === 'photo') {
      await http.post(`/admin/materials/photos/${current.id}/review`, {
        decision: current.decision,
        reason: reason.value.trim() || undefined,
      })
    } else {
      await http.post(`/admin/materials/pairs/${current.id}/${current.kind}`, { reason: reason.value.trim() })
    }
    success.value = current.kind === 'accept'
      ? '人工验收完成。'
      : current.kind === 'end'
        ? '帮扶对已解除。'
        : '照片审核完成。'
    action.value = null
    await load()
  } catch (e) {
    error.value = apiErrorMessage(e)
  } finally {
    busy.value = false
  }
}

function title() {
  if (action.value?.kind === 'accept') return '人工验收帮扶对'
  if (action.value?.kind === 'end') return '解除帮扶对'
  return action.value?.decision === 'REJECTED' ? '驳回照片' : '通过照片'
}

onMounted(load)
</script>

<template>
  <div class="page-actions">
    <label>筛选期次<select v-model="periodId" @change="load"><option value="">全部期次</option><option v-for="p in periods" :key="p.id" :value="p.id">{{ p.name }}</option></select></label>
  </div>
  <div v-if="error" class="notice danger action-feedback" role="alert" aria-live="assertive"><strong>操作未完成</strong><p>{{ error }}</p></div>
  <div v-if="success" class="notice success action-feedback" role="status" aria-live="polite"><strong>操作已完成</strong><p>{{ success }}</p></div>
  <div v-if="!pairs.length" class="empty-state card"><h3>暂无正式帮扶对</h3></div>

  <article v-for="pair in pairs" :key="pair.id" class="card">
    <header>
      <div><span class="tag">{{ pair.periodName }}</span><h2>{{ pair.navigator.name }} 与 {{ pair.climber.name }}</h2><p>{{ pair.pairNo }} · {{ labelOf(pair.periodStatus) }} · {{ labelOf(pair.status) }}</p></div>
      <div class="table-actions"><button v-if="pair.status === 'ACTIVE' && ['TRACKING', 'ACCEPTANCE'].includes(pair.periodStatus)" class="button danger small" @click="open('end', pair.id)">解除</button><button v-if="pair.canAccept" class="button primary small" :disabled="busy || !acceptanceState(pair).ready" :title="acceptanceState(pair).ready ? '' : acceptanceState(pair).blockers.join('；')" @click="open('accept', pair.id)">人工验收</button></div>
    </header>
    <div class="card-body">
      <div v-if="pair.status === 'ACTIVE' && pair.periodStatus === 'ACCEPTANCE'" class="notice acceptance-readiness" :class="{ ready: acceptanceState(pair).ready }">
        <strong>验收前检查</strong>
        <p>审核通过照片：{{ acceptanceState(pair).approvedPhotos }} / {{ pair.photoMin }}—{{ pair.photoMax }}；已提交活动感悟：{{ acceptanceState(pair).submittedReflections }} / 2</p>
        <ul v-if="!acceptanceState(pair).ready"><li v-for="blocker in acceptanceState(pair).blockers" :key="blocker">{{ blocker }}</li></ul>
        <p v-else>材料条件已满足，可以进行人工验收。</p>
      </div>
      <div class="section-heading"><h3>过程照片（通过 {{ pair.photos.filter(x => x.status === 'APPROVED').length }} / 要求 {{ pair.photoMin }}—{{ pair.photoMax }}）</h3></div>
      <div class="material-list"><div v-for="photo in pair.photos" :key="photo.id" class="material-item"><div><strong>{{ photo.originalFilename }}</strong><small>{{ photo.uploaderName }} · {{ labelOf(photo.status) }} · 第{{ photo.revisionNo }}版</small><p>{{ photo.description }}</p><p v-if="photo.rejectionReason" class="text-danger">驳回原因：{{ photo.rejectionReason }}</p></div><div class="table-actions"><button class="button ghost small" @click="previewPhoto=photo">站内预览</button><template v-if="photo.status === 'ADMIN_PENDING'"><button class="button danger small" @click="open('photo', photo.id, 'REJECTED')">驳回</button><button class="button primary small" @click="open('photo', photo.id, 'APPROVED')">通过</button></template></div></div></div>
      <div class="section-heading"><h3>双方活动感悟</h3></div>
      <div v-if="!pair.reflections.length" class="muted">暂无已提交活动感悟。</div>
      <div v-else class="reflection-grid"><button v-for="item in pair.reflections" :key="item.id" type="button" class="material-item reflection-card" :aria-label="`打开${item.authorName}的完整活动感悟`" aria-haspopup="dialog" @click="openReflection(item)"><div><strong>{{ item.authorName }} · {{ item.wordCount }}字 · {{ reflectionStatusLabel(item.status) }}</strong><p class="reflection-preview">{{ item.content }}</p><small class="reflection-open-hint">点击查看完整感悟</small></div></button></div>
      <div v-if="pair.status === 'COMPLETED'" class="notice success">验收备注：{{ pair.acceptanceNote }}</div><div v-if="pair.status === 'ENDED'" class="notice danger">解除原因：{{ pair.endReason }}</div>
    </div>
  </article>

  <dialog :open="Boolean(action)" class="modal"><form @submit.prevent="submit"><header><h2>{{ title() }}</h2></header><div class="modal-body"><div v-if="action?.kind === 'accept'" class="notice warning">照片需在要求数量内且审核通过；双方活动感悟提交后即可验收，帮扶是否完成仍由管理员人工判断。</div><label>{{ action?.decision === 'REJECTED' || action?.kind === 'end' ? '原因（必填）' : '备注（必填）' }}<textarea v-model="reason" maxlength="500" required /></label></div><footer><button type="button" class="button ghost" @click="action = null">取消</button><button class="button primary" :disabled="busy || !reason.trim()">确认</button></footer></form></dialog>
  <dialog :open="Boolean(reflectionDetail)" class="modal reflection-detail-modal" aria-label="完整活动感悟" @click.self="reflectionDetail = null" @keydown.esc.window="reflectionDetail = null"><header><div><span class="eyebrow">活动感悟</span><h2>{{ reflectionDetail?.authorName }}的完整感悟</h2></div><button type="button" class="icon-ghost modal-close" aria-label="关闭完整活动感悟" @click="reflectionDetail = null">×</button></header><div v-if="reflectionDetail" class="modal-body"><p class="reflection-detail-meta">{{ reflectionDetail.wordCount }}字 · {{ reflectionStatusLabel(reflectionDetail.status) }}</p><p class="reflection-full">{{ reflectionDetail.content }}</p></div><footer><button type="button" class="button primary" @click="reflectionDetail = null">关闭</button></footer></dialog>
  <ImagePreviewDialog :photo="previewPhoto" @close="previewPhoto=null" />
</template>
