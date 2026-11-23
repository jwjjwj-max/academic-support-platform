<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowRight } from 'lucide-vue-next'
import AuthLayout from '@/layouts/AuthLayout.vue'
import { useAuthStore } from '@/stores/auth'
import { apiErrorMessage } from '@/services/http'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const loginName = ref('')
const password = ref('')
const error = ref('')
const submitting = ref(false)

async function submit() {
  error.value = ''
  if (!loginName.value.trim() || !password.value) { error.value = '请输入账号和密码'; return }
  submitting.value = true
  try {
    await auth.login(loginName.value, password.value)
    await router.replace(String(route.query.redirect || '/dashboard'))
  } catch (reason) { error.value = apiErrorMessage(reason) }
  finally { submitting.value = false }
}
</script>

<template>
  <AuthLayout>
    <div class="auth-heading">
      <h1 id="auth-title">欢迎回来</h1>
      <p>使用学号或管理员账号登录。</p>
    </div>
    <div v-if="route.query.expired" class="notice warning">登录已失效，请重新登录。</div>
    <form class="auth-login-form" @submit.prevent="submit">
      <label>账号<input v-model="loginName" autocomplete="username" /></label>
      <label>密码<input v-model="password" type="password" autocomplete="current-password" /></label>
      <p v-if="error" class="form-error" role="alert">{{ error }}</p>
      <button class="button primary full auth-submit" :disabled="submitting">
        {{ submitting ? '正在登录…' : '登录' }}<ArrowRight :size="17" aria-hidden="true" />
      </button>
    </form>
    <p class="auth-help">忘记密码？请联系学院管理员核验并重置。</p>
  </AuthLayout>
</template>

