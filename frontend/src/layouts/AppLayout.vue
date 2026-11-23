<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { onClickOutside } from '@vueuse/core'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import { Award, Bell, CalendarDays, ChevronDown, ClipboardCheck, FileClock, FileSpreadsheet, GitMerge, Home, Images, LogOut, Menu, Send, ShieldCheck, UserRound, Users, X } from 'lucide-vue-next'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const menuOpen = ref(false)
const accountOpen = ref(false)
const accountMenu = ref<HTMLElement | null>(null)
const accountTrigger = ref<HTMLButtonElement | null>(null)
onClickOutside(accountMenu, () => { accountOpen.value = false })
watch(() => route.fullPath, () => { accountOpen.value = false })

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
    { to: '/applications', label: '期次报名', icon: Send },
    { to: '/matching', label: '我的匹配', icon: GitMerge },
    { to: '/materials', label: '帮扶材料', icon: Images },
    { to: '/results', label: '我的成果', icon: FileSpreadsheet },
    { to: '/moral-files', label: '德育素质分', icon: Award },
    { to: '/notifications', label: '站内通知', icon: Bell },
  ]
})

function dismissAccountMenu(event: KeyboardEvent) {
  if (!accountOpen.value) return
  event.stopPropagation()
  accountOpen.value = false
  accountTrigger.value?.focus()
}

function closeAccountOnBlur(event: FocusEvent) {
  if (event.relatedTarget instanceof Node && accountMenu.value?.contains(event.relatedTarget)) return
  accountOpen.value = false
}

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
    </aside>
    <div v-if="menuOpen" class="scrim" @click="menuOpen = false" />
    <main class="main-column">
      <header class="topbar">
        <button class="menu-button" aria-label="打开菜单" @click="menuOpen = true"><Menu :size="20" /></button>
        <div class="topbar-title"><h1>{{ route.meta.title }}</h1><p>学业帮扶智慧平台</p></div>
        <div v-if="auth.user" ref="accountMenu" class="account-menu" @keydown.esc="dismissAccountMenu" @focusout="closeAccountOnBlur">
          <button ref="accountTrigger" class="account-trigger" type="button"
            :aria-label="`账号菜单，${auth.user.displayName}`" :aria-expanded="accountOpen"
            aria-controls="account-actions" @click="accountOpen = !accountOpen">
            <UserRound :size="18" aria-hidden="true" />
            <span>{{ auth.user.displayName }}</span>
            <ChevronDown :size="15" aria-hidden="true" />
          </button>
          <div v-show="accountOpen" id="account-actions" class="account-actions">
            <RouterLink to="/profile" @click="accountOpen = false"><UserRound :size="17" aria-hidden="true" />个人资料</RouterLink>
            <button type="button" @click="logout"><LogOut :size="17" aria-hidden="true" />退出登录</button>
          </div>
        </div>
      </header>
      <div class="page-content"><RouterView /></div>
    </main>
  </div>
</template>

<style scoped>
.topbar-title {
  min-width: 0;
}

.topbar-title h1,
.topbar-title p,
.account-trigger span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.account-menu {
  position: relative;
  min-width: 0;
  max-width: 13rem;
  margin-left: auto;
}

.account-trigger {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  min-height: 40px;
  padding: 7px 10px;
  border: 1px solid transparent;
  border-radius: 8px;
  background: transparent;
  color: #17466d;
}

.account-trigger svg {
  flex: none;
}

.account-trigger:hover,
.account-trigger[aria-expanded="true"] {
  border-color: #bedaf1;
  background: #e3f2fe;
}

.account-actions {
  position: absolute;
  z-index: 10;
  inset: calc(100% + 7px) 0 auto auto;
  display: grid;
  gap: 2px;
  min-width: 152px;
  padding: 5px;
  border: 1px solid var(--line);
  border-radius: 10px;
  background: #fff;
  box-shadow: 0 8px 22px #17284024;
}

.account-actions a,
.account-actions button {
  display: flex;
  align-items: center;
  gap: 9px;
  min-height: 42px;
  padding: 7px 10px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: #17466d;
  font-size: 14px;
  text-align: left;
  text-decoration: none;
}

.account-actions a:hover,
.account-actions button:hover {
  background: #e3f2fe;
}

.account-menu :focus-visible {
  outline: 2px solid var(--primary);
  outline-offset: 1px;
}

@media (max-width: 850px) {
  .topbar {
    gap: 9px;
  }

  .topbar-title h1 {
    font-size: clamp(16px, 4vw, 20px);
  }

  .account-menu {
    max-width: min(38%, 13rem);
  }

  .account-trigger {
    gap: 5px;
    padding-inline: 6px;
    font-size: 14px;
  }
}
</style>
