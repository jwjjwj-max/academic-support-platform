<script setup lang="ts">
import { computed, ref } from 'vue'
import type { MatchingExclusion, MatchingFacts, MatchingProvider, MatchingStudent, MatchingWorkspace } from '@/types/api'

const props = defineProps<{ workspace: MatchingWorkspace; provider?: MatchingProvider; editable: boolean; busy: boolean }>()
const emit = defineEmits<{
  analyze: [students: MatchingStudent[]]
  review: [student: MatchingStudent, facts: MatchingFacts]
  exclude: [request: MatchingExclusion & { excluded: boolean }]
  saveKey: [key: string]
  clearKey: []
  testKey: []
}>()
const keyword = ref('')
const apiKey = ref('')
const opened = ref<number>()
const editor = ref<MatchingFacts>()
const approved = ref<string[]>([])
const exclusion = ref<{ firstApplicationId: number | ''; secondApplicationId: number | ''; reason: string; excluded: boolean }>()
const factFields = [{ key: 'needs', title: '需要帮助' }, { key: 'offers', title: '可以提供帮助' }] as const
const analyzed = computed(() => props.workspace.students.filter(student => student.evidence).length)
const visible = computed(() => {
  const search = keyword.value.trim().toLowerCase()
  return props.workspace.students.filter(student => !search ||
    student.name.toLowerCase().includes(search) || student.studentNo.toLowerCase().includes(search))
})
const canSend = computed(() => props.editable && props.workspace.settings.policy.allowExternalAnalysis &&
  props.provider?.configured && props.provider.encryptionReady)
const queue = computed(() => props.workspace.students.filter(student =>
  approved.value.includes(approvalKey(student)) && !student.evidence && student.externalPreview))

function approvalKey(student: MatchingStudent) {
  return `${student.id}:${student.revision}:${student.externalPreview ?? ''}`
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

function submitExclusion() {
  const draft = exclusion.value
  if (!draft || !draft.firstApplicationId || !draft.secondApplicationId || !draft.reason.trim()) return
  emit('exclude', { ...draft, firstApplicationId: draft.firstApplicationId, secondApplicationId: draft.secondApplicationId, reason: draft.reason.trim() })
  exclusion.value = undefined
}

function saveKey() {
  if (!apiKey.value.trim()) return
  emit('saveKey', apiKey.value.trim())
  apiKey.value = ''
}

function name(id: number) {
  return props.workspace.students.find(student => student.id === id)?.name ?? `报名 #${id}`
}
</script>

<template>
  <section class="card">
    <header><div><h2>自述分析</h2><p>已有依据 {{ analyzed }} 份 · 待处理 {{ workspace.students.length - analyzed }} 份</p></div></header>
    <div class="card-body evidence-layout">
      <details>
        <summary>DeepSeek 连接设置 · {{ provider?.configured ? `已配置（尾号 ${provider.suffix}）` : '待配置' }}</summary>
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
      </details>
      <p v-if="!workspace.settings.policy.allowExternalAnalysis" class="notice">本期外发分析尚未启用。可以人工录入带原文依据的课程需求与能力。</p>
      <div class="evidence-toolbar">
        <input v-model="keyword" type="search" aria-label="查找自述" placeholder="按姓名或学号查找" />
        <button class="button primary small" :disabled="busy || !canSend || !queue.length" @click="emit('analyze', queue)">分析选中自述 {{ queue.length }} 份</button>
      </div>
      <p v-if="!visible.length" class="muted">没有符合条件的入池报名。</p>
      <details v-for="student in visible" :key="student.id" class="evidence-row">
        <summary>{{ student.name }} · {{ student.studentNo }} · {{ student.grade }} 级
          <span class="tag">{{ student.evidence?.reviewed ? '人工已复核' : student.evidence ? '已提取' : '待处理' }}</span>
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
  <section class="card">
    <header><div><h2>禁止组合</h2><p>核实后的私下约组在试算、人工调整和重匹配时均被排除。</p></div>
      <button v-if="editable" class="button secondary small" :disabled="busy" @click="exclusion = { firstApplicationId: '', secondApplicationId: '', reason: '', excluded: true }">登记排除</button>
    </header>
    <div class="card-body">
      <ul v-if="workspace.exclusions.length" class="compact-list">
        <li v-for="item in workspace.exclusions" :key="`${item.firstApplicationId}-${item.secondApplicationId}`">
          <strong>{{ name(item.firstApplicationId) }} / {{ name(item.secondApplicationId) }}</strong> · {{ item.reason }}
          <button v-if="editable" class="button ghost small" :disabled="busy" @click="exclusion = { ...item, reason: '', excluded: false }">解除排除</button>
        </li>
      </ul>
      <p v-else class="muted">暂无已核实的禁止组合。</p>
      <form v-if="exclusion && editable" class="form-grid" @submit.prevent="submitExclusion">
        <label v-for="side in (['firstApplicationId', 'secondApplicationId'] as const)" :key="side">{{ side === 'firstApplicationId' ? '学生一' : '学生二' }}
          <select v-model="exclusion[side]" required :disabled="!exclusion.excluded || busy">
            <option value="" disabled>选择入池学生</option>
            <option v-for="student in workspace.students" :key="student.id" :value="student.id">{{ student.name }} · {{ student.studentNo }}</option>
          </select>
        </label>
        <label class="full-row">核实或解除原因<textarea v-model.trim="exclusion.reason" maxlength="500" required /></label>
        <div class="form-actions full-row"><button type="button" class="button ghost" @click="exclusion = undefined">取消</button><button class="button primary" :disabled="busy || exclusion.firstApplicationId === exclusion.secondApplicationId">确认{{ exclusion.excluded ? '排除' : '解除' }}</button></div>
      </form>
    </div>
  </section>
</template>

<style scoped>
.evidence-layout { display: grid; gap: 12px; }
.evidence-toolbar,.connection-actions { display:flex; gap:8px; align-items:end; flex-wrap:wrap; }
.evidence-toolbar input { width:auto; min-width:0; max-width:100%; }
.evidence-row { border-top:1px solid var(--line); padding-top:10px; min-width:0; }
.statement { display:block; white-space:pre-wrap; overflow-wrap:anywhere; }
.evidence-check { display:flex; align-items:center; }
.evidence-check input { width:auto; min-height:0; }
.fact-fieldset { min-width:0; border:1px solid var(--line); border-radius:8px; }
.fact-row { align-items:end; margin-bottom:8px; }
</style>
