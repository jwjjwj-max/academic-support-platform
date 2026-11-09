<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { MatchCandidate, MatchingRoundView, MatchingStudent } from '@/types/api'
import { adjustmentImpact, matchKindLabel, members, pairName, selectedPairs } from '@/utils/matching-workbench'
import { labelOf } from '@/utils/labels'

const props = defineProps<{
  view: MatchingRoundView
  previous?: MatchingRoundView
  students: MatchingStudent[]
  historical?: boolean
  editable: boolean
  busy: boolean
}>()
const emit = defineEmits<{ adjust: [data: { revision: number; removeId?: number; selectId?: number; reason: string }] }>()
const filter = ref<'selected' | 'waiting' | 'diff'>('selected')
const resultTabs = computed(() => ([
  { id: 'selected', text: '方案组合' }, { id: 'waiting', text: '待安排' }, { id: 'diff', text: '与上轮对比' },
] as const).filter(entry => !props.historical || entry.id !== 'diff'))
const editing = ref<{ pair?: MatchCandidate; student?: MatchingStudent }>()
const choice = ref<number | 'dissolve' | ''>('')
const reason = ref('')
const selected = computed(() => selectedPairs(props.view))
const people = computed(() => new Map(props.students.map(student => [student.id, student])))
const occupied = computed(() => new Set(selected.value.flatMap(members)))
const waiting = computed(() => props.view.participantIds.filter(id => !occupied.value.has(id)))
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
const impact = computed(() => choice.value ? adjustmentImpact(props.view, editing.value?.pair?.id, replacement.value) : undefined)
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

function open(pair?: MatchCandidate, student?: MatchingStudent) {
  editing.value = { pair, student }
  choice.value = ''
  reason.value = ''
}

function save() {
  if (!props.editable || props.view.round.draftRevision == null || !choice.value || !impact.value?.valid || !reason.value.trim()) return
  emit('adjust', { revision: props.view.round.draftRevision, removeId: editing.value?.pair?.id,
    selectId: replacement.value?.id, reason: reason.value.trim() })
}

function personName(id: number) {
  const participant = people.value.get(id)
  if (participant) return participant.name
  const pair = props.view.candidates.find(item => members(item).includes(id))
  return pair ? (pair.navigatorApplicationId === id ? pair.navigatorName : pair.climberName) : `报名 #${id}`
}

function namesForCandidateIds(ids: number[]) {
  const map = new Map(props.view.candidates.map(pair => [pair.id, pairName(pair)]))
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
        <div><strong>{{ occupied.size }}<template v-if="view.policy"> / {{ view.participantIds.length }}</template></strong><small>方案覆盖人数</small></div>
        <div v-if="view.policy"><strong>{{ selected.filter(pair => pair.matchKind === 'PEER').length }} 组</strong><small>大一互助</small></div>
        <div v-if="view.policy"><strong>{{ waiting.length }} 人</strong><small>待安排</small></div>
      </div>
      <div class="table-actions round-actions" aria-label="结果视图">
        <button v-for="entry in resultTabs"
          :key="entry.id" class="button small" :class="filter === entry.id ? 'secondary' : 'ghost'" :aria-pressed="filter === entry.id"
          :disabled="entry.id === 'waiting' && !view.policy" @click="filter = entry.id">{{ entry.text }}</button>
      </div>
      <template v-if="filter === 'selected'">
        <p v-if="!selected.length" class="muted">本轮尚未安排组合，请查看待安排名单。</p>
        <article v-for="pair in selected" :key="pair.id" class="result-row">
          <div class="round-header">
            <div><h3>{{ pairName(pair) }}</h3><p><span class="tag">{{ matchKindLabel(pair.matchKind) }}</span>
              <span v-if="pair.manuallySelected" class="tag">人工调整</span>
              <span v-if="view.round.status === 'PUBLISHED'" class="tag">{{ labelOf(pair.status) }}</span></p>
            </div>
            <button v-if="editable" class="button ghost small" :disabled="busy" @click="open(pair)">调整组合</button>
          </div>
          <p class="muted">覆盖科目 · {{ pair.matchedSubjects.join('、') }}</p>
          <details>
            <summary>{{ pair.coverage ? '查看依据' : '查看历史评分' }}</summary>
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
              <div v-for="id in members(pair)" :key="id"><strong>{{ personName(id) }}的报名自述</strong><p>{{ people.get(id)?.statement }}</p></div>
            </div>
          </details>
        </article>
      </template>
      <template v-else-if="filter === 'waiting'">
        <p v-if="!waiting.length" class="muted">本轮可安排的报名均已覆盖。</p>
        <div v-for="id in waiting" :key="id" class="result-row round-header">
          <div><strong>{{ personName(id) }}</strong><p class="muted">{{ pendingReason(id) }}</p></div>
          <button v-if="editable && people.get(id) && compatible.get(id)?.some(pair => adjustmentImpact(view, undefined, pair).valid)"
            class="button secondary small" :disabled="busy" @click="open(undefined, people.get(id))">手动组队</button>
        </div>
      </template>
      <template v-else>
        <p v-if="!previous" class="muted">尚无上一轮方案可对比。</p>
        <template v-else>
          <p>对比第 {{ previous.round.roundNo }} 轮，组合数 {{ selectedPairs(previous).length }} → {{ selected.length }}</p>
          <p v-if="!changes.length" class="muted">组合无变化。</p>
          <div v-for="entry in changes" :key="`${entry.action}-${entry.pair.id}`" class="result-row">{{ entry.action }} · {{ pairName(entry.pair) }}</div>
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
      <header><h2>{{ editing.pair ? `调整 ${pairName(editing.pair)}` : `为${editing.student?.name}手动组队` }}</h2></header>
      <div class="modal-body">
        <label>调整为<select v-model="choice" required :disabled="busy">
          <option value="" disabled>请选择调整方式</option>
          <option v-for="pair in options" :key="pair.id" :value="pair.id">{{ pairName(pair) }} · {{ matchKindLabel(pair.matchKind) }}</option>
          <option v-if="editing.pair" value="dissolve">拆组，双方回到待安排</option>
        </select></label>
        <div v-if="impact" class="notice warning" role="status">
          <p v-if="replacement">组建 · {{ pairName(replacement) }}（{{ replacement.matchedSubjects.join('、') }}）</p>
          <p v-if="impact.removed.length">解除 · {{ impact.removed.map(pairName).join('；') }}</p>
          <p v-if="impact.returned.length">回到待安排 · {{ impact.returned.map(personName).join('、') }}</p>
        </div>
        <label>调整原因<textarea v-model.trim="reason" required maxlength="500" :disabled="busy" /></label>
      </div>
      <footer><button type="button" class="button ghost" :disabled="busy" @click="editing = undefined">取消</button>
        <button class="button primary" :disabled="busy || !choice || !impact?.valid || !reason">保存人工调整</button></footer>
    </form>
  </dialog>
</template>

<style scoped>
.round-panel { display:grid; gap:10px; }
.round-summary,.round-header { display:flex; flex-wrap:wrap; justify-content:space-between; align-items:center; gap:10px; }
.round-summary { justify-content:flex-start; gap:12px 28px; }
.round-summary strong,.round-summary small { display:block; }
.round-summary strong { font-size:20px; }
.round-actions { flex-wrap:wrap; }
.result-row { border-top:1px solid var(--line); padding:10px 0; }
.result-row h3,.result-row p { margin:4px 0; }
.basis { overflow-wrap:anywhere; white-space:pre-wrap; }
.basis.grid { margin:8px 0; }
.basis p { margin:4px 0; }
</style>
