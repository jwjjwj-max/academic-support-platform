<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { apiErrorMessage, http } from '@/services/http'
import { useAuthStore } from '@/stores/auth'
import type { ApiResponse, MatchCandidate, Period } from '@/types/api'
import { labelOf } from '@/utils/labels'
import { canStudentDecide } from '@/utils/matching-stage'

const auth = useAuthStore(), items = ref<MatchCandidate[]>([]), loading = ref(true), error = ref(''), success = ref('')
const periods = ref<Period[]>([])
const rejectTarget = ref<MatchCandidate | null>(null), reason = ref(''), submitting = ref(false)
function myDecision(item: MatchCandidate) { return auth.user?.id === item.navigatorUserId ? item.navigatorDecision : item.climberDecision }
function periodStatus(item: MatchCandidate) { return periods.value.find(period => period.id === item.periodId)?.status }
async function load() { loading.value = true; try { const [matchingResult, periodResult] = await Promise.all([http.get<ApiResponse<MatchCandidate[]>>('/matching/mine'), http.get<ApiResponse<Period[]>>('/periods')]); items.value = matchingResult.data.data; periods.value = periodResult.data.data } catch (e) { error.value = apiErrorMessage(e) } finally { loading.value = false } }
async function decide(item: MatchCandidate, decision: 'CONFIRMED' | 'REJECTED') {
  if (decision === 'REJECTED') { rejectTarget.value = item; reason.value = ''; return }
  submitting.value = true; try { await http.post(`/matching/candidates/${item.id}/decision`, { decision }); success.value = '已确认匹配，等待对方确认。'; await load() } catch (e) { error.value = apiErrorMessage(e) } finally { submitting.value = false }
}
async function reject() { if (!rejectTarget.value || !reason.value.trim()) return; submitting.value = true; try { await http.post(`/matching/candidates/${rejectTarget.value.id}/decision`, { decision: 'REJECTED', reason: reason.value.trim() }); rejectTarget.value = null; success.value = '已拒绝，管理员将只对受影响人员进行局部重新匹配。'; await load() } catch (e) { error.value = apiErrorMessage(e) } finally { submitting.value = false } }
onMounted(load)
</script>
<template>
  <div v-if="error" class="notice danger">{{ error }}</div><div v-if="success" class="notice success">{{ success }}</div>
  <div v-if="loading" class="loading-state">正在加载匹配结果…</div><div v-else-if="!items.length" class="empty-state card"><h3>暂无已发布的匹配结果</h3><p>匹配方案经管理员确认并发布后才会在这里显示。</p></div>
  <article v-for="item in items" :key="item.id" class="card"><header><div><span class="tag">匹配结果已生成</span><h2>{{ item.status === 'CONFIRMED' ? '正式帮扶对' : item.status === 'REJECTED' ? '已拒绝的组合' : '待双方确认' }}</h2><p>共同科目：{{ item.matchedSubjects.join('、') }} · 匹配依据仅管理员可见</p></div></header><div class="card-body">
    <div class="grid two"><div class="metric-card"><span>领航者</span><strong>{{ item.navigatorName }}</strong><small>{{ item.navigatorGrade }} 级 · {{ item.navigatorMajor }} · {{ labelOf(item.navigatorDecision) }}</small></div><div class="metric-card"><span>攀登者</span><strong>{{ item.climberName }}</strong><small>{{ item.climberGrade }} 级 · {{ item.climberMajor }} · {{ labelOf(item.climberDecision) }}</small></div></div>
    <div v-if="item.status === 'CONFIRMED'" class="notice success"><strong>双方已确认，联系方式已开放</strong><p>微信：{{ item.partnerWechat }}<br />联系电话：{{ item.partnerPhone || '对方未填写' }}</p></div>
    <div v-else-if="item.status === 'PUBLISHED' && myDecision(item) === 'PENDING' && canStudentDecide(periodStatus(item))" class="form-actions"><button class="button danger" @click="decide(item, 'REJECTED')">拒绝并填写原因</button><button class="button primary" :disabled="submitting" @click="decide(item, 'CONFIRMED')">确认结对</button></div>
    <div v-else-if="item.status === 'PUBLISHED' && myDecision(item) === 'PENDING'" class="notice">当前不在双方确认阶段，如需处理请联系管理员。</div>
    <div v-else-if="item.status === 'REJECTED'" class="notice danger">拒绝原因：{{ item.rejectionReason }}</div><div v-else class="notice">你已确认，正在等待对方处理。</div>
  </div></article>
  <dialog :open="Boolean(rejectTarget)" class="modal"><form @submit.prevent="reject"><header><h2>拒绝本次匹配</h2></header><div class="modal-body"><div class="notice warning">拒绝只影响本组合，不会改变其他已确认帮扶对。</div><label>拒绝原因<textarea v-model="reason" required maxlength="500" /></label></div><footer><button type="button" class="button ghost" @click="rejectTarget = null">取消</button><button class="button danger" :disabled="submitting || !reason.trim()">确认拒绝</button></footer></form></dialog>
</template>
