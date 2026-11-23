<script setup lang="ts">
import { BookOpenCheck } from 'lucide-vue-next'
import notebook from '@/assets/campus-notebook.webp'

defineProps<{ wide?: boolean }>()
</script>

<template>
  <main class="auth-page">
    <div class="auth-frame" :class="{ 'auth-frame-wide': wide }">
      <div class="auth-notebook">
        <div class="auth-brand">
          <span class="auth-brand-mark"><BookOpenCheck :size="23" aria-hidden="true" /></span>
          <div><strong>学业帮扶智慧平台</strong><small>单学院试运行版</small></div>
        </div>
        <img class="auth-notebook-art" :src="notebook" alt="" width="320" height="320" />
      </div>
      <section class="auth-card" aria-labelledby="auth-title">
        <nav class="auth-nav" aria-label="账号入口">
          <RouterLink to="/login">登录</RouterLink>
          <RouterLink to="/register">学生注册</RouterLink>
        </nav>
        <slot />
      </section>
    </div>
  </main>
</template>

<style src="../styles/auth.css"></style>
