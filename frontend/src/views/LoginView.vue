<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { BookOpenCheck } from 'lucide-vue-next'
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
  <main class="auth-page">
    <section class="auth-card">
      <div class="auth-brand"><span class="brand-mark"><BookOpenCheck :size="26" /></span><div><strong>学业帮扶智慧平台</strong><small>单学院试运行版</small></div></div>
      <div class="auth-heading"><h1>欢迎登录</h1><p>使用学号或管理员账号进入平台</p></div>
      <div v-if="route.query.expired" class="notice warning">登录已失效，请重新登录。</div>
      <form @submit.prevent="submit">
        <label>账号<input v-model="loginName" autocomplete="username" autofocus /></label>
        <label>密码<input v-model="password" type="password" autocomplete="current-password" /></label>
        <p v-if="error" class="form-error" role="alert">{{ error }}</p>
        <button class="button primary full" :disabled="submitting">{{ submitting ? '正在登录…' : '登录' }}</button>
      </form>
      <p class="auth-footer">学生还没有账号？<RouterLink to="/register">注册并提交审核</RouterLink></p>
      <p class="auth-help">忘记密码请联系学院管理员人工核验并重置。</p>
    </section>
  </main>
</template>

