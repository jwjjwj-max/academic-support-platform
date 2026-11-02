import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import type { AccountType } from '@/types/api'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/', redirect: '/dashboard' },
    { path: '/login', component: () => import('@/views/LoginView.vue'), meta: { public: true, title: '登录' } },
    { path: '/register', component: () => import('@/views/RegisterView.vue'), meta: { public: true, title: '学生注册' } },
    {
      path: '/', component: () => import('@/layouts/AppLayout.vue'),
      children: [
        { path: 'dashboard', component: () => import('@/views/DashboardView.vue'), meta: { title: '首页' } },
        { path: 'profile', component: () => import('@/views/ProfileView.vue'), meta: { title: '个人资料', roles: ['STUDENT'] } },
        { path: 'applications', component: () => import('@/views/ApplicationView.vue'), meta: { title: '期次报名', roles: ['STUDENT'] } },
        { path: 'matching', component: () => import('@/views/MatchingView.vue'), meta: { title: '我的匹配', roles: ['STUDENT'] } },
        { path: 'materials', component: () => import('@/views/MaterialsView.vue'), meta: { title: '帮扶材料', roles: ['STUDENT'] } },
        { path: 'results', component: () => import('@/views/ResultsView.vue'), meta: { title: '我的成果', roles: ['STUDENT'] } },
        { path: 'moral-files', component: () => import('@/views/MoralFilesView.vue'), meta: { title: '德育素质分', roles: ['STUDENT'] } },
        { path: 'notifications', component: () => import('@/views/NotificationsView.vue'), meta: { title: '站内通知', roles: ['STUDENT'] } },
        { path: 'admin/students', component: () => import('@/views/admin/StudentReviewView.vue'), meta: { title: '学生账号审核', roles: ['ADMIN'] } },
        { path: 'admin/periods', component: () => import('@/views/admin/PeriodView.vue'), meta: { title: '活动期次', roles: ['ADMIN'] } },
        { path: 'admin/applications', component: () => import('@/views/admin/ApplicationReviewView.vue'), meta: { title: '报名审核', roles: ['ADMIN'] } },
        { path: 'admin/matching', component: () => import('@/views/admin/MatchingManageView.vue'), meta: { title: '智能匹配', roles: ['ADMIN'] } },
        { path: 'admin/materials', component: () => import('@/views/admin/MaterialManageView.vue'), meta: { title: '材料与验收', roles: ['ADMIN'] } },
        { path: 'admin/grades', component: () => import('@/views/admin/GradeManageView.vue'), meta: { title: '成绩成果', roles: ['ADMIN'] } },
        { path: 'admin/moral-files', component: () => import('@/views/admin/MoralFileManageView.vue'), meta: { title: '德育素质分', roles: ['ADMIN'] } },
        { path: 'admin/notifications', component: () => import('@/views/admin/NotificationManageView.vue'), meta: { title: '站内通知', roles: ['ADMIN'] } },
        { path: 'admin/audit-logs', component: () => import('@/views/admin/AuditLogView.vue'), meta: { title: '操作日志', roles: ['ADMIN'] } },
        { path: 'owner/admins', component: () => import('@/views/owner/AdminAccountView.vue'), meta: { title: '管理员账号', roles: ['SYSTEM_OWNER'] } },
      ],
    },
    { path: '/:pathMatch(.*)*', redirect: '/dashboard' },
  ],
})

router.beforeEach(async (to) => {
  document.title = `${String(to.meta.title || '学业帮扶')}｜学业帮扶智慧平台`
  if (to.meta.public) return true
  const auth = useAuthStore()
  if (!auth.token) return { path: '/login', query: { redirect: to.fullPath } }
  if (!auth.user) {
    try { await auth.loadUser() } catch { auth.logout(); return { path: '/login', query: { expired: '1' } } }
  }
  const roles = to.meta.roles as AccountType[] | undefined
  if (roles && auth.user && !roles.includes(auth.user.accountType)) return '/dashboard'
  return true
})

export default router
