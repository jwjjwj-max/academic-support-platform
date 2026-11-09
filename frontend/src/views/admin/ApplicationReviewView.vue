<script setup lang="ts">
import ApplicationStatistics from '@/components/ApplicationStatistics.vue'
import PaginationBar from '@/components/PaginationBar.vue'
import SubjectEditor from '@/components/SubjectEditor.vue'
import { MAJORS } from '@/constants/academic'
import { computed, nextTick, onMounted, ref } from 'vue'
import { isAxiosError } from 'axios'
import { apiErrorMessage, http } from '@/services/http'
import type { ApiResponse, ParticipationApplication, Period, SelectionStatus } from '@/types/api'
import { applicationAdminPermissions } from '@/utils/application-stage'
import { labelOf } from '@/utils/labels'
import { validateSubjects } from '@/utils/subjects'

const periods = ref<Period[]>([])
const allApplications = ref<ParticipationApplication[]>([])
const keyword = ref('')
const items = computed(() => {
  const search = keyword.value.trim().toLowerCase()
  return allApplications.value.filter(item => {
    const matchesStatus = !status.value || (status.value === 'SUBMITTED' ? isReviewable(item) : item.applicationStatus === status.value)
    return matchesStatus && (!search || [item.realName, item.studentNo].some(value => value?.toLowerCase().includes(search)))
  })
})
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
const modalError = ref('')
const conflict = ref(false)
const splitPending = ref(false)
const correction = ref<{ grade: number | ''; major: string; cumulativeGpa: number | ''; subjects: string[]; statementText: string; reason: string } | null>(null)
const showSubjectErrors = ref(false)

const selectedPeriod = computed(() => periods.value.find((item) => item.id === periodId.value))
const permissions = computed(() => applicationAdminPermissions(selectedPeriod.value?.status))

async function loadPeriods() {
  periods.value = (await http.get<ApiResponse<Period[]>>('/periods')).data.data
  if (!periodId.value && periods.value.length) periodId.value = periods.value[0].id
}

let requestVersion = 0
async function load(reopenId?: number) {
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
    const updated = result.find(item => item.id === reopenId)
    if (updated) openReview(updated)
  } catch (e) {
    if (version === requestVersion) error.value = apiErrorMessage(e)
  } finally {
    if (version === requestVersion) loading.value = false
  }
}

async function refresh(reopenId?: number) {
  try {
    await loadPeriods()
    await load(reopenId)
  } catch (e) {
    error.value = apiErrorMessage(e)
    loading.value = false
  }
}

function isReviewable(item: ParticipationApplication) {
  return ['SUBMITTED', 'UNDER_REVIEW'].includes(item.applicationStatus)
}

function openReview(item: ParticipationApplication) {
  target.value = item
  correction.value = null
  decision.value = 'APPROVED'
  reason.value = ''
  modalError.value = ''
  conflict.value = false
}

function startCorrection(item: ParticipationApplication) {
  correction.value = {
    grade: item.grade, major: item.major, cumulativeGpa: item.cumulativeGpa,
    subjects: item.subjects.length ? [...item.subjects] : [''], statementText: item.statementText, reason: '',
  }
  modalError.value = ''
  showSubjectErrors.value = false
  splitPending.value = false
}

function handleModalError(e: unknown) {
  modalError.value = apiErrorMessage(e)
  conflict.value = isAxiosError(e) && e.response?.status === 409
}

async function review() {
  const item = target.value
  if (!item || !isReviewable(item) || !permissions.value.canReview || submitting.value || correction.value || conflict.value) return
  submitting.value = true
  modalError.value = ''
  try {
    await http.post(`/admin/applications/${item.id}/review`, { decision: decision.value, reason: reason.value, revision: item.revision })
    success.value = decision.value === 'APPROVED'
      ? '报名已审核通过，请继续选择入选状态。'
      : '报名已驳回，学生可在报名审核阶段按原因修改重提。'
    target.value = null
    await load()
  } catch (e) {
    handleModalError(e)
  } finally {
    submitting.value = false
  }
}

async function saveCorrection(approve: boolean) {
  const item = target.value
  const draft = correction.value
  if (!item || !draft || !permissions.value.canReview || submitting.value || splitPending.value || conflict.value) return
  if (approve && !isReviewable(item)) return
  modalError.value = ''
  const changes: Partial<Pick<ParticipationApplication, 'grade' | 'major' | 'cumulativeGpa' | 'subjects' | 'statementText'>> = {}
  if (draft.grade !== item.grade) {
    if (draft.grade === '' || !Number.isInteger(draft.grade) || draft.grade < 2000 || draft.grade > 2100) {
      modalError.value = '请输入 2000 至 2100 之间的四位年级'; return
    }
    changes.grade = draft.grade
  }
  if (draft.major !== item.major) {
    if (!MAJORS.some(major => major === draft.major)) { modalError.value = '请选择有效专业'; return }
    changes.major = draft.major
  }
  if (draft.cumulativeGpa !== item.cumulativeGpa) {
    if (draft.cumulativeGpa === '' || !Number.isFinite(draft.cumulativeGpa) || draft.cumulativeGpa < 0 || draft.cumulativeGpa > 5) {
      modalError.value = '报名累计平均绩点须在 0 至 5 之间'; return
    }
    changes.cumulativeGpa = draft.cumulativeGpa
  }
  if (draft.subjects.length !== item.subjects.length || draft.subjects.some((subject, index) => subject !== item.subjects[index])) {
    showSubjectErrors.value = true
    const subjectError = validateSubjects(draft.subjects)
    if (subjectError) { modalError.value = subjectError; return }
    changes.subjects = draft.subjects.map(value => value.trim())
  }
  if (draft.statementText !== item.statementText) {
    const statement = draft.statementText.trim()
    if (statement.length < 30 || statement.length > 500) { modalError.value = '报名自述须为 30 至 500 字'; return }
    changes.statementText = statement
  }
  if (!Object.keys(changes).length) { modalError.value = '请修改至少一项报名资料'; return }
  if (!draft.reason.trim()) { modalError.value = '请填写更正原因'; return }
  submitting.value = true
  try {
    await http.post(`/admin/applications/${item.id}/correction`, {
      ...changes, reason: draft.reason.trim(), approve, revision: item.revision, currentProfileVersion: item.currentProfileVersion,
    })
    success.value = approve ? '更正已保存，报名已审核通过。请继续选择入选状态。' : '报名资料更正已保存。'
    await load(approve ? undefined : item.id)
  } catch (e) {
    handleModalError(e)
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

onMounted(() => refresh())
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
    <header class="review-header">
      <div>
        <h2>报名审核与匹配池</h2>
        <p>报名不按成绩自动淘汰；审核后由管理员人工选择入池、候补或未入选</p>
      </div>
      <div class="inline-filters review-filters">
        <div class="review-search">
          <input v-model="keyword" type="search" aria-label="姓名或学号" placeholder="输入姓名或学号" maxlength="64" @input="filterChanged" />
          <button v-if="keyword" type="button" class="button ghost small" @click="keyword = ''; filterChanged()">清空搜索</button>
        </div>
        <select v-model="status" aria-label="审核状态" @change="filterChanged">
          <option value="SUBMITTED">待审核</option>
          <option value="APPROVED">已通过</option>
          <option value="REJECTED">已驳回</option>
          <option value="">全部</option>
        </select>
        <button class="button ghost small" :disabled="loading || submitting" @click="refresh()">刷新数据</button>
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
    <div v-else-if="!items.length && !error" class="empty-state">
      <h3>没有符合条件的报名</h3>
      <p>{{ selectedPeriod?.name || '未选择期次' }} · {{ status ? labelOf(status) : '全部审核状态' }}<template v-if="keyword.trim()"> · 搜索「{{ keyword.trim() }}」</template></p>
      <button v-if="status" class="button ghost small" @click="status = ''; filterChanged()">查看全部审核状态</button>
    </div>
    <div v-else-if="items.length" class="table-wrap">
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
              <div class="table-actions selection-actions">
                <button class="button small" :class="isReviewable(item) && permissions.canReview ? 'primary' : 'ghost'" :disabled="submitting" @click="openReview(item)">{{ isReviewable(item) && permissions.canReview ? '人工审核' : '查看报名' }}</button>
                <template v-if="item.applicationStatus === 'APPROVED'">
                <button v-if="item.selectionStatus === 'MATCHING_POOL'" class="button small danger" :disabled="submitting || !permissions.canSelect" :title="permissions.canSelect ? '撤出后本期匹配不会使用该报名' : permissions.notice" @click="withdraw(item)">撤出匹配池</button>
                <button v-else class="button small secondary" :disabled="submitting || !permissions.canSelect" :title="permissions.canSelect ? '' : permissions.notice" @click="select(item, 'MATCHING_POOL')">入匹配池</button>
                <button class="button small ghost" :disabled="submitting || !permissions.canSelect" :title="permissions.canSelect ? '' : permissions.notice" @click="select(item, 'WAITLIST')">候补</button>
                <button class="button small danger" :disabled="submitting || !permissions.canSelect" :title="permissions.canSelect ? '' : permissions.notice" @click="select(item, 'NOT_SELECTED')">未入选</button>
                </template>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <PaginationBar :page="page" :size="size" :total="items.length" :loading="loading" @update:page="page = $event" @update:size="changeSize" />
  </section>

  <dialog v-if="target" open class="modal review-modal" aria-label="报名审核详情">
    <form @submit.prevent="review">
      <header><h2>{{ target.realName }} 的报名</h2></header>
      <div class="modal-body">
        <div v-if="modalError" class="notice danger" role="alert">
          <p>{{ modalError }}</p>
          <button v-if="conflict" type="button" class="button ghost small" @click="refresh(target.id)">重新加载报名</button>
        </div>
        <dl v-if="!correction" class="detail-list">
          <div><dt>学号 / 身份</dt><dd>{{ target.studentNo }} · {{ labelOf(target.participantRole) }}</dd></div>
          <div><dt>年级 / 专业</dt><dd>{{ target.grade }} 级 · {{ target.major }}</dd></div>
          <div><dt>报名累计平均绩点</dt><dd>{{ target.cumulativeGpa.toFixed(2) }}</dd></div>
          <div><dt>课程</dt><dd>{{ target.subjects.join('、') }}</dd></div>
          <div><dt>报名自述</dt><dd class="statement">{{ target.statementText }}</dd></div>
        </dl>
        <template v-if="correction">
          <p class="muted correction-note">更正年级或专业会同步对应的学生账号资料。请核对学号 {{ target.studentNo }} 与实际年级。</p>
          <div class="form-grid">
            <label>年级<input v-model.number="correction.grade" type="number" min="2000" max="2100" /></label>
            <label>专业<select v-model="correction.major"><option v-if="!MAJORS.some(major => major === target?.major)" :value="target.major">{{ target.major }}</option><option v-for="major in MAJORS" :key="major" :value="major">{{ major }}</option></select></label>
            <label>报名累计平均绩点<input v-model.number="correction.cumulativeGpa" type="number" min="0" max="5" step="0.01" /></label>
          </div>
          <SubjectEditor v-model="correction.subjects" v-model:split-pending="splitPending" :show-errors="showSubjectErrors" title="帮扶科目" />
          <label>报名自述<textarea v-model="correction.statementText" maxlength="500" /></label>
          <label>更正原因<textarea v-model="correction.reason" maxlength="500" required /></label>
        </template>
        <template v-else-if="permissions.canReview && isReviewable(target)">
          <label>审核结论<select v-model="decision"><option value="APPROVED">审核通过</option><option value="REJECTED">驳回修改</option></select></label>
          <label v-if="decision === 'REJECTED'">驳回原因<textarea v-model="reason" required maxlength="500" /></label>
        </template>
      </div>
      <footer>
        <button type="button" class="button ghost" :disabled="submitting" @click="target = null">关闭</button>
        <template v-if="permissions.canReview && (isReviewable(target) || ['APPROVED', 'REJECTED'].includes(target.applicationStatus))">
          <template v-if="correction">
            <button type="button" class="button secondary" :disabled="submitting || conflict || splitPending" @click="saveCorrection(false)">保存更正</button>
            <button v-if="isReviewable(target)" type="button" class="button primary" :disabled="submitting || conflict || splitPending" @click="saveCorrection(true)">保存并审核通过</button>
          </template>
          <template v-else>
            <button type="button" class="button secondary" :disabled="submitting || conflict" @click="startCorrection(target)">更正报名资料</button>
            <button v-if="isReviewable(target)" class="button primary" :disabled="submitting || conflict">确认审核</button>
          </template>
        </template>
      </footer>
    </form>
  </dialog>
</template>

<style scoped>
.review-modal footer { flex-wrap: wrap; }
.statement { white-space: pre-wrap; overflow-wrap: anywhere; }
.correction-note { margin: 0; }
</style>
