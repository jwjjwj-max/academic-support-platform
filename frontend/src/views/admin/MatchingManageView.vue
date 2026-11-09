<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { isAxiosError } from 'axios'
import MatchingEvidencePanel from '@/components/MatchingEvidencePanel.vue'
import MatchingExclusionsPanel from '@/components/MatchingExclusionsPanel.vue'
import MatchingRoundPanel from '@/components/MatchingRoundPanel.vue'
import MatchingRulesEditor from '@/components/MatchingRulesEditor.vue'
import { apiErrorMessage, http } from '@/services/http'
import type {
  ApiResponse, MatchingExclusion, MatchingFacts, MatchingPolicy, MatchingProvider, MatchingRound,
  MatchingRoundView, MatchingStudent, MatchingWorkspace, Period,
} from '@/types/api'
import { labelOf } from '@/utils/labels'
import { canGenerateLocalRematch, canGenerateMatching, canOperateRound } from '@/utils/matching-stage'
import { matchKindLabel, selectedPairs } from '@/utils/matching-workbench'

const tabs = [
  { id: 'rules', title: '规则配置' }, { id: 'evidence', title: 'DeepSeek 分析' },
  { id: 'trial', title: '试算与发布' }, { id: 'history', title: '轮次记录' },
] as const
const tab = ref<typeof tabs[number]['id']>('trial')
const periods = ref<Period[]>([])
const periodId = ref<number>()
const workspace = ref<MatchingWorkspace>()
const provider = ref<MatchingProvider>()
const rounds = ref<MatchingRound[]>([])
const current = ref<MatchingRoundView>()
const previous = ref<MatchingRoundView>()
const historical = ref<MatchingRoundView>()
const analysisFailures = ref<Record<number, string>>({})
const loading = ref(true), busy = ref(false), error = ref(''), success = ref(''), progress = ref('')
let readSequence = 0

const period = computed(() => periods.value.find(item => item.id === periodId.value))
const base = computed(() => `/admin/matching/periods/${periodId.value}/workspace`)
const published = computed(() => rounds.value.some(round => round.status === 'PUBLISHED'))
const draft = computed(() => rounds.value.find(round => round.status === 'DRAFT'))
const recordedRounds = computed(() => rounds.value.filter(round => round.status !== 'DRAFT'))
const canEditRules = computed(() => !published.value && ['REVIEW', 'MATCHING'].includes(period.value?.status ?? ''))
const canEditEvidence = computed(() => ['REVIEW', 'MATCHING', 'CONFIRMATION'].includes(period.value?.status ?? ''))
const canPreview = computed(() => canGenerateMatching(period.value?.status) && !published.value)
const canRematch = computed(() => canGenerateLocalRematch(period.value?.status))
const draftOpen = computed(() => current.value?.round.status === 'DRAFT' && canOperateRound(period.value?.status, current.value.round.roundType))
const stale = computed(() => !!current.value?.policy && current.value.round.workspaceRevision !== workspace.value?.settings.revision)
const editable = computed(() => Boolean(draftOpen.value && !stale.value && current.value?.policy))
const canPublish = computed(() => draftOpen.value && !stale.value && selectedPairs(current.value).length > 0 &&
  (Boolean(current.value?.policy) || workspace.value?.settings.revision === 0))

async function fetchData<T>(path: string) {
  return (await http.get<ApiResponse<T>>(path)).data.data
}

async function refresh() {
  const sequence = ++readSequence
  const path = base.value
  const [settings, list] = await Promise.all([
    fetchData<MatchingWorkspace>(path), fetchData<MatchingRound[]>(`${path}/rounds`),
  ])
  const latest = list.find(round => round.status === 'DRAFT') ?? list.find(round => round.status === 'PUBLISHED')
  const last = latest?.status === 'DRAFT' ? list.find(round => round.roundNo < latest.roundNo) : undefined
  const [active, compare] = await Promise.all([
    latest ? fetchData<MatchingRoundView>(`${path}/rounds/${latest.id}`) : undefined,
    last ? fetchData<MatchingRoundView>(`${path}/rounds/${last.id}`) : undefined,
  ])
  if (sequence !== readSequence) return
  workspace.value = settings
  rounds.value = list
  current.value = active
  previous.value = compare
}

async function loadPeriod() {
  if (!periodId.value) return
  historical.value = undefined
  analysisFailures.value = {}
  workspace.value = undefined
  current.value = undefined
  rounds.value = []
  loading.value = true
  error.value = ''; success.value = ''
  try {
    await refresh()
  } catch (exception) {
    error.value = apiErrorMessage(exception)
  } finally {
    loading.value = false
  }
}

async function operate(action: () => Promise<void>) {
  if (busy.value || loading.value) return
  busy.value = true
  error.value = ''; success.value = ''
  try {
    await action()
  } catch (exception) {
    error.value = apiErrorMessage(exception)
  } finally {
    busy.value = false
    progress.value = ''
  }
}

function savePolicy(policy: MatchingPolicy, message: string) {
  return operate(async () => {
    await http.put(`${base.value}/policy`, { revision: workspace.value?.settings.revision, policy })
    await refresh()
    success.value = message
  })
}

function saveRules(rules: Pick<MatchingPolicy, 'freshmanGrade' | 'rules'>) {
  const policy = workspace.value?.settings.policy
  if (policy && canEditRules.value) return savePolicy({ ...policy, ...rules }, '本期规则已保存。')
}

function saveExternalPermission(allowExternalAnalysis: boolean) {
  const policy = workspace.value?.settings.policy
  if (policy && canEditRules.value) return savePolicy({ ...policy, allowExternalAnalysis }, '本期外发设置已保存。')
}

function analyze(students: MatchingStudent[]) {
  return operate(async () => {
    let done = 0
    let skipped = 0
    try {
      for (const [index, student] of students.entries()) {
        progress.value = `正在分析第 ${index + 1} / ${students.length} 份自述`
        try {
          await http.post(`${base.value}/students/${student.id}/analyze`, {
            applicationRevision: student.revision, confirmedPreview: student.externalPreview,
          }, { timeout: 32_000 })
          delete analysisFailures.value[student.id]
          done++
        } catch (exception) {
          const message = apiErrorMessage(exception)
          analysisFailures.value[student.id] = message
          const code = isAxiosError<ApiResponse<unknown>>(exception) ? exception.response?.data?.code : undefined
          if (!code || ![
            'MATCHING_ANALYSIS_IN_PROGRESS', 'MATCHING_VERSION_CONFLICT', 'MATCHING_PREVIEW_CHANGED',
            'MATCHING_STUDENT_NOT_IN_POOL', 'MATCHING_EVIDENCE_EMPTY', 'INVALID_MATCHING_EVIDENCE',
            'INVALID_MATCHING_QUOTE', 'MATCHING_TOPIC_NOT_GROUNDED',
          ].includes(code)) {
            error.value = `${message}。批量分析已暂停，尚有 ${students.length - index - 1} 份未尝试。`
            break
          }
          skipped++
        }
      }
    } finally {
      await refresh()
      if (done) success.value = `已完成 ${done} 份自述分析。`
      if (skipped) error.value = `${error.value}${skipped} 份分析未完成，可展开对应报名查看原因。`
    }
  })
}

function review(student: MatchingStudent, facts: MatchingFacts) {
  return operate(async () => {
    await http.put(`${base.value}/students/${student.id}/evidence`, {
      applicationRevision: student.revision, revision: workspace.value?.settings.revision, facts,
    })
    await refresh()
    success.value = '已保存人工复核。'
  })
}

function exclude(body: MatchingExclusion & { excluded: boolean }) {
  return operate(async () => {
    await http.post(`${base.value}/exclusions`, body)
    await refresh()
    success.value = body.excluded ? '已登记禁止组合。' : '已解除禁止组合。'
  })
}

function saveKey(apiKey: string) {
  return operate(async () => {
    provider.value = (await http.put<ApiResponse<MatchingProvider>>('/admin/matching/deepseek', { apiKey })).data.data
    success.value = '密钥已加密保存。'
  })
}

function clearKey() {
  if (!window.confirm('移除 DeepSeek 密钥后，本平台将暂停外发分析。确认移除？')) return
  return operate(async () => {
    provider.value = (await http.delete<ApiResponse<MatchingProvider>>('/admin/matching/deepseek')).data.data
    success.value = '已移除密钥。'
  })
}

function testKey() {
  return operate(async () => {
    await http.post('/admin/matching/deepseek/test', undefined, { timeout: 32_000 })
    success.value = 'DeepSeek 示例文本测试通过。'
  })
}

function preview() {
  const settings = workspace.value?.settings
  if (!settings || !canPreview.value) return
  if (!settings.revision) { tab.value = 'rules'; return }
  const replaced = draft.value
  if (replaced && !window.confirm('重新试算会替换当前草稿。原方案及人工调整可在轮次记录中查看。继续？')) return
  return operate(async () => {
    await http.post(`${base.value}/preview`, { revision: settings.revision, replaceRoundId: replaced?.id, replaceRevision: replaced?.draftRevision },
      { timeout: 35_000 })
    await refresh()
    success.value = '已生成试算，请复核组合和待安排名单。'
  })
}

function rematch() {
  return operate(async () => {
    await http.post(`/admin/matching/periods/${periodId.value}/rematch`, undefined, { timeout: 35_000 })
    await refresh()
    success.value = '已为受拒绝影响的学生生成局部重匹配草稿。'
  })
}

function adjust(body: { revision: number; removeId?: number; selectId?: number; reason: string }) {
  if (!current.value || !editable.value) return
  const id = current.value.round.id
  return operate(async () => {
    current.value = (await http.post<ApiResponse<MatchingRoundView>>(`/admin/matching/rounds/${id}/adjust`, body)).data.data
    rounds.value = rounds.value.map(round => round.id === id ? current.value!.round : round)
    success.value = '人工调整已保存，结果人数已更新。'
  })
}

function discard() {
  if (!current.value || !draftOpen.value || !window.confirm('确认放弃这份草稿？原方案及人工调整仍可在轮次记录中查看。')) return
  const round = current.value.round
  return operate(async () => {
    await http.post(`/admin/matching/rounds/${round.id}/discard`, { revision: round.draftRevision, reason: '管理员在工作台放弃草稿' })
    await refresh()
    success.value = '草稿已移至轮次记录。'
  })
}

function publish() {
  if (!current.value || !canPublish.value) return
  const round = current.value.round
  const count = selectedPairs(current.value).length
  if (!window.confirm(`确认发布 ${count} 组？发布后草稿将锁定，学生在双方确认阶段查看并确认。`)) return
  return operate(async () => {
    await http.post(`/admin/matching/rounds/${round.id}/publish`, undefined, { params: { revision: round.draftRevision } })
    await refresh()
    success.value = '匹配已发布。学生在双方确认阶段查看结果。'
  })
}

function inspect(round: MatchingRound) {
  return operate(async () => {
    historical.value = await fetchData<MatchingRoundView>(`${base.value}/rounds/${round.id}`)
  })
}

onMounted(async () => {
  try {
    [periods.value, provider.value] = await Promise.all([
      fetchData<Period[]>('/periods'), fetchData<MatchingProvider>('/admin/matching/deepseek'),
    ])
    periodId.value = periods.value.find(item => ['MATCHING', 'CONFIRMATION', 'REVIEW'].includes(item.status))?.id ?? periods.value[0]?.id
    await loadPeriod()
  } catch (exception) {
    error.value = apiErrorMessage(exception)
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="workbench-toolbar">
    <label>活动期次<select v-model="periodId" :disabled="busy || loading" @change="loadPeriod"><option v-for="item in periods" :key="item.id" :value="item.id">{{ item.name }} · {{ labelOf(item.status) }}</option></select></label>
    <button class="button ghost small" :disabled="busy || loading || !periodId" @click="operate(refresh)">刷新工作台</button>
  </div>
  <nav class="workbench-tabs" aria-label="智能匹配工作台">
    <button v-for="item in tabs" :key="item.id" class="button small" :class="tab === item.id ? 'primary' : 'ghost'"
      :aria-pressed="tab === item.id" :disabled="busy" @click="tab = item.id">{{ item.title }}</button>
  </nav>
  <div v-if="error" class="notice danger" role="alert">{{ error }}</div>
  <div v-if="success" class="notice success" role="status">{{ success }}</div>
  <div v-if="progress" class="notice" role="status">{{ progress }}</div>
  <div v-if="loading" class="loading-state">正在加载匹配工作台…</div>
  <div v-else-if="workspace">
    <section v-show="tab === 'rules'" aria-label="规则配置" data-panel="rules">
      <MatchingRulesEditor :settings="workspace.settings" :editable="canEditRules" :busy="busy" @save="saveRules" />
      <MatchingExclusionsPanel :students="workspace.students" :exclusions="workspace.exclusions" :editable="canEditEvidence" :busy="busy" @save="exclude" />
    </section>
    <section v-show="tab === 'evidence'" aria-label="DeepSeek 分析" data-panel="evidence">
      <MatchingEvidencePanel :workspace="workspace" :provider="provider" :editable="canEditEvidence"
        :permission-editable="canEditRules" :busy="busy" :analysis-failures="analysisFailures" @analyze="analyze" @review="review"
        @save-permission="saveExternalPermission" @save-key="saveKey" @clear-key="clearKey" @test-key="testKey" />
    </section>
    <section v-if="tab === 'trial'" aria-label="试算与发布" data-panel="trial">
      <div class="notice">
        <template v-if="current?.round.status === 'PUBLISHED'">
          <strong>第 {{ current.round.roundNo }} 轮已发布 · {{ selectedPairs(current).length }} 组</strong>
          <p>{{ canRematch ? '学生拒绝后可发起局部重新匹配。' : '已发布方案可在轮次记录中查看。' }}</p>
        </template>
        <template v-else>
          <strong>先试算，再人工检查并发布</strong>
          <p v-if="!canPreview && !canRematch && !current">本期可在报名审核阶段准备规则和依据，进入匹配阶段后生成草稿。</p>
          <p v-else>核对组合和待安排名单后，可在草稿里调整并确认发布。</p>
        </template>
      </div>
      <div class="workbench-tabs">
        <button v-if="canPreview" class="button secondary" :disabled="busy" @click="preview">{{ workspace.settings.revision ? '生成试算' : '先保存本期规则' }}</button>
        <button v-if="canRematch" class="button secondary" :disabled="busy || Boolean(draft)" @click="rematch">局部重新匹配</button>
        <button v-if="draftOpen" class="button ghost" :disabled="busy" @click="discard">放弃草稿</button>
        <button v-if="draftOpen" class="button primary" :disabled="busy || !canPublish" @click="publish">复核并确认发布 {{ selectedPairs(current).length }} 组</button>
      </div>
      <div v-if="stale && draftOpen" class="notice warning" role="status">规则或依据已更新。请重新试算后再调整和发布。局部重匹配草稿可放弃后重算。</div>
      <MatchingRoundPanel v-if="current?.round.status === 'DRAFT'" :view="current" :previous="previous" :students="workspace.students" :editable="editable" :busy="busy" @adjust="adjust" />
    </section>
    <section v-if="tab === 'history'" aria-label="轮次记录" data-panel="history">
      <section class="card">
        <header><div><h2>轮次记录</h2><p>保留历史方案、使用的规则和人工调整。</p></div></header>
        <div class="card-body">
          <p v-if="!recordedRounds.length" class="muted">暂无已发布或已替换的轮次。当前草稿在「试算与发布」中查看。</p>
          <div v-for="round in recordedRounds" :key="round.id" class="history-row">
            <div><strong>第 {{ round.roundNo }} 轮</strong> · {{ round.roundType === 'LOCAL_REMATCH' ? '局部重匹配' : '普通匹配' }}
              · {{ round.status === 'PUBLISHED' ? '已发布' : '已被替换' }}</div>
            <button class="button ghost small" :disabled="busy" @click="inspect(round)">查看方案</button>
          </div>
        </div>
      </section>
      <section v-if="historical?.policy" :key="`rules-${historical.round.id}`" class="card">
        <details class="card-body">
          <summary>第 {{ historical.round.roundNo }} 轮规则快照</summary>
          <p>本轮大一入学年 · {{ historical.policy.freshmanGrade }}</p>
          <ol class="compact-list">
            <li v-for="rule in historical.policy.rules" :key="rule.kind">
              <strong>{{ matchKindLabel(rule.kind) }}</strong>
              <template v-if="rule.enabled"> · {{ rule.kind === 'PEER' && rule.minimumCoverage === 0 ? '可无课程依据结对' : `${rule.kind === 'PEER' ? '每个方向' : ''}至少覆盖 ${rule.minimumCoverage} 门` }} · {{ rule.maximumPairs == null ? '组数不限' : `最多 ${rule.maximumPairs} 组` }}</template>
              <template v-else> · 未启用</template>
              <small v-if="rule.enabled && rule.kind === 'REGULAR'">{{ rule.allowOlderNavigator ? '允许更高年级领航' : '仅同年级领航' }} · {{ rule.requireHigherGpa ? '要求领航者绩点更高' : '未设置绩点差门槛' }}</small>
              <small v-if="rule.enabled && rule.kind === 'FRESHMAN_SUPPORT'">{{ rule.allowOlderNavigator ? '允许大二及大三领航者' : '仅允许大二领航者' }}</small>
            </li>
          </ol>
        </details>
      </section>
      <MatchingRoundPanel v-if="historical" :key="historical.round.id" :view="historical" :students="workspace.students"
        historical :editable="false" :busy="busy" />
    </section>
  </div>
</template>

<style scoped>
.workbench-toolbar,.workbench-tabs,.history-row { display:flex; flex-wrap:wrap; gap:8px; align-items:end; }
.workbench-tabs { margin:10px 0; }
.workbench-toolbar select { min-width:0; max-width:100%; }
.workbench-toolbar label { min-width:0; }
.history-row { justify-content:space-between; align-items:center; padding:8px 0; border-bottom:1px solid var(--line); }
</style>
