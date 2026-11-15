<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { MatchCandidate, MatchingRoundView } from '@/types/api'
import { selectedPairs } from '@/utils/matching-workbench'

const props = defineProps<{ views: MatchingRoundView[]; canReconsider: boolean; busy: boolean; deadlinePassed: boolean }>()
const emit = defineEmits<{ reconsider: [candidateId: number, reason: string] }>()
const scope = ref('ALL')
const editing = ref<MatchCandidate>()
const reason = ref('')
const rows = computed(() => props.views.flatMap(view => selectedPairs(view).map(pair => ({
  pair, roundNo: view.round.roundNo,
  navigator: view.participants?.find(person => person.id === pair.navigatorApplicationId),
  climber: view.participants?.find(person => person.id === pair.climberApplicationId),
}))))
const states = [
  { value: 'ALL', label: '全部' }, { value: 'PUBLISHED', label: '待确认' },
  { value: 'CONFIRMED', label: '双方已确认' }, { value: 'REJECTED', label: '已拒绝' },
]
const visible = computed(() => rows.value.filter(({ pair }) => scope.value === 'ALL' || pair.status === scope.value))

watch(() => props.views, () => { editing.value = undefined })

function decisionLabel(decision: string) {
  if (decision === 'CONFIRMED') return '已同意'
  if (decision === 'REJECTED') return '已拒绝'
  return '待确认'
}

function open(pair: MatchCandidate) {
  editing.value = pair
  reason.value = ''
}

function submit() {
  if (editing.value && reason.value.trim() && !props.busy && props.canReconsider) {
    emit('reconsider', editing.value.id, reason.value.trim())
  }
}
</script>

<template>
  <section class="card" aria-label="确认进度">
    <header><h2>确认进度</h2></header>
    <div class="card-body">
      <div class="table-actions confirmation-filters" aria-label="筛选确认状态">
        <button v-for="state in states" :key="state.value" class="button small"
          :class="scope === state.value ? 'secondary' : 'ghost'" :aria-pressed="scope === state.value" @click="scope = state.value">
          {{ state.label }}（{{ state.value === 'ALL' ? rows.length : rows.filter(({ pair }) => pair.status === state.value).length }}）
        </button>
      </div>
      <div v-if="visible.length" class="table-wrap">
        <table aria-label="已发布组合的确认状态">
          <thead><tr><th>轮次</th><th>成员一</th><th>成员二</th><th>组合状态</th><th v-if="canReconsider">操作</th></tr></thead>
          <tbody>
            <tr v-for="row in visible" :key="row.pair.id">
              <td>第 {{ row.roundNo }} 轮</td>
              <td>{{ row.pair.navigatorName }}<template v-if="row.navigator?.studentNo">（{{ row.navigator.studentNo }}）</template>
                <small>{{ decisionLabel(row.pair.navigatorDecision) }}</small></td>
              <td>{{ row.pair.climberName }}<template v-if="row.climber?.studentNo">（{{ row.climber.studentNo }}）</template>
                <small>{{ decisionLabel(row.pair.climberDecision) }}</small></td>
              <td>{{ states.find(state => state.value === row.pair.status)?.label }}
                <small v-if="row.pair.rejectionReason">{{ row.pair.rejectionReason }}</small></td>
              <td v-if="canReconsider"><button v-if="row.pair.status === 'REJECTED'" class="button secondary small" :disabled="busy"
                @click="open(row.pair)">登记改为同意</button></td>
            </tr>
          </tbody>
        </table>
      </div>
      <p v-else class="muted">当前筛选下没有组合。</p>
    </div>
  </section>
  <dialog v-if="editing" open class="modal" aria-label="登记改为同意">
    <form @submit.prevent="submit">
      <header><h2>登记改为同意</h2></header>
      <div class="modal-body">
        <p>请核实原拒绝方 {{ editing.navigatorDecision === 'REJECTED' ? editing.navigatorName : editing.climberName }} 已同意恢复原组合。</p>
        <p v-if="[editing.navigatorDecision, editing.climberDecision].includes('PENDING')" class="muted">
          另一方仍需确认。{{ deadlinePassed ? '本期确认已截止，请在活动期次延长确认时间。' : '' }}
        </p>
        <p v-else class="muted">另一方已经同意，保存后将生成正式帮扶对。</p>
        <label>核实记录<textarea v-model.trim="reason" required maxlength="500" :disabled="busy" placeholder="填写何时、通过什么方式核实了学生的新决定" /></label>
      </div>
      <footer>
        <button type="button" class="button ghost" :disabled="busy" @click="editing = undefined">取消</button>
        <button class="button primary" :disabled="busy || !reason">保存记录</button>
      </footer>
    </form>
  </dialog>
</template>

<style scoped>
.confirmation-filters { flex-wrap:wrap; margin-bottom:12px; }
.modal-body p { margin:0; }
</style>
