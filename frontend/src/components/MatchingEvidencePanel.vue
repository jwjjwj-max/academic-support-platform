<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { MatchingFacts, MatchingProvider, MatchingStudent, MatchingWorkspace } from '@/types/api'

const props = defineProps<{
  workspace: MatchingWorkspace
  provider?: MatchingProvider
  editable: boolean
  permissionEditable: boolean
  busy: boolean
  analysisFailures?: Record<number, string>
}>()
const emit = defineEmits<{
  analyze: [students: MatchingStudent[]]
  review: [student: MatchingStudent, facts: MatchingFacts]
  savePermission: [allowed: boolean]
  saveKey: [key: string]
  clearKey: []
  testKey: []
}>()
const keyword = ref('')
const apiKey = ref('')
const opened = ref<number>()
const editor = ref<MatchingFacts>()
const approved = ref<string[]>([])
const externalAllowed = ref(false)
watch(() => props.workspace.settings.policy.allowExternalAnalysis, allowed => {
  externalAllowed.value = allowed
}, { immediate: true })
const factFields = [{ key: 'needs', title: '需要帮助' }, { key: 'offers', title: '可以提供帮助' }] as const
const analyzed = computed(() => props.workspace.students.filter(student => student.evidence).length)
const visible = computed(() => {
  const search = keyword.value.trim().toLowerCase()
  return props.workspace.students.filter(student => !search ||
    student.name.toLowerCase().includes(search) || student.studentNo.toLowerCase().includes(search))
})
const canSend = computed(() => props.editable && props.workspace.settings.policy.allowExternalAnalysis &&
  props.provider?.configured && props.provider.encryptionReady)
const pendingVisible = computed(() => visible.value.filter(student => !student.evidence && student.externalPreview?.trim()))
const queue = computed(() => pendingVisible.value.filter(student => approved.value.includes(approvalKey(student))))

function approvalKey(student: MatchingStudent) {
  return `${student.id}:${student.revision}:${student.externalPreview ?? ''}`
}

function confirmVisible() {
  if (!canSend.value || props.busy) return
  approved.value = [...new Set([...approved.value, ...pendingVisible.value.map(approvalKey)])]
}

function analyzeConfirmed() {
  if (canSend.value && !props.busy && queue.value.length) emit('analyze', queue.value)
}

function review(student: MatchingStudent) {
  opened.value = student.id
  editor.value = {
    needs: student.evidence?.facts.needs.map(topic => ({ ...topic })) ?? [],
    offers: student.evidence?.facts.offers.map(topic => ({ ...topic })) ?? [],
  }
}

function saveEvidence(student: MatchingStudent) {
  if (editor.value) emit('review', student, editor.value)
}

function saveKey() {
  if (!apiKey.value.trim()) return
  emit('saveKey', apiKey.value.trim())
  apiKey.value = ''
}
</script>

<template>
  <section class="card">
    <header><div><h2>自述分析</h2><p>已有依据 {{ analyzed }} 份 · 待处理 {{ workspace.students.length - analyzed }} 份</p></div></header>
    <div class="card-body evidence-layout">
      <details>
        <summary>连接与外发设置 · {{ provider?.configured ? `已配置密钥（尾号 ${provider.suffix}）` : '密钥待配置' }}</summary>
        <p v-if="provider" class="muted">模型 · {{ provider.model }}</p>
        <p class="muted">连接测试只使用示例文本。后端保存加密密钥，正式分析从下方确认过的脱敏文本发送。</p>
        <p v-if="!provider?.encryptionReady" class="notice warning">服务器尚未配置 MATCHING_ENCRYPTION_KEY，仍可人工复核自述并试算。</p>
        <form class="form-grid" @submit.prevent="saveKey">
          <label>新 API 密钥<input v-model="apiKey" type="password" maxlength="256" autocomplete="new-password" :disabled="busy || !provider?.encryptionReady" /></label>
          <div class="form-actions connection-actions">
            <button class="button secondary small" :disabled="busy || !provider?.encryptionReady || !apiKey.trim()">保存密钥</button>
            <button type="button" class="button ghost small" :disabled="busy || !provider?.configured || !provider.encryptionReady" @click="emit('testKey')">测试连接</button>
            <button type="button" class="button ghost small" :disabled="busy || !provider?.configured" @click="emit('clearKey')">移除密钥</button>
          </div>
        </form>
        <form class="permission-actions" @submit.prevent="emit('savePermission', externalAllowed)">
          <label class="evidence-check">
            <input v-model="externalAllowed" type="checkbox" :disabled="busy || !permissionEditable" />
            允许本期将核对过的脱敏自述提交给 DeepSeek
          </label>
          <button class="button secondary small" :disabled="busy || !permissionEditable || externalAllowed === workspace.settings.policy.allowExternalAnalysis">保存外发设置</button>
        </form>
        <p v-if="!workspace.settings.revision && permissionEditable" class="muted">当前使用建议规则。保存外发设置时会一并保存当前规则。</p>
        <p v-else-if="!permissionEditable" class="muted">本期外发设置可在报名审核阶段或首次发布前的匹配阶段修改。</p>
      </details>
      <p v-if="!workspace.students.length" class="muted">本期匹配池暂无学生。请先在「报名审核」将符合条件的学生入池。</p>
      <p v-else-if="!workspace.settings.policy.allowExternalAnalysis" class="muted">本期外发分析尚未启用，可在「连接与外发设置」开启，或直接人工复核自述。</p>
      <div v-if="workspace.students.length" class="evidence-toolbar">
        <input v-model="keyword" type="search" aria-label="查找自述" placeholder="按姓名或学号查找" />
      </div>
      <section v-if="workspace.students.length" class="batch-analysis" aria-label="批量分析">
        <div>
          <h3>批量确认与分析</h3>
          <p role="status">当前筛选可确认 {{ pendingVisible.length }} 份 · 已确认 {{ queue.length }} 份</p>
          <p class="muted">核对本批脱敏预览后可一键确认。点「开始批量分析」才会将已确认的预览发送给 DeepSeek。</p>
        </div>
        <div class="batch-actions">
          <button type="button" class="button secondary" :disabled="busy || !canSend || queue.length === pendingVisible.length" @click="confirmVisible">
            一键确认脱敏预览
          </button>
          <button type="button" class="button primary" :disabled="busy || !canSend || !queue.length" @click="analyzeConfirmed">
            开始批量分析（{{ queue.length }} 份）
          </button>
        </div>
        <details v-if="pendingVisible.length" class="batch-preview">
          <summary>集中查看本批脱敏预览（{{ pendingVisible.length }} 份）</summary>
          <ul>
            <li v-for="student in pendingVisible" :key="student.id">
              <strong>{{ student.name }} · {{ student.studentNo }}</strong>
              <p class="statement">{{ student.externalPreview }}</p>
              <label v-if="canSend" class="evidence-check">
                <input v-model="approved" type="checkbox" :value="approvalKey(student)" :disabled="busy" />
                确认 {{ student.name }} 的预览可发送
              </label>
            </li>
          </ul>
        </details>
      </section>
      <p v-if="workspace.students.length && !visible.length" class="muted">没有符合条件的入池报名。</p>
      <details v-for="student in visible" :key="student.id" class="evidence-row">
        <summary>{{ student.name }} · {{ student.studentNo }} · {{ student.grade }} 级
          <span class="tag">{{ student.evidence?.reviewed ? '人工已复核' : student.evidence ? '已提取' : analysisFailures?.[student.id] ? '分析未完成' : '待处理' }}</span>
          <span v-if="student.pairingClue" class="tag">有约组线索</span>
        </summary>
        <div class="grid two">
          <div><strong>报名自述原文</strong><p class="statement">{{ student.statement }}</p></div>
          <div>
            <strong>脱敏外发预览</strong><p class="statement">{{ student.externalPreview || '暂无可外发文本' }}</p>
            <label v-if="canSend && !student.evidence && student.externalPreview" class="evidence-check">
              <input v-model="approved" type="checkbox" :value="approvalKey(student)" :disabled="busy" />确认本条预览可发送
            </label>
          </div>
        </div>
        <p>报名科目 · {{ student.subjects.join('、') }}</p>
        <p v-if="!student.evidence && analysisFailures?.[student.id]" class="text-danger">{{ analysisFailures[student.id] }}</p>
        <dl v-if="student.evidence && opened !== student.id" class="detail-list">
          <div v-for="field in factFields" :key="field.key">
            <dt>{{ field.title }}</dt>
            <dd v-if="student.evidence.facts[field.key].length">
              <p v-for="(topic, index) in student.evidence.facts[field.key]" :key="index">{{ topic.course }} {{ topic.knowledgePoint }}<small class="statement">「{{ topic.quote }}」</small></p>
            </dd>
            <dd v-else>暂无明确依据</dd>
          </div>
        </dl>
        <button v-if="editable && opened !== student.id" type="button" class="button secondary small" :disabled="busy" @click="review(student)">复核或录入依据</button>
        <form v-if="editable && editor && opened === student.id" class="evidence-layout" @submit.prevent="saveEvidence(student)">
          <fieldset v-for="field in factFields" :key="field.key" class="fact-fieldset" :disabled="busy">
            <legend>{{ field.title }}</legend>
            <div v-for="(topic, index) in editor[field.key]" :key="index" class="form-grid fact-row">
              <label>课程<input v-model.trim="topic.course" maxlength="80" required /></label>
              <label>知识点<input v-model.trim="topic.knowledgePoint" maxlength="80" /></label>
              <label>原文依据<input v-model.trim="topic.quote" maxlength="200" required /></label>
              <div class="form-actions"><button type="button" class="button ghost small" @click="editor[field.key].splice(index, 1)">删除本项</button></div>
            </div>
            <button type="button" class="button ghost small" :disabled="editor[field.key].length >= 8" @click="editor[field.key].push({ course: '', quote: '' })">添加一项</button>
          </fieldset>
          <div class="form-actions"><button type="button" class="button ghost" @click="opened = undefined">关闭</button><button class="button primary" :disabled="busy">保存人工复核</button></div>
        </form>
      </details>
    </div>
  </section>
</template>

<style scoped>
.evidence-layout { display: grid; gap: 12px; }
.evidence-toolbar,.connection-actions { display:flex; gap:8px; align-items:end; flex-wrap:wrap; }
.permission-actions { display:flex; gap:8px 12px; align-items:center; flex-wrap:wrap; margin-top:12px; }
.evidence-toolbar input { width:auto; min-width:0; max-width:100%; }
.batch-analysis { display:grid; gap:10px; padding:16px; border:1px solid var(--line); border-radius:8px; background:#f8fafc; }
.batch-analysis h3,.batch-analysis p { margin:0; }
.batch-analysis h3 { font-size:18px; }
.batch-analysis h3 + p { margin:6px 0 3px; font-weight:650; }
.batch-actions { display:flex; flex-wrap:wrap; gap:10px; }
.batch-actions .button { min-height:46px; padding:10px 18px; font-size:16px; }
.batch-preview ul { list-style:none; padding:0; margin:8px 0 0; }
.batch-preview li { border-top:1px solid var(--line); padding:10px 0; }
.batch-preview li p { margin:5px 0; }
@media (max-width:540px) { .batch-actions > button { width:100%; } }
.evidence-row { border-top:1px solid var(--line); padding-top:10px; min-width:0; }
.statement { display:block; white-space:pre-wrap; overflow-wrap:anywhere; }
.evidence-check { display:flex; align-items:center; }
.evidence-check input { width:auto; min-height:0; }
.fact-fieldset { min-width:0; border:1px solid var(--line); border-radius:8px; }
.fact-row { align-items:end; margin-bottom:8px; }
</style>
