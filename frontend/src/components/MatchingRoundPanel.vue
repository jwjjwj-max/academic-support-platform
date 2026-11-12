<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { ApiResponse, ManualMatchOption, MatchCandidate, MatchingAdjustment, MatchingRoundView, MatchingStudent } from '@/types/api'
import { apiErrorMessage, http } from '@/services/http'
import { adjustmentImpact, matchKindLabel, members, selectedPairs } from '@/utils/matching-workbench'
import { labelOf } from '@/utils/labels'

const props = defineProps<{
  view: MatchingRoundView
  previous?: MatchingRoundView
  students: MatchingStudent[]
  historical?: boolean
  editable: boolean
  busy: boolean
}>()
const emit = defineEmits<{ adjust: [data: MatchingAdjustment] }>()
const filter = ref<'selected' | 'waiting' | 'diff'>('selected')
const expandedPair = ref<number>()
const editing = ref<{ pair?: MatchCandidate; student?: MatchingStudent }>()
const choice = ref<number | 'dissolve' | ''>('')
const reason = ref('')
const manualMode = ref(false)
const manualAnchor = ref<number>()
const partnerId = ref<number | ''>('')
const partnerQuery = ref('')
const useExtraSlot = ref(false)
const manualOptions = ref<ManualMatchOption[]>([])
const manualLoading = ref(false)
const manualError = ref('')
const canSpecify = computed(() => props.editable && props.view.round.roundType === 'NORMAL')
const selected = computed(() => selectedPairs(props.view))
const people = computed(() => new Map(props.students.map(student => [student.id, student])))
const identities = computed(() => new Map([
  ...props.students, ...(props.previous?.participants ?? []), ...(props.view.participants ?? []),
].map(student => [student.id, student])))
const occupied = computed(() => new Set(selected.value.flatMap(members)))
const waiting = computed(() => props.view.participantIds.filter(id => !occupied.value.has(id)))
const waitingByYear = computed(() => {
  const freshmanGrade = props.view.policy?.freshmanGrade
  const counts = new Map<number, number>()
  for (const id of waiting.value) {
    const grade = identities.value.get(id)?.grade ?? people.value.get(id)?.grade
    if (grade != null) counts.set(grade, (counts.get(grade) ?? 0) + 1)
  }
  const rows = ['大一', '大二', '大三'].map((label, offset) => ({
    label, count: freshmanGrade == null ? 0 : (counts.get(freshmanGrade - offset) ?? 0),
  }))
  const maximum = Math.max(1, ...rows.map(row => row.count))
  return { rows, maximum, other: waiting.value.length - rows.reduce((total, row) => total + row.count, 0) }
})
const resultTabs = computed(() => ([
  { id: 'waiting', text: `待安排（${waiting.value.length} 人）`, visible: !!props.view.policy },
  { id: 'selected', text: `方案组合（${selected.value.length} 组）`, visible: true },
  { id: 'diff', text: '与上轮对比', visible: !props.historical },
] as const).filter(entry => entry.visible))
const compatible = computed(() => {
  const byStudent = new Map<number, MatchCandidate[]>()
  for (const pair of props.view.candidates) {
    if (pair.status !== 'GENERATED') continue
    for (const id of members(pair)) {
      const candidates = byStudent.get(id) ?? []
      candidates.push(pair)
      byStudent.set(id, candidates)
    }
  }
  return byStudent
})
const options = computed(() => {
  if (!editing.value) return []
  const ids = editing.value.pair ? members(editing.value.pair) : [editing.value.student!.id]
  return [...new Set(ids.flatMap(id => compatible.value.get(id) ?? []))]
    .filter(pair => adjustmentImpact(props.view, editing.value?.pair?.id, pair).valid)
})
const replacement = computed(() => options.value.find(pair => pair.id === choice.value))
const manualPartner = computed(() => manualOptions.value.find(pair => otherMember(pair) === partnerId.value))
const canUseExtraSlot = computed(() => {
  const partner = manualPartner.value
  if (!manualMode.value || !partner || editing.value?.pair || partner.matchKind === 'PEER'
      || occupied.value.has(partner.climberApplicationId)
      || !props.view.extraSlotNavigatorIds?.includes(partner.navigatorApplicationId)) return false
  const assigned = selected.value.filter(pair => members(pair).includes(partner.navigatorApplicationId))
  return assigned.length === 1 && assigned[0].navigatorApplicationId === partner.navigatorApplicationId
    && assigned[0].matchKind !== 'PEER' && assigned[0].navigatorSlot !== 2
})
const addingExtraPair = computed(() => canUseExtraSlot.value && useExtraSlot.value)
const visiblePartners = computed(() => {
  const query = partnerQuery.value.trim().toLowerCase()
  return manualOptions.value.filter(pair => personIdentity(otherMember(pair)).toLowerCase().includes(query))
})
const impact = computed(() => {
  const next = manualMode.value ? manualPartner.value : replacement.value
  if (next || !manualMode.value && choice.value === 'dissolve') {
    return adjustmentImpact(props.view, editing.value?.pair?.id, next, addingExtraPair.value)
  }
})
const changes = computed(() => {
  if (!props.previous) return []
  const key = (pair: MatchCandidate) => `${pair.matchKind}:${members(pair).sort((a, b) => a - b).join('-')}`
  const old = selectedPairs(props.previous)
  const oldKeys = new Set(old.map(key)), newKeys = new Set(selected.value.map(key))
  return [
    ...selected.value.filter(pair => !oldKeys.has(key(pair))).map(pair => ({ action: '新增', pair })),
    ...old.filter(pair => !newKeys.has(key(pair))).map(pair => ({ action: '移除', pair })),
  ]
})
watch(() => [props.view.round.id, props.view.round.draftRevision, props.editable], () => { editing.value = undefined })
watch(canUseExtraSlot, available => { useExtraSlot.value = available })
watch(() => editing.value && manualMode.value ? manualAnchor.value : undefined, async (anchor, _previous, onCleanup) => {
  manualOptions.value = []
  partnerId.value = ''
  partnerQuery.value = ''
  manualError.value = ''
  manualLoading.value = false
  const revision = props.view.round.draftRevision
  if (anchor == null || revision == null) return
  const controller = new AbortController()
  onCleanup(() => controller.abort())
  manualLoading.value = true
  try {
    const response = await http.get<ApiResponse<ManualMatchOption[]>>(`/admin/matching/rounds/${props.view.round.id}/manual-options`, {
      params: { applicationId: anchor, revision }, signal: controller.signal,
    })
    if (!controller.signal.aborted) {
      const existingPartners = new Set(selected.value.filter(pair => members(pair).includes(anchor)).flatMap(members))
      manualOptions.value = response.data.data.filter(option => !existingPartners.has(otherMember(option)))
    }
  } catch (error) {
    if (!controller.signal.aborted) manualError.value = apiErrorMessage(error)
  } finally {
    if (!controller.signal.aborted) manualLoading.value = false
  }
})
watch(() => props.view.round.id, () => {
  filter.value = props.view.policy && waiting.value.length ? 'waiting' : 'selected'
  expandedPair.value = undefined
}, { immediate: true })

function open(pair?: MatchCandidate, student?: MatchingStudent) {
  editing.value = { pair, student }
  choice.value = ''
  reason.value = ''
  manualMode.value = false
  if (!pair && canSpecify.value && !options.value.length) specify()
}

function specify() {
  manualAnchor.value = editing.value?.student?.id ?? editing.value?.pair?.navigatorApplicationId
  manualMode.value = true
}

function otherMember(pair: ManualMatchOption) {
  return pair.navigatorApplicationId === manualAnchor.value ? pair.climberApplicationId : pair.navigatorApplicationId
}

function save() {
  if (!props.editable || props.view.round.draftRevision == null || !impact.value?.valid || !reason.value.trim()) return
  const body = { revision: props.view.round.draftRevision, removeId: editing.value?.pair?.id, reason: reason.value.trim() }
  if (manualMode.value && manualPartner.value) {
    emit('adjust', { ...body, applicationId: manualAnchor.value, partnerId: otherMember(manualPartner.value),
      ...(addingExtraPair.value ? { extraSlot: true } : {}) })
  } else {
    emit('adjust', { ...body, selectId: replacement.value?.id })
  }
}

function personIdentity(id: number, fallback = `报名 #${id}`) {
  const participant = identities.value.get(id)
  return participant ? `${participant.name}（${participant.studentNo}）` : fallback
}

function pairIdentity(pair: MatchCandidate) {
  const first = personIdentity(pair.navigatorApplicationId, pair.navigatorName)
  const second = personIdentity(pair.climberApplicationId, pair.climberName)
  return `${first} ${pair.matchKind === 'PEER' ? '↔' : '→'} ${second}`
}

function namesForCandidateIds(ids: number[]) {
  const map = new Map(props.view.candidates.map(pair => [pair.id, pairIdentity(pair)]))
  return ids.map(id => map.get(id) ?? `组合 #${id}`).join('；') || '无'
}

function pendingReason(id: number) {
  if (props.historical) return '该轮未安排'
  if (compatible.value.get(id)?.length) return '有合格候选，搭档或规则名额已被占用'
  const student = people.value.get(id)
  if (student && student.grade === props.view.policy?.freshmanGrade && !student.evidence) return '无合格候选，可先复核互助能力或领航科目'
  return '本轮没有符合规则的候选'
}
</script>

<template>
  <section class="card">
    <header><div><h2>第 {{ view.round.roundNo }} 轮 · {{ view.round.roundType === 'LOCAL_REMATCH' ? '局部重匹配' : '本期试算' }}</h2>
      <p>{{ view.round.status === 'DRAFT' ? '草稿' : view.round.status === 'PUBLISHED' ? '已发布' : '已被替换' }}
        · {{ view.round.workspaceRevision ? `配置与依据版本 ${view.round.workspaceRevision}` : '历史六项评分' }}</p></div>
    </header>
    <div class="card-body round-panel">
      <div class="round-summary" role="status">
        <span>方案覆盖 {{ occupied.size }}<template v-if="view.policy"> / {{ view.participantIds.length }}</template> 人</span>
        <template v-if="view.policy">
          <span>高年级带大一 {{ selected.filter(pair => pair.matchKind === 'FRESHMAN_SUPPORT').length }} 组</span>
          <span>大一结对 {{ selected.filter(pair => pair.matchKind === 'PEER').length }} 组</span>
        </template>
      </div>
      <div class="table-actions round-actions" aria-label="结果视图">
        <button v-for="entry in resultTabs"
          :key="entry.id" class="button small" :class="filter === entry.id ? 'secondary' : 'ghost'" :aria-pressed="filter === entry.id"
          @click="filter = entry.id">{{ entry.text }}</button>
      </div>
      <template v-if="filter === 'selected'">
        <p v-if="!selected.length" class="muted">本轮尚未安排组合，请查看待安排名单。</p>
        <div v-else class="table-wrap"><table class="match-table" aria-label="方案组合名单">
          <thead><tr><th scope="col">成员一</th><th scope="col">成员二</th><th scope="col">匹配类型</th><th scope="col">学习科目</th><th scope="col">操作</th></tr></thead>
          <tbody><template v-for="pair in selected" :key="pair.id">
            <tr>
              <td class="identity">{{ personIdentity(pair.navigatorApplicationId, pair.navigatorName) }}</td>
              <td class="identity">{{ personIdentity(pair.climberApplicationId, pair.climberName) }}</td>
              <td>{{ matchKindLabel(pair.matchKind) }}
                <small v-if="pair.navigatorSlot === 2">领航者额外名额</small>
                <small v-else-if="pair.manuallySelected">{{ pair.coverage?.exceptions?.length ? '人工例外' : '人工调整' }}</small>
                <small v-if="view.round.status === 'PUBLISHED'">{{ labelOf(pair.status) }}</small>
              </td>
              <td>
                <div v-if="pair.matchedSubjects.length">覆盖 · {{ pair.matchedSubjects.join('、') }}</div>
                <div v-if="pair.coverage?.sharedStudySubjects?.length">共学 · {{ pair.coverage.sharedStudySubjects.join('、') }}</div>
                <div v-else-if="pair.matchKind === 'PEER' && !pair.matchedSubjects.length" class="muted">双方商定</div>
              </td>
              <td><div class="table-actions round-actions">
                <button class="button ghost small" :aria-expanded="expandedPair === pair.id" @click="expandedPair = expandedPair === pair.id ? undefined : pair.id">
                  {{ expandedPair === pair.id ? '收起详情' : pair.coverage ? '查看依据' : '查看历史评分' }}
                </button>
                <button v-if="editable" class="button ghost small" :disabled="busy" @click="open(pair)">调整组合</button>
              </div></td>
            </tr>
            <tr v-if="expandedPair === pair.id" class="detail-row"><td colspan="5">
            <p v-if="pair.coverage?.exceptions?.length" class="muted">人工例外 · {{ pair.coverage.exceptions.join('、') }}</p>
            <div v-if="pair.coverage" class="grid two basis">
              <div v-for="direction in [
                { name: `${pair.navigatorName} → ${pair.climberName}`, items: pair.coverage.firstToSecond, uncovered: pair.coverage.secondUncovered },
                { name: `${pair.climberName} → ${pair.navigatorName}`, items: pair.coverage.secondToFirst, uncovered: pair.coverage.firstUncovered },
              ].filter((_, index) => index === 0 || pair.matchKind === 'PEER')" :key="direction.name">
                <strong>{{ direction.name }}</strong>
                <ul class="compact-list">
                  <li v-for="item in direction.items" :key="item.course">{{ item.course }} {{ item.knowledgePoint }}
                    <small>供给依据 · {{ item.offerQuote }}</small><small>需求依据 · {{ item.needQuote }}</small>
                  </li>
                </ul>
                <p class="muted">未覆盖 · {{ direction.uncovered.join('、') || '无' }}</p>
              </div>
            </div>
            <ul v-else class="compact-list"><li v-for="(detail, key) in pair.scoreDetail" :key="key">{{ labelOf(String(key)) }} · {{ detail.score }}/{{ detail.max }}<small>{{ detail.explanation }}</small></li></ul>
            <div v-if="!historical && people.get(pair.navigatorApplicationId) && people.get(pair.climberApplicationId)" class="grid two basis">
              <div v-for="id in members(pair)" :key="id"><strong>{{ personIdentity(id) }}的报名自述</strong><p>{{ people.get(id)?.statement }}</p></div>
            </div>
            </td></tr>
          </template></tbody>
        </table></div>
      </template>
      <template v-else-if="filter === 'waiting'">
        <figure class="waiting-chart">
          <figcaption>各年级待安排人数</figcaption>
          <div v-for="row in waitingByYear.rows" :key="row.label" class="waiting-chart-row">
            <span>{{ row.label }}</span>
            <div class="waiting-chart-track" aria-hidden="true"><div :style="{ width: `${row.count / waitingByYear.maximum * 100}%` }" /></div>
            <strong>{{ row.count }} 人</strong>
          </div>
          <small v-if="waitingByYear.other" class="muted">其他或年级不详 · {{ waitingByYear.other }} 人</small>
        </figure>
        <p v-if="!waiting.length" class="muted">本轮可安排的报名均已覆盖。</p>
        <div v-else class="table-wrap"><table class="match-table" aria-label="待安排名单">
          <thead><tr><th scope="col">姓名</th><th scope="col">学号</th><th scope="col">未匹配原因</th><th v-if="editable" scope="col">操作</th></tr></thead>
          <tbody><tr v-for="id in waiting" :key="id">
            <td class="identity"><strong>{{ identities.get(id)?.name ?? `报名 #${id}` }}</strong></td>
            <td class="identity student-number">{{ identities.get(id)?.studentNo ?? '—' }}</td>
            <td>{{ pendingReason(id) }}</td>
            <td v-if="editable"><button v-if="people.get(id) && (canSpecify || compatible.get(id)?.some(pair => adjustmentImpact(view, undefined, pair).valid))"
              class="button secondary small" :disabled="busy" @click="open(undefined, people.get(id))">手动组队</button></td>
          </tr></tbody>
        </table></div>
      </template>
      <template v-else>
        <p v-if="!previous" class="muted">尚无上一轮方案可对比。</p>
        <template v-else>
          <p>对比第 {{ previous.round.roundNo }} 轮，组合数 {{ selectedPairs(previous).length }} → {{ selected.length }}</p>
          <p v-if="!changes.length" class="muted">组合无变化。</p>
          <div v-else class="table-wrap"><table class="match-table" aria-label="与上轮对比">
            <thead><tr><th scope="col">变化</th><th scope="col">组合</th><th scope="col">匹配类型</th></tr></thead>
            <tbody><tr v-for="entry in changes" :key="`${entry.action}-${entry.pair.id}`">
              <td>{{ entry.action }}</td><td>{{ pairIdentity(entry.pair) }}</td><td>{{ matchKindLabel(entry.pair.matchKind) }}</td>
            </tr></tbody>
          </table></div>
        </template>
      </template>
      <details v-if="view.adjustments.length">
        <summary>人工调整记录 · {{ view.adjustments.length }} 次</summary>
        <div v-for="(entry, index) in view.adjustments" :key="index" class="result-row basis">
          <strong>{{ entry.administrator }} · {{ entry.time.replace('T', ' ') }}</strong>
          <p>{{ entry.reason }}</p>
          <p>移除 · {{ namesForCandidateIds(entry.before.filter(id => !entry.after.includes(id))) }}</p>
          <p>新增 · {{ namesForCandidateIds(entry.after.filter(id => !entry.before.includes(id))) }}</p>
        </div>
      </details>
    </div>
  </section>
  <dialog v-if="editing" open class="modal" aria-label="人工调整组合">
    <form @submit.prevent="save">
      <header><h2>{{ editing.pair ? `调整 ${pairIdentity(editing.pair)}` : `为 ${personIdentity(editing.student!.id)} 手动组队` }}</h2></header>
      <div class="modal-body">
        <label v-if="!manualMode">调整为<select v-model="choice" required :disabled="busy">
          <option value="" disabled>请选择调整方式</option>
          <option v-for="pair in options" :key="pair.id" :value="pair.id">{{ pairIdentity(pair) }} · {{ matchKindLabel(pair.matchKind) }}</option>
          <option v-if="editing.pair" value="dissolve">拆组，双方回到待安排</option>
        </select></label>
        <div v-if="canSpecify" class="table-actions">
          <button v-if="!manualMode" type="button" class="button secondary small" :disabled="busy" @click="specify">管理员指定搭档</button>
          <button v-else type="button" class="button ghost small" :disabled="busy" @click="manualMode = false">返回规则候选</button>
        </div>
        <template v-if="manualMode">
          <label v-if="editing.pair">保留哪位成员<select v-model="manualAnchor" :disabled="busy">
            <option v-for="id in members(editing.pair)" :key="id" :value="id">{{ personIdentity(id) }}</option>
          </select></label>
          <label>搜索搭档<input v-model="partnerQuery" type="search" placeholder="输入姓名或学号" :disabled="busy || manualLoading" @input="partnerId = ''" /></label>
          <label>指定搭档<select v-model="partnerId" required :disabled="busy || manualLoading || !visiblePartners.length">
            <option value="" disabled>{{ manualLoading ? '正在加载搭档…' : visiblePartners.length ? '请选择搭档' : '没有符合搜索条件的可指定搭档' }}</option>
            <option v-for="option in visiblePartners" :key="otherMember(option)" :value="otherMember(option)">
              {{ personIdentity(otherMember(option)) }} · {{ matchKindLabel(option.matchKind) }}{{ option.exceptions.length ? ' · 需例外' : '' }}
            </option>
          </select></label>
          <label v-if="canUseExtraSlot" class="extra-slot-choice">
            <input v-model="useExtraSlot" type="checkbox" :disabled="busy" />保留原帮扶对，使用本期额外名额
          </label>
          <p v-if="manualError" class="text-danger" role="alert">{{ manualError }}</p>
        </template>
        <div v-if="impact" class="notice warning" role="status">
          <template v-if="manualMode && manualPartner && manualAnchor != null">
            <p>组建 · {{ personIdentity(manualAnchor) }} 与 {{ personIdentity(otherMember(manualPartner)) }}</p>
            <p v-if="addingExtraPair">保留现有帮扶对，两组独立确认和验收。</p>
            <p v-if="manualPartner.exceptions.length">人工例外 · {{ manualPartner.exceptions.join('、') }}</p>
          </template>
          <p v-if="!manualMode && replacement">组建 · {{ pairIdentity(replacement) }}<template v-if="replacement.matchedSubjects.length">（{{ replacement.matchedSubjects.join('、') }}）</template></p>
          <p v-if="impact.removed.length">解除 · {{ impact.removed.map(pairIdentity).join('；') }}</p>
          <p v-if="impact.returned.length">回到待安排 · {{ impact.returned.map(id => personIdentity(id)).join('、') }}</p>
          <p v-if="!impact.valid">该调整会超过本期规则组数上限。</p>
        </div>
        <label>调整原因<textarea v-model.trim="reason" required maxlength="500" :disabled="busy" /></label>
      </div>
      <footer><button type="button" class="button ghost" :disabled="busy" @click="editing = undefined">取消</button>
        <button class="button primary" :disabled="busy || !impact?.valid || !reason">保存人工调整</button></footer>
    </form>
  </dialog>
</template>

<style scoped>
.round-panel { display:grid; gap:10px; }
.round-summary { display:flex; flex-wrap:wrap; gap:4px 20px; font-size:13px; color:var(--muted); }
.round-actions { flex-wrap:wrap; }
.extra-slot-choice { display:flex; align-items:center; gap:8px; }
.extra-slot-choice input { width:auto; min-height:0; }
.waiting-chart { display:grid; gap:6px; width:min(100%, 420px); margin:0 0 4px; }
.waiting-chart figcaption { font-size:14px; font-weight:650; }
.waiting-chart-row { display:grid; grid-template-columns:2.5em minmax(0, 1fr) 4em; align-items:center; gap:8px; font-size:13px; }
.waiting-chart-row strong { text-align:right; font-variant-numeric:tabular-nums; }
.waiting-chart-track { height:8px; overflow:hidden; border-radius:4px; background:#edf0f4; }
.waiting-chart-track > div { height:100%; border-radius:inherit; background:var(--primary); }
.match-table th,.match-table td { padding:9px 10px; }
.match-table th,.match-table .identity,.match-table button { white-space:nowrap; }
.match-table .student-number { font-variant-numeric:tabular-nums; }
.detail-row { background:#f8fafc; }
.result-row { border-top:1px solid var(--line); padding:10px 0; }
.basis { overflow-wrap:anywhere; white-space:pre-wrap; }
.basis.grid { margin:8px 0; }
.basis p { margin:4px 0; }
</style>
