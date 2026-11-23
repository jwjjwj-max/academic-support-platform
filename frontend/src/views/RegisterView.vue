<script setup lang="ts">
import { COLLEGE, MAJORS } from '@/constants/academic'
import { reactive, ref } from 'vue'
import { ArrowRight, Check } from 'lucide-vue-next'
import AuthLayout from '@/layouts/AuthLayout.vue'
import { http, apiErrorMessage } from '@/services/http'

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
  <AuthLayout wide>
    <div v-if="completed" class="auth-complete">
      <span class="auth-success-mark"><Check :size="27" aria-hidden="true" /></span>
      <div class="auth-heading">
        <h1 id="auth-title">注册资料已提交</h1>
        <p>你可以登录查看审核进度或修改资料。审核通过后，就可以报名参加帮扶活动。</p>
      </div>
      <RouterLink class="button primary auth-submit" to="/login">返回登录<ArrowRight :size="17" aria-hidden="true" /></RouterLink>
    </div>
    <template v-else>
      <div class="auth-heading">
        <h1 id="auth-title">创建学生账号</h1>
        <p>填写本人资料，提交后由学院管理员审核。</p>
      </div>
      <form class="auth-register-form" @submit.prevent="submit">
        <label>学号<input v-model.trim="form.studentNo" autocomplete="username" /><small>{{ fieldErrors.studentNo }}</small></label>
        <label>姓名<input v-model.trim="form.realName" autocomplete="name" /><small>{{ fieldErrors.realName }}</small></label>
        <label>学院<input :value="COLLEGE" readonly aria-readonly="true" /><small>{{ fieldErrors.college }}</small></label>
        <label>专业<select v-model="form.major" required><option disabled value="">请选择专业</option><option v-for="major in MAJORS" :key="major" :value="major">{{ major }}</option></select><small>{{ fieldErrors.major }}</small></label>
        <label>年级<input v-model.number="form.grade" type="number" min="2000" max="2100" /><small>{{ fieldErrors.grade }}</small></label>
        <label>微信号<input v-model.trim="form.wechat" /><small>{{ fieldErrors.wechat }}</small></label>
        <label class="full-row">联系电话（选填）<input v-model.trim="form.phone" inputmode="tel" autocomplete="tel" /><small>{{ fieldErrors.phone }}</small></label>
        <label>
          密码<input v-model="form.password" type="password" autocomplete="new-password" />
          <small :class="{ 'auth-field-hint': !fieldErrors.password }">{{ fieldErrors.password || '至少 8 位，包含字母和数字' }}</small>
        </label>
        <label>确认密码<input v-model="form.confirmPassword" type="password" autocomplete="new-password" /><small>{{ fieldErrors.passwordConfirmed }}</small></label>
        <p v-if="error" class="form-error full-row" role="alert">{{ error }}</p>
        <button class="button primary full auth-submit full-row" :disabled="submitting">
          {{ submitting ? '提交中…' : '提交审核' }}<ArrowRight :size="17" aria-hidden="true" />
        </button>
      </form>
    </template>
  </AuthLayout>
</template>
