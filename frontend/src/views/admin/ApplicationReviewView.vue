<script setup lang="ts">
import ApplicationStatistics from '@/components/ApplicationStatistics.vue'
import PaginationBar from '@/components/PaginationBar.vue'
import { computed, nextTick, onMounted, ref } from 'vue'
import { apiErrorMessage, http } from '@/services/http'
import type { ApiResponse, ParticipationApplication, Period, SelectionStatus } from '@/types/api'
import { applicationAdminPermissions } from '@/utils/application-stage'
import { labelOf } from '@/utils/labels'

const periods = ref<Period[]>([])
const allApplications = ref<ParticipationApplication[]>([])
const items = computed(() => allApplications.value.filter(item => !status.value || (status.value === 'SUBMITTED'
  ? ['SUBMITTED', 'UNDER_REVIEW'].includes(item.applicationStatus) : item.applicationStatus === status.value)))
const page = ref(1), size = ref(20)
const visibleItems = computed(() => items.value.slice((page.value - 1) * size.value, page.value * size.value))
function filterChanged() { page.value = 1 }
function periodChanged() { page.value = 1; void load() }
function changeSize(value: number) { size.value = value; page.value = 1 }
const periodId = ref<number | ''>('')
const status = ref('SUBMITTED')
const loading = ref(true)
const error = ref('')
const success = ref('')
const target = ref<ParticipationApplication | null>(null)
const decision = ref<'APPROVED' | 'REJECTED'>('APPROVED')
const reason = ref('')
const submitting = ref(false)

const selectedPeriod = computed(() => periods.value.find((item) => item.id === periodId.value))
const permissions = computed(() => applicationAdminPermissions(selectedPeriod.value?.status))

async function loadPeriods() {
  periods.value = (await http.get<ApiResponse<Period[]>>('/periods')).data.data
  if (!periodId.value && periods.value.length) periodId.value = periods.value[0].id
}

let requestVersion = 0
async function load() {
  const version = ++requestVersion
  target.value = null
  loading.value = true
  error.value = ''
  allApplications.value = []
  if (!periodId.value) { loading.value = false; return }
  try {
    const result = (await http.get<ApiResponse<ParticipationApplication[]>>('/admin/applications', {
      params: { periodId: periodId.value },
    })).data.data
    if (version !== requestVersion) return
    allApplications.value = result
    page.value = Math.min(page.value, Math.max(1, Math.ceil(items.value.length / size.value)))
  } catch (e) {
    if (version === requestVersion) error.value = apiErrorMessage(e)
  } finally {
    if (version === requestVersion) loading.value = false
  }
}

function openReview(item: ParticipationApplication) {
  if (!permissions.value.canReview) return
  target.value = item
  decision.value = 'APPROVED'
  reason.value = ''
}

async function review() {
  if (!target.value || !permissions.value.canReview) return
  submitting.value = true
  try {
    await http.post(`/admin/applications/${target.value.id}/review`, { decision: decision.value, reason: reason.value })
    success.value = decision.value === 'APPROVED'
      ? '报名已审核通过，请继续选择入选状态。'
      : '报名已驳回，学生可在报名审核阶段按原因修改重提。'
    target.value = null
    await load()
  } catch (e) {
    error.value = apiErrorMessage(e)
  } finally {
    submitting.value = false
  }
}

async function select(item: ParticipationApplication, selectionStatus: SelectionStatus) {
  if (!permissions.value.canSelect || submitting.value) return
  const scrollTop = window.scrollY
  submitting.value = true
  try {
    await http.post(`/admin/applications/${item.id}/selection`, { selectionStatus })
    success.value = selectionStatus === 'MATCHING_POOL'
      ? `已将${item.realName}加入匹配池，后续匹配会使用该报名。`
      : selectionStatus === 'REMOVED_FROM_POOL'
        ? `已将${item.realName}撤出匹配池，当前不会参与本期匹配。`
        : '入选状态已保存'
    await load()
    await nextTick()
    window.scrollTo({ top: scrollTop, behavior: 'auto' })
  } catch (e) {
    error.value = apiErrorMessage(e)
  } finally {
    submitting.value = false
  }
}

function withdraw(item: ParticipationApplication) {
  if (!window.confirm(`确定将${item.realName}撤出匹配池吗？撤出后本期匹配不会再使用该报名。`)) return
  void select(item, 'REMOVED_FROM_POOL')
}

onMounted(async () => {
  try {
    await loadPeriods()
    await load()
  } catch (e) {
    error.value = apiErrorMessage(e)
    loading.value = false
  }
})
</script>

<template>
  <ApplicationStatistics :applications="allApplications" :period-name="selectedPeriod?.name || '未选择期次'" :loading="loading" :unavailable="Boolean(error)">
    <template #controls><label>活动期次
        <select v-model.number="periodId" aria-label="期次" @change="periodChanged">
          <option v-for="item in periods" :key="item.id" :value="item.id">{{ item.shortName }}</option>
        </select>
    </label></template>
  </ApplicationStatistics>
  <section class="card">
    <header>
      <div>
        <h2>报名审核与匹配池</h2>
        <p>报名不按成绩自动淘汰；审核后由管理员人工选择入池、候补或未入选</p>
      </div>
      <div class="inline-filters">
        <button class="button ghost small" :disabled="loading" @click="load">刷新数据</button>

        <select v-model="status" aria-label="审核状态" @change="filterChanged">
          <option value="SUBMITTED">待审核</option>
          <option value="APPROVED">已通过</option>
          <option value="REJECTED">已驳回</option>
          <option value="">全部</option>
        </select>
      </div>
    </header>

    <p class="card-body muted">当前按期次和审核状态筛选；选择“全部”可查看所选期次的所有报名。未提交报名的学生不在此列表。</p>
    <div class="notice" :class="permissions.noticeTone">
      <strong>{{ selectedPeriod ? `${selectedPeriod.name} · ${labelOf(selectedPeriod.status)}` : '未选择期次' }}</strong>
      <p>{{ permissions.notice }}</p>
    </div>
    <div v-if="error" class="notice danger action-feedback" role="alert" aria-live="assertive"><strong>操作未完成</strong><p>{{ error }}</p></div>
    <div v-if="success" class="notice success action-feedback" role="status" aria-live="polite"><strong>操作已完成</strong><p>{{ success }}</p></div>
    <div v-if="loading" class="loading-state">正在加载…</div>
    <div v-else-if="!items.length" class="empty-state">
      <h3>没有符合条件的报名</h3>
      <p>可以切换期次或审核状态。</p>
    </div>
    <div v-else class="table-wrap">
      <table>
        <thead><tr><th>学生</th><th>身份 / 年级专业</th><th>报名累计绩点</th><th>手填科目</th><th>审核 / 入选</th><th>操作</th></tr></thead>
        <tbody>
          <tr v-for="item in visibleItems" :key="item.id">
            <td><strong>{{ item.realName }}</strong><small>{{ item.studentNo }}</small></td>
            <td>{{ labelOf(item.participantRole) }}<small>{{ item.grade }} 级 · {{ item.major }}</small></td>
            <td>{{ item.cumulativeGpa.toFixed(2) }}<small>报名累计平均绩点</small></td>
            <td><span v-for="subject in item.subjects" :key="subject" class="tag">{{ subject }}</span></td>
            <td><span class="status-badge" :class="item.applicationStatus.toLowerCase()">{{ labelOf(item.applicationStatus) }}</span><small>{{ labelOf(item.selectionStatus) }}</small></td>
            <td>
              <div v-if="item.applicationStatus === 'SUBMITTED' || item.applicationStatus === 'UNDER_REVIEW'" class="table-actions">
                <button class="button small primary" :disabled="!permissions.canReview" :title="permissions.canReview ? '' : permissions.notice" @click="openReview(item)">人工审核</button>
              </div>
              <div v-else-if="item.applicationStatus === 'APPROVED'" class="table-actions selection-actions">
                <button v-if="item.selectionStatus === 'MATCHING_POOL'" class="button small danger" :disabled="submitting || !permissions.canSelect" :title="permissions.canSelect ? '撤出后本期匹配不会使用该报名' : permissions.notice" @click="withdraw(item)">撤出匹配池</button>
                <button v-else class="button small secondary" :disabled="submitting || !permissions.canSelect" :title="permissions.canSelect ? '' : permissions.notice" @click="select(item, 'MATCHING_POOL')">入匹配池</button>
                <button class="button small ghost" :disabled="submitting || !permissions.canSelect" :title="permissions.canSelect ? '' : permissions.notice" @click="select(item, 'WAITLIST')">候补</button>
                <button class="button small danger" :disabled="submitting || !permissions.canSelect" :title="permissions.canSelect ? '' : permissions.notice" @click="select(item, 'NOT_SELECTED')">未入选</button>
              </div>
              <span v-else class="muted">等待学生重提</span>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <PaginationBar :page="page" :size="size" :total="items.length" :loading="loading" @update:page="page = $event" @update:size="changeSize" />
  </section>

  <dialog :open="Boolean(target)" class="modal">
    <form @submit.prevent="review">
      <header><h2>审核 {{ target?.realName }} 的报名</h2></header>
      <div class="modal-body">
        <dl class="detail-list">
          <div><dt>身份</dt><dd>{{ labelOf(target?.participantRole) }}</dd></div>
          <div><dt>报名累计平均绩点</dt><dd>{{ target?.cumulativeGpa }}</dd></div>
          <div><dt>课程</dt><dd>{{ target?.subjects.join('、') }}</dd></div>
          <div><dt>报名自述</dt><dd>{{ target?.statementText }}</dd></div>
        </dl>
        <label>审核结论<select v-model="decision"><option value="APPROVED">审核通过</option><option value="REJECTED">驳回修改</option></select></label>
        <label v-if="decision === 'REJECTED'">驳回原因<textarea v-model="reason" required maxlength="500" /></label>
      </div>
      <footer>
        <button type="button" class="button ghost" @click="target = null">取消</button>
        <button class="button primary" :disabled="submitting || !permissions.canReview">确认审核</button>
      </footer>
    </form>
  </dialog>
</template>
