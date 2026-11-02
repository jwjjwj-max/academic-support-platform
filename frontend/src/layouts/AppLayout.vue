<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import { Award, Bell, BookOpen, CalendarDays, ClipboardCheck, FileClock, FileSpreadsheet, GitMerge, Home, Images, LogOut, Menu, Send, ShieldCheck, UserRound, Users, X } from 'lucide-vue-next'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const menuOpen = ref(false)
const items = computed(() => {
  if (auth.user?.accountType === 'ADMIN') return [
    { to: '/dashboard', label: '仪表盘', icon: Home },
    { to: '/admin/students', label: '学生账号审核', icon: Users },
    { to: '/admin/periods', label: '活动期次', icon: CalendarDays },
    { to: '/admin/applications', label: '报名审核', icon: Send },
    { to: '/admin/matching', label: '智能匹配', icon: GitMerge },
    { to: '/admin/materials', label: '材料与验收', icon: ClipboardCheck },
    { to: '/admin/grades', label: '成绩成果', icon: FileSpreadsheet },
    { to: '/admin/moral-files', label: '德育素质分', icon: Award },
    { to: '/admin/notifications', label: '站内通知', icon: Bell },
    { to: '/admin/audit-logs', label: '操作日志', icon: FileClock },
  ]
  if (auth.user?.accountType === 'SYSTEM_OWNER') return [
    { to: '/dashboard', label: '系统概览', icon: Home },
    { to: '/owner/admins', label: '管理员账号', icon: ShieldCheck },
  ]
  return [
    { to: '/dashboard', label: '首页', icon: Home },
    { to: '/profile', label: '个人资料', icon: UserRound },
    { to: '/applications', label: '期次报名', icon: Send },
    { to: '/matching', label: '我的匹配', icon: GitMerge },
    { to: '/materials', label: '帮扶材料', icon: Images },
    { to: '/results', label: '我的成果', icon: FileSpreadsheet },
    { to: '/moral-files', label: '德育素质分', icon: Award },
    { to: '/notifications', label: '站内通知', icon: Bell },
  ]
})

function logout() { auth.logout(); router.replace('/login') }
</script>

<template>
  <div class="app-shell">
    <aside class="sidebar" :class="{ open: menuOpen }">
      <div class="brand"><span class="brand-mark">1+1</span><span><strong>学业帮扶平台</strong><small>学院试运行系统</small></span></div>
      <button class="mobile-close" aria-label="关闭菜单" @click="menuOpen = false"><X :size="20" /></button>
      <nav aria-label="主导航">
        <RouterLink v-for="item in items" :key="item.to" :to="item.to" @click="menuOpen = false">
          <component :is="item.icon" :size="17" /><span>{{ item.label }}</span>
        </RouterLink>
      </nav>
      <div class="sidebar-account">
        <span class="avatar">{{ auth.user?.displayName.slice(0, 1) }}</span>
        <span><strong>{{ auth.user?.displayName }}</strong><small>{{ auth.user?.accountType === 'ADMIN' ? '管理员' : auth.user?.accountType === 'SYSTEM_OWNER' ? '系统负责人' : '学生' }}</small></span>
        <button class="icon-ghost" aria-label="退出登录" @click="logout"><LogOut :size="18" /></button>
      </div>
    </aside>
    <div v-if="menuOpen" class="scrim" @click="menuOpen = false" />
    <main class="main-column">
      <header class="topbar">
        <button class="menu-button" aria-label="打开菜单" @click="menuOpen = true"><Menu :size="20" /></button>
        <div><h1>{{ route.meta.title }}</h1><p>学业帮扶智慧平台</p></div>
        <div class="topbar-user"><BookOpen :size="18" /><span>{{ auth.user?.displayName }}</span></div>
      </header>
      <div class="page-content"><RouterView /></div>
    </main>
  </div>
</template>
