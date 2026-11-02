<script setup lang="ts">
import { computed, ref } from 'vue'
import type { ParticipationApplication } from '@/types/api'
const props = defineProps<{ applications: ParticipationApplication[]; periodName: string; loading: boolean; unavailable?: boolean }>()
const scope = ref<'APPROVED' | 'ALL' | 'POOL'>('APPROVED')
const scopes = [{ value: 'ALL', label: '全部报名' }, { value: 'APPROVED', label: '审核通过' }, { value: 'POOL', label: '已入匹配池' }] as const
const summary = computed(() => ({
  total: props.applications.length,
  navigators: props.applications.filter(item => item.participantRole === 'NAVIGATOR').length,
  climbers: props.applications.filter(item => item.participantRole === 'CLIMBER').length,
  pending: props.applications.filter(item => ['SUBMITTED', 'UNDER_REVIEW'].includes(item.applicationStatus)).length,
}))
const included = computed(() => props.applications.filter(item => scope.value === 'ALL' ||
  (item.applicationStatus === 'APPROVED' && (scope.value === 'APPROVED' || item.selectionStatus === 'MATCHING_POOL'))))
const rows = computed(() => {
  const groups = new Map<number, { grade: number; navigators: number; climbers: number; total: number }>()
  for (const item of included.value) {
    const grade = Number.isInteger(item.grade) ? item.grade : 0
    const row = groups.get(grade) ?? { grade, navigators: 0, climbers: 0, total: 0 }
    if (item.participantRole === 'NAVIGATOR') row.navigators++
    if (item.participantRole === 'CLIMBER') row.climbers++
    row.total++; groups.set(grade, row)
  }
  return [...groups.values()].sort((a, b) => (a.grade || Infinity) - (b.grade || Infinity))
})
const maximum = computed(() => Math.max(1, ...rows.value.flatMap(row => [row.navigators, row.climbers])))
const totals = computed(() => rows.value.reduce((sum, row) => ({ navigators: sum.navigators + row.navigators, climbers: sum.climbers + row.climbers, total: sum.total + row.total }), { navigators: 0, climbers: 0, total: 0 }))
const scopeLabel = computed(() => scopes.find(item => item.value === scope.value)?.label)
function gradeLabel(grade: number) { return grade ? `${grade}级` : '未填写年级' }
</script>

<template>
  <section class="card application-statistics" aria-labelledby="application-statistics-title" :aria-busy="loading">
    <header><div><h2 id="application-statistics-title">本期报名统计</h2><p>{{ periodName }} · 按整个期次统计，不受下方名单筛选和分页影响</p></div><slot name="controls" /></header>
    <div v-if="loading" class="loading-state" role="status">正在加载本期统计…</div>
    <div v-else-if="unavailable" class="empty-state" role="alert">统计暂不可用，请点击“刷新数据”重试。</div>
    <div v-else class="card-body">
      <div class="summary-cards">
        <div class="summary-card"><span>报名总人数</span><strong data-testid="summary-total">{{ summary.total }}</strong><small>本期全部报名</small></div>
        <div class="summary-card navigator"><span>学业领航者</span><strong data-testid="summary-navigators">{{ summary.navigators }}</strong><small>包含各审核状态</small></div>
        <div class="summary-card climber"><span>学业攀登者</span><strong data-testid="summary-climbers">{{ summary.climbers }}</strong><small>包含各审核状态</small></div>
        <div class="summary-card"><span>待审核人数</span><strong data-testid="summary-pending">{{ summary.pending }}</strong><small>待审核及审核中</small></div>
      </div>
      <div class="statistics-heading"><h3>各年级人数</h3><label>统计口径<select v-model="scope" aria-label="人数统计口径"><option v-for="item in scopes" :key="item.value" :value="item.value">{{ item.label }}</option></select></label></div>
      <p class="statistics-note">当前口径：{{ scopeLabel }} · 共 {{ totals.total }} 人。切换口径仅影响下方图表和明细，上方始终汇总全部报名。</p>
      <div v-if="!rows.length" class="statistics-empty">当前口径暂无报名，切换统计口径可查看其他人数。</div>
      <div v-else class="statistics-layout">
        <div class="grade-chart" role="img" :aria-label="`${scopeLabel}的各年级领航者与攀登者人数对比，精确数据见右侧或下方明细表`">
          <div class="chart-legend"><span><i class="navigator-color" />学业领航者</span><span><i class="climber-color" />学业攀登者</span></div>
          <div v-for="row in rows" :key="row.grade" class="chart-grade">
            <strong>{{ gradeLabel(row.grade) }}</strong>
            <div class="grade-bars"><div class="bar-row"><span>领航者</span><div class="bar-track"><div class="bar navigator-color" :style="{ width: `${row.navigators / maximum * 100}%` }" /></div><b>{{ row.navigators }} 人</b></div>
            <div class="bar-row"><span>攀登者</span><div class="bar-track"><div class="bar climber-color" :style="{ width: `${row.climbers / maximum * 100}%` }" /></div><b>{{ row.climbers }} 人</b></div></div>
          </div>
        </div>
        <div class="table-wrap statistics-table"><table><caption>年级人数明细（{{ scopeLabel }}）</caption><thead><tr><th scope="col">年级</th><th scope="col">学业领航者</th><th scope="col">学业攀登者</th><th scope="col">合计</th></tr></thead><tbody><tr v-for="row in rows" :key="row.grade"><th scope="row">{{ gradeLabel(row.grade) }}</th><td>{{ row.navigators }}</td><td>{{ row.climbers }}</td><td>{{ row.total }}</td></tr></tbody><tfoot><tr><th scope="row">合计</th><td>{{ totals.navigators }}</td><td>{{ totals.climbers }}</td><td>{{ totals.total }}</td></tr></tfoot></table></div>
      </div>
    </div>
  </section>
</template>
<style scoped>
.application-statistics>header{flex-wrap:wrap}.application-statistics>header :deep(label){min-width:145px}.summary-cards{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:14px}.summary-card{padding:18px;border:1px solid var(--line);border-radius:8px;background:#f8fafc}.summary-card span,.summary-card small{display:block;color:var(--muted)}.summary-card span{font-size:13px}.summary-card strong{display:block;font-size:30px;margin:9px 0}.summary-card small{font-size:12px}.summary-card.navigator strong{color:#2863b6}.summary-card.climber strong{color:#16875b}.statistics-heading{display:flex;align-items:center;justify-content:space-between;gap:14px;margin-top:24px}.statistics-heading h3{font-size:16px;margin:0}.statistics-heading label{display:flex;align-items:center;gap:10px;white-space:nowrap}.statistics-heading select{width:auto}.statistics-note{font-size:12px;color:var(--muted);line-height:1.7}.statistics-layout{display:grid;grid-template-columns:minmax(0,1.2fr) minmax(0,1fr);gap:24px;align-items:start;margin-top:18px}.chart-legend{display:flex;gap:18px;font-size:12px;color:var(--muted);margin-bottom:20px}.chart-legend span{display:flex;align-items:center;gap:7px}.chart-legend i{display:inline-block;width:10px;height:10px;border-radius:3px}.navigator-color{background:#2863b6}.climber-color{background:#16875b}.chart-grade{display:grid;grid-template-columns:75px minmax(0,1fr);gap:10px;margin-bottom:18px;align-items:center;font-size:13px}.grade-bars{display:grid;gap:8px}.bar-row{display:grid;grid-template-columns:42px minmax(0,1fr) 45px;gap:8px;align-items:center;font-size:12px}.bar-row>span{color:var(--muted)}.bar-row b{text-align:right;font-variant-numeric:tabular-nums}.bar-track{height:14px;border-radius:4px;background:#edf0f4;overflow:hidden}.bar{height:100%;border-radius:4px}.statistics-table table{min-width:340px}.statistics-table caption{text-align:left;font-size:13px;font-weight:700;padding-bottom:12px}.statistics-table th:not(:first-child),.statistics-table td{text-align:right;font-variant-numeric:tabular-nums}.statistics-table tfoot{font-weight:700;background:#f8fafc}.statistics-table tbody th{background:transparent;color:inherit;font-size:13px}.statistics-empty{padding:28px 12px;text-align:center;color:var(--muted);background:#f8fafc;border-radius:8px}
@media(max-width:1050px){.statistics-layout{grid-template-columns:1fr}}@media(max-width:600px){.summary-cards{grid-template-columns:repeat(2,minmax(0,1fr));gap:10px}.summary-card{padding:14px}.summary-card strong{font-size:26px}.statistics-heading{align-items:flex-start;flex-direction:column}.chart-grade{grid-template-columns:60px minmax(0,1fr)}.bar-row{grid-template-columns:38px minmax(0,1fr) 38px;gap:5px}.statistics-layout{gap:14px}.chart-legend{gap:12px}}
</style>
