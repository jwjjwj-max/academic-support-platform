<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { apiErrorMessage, http } from '@/services/http'
import { useAuthStore } from '@/stores/auth'
import type { ApiResponse, ParticipationApplication, Period } from '@/types/api'
import { registrationWindowMessage, registrationWindowState, studentApplicationStatus, studentPeriodLabel } from '@/utils/period-workflow'

const auth = useAuthStore()
const periods = ref<Period[]>([])
const applications = ref<ParticipationApplication[]>([])
const loading = ref(true), submitting = ref(false), error = ref(''), success = ref('')
const styles = ['计划型', '互动讨论', '例题驱动', '自主探索', '总结复盘']
const personalities = ['耐心', '开朗', '细致', '主动', '沉稳']
const form = reactive({ periodId: 0, participantRole: 'CLIMBER', cumulativeGpa: 0, subjectsText: '', statementText: '', learningStyles: [] as string[], personalities: [] as string[] })
const registrationPeriods = computed(() => periods.value.filter(item => registrationWindowState(item) === 'OPEN'))
const resubmitPeriod = computed(() => {
  const rejected = applications.value.find(item => item.applicationStatus === 'REJECTED')
  return rejected ? periods.value.find(item => item.id === rejected.periodId && item.status === 'REVIEW') : undefined
})
const applicationFormPeriods = computed(() => {
  const available = [...registrationPeriods.value]
  if (resubmitPeriod.value && !available.some(item => item.id === resubmitPeriod.value?.id)) available.push(resubmitPeriod.value)
  return available
})
const currentPeriod = computed(() => periods.value[0])
const profileApproved = computed(() => auth.user?.profileReviewStatus === 'APPROVED')

function editRejected(item: ParticipationApplication) {
  form.periodId = item.periodId; form.participantRole = item.participantRole; form.cumulativeGpa = item.cumulativeGpa
  form.subjectsText = item.subjects.join('；'); form.statementText = item.statementText
  form.learningStyles = [...item.learningStyles]; form.personalities = [...item.personalities]
  window.scrollTo({ top: 0, behavior: 'smooth' })
}
async function load() {
  loading.value = true; error.value = ''
  try {
    const [periodResponse, applicationResponse] = await Promise.all([
      http.get<ApiResponse<Period[]>>('/periods'), http.get<ApiResponse<ParticipationApplication[]>>('/applications/mine'),
    ])
    periods.value = periodResponse.data.data; applications.value = applicationResponse.data.data
    if (!form.periodId && applicationFormPeriods.value.length) form.periodId = applicationFormPeriods.value[0].id
  } catch (reason) { error.value = apiErrorMessage(reason) } finally { loading.value = false }
}
async function submit() {
  error.value = ''; success.value = ''
  const subjects = [...new Set(form.subjectsText.split(/[、,，;；\n]/).map(v => v.trim()).filter(Boolean))]
  if (!subjects.length) { error.value = '请至少填写一门希望帮扶或可帮扶的课程'; return }
  if (form.statementText.trim().length < 30) { error.value = '报名自述至少填写 30 个字'; return }
  if (!form.learningStyles.length || !form.personalities.length) { error.value = '请选择学习风格和性格特点'; return }
  submitting.value = true
  try {
    await http.post('/applications', { ...form, subjects, statementText: form.statementText.trim() })
    success.value = '报名已提交管理员审核，提交后不能自行退出。'; await load()
  } catch (reason) { error.value = apiErrorMessage(reason) } finally { submitting.value = false }
}
onMounted(load)
</script>

<template>
  <div v-if="error" class="notice danger" role="alert">{{ error }}</div>
  <div v-if="success" class="notice success" role="status">{{ success }}</div>
  <section class="card"><header><div><h2>提交期次报名</h2><p>每期只能选择一个身份；报名累计平均绩点由本人查询后填写</p></div></header>
    <div class="card-body">
      <div v-if="!profileApproved" class="notice warning">个人资料审核通过后才能报名。请先等待管理员审核或按驳回原因修改资料。</div>
      <div v-else-if="!applicationFormPeriods.length" class="empty-state"><h3>当前不能提交首次报名</h3><p v-if="currentPeriod">{{ registrationWindowMessage(currentPeriod) }}</p><p v-else>等待学院发布活动期次。</p></div>
      <form v-else class="form-grid" @submit.prevent="submit">
        <div v-if="resubmitPeriod" class="notice warning full-row">本期处于报名审核阶段，仅允许已被驳回的报名按原因修改重提，不再接受新的首次报名。</div>
        <label>活动期次<select v-model.number="form.periodId" required><option v-for="item in applicationFormPeriods" :key="item.id" :value="item.id">{{ item.name }}</option></select></label>
        <label>本期身份<select v-model="form.participantRole" required><option value="CLIMBER">学业攀登者</option><option value="NAVIGATOR">学业领航者</option></select><small>同一期次提交后不能更换身份</small></label>
        <label>报名累计平均绩点<input v-model.number="form.cumulativeGpa" type="number" min="0" max="5" step="0.01" required /><small>截至报名时全部已修课程累计平均绩点，不是单学期绩点</small></label>
        <label>帮扶科目<input v-model.trim="form.subjectsText" required placeholder="例如：高等数学；数据结构" /><small>手动填写，多个科目用逗号或分号分隔</small></label>
        <fieldset class="choice-group full-row"><legend>学习风格（可多选，最多 3 项）</legend><label v-for="item in styles" :key="item"><input v-model="form.learningStyles" type="checkbox" :value="item" :disabled="!form.learningStyles.includes(item) && form.learningStyles.length >= 3" />{{ item }}</label></fieldset>
        <fieldset class="choice-group full-row"><legend>性格特点（可多选，最多 3 项）</legend><label v-for="item in personalities" :key="item"><input v-model="form.personalities" type="checkbox" :value="item" :disabled="!form.personalities.includes(item) && form.personalities.length >= 3" />{{ item }}</label></fieldset>
        <label class="full-row">报名自述<textarea v-model="form.statementText" minlength="30" maxlength="500" required placeholder="说明需要帮助或能够提供帮助的课程基础、学习情况等" /><small>{{ form.statementText.length }}/500，至少 30 字</small></label>
        <div class="notice warning full-row">报名提交后学生不能自行退出；如确需退出，请联系管理员处理。</div>
        <div class="form-actions full-row"><button class="button primary" :disabled="submitting">{{ submitting ? '提交中…' : '提交管理员审核' }}</button></div>
      </form>
    </div>
  </section>
  <section class="card"><header><div><h2>我的报名记录</h2><p>资料快照在报名时生成，之后修改个人资料不会改变当期报名</p></div></header>
    <div v-if="loading" class="loading-state">正在加载…</div><div v-else-if="!applications.length" class="empty-state"><h3>暂无报名记录</h3><p>开放报名后可在上方提交。</p></div>
    <div v-else class="table-wrap"><table><thead><tr><th>期次 / 身份</th><th>活动阶段</th><th>报名累计绩点</th><th>科目</th><th>我的状态</th><th>操作</th></tr></thead><tbody><tr v-for="item in applications" :key="item.id"><td><strong>{{ item.periodName }}</strong><small>{{ item.participantRole === 'NAVIGATOR' ? '学业领航者' : '学业攀登者' }}</small></td><td>{{ studentPeriodLabel(periods.find(period => period.id === item.periodId)?.status || '') }}</td><td>{{ item.cumulativeGpa.toFixed(2) }}</td><td><span v-for="subject in item.subjects" :key="subject" class="tag">{{ subject }}</span></td><td><span class="status-badge" :class="item.applicationStatus.toLowerCase()">{{ studentApplicationStatus(item) }}</span><small v-if="item.rejectionReason">原因：{{ item.rejectionReason }}</small></td><td><button v-if="item.applicationStatus === 'REJECTED' && periods.find(period => period.id === item.periodId)?.status === 'REVIEW'" class="button small secondary" @click="editRejected(item)">修改重提</button><span v-else class="muted">不可自行退出</span></td></tr></tbody></table></div>
  </section>
</template>
