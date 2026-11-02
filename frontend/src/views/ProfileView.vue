<script setup lang="ts">
import { COLLEGE, MAJORS } from '@/constants/academic'
import { onMounted, reactive, ref } from 'vue'
import { http, apiErrorMessage } from '@/services/http'
import { useAuthStore } from '@/stores/auth'
import type { ApiResponse, StudentProfile } from '@/types/api'

const auth = useAuthStore()
const form = reactive({ studentNo: '', realName: '', college: COLLEGE, major: '', grade: 2026, wechat: '', phone: '' })
const profile = ref<StudentProfile | null>(null)
const loading = ref(true), saving = ref(false), error = ref(''), success = ref('')

function apply(value: StudentProfile) { profile.value = value; Object.assign(form, value, { college: COLLEGE, major: MAJORS.some(major => major === value.major) ? value.major : '' }) }
async function load() { loading.value = true; try { apply((await http.get<ApiResponse<StudentProfile>>('/students/me/profile')).data.data) } catch (e) { error.value = apiErrorMessage(e) } finally { loading.value = false } }
async function save() { saving.value = true; error.value = ''; success.value = ''; try { apply((await http.put<ApiResponse<StudentProfile>>('/students/me/profile', form)).data.data); await auth.loadUser(); success.value = '资料已保存。身份资料发生变化时，账号将重新进入审核。' } catch (e) { error.value = apiErrorMessage(e) } finally { saving.value = false } }
onMounted(load)
</script>

<template>
  <div v-if="loading" class="loading-state">正在加载资料…</div>
  <template v-else-if="profile">
    <div class="notice" :class="{ danger: profile.reviewStatus === 'REJECTED', success: profile.reviewStatus === 'APPROVED' }">
      <strong>审核状态：{{ { PENDING: '待审核', APPROVED: '已通过', REJECTED: '已驳回' }[profile.reviewStatus] }}</strong>
      <p v-if="profile.rejectionReason">驳回原因：{{ profile.rejectionReason }}</p>
      <p v-else>微信号和选填联系电话可直接维护；姓名、学号、学院、专业或年级变化后需要重新审核。</p>
    </div>
    <div v-if="profile.college !== COLLEGE || !MAJORS.some(major => major === profile?.major)" class="notice">原资料：{{ profile.college }} · {{ profile.major }}。保存时将使用固定学院及所选专业，身份资料变化后需重新审核。</div>
    <section class="card"><header><h2>个人资料</h2><span class="tag">版本 {{ profile.profileVersion }}</span></header><form class="card-body form-grid" @submit.prevent="save">
      <label>学号<input v-model.trim="form.studentNo" required /></label><label>姓名<input v-model.trim="form.realName" required /></label>
      <label>学院<input :value="COLLEGE" readonly aria-readonly="true" /></label><label>专业<select v-model="form.major" required><option disabled value="">请选择专业</option><option v-for="major in MAJORS" :key="major" :value="major">{{ major }}</option></select></label>
      <label>年级<input v-model.number="form.grade" type="number" min="2000" max="2100" required /></label><label>微信号<input v-model.trim="form.wechat" required /></label>
      <label>联系电话（选填）<input v-model.trim="form.phone" /></label>
      <p v-if="error" class="form-error full-row" role="alert">{{ error }}</p><p v-if="success" class="form-success full-row" role="status">{{ success }}</p>
      <div class="form-actions full-row"><button class="button primary" :disabled="saving">{{ saving ? '保存中…' : profile.reviewStatus === 'REJECTED' ? '修改并重新提交' : '保存资料' }}</button></div>
    </form></section>
  </template>
</template>

