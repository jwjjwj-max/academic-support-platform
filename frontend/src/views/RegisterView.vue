<script setup lang="ts">
import { COLLEGE, MAJORS } from '@/constants/academic'
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { http, apiErrorMessage } from '@/services/http'

const router = useRouter()
const form = reactive({ studentNo: '', realName: '', college: COLLEGE, major: '', grade: new Date().getFullYear() - 1, wechat: '', phone: '', password: '', confirmPassword: '' })
const error = ref('')
const fieldErrors = ref<Record<string, string>>({})
const submitting = ref(false)
const completed = ref(false)

async function submit() {
  error.value = ''; fieldErrors.value = {}; submitting.value = true
  try { await http.post('/students/register', form); completed.value = true }
  catch (reason: any) {
    error.value = apiErrorMessage(reason)
    if (reason.response?.data?.code === 'VALIDATION_ERROR') fieldErrors.value = reason.response.data.data || {}
  } finally { submitting.value = false }
}
</script>

<template>
  <main class="auth-page register-page"><section class="auth-card wide">
    <template v-if="completed">
      <div class="success-mark">✓</div><h1>注册资料已提交</h1>
      <p class="center muted">管理员审核通过前，你可以登录查看状态或修改资料；审核通过后才能参与期次报名。</p>
      <button class="button primary" @click="router.push('/login')">返回登录</button>
    </template>
    <template v-else>
      <div class="auth-heading"><h1>学生注册</h1><p>填写本人真实资料，提交后由管理员人工审核</p></div>
      <form class="form-grid" @submit.prevent="submit">
        <label>学号<input v-model.trim="form.studentNo" /><small>{{ fieldErrors.studentNo }}</small></label>
        <label>姓名<input v-model.trim="form.realName" /><small>{{ fieldErrors.realName }}</small></label>
        <label>学院<input :value="COLLEGE" readonly aria-readonly="true" /><small>{{ fieldErrors.college }}</small></label>
        <label>专业<select v-model="form.major" required><option disabled value="">请选择专业</option><option v-for="major in MAJORS" :key="major" :value="major">{{ major }}</option></select><small>{{ fieldErrors.major }}</small></label>
        <label>年级<input v-model.number="form.grade" type="number" min="2000" max="2100" /><small>{{ fieldErrors.grade }}</small></label>
        <label>微信号<input v-model.trim="form.wechat" /><small>{{ fieldErrors.wechat }}</small></label>
        <label>联系电话（选填）<input v-model.trim="form.phone" inputmode="tel" /><small>{{ fieldErrors.phone }}</small></label>
        <span />
        <label>密码<input v-model="form.password" type="password" autocomplete="new-password" /><small>{{ fieldErrors.password || '至少 8 位，包含字母和数字' }}</small></label>
        <label>确认密码<input v-model="form.confirmPassword" type="password" autocomplete="new-password" /><small>{{ fieldErrors.passwordConfirmed }}</small></label>
        <p v-if="error" class="form-error full-row" role="alert">{{ error }}</p>
        <div class="form-actions full-row"><RouterLink class="button secondary" to="/login">返回登录</RouterLink><button class="button primary" :disabled="submitting">{{ submitting ? '提交中…' : '提交审核' }}</button></div>
      </form>
    </template>
  </section></main>
</template>
