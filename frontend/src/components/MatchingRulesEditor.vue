<script setup lang="ts">
import { ref, watch } from 'vue'
import type { MatchingPolicy, MatchingPolicyVersion } from '@/types/api'
import { matchKindLabel } from '@/utils/matching-workbench'

const props = defineProps<{ settings: MatchingPolicyVersion; editable: boolean; busy: boolean }>()
const emit = defineEmits<{ save: [policy: MatchingPolicy] }>()
const draft = ref<MatchingPolicy>()

watch(() => props.settings.policy, policy => {
  draft.value = { ...policy, rules: policy.rules.map(rule => ({ ...rule })) }
}, { immediate: true })

function move(index: number, direction: number) {
  if (!draft.value) return
  const rules = draft.value.rules
  const next = index + direction
  if (next >= 0 && next < rules.length) [rules[index], rules[next]] = [rules[next], rules[index]]
}

function save() {
  if (!draft.value) return
  emit('save', { ...draft.value, rules: draft.value.rules.map(rule => ({
    ...rule, maximumPairs: typeof rule.maximumPairs === 'number' ? rule.maximumPairs : null,
  })) })
}
</script>

<template>
  <section v-if="draft" class="card">
    <header><div><h2>本期匹配规则</h2><p>{{ settings.revision ? `配置与依据版本 ${settings.revision}` : '建议配置，尚未保存' }}</p></div></header>
    <form class="card-body" @submit.prevent="save">
      <div class="form-grid">
        <label>本期大一入学年<input v-model.number="draft.freshmanGrade" type="number" min="2000" max="2100" required :disabled="!editable || busy" /></label>
        <label class="risk-confirm"><input v-model="draft.allowExternalAnalysis" type="checkbox" :disabled="!editable || busy" />已确认本期允许将审核后的脱敏学业自述提交给 DeepSeek</label>
      </div>
      <p class="muted">顺序靠前的规则优先获得名额。系统在所有合格候选中统一分配，同等覆盖人数时优先覆盖更多学习需求。</p>
      <fieldset v-for="(rule, index) in draft.rules" :key="rule.kind" class="rule-fieldset" :disabled="!editable || busy">
        <legend>{{ index + 1 }}. {{ matchKindLabel(rule.kind) }}</legend>
        <div class="form-grid">
          <label>{{ rule.kind === 'PEER' ? '每个方向至少覆盖的课程数' : '至少覆盖的课程数' }}<input v-model.number="rule.minimumCoverage" type="number" min="1" max="8" required /></label>
          <label>最多组数<input v-model.number="rule.maximumPairs" type="number" min="0" max="200" placeholder="留空表示不限" /></label>
          <div class="rule-checks">
            <label class="check"><input v-model="rule.enabled" type="checkbox" />启用</label>
            <template v-if="rule.kind === 'REGULAR'">
              <label class="check"><input v-model="rule.allowOlderNavigator" type="checkbox" />允许更高年级领航</label>
              <label class="check"><input v-model="rule.requireHigherGpa" type="checkbox" />要求领航者累计绩点更高</label>
            </template>
            <p v-else class="muted">按课程与能力证据匹配，适用于暂无成绩的新生。</p>
          </div>
          <div class="form-actions">
            <button type="button" class="button ghost small" :disabled="index === 0" @click="move(index, -1)">上移</button>
            <button type="button" class="button ghost small" :disabled="index === draft.rules.length - 1" @click="move(index, 1)">下移</button>
          </div>
        </div>
      </fieldset>
      <div class="notice">每轮均检查报名审核与入池状态、一人一组、禁止组合及有效拒绝记录。</div>
      <div v-if="editable" class="form-actions"><button class="button primary" :disabled="busy || !draft.rules.some(rule => rule.enabled)">保存规则</button></div>
    </form>
  </section>
</template>

<style scoped>
.rule-fieldset { margin: 12px 0; padding: 12px; border: 1px solid var(--line); border-radius: 8px; min-width: 0; }
.rule-fieldset legend { padding: 0 6px; font-weight: 650; }
.rule-checks { display: flex; align-items: center; gap: 8px 12px; flex-wrap: wrap; }
.rule-checks p { margin: 0; font-size: 13px; }
.check { display: flex; align-items: center; }
.check input { width: auto; min-height: 0; }
</style>
