<script setup lang="ts">
import PaginationBar from '@/components/PaginationBar.vue'
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { http, apiErrorMessage } from '@/services/http'
import type { ApiResponse, StudentProfile } from '@/types/api'
import { labelOf } from '@/utils/labels'

const students = ref<StudentProfile[]>([]), loading = ref(true), error = ref(''), status = ref('PENDING')
const keyword = ref('')
const reviewTarget = ref<StudentProfile | null>(null), decision = ref<'APPROVED' | 'REJECTED'>('APPROVED'), reason = ref(''), submitting = ref(false)
const resetTarget = ref<StudentProfile | null>(null), newPassword = ref(''), resetReason = ref('')
const page = ref(1), size = ref(20), total = ref(0)
let requestVersion = 0
let searchTimer: ReturnType<typeof setTimeout> | undefined
async function load() {
  clearTimeout(searchTimer)
  const version = ++requestVersion
  loading.value = true; error.value = ''
  try {
    const search = keyword.value.trim()
    const response = await http.get<ApiResponse<{ records: StudentProfile[]; total: number }>>('/admin/students', {
      params: { status: status.value || undefined, page: page.value, size: size.value, ...(search ? { keyword: search } : {}) },
    })
    if (version !== requestVersion) return
    total.value = response.data.data.total
    const lastPage = Math.max(1, Math.ceil(total.value / size.value))
    if (page.value > lastPage) { page.value = lastPage; await load(); return }
    students.value = response.data.data.records
  } catch (e) {
    if (version === requestVersion) {
      error.value = apiErrorMessage(e)
      students.value = []
      total.value = 0
    }
  }
  finally { if (version === requestVersion) loading.value = false }
}
watch(keyword, () => {
  page.value = 1
  loading.value = true
  requestVersion++
  clearTimeout(searchTimer)
  searchTimer = setTimeout(load, 250)
}, { flush: 'sync' })
onBeforeUnmount(() => { requestVersion++; clearTimeout(searchTimer) })
function filterChanged() { page.value = 1; void load() }
function changePage(value: number) { page.value = value; void load() }
function changeSize(value: number) { size.value = value; filterChanged() }
async function submitReview() { if (!reviewTarget.value) return; submitting.value = true; try { await http.post(`/admin/students/${reviewTarget.value.userId}/review`, { decision: decision.value, reason: reason.value }); reviewTarget.value = null; reason.value = ''; await load() } catch (e) { error.value = apiErrorMessage(e) } finally { submitting.value = false } }
async function submitReset() { if (!resetTarget.value) return; submitting.value = true; try { await http.post(`/admin/students/${resetTarget.value.userId}/password-reset`, { newPassword: newPassword.value, reason: resetReason.value }); resetTarget.value = null; newPassword.value = ''; resetReason.value = '' } catch (e) { error.value = apiErrorMessage(e) } finally { submitting.value = false } }
onMounted(load)
</script>

<template>
  <section class="card">
    <header class="review-header">
      <div><h2>学生账号审核</h2><p>注册和资料变更均由管理员人工审核</p></div>
      <div class="inline-filters review-filters">
        <form class="review-search" @submit.prevent="load">
          <input v-model="keyword" type="search" aria-label="姓名或学号" placeholder="输入姓名或学号" maxlength="64" />
          <button v-if="keyword" type="button" class="button ghost small" @click="keyword = ''">清空搜索</button>
        </form>
        <select v-model="status" aria-label="学生审核状态" @change="filterChanged"><option value="PENDING">待审核</option><option value="APPROVED">已通过</option><option value="REJECTED">已驳回</option><option value="">全部</option></select>
        <button class="button ghost small" :disabled="loading" @click="load">刷新数据</button>
      </div>
    </header>
    <div v-if="error" class="notice danger">{{ error }}</div><div v-if="loading" class="loading-state">正在加载…</div>
    <div v-else-if="!students.length && !error" class="empty-state">
      <h3>没有符合条件的学生</h3>
      <p>{{ status === 'PENDING' ? '待审核' : status ? labelOf(status) : '全部审核状态' }}<template v-if="keyword.trim()"> · 搜索「{{ keyword.trim() }}」</template></p>
      <button v-if="status" class="button ghost small" @click="status = ''; filterChanged()">查看全部审核状态</button>
    </div>
    <div v-else class="table-wrap"><table><thead><tr><th>学生</th><th>学院 / 专业</th><th>年级</th><th>联系方式</th><th>状态</th><th>操作</th></tr></thead><tbody><tr v-for="item in students" :key="item.userId"><td><strong>{{ item.realName }}</strong><small>{{ item.studentNo }}</small></td><td>{{ item.college }}<small>{{ item.major }}</small></td><td>{{ item.grade }}</td><td>微信：{{ item.wechat }}<small>电话：{{ item.phone || '未填写' }}</small></td><td><span class="status-badge" :class="item.reviewStatus.toLowerCase()">{{ labelOf(item.reviewStatus) }}</span></td><td><div class="table-actions"><button class="button small secondary" @click="reviewTarget = item; decision = 'APPROVED'; reason = ''">审核</button><button class="button small ghost" @click="resetTarget = item">重置密码</button></div></td></tr></tbody></table></div>
    <PaginationBar :page="page" :size="size" :total="total" :loading="loading" @update:page="changePage" @update:size="changeSize" />
  </section>
  <dialog :open="Boolean(reviewTarget)" class="modal"><form @submit.prevent="submitReview"><header><h2>审核 {{ reviewTarget?.realName }}</h2></header><div class="modal-body"><label>审核结果<select v-model="decision"><option value="APPROVED">通过</option><option value="REJECTED">驳回</option></select></label><label v-if="decision === 'REJECTED'">驳回原因<textarea v-model="reason" required maxlength="500" /></label></div><footer><button type="button" class="button ghost" @click="reviewTarget = null">取消</button><button class="button primary" :disabled="submitting">确认审核</button></footer></form></dialog>
  <dialog :open="Boolean(resetTarget)" class="modal"><form @submit.prevent="submitReset"><header><h2>重置 {{ resetTarget?.realName }} 的密码</h2></header><div class="modal-body"><label>新密码<input v-model="newPassword" type="password" minlength="8" required /><small>至少 8 位，包含字母和数字</small></label><label>重置原因<textarea v-model="resetReason" required maxlength="500" /></label></div><footer><button type="button" class="button ghost" @click="resetTarget = null">取消</button><button class="button primary" :disabled="submitting">确认重置</button></footer></form></dialog>
</template>
