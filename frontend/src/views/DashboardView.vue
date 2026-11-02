<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { http } from '@/services/http'
import type { ApiResponse, ParticipationApplication, Period, TodoItem } from '@/types/api'
import { labelOf } from '@/utils/labels'
import { registrationWindowMessage, studentApplicationStatus, studentPeriodLabel } from '@/utils/period-workflow'

const auth = useAuthStore()
const periods = ref<Period[]>([])
const applications = ref<ParticipationApplication[]>([])
const todos = ref<TodoItem[]>([])
const current = computed(() => periods.value[0])
const myCurrentApplication = computed(() => applications.value.find(item => item.periodId === current.value?.id))
const roleLabel = computed(() => auth.user?.accountType === 'ADMIN' ? '管理员' : auth.user?.accountType === 'SYSTEM_OWNER' ? '系统负责人' : '学生')
onMounted(async () => { try { periods.value = (await http.get<ApiResponse<Period[]>>('/periods')).data.data; if (auth.user?.accountType === 'STUDENT') { applications.value = (await http.get<ApiResponse<ParticipationApplication[]>>('/applications/mine')).data.data; todos.value = (await http.get<ApiResponse<TodoItem[]>>('/todos/mine')).data.data } else if (auth.user?.accountType === 'ADMIN') todos.value = (await http.get<ApiResponse<TodoItem[]>>('/admin/todos')).data.data } catch { periods.value = []; applications.value = []; todos.value = [] } })
</script>

<template>
  <section class="hero"><div><span class="eyebrow">{{ roleLabel }}</span><h2>欢迎回来，{{ auth.user?.displayName }}</h2><p>这里展示与你当前账号权限相关的任务。数据来自正式后端服务，不使用原型演示数据。</p></div></section>
  <div v-if="auth.user?.accountType === 'STUDENT' && auth.user.profileReviewStatus !== 'APPROVED'" class="notice" :class="{ danger: auth.user.profileReviewStatus === 'REJECTED' }">
    <strong>{{ auth.user.profileReviewStatus === 'REJECTED' ? '资料审核被驳回' : '资料正在等待管理员审核' }}</strong>
    <p>{{ auth.user.profileRejectionReason || '审核通过前可以查看和修改个人资料，但不能参与活动报名。' }}</p>
    <RouterLink class="button secondary small" to="/profile">查看个人资料</RouterLink>
  </div>
  <div v-if="auth.user?.accountType === 'STUDENT' && current" class="notice">
    <strong>活动阶段：{{ studentPeriodLabel(current.status) }}</strong>
    <p>{{ registrationWindowMessage(current) }}</p>
    <p><strong>我的状态：</strong>{{ myCurrentApplication ? studentApplicationStatus(myCurrentApplication) : '本期尚未报名' }}</p>
  </div>
  <div class="grid three">
    <article class="metric-card"><span>账号类型</span><strong>{{ roleLabel }}</strong><small>基于后端权限判定</small></article>
    <article class="metric-card"><span>当前期次</span><strong>{{ current?.shortName || '暂无' }}</strong><small>{{ current ? (auth.user?.accountType === 'STUDENT' ? studentPeriodLabel(current.status) : labelOf(current.status)) : '等待管理员创建' }}</small></article>
    <article class="metric-card"><span>关键待办</span><strong>{{ todos.reduce((sum,item)=>sum+item.count,0) }}</strong><small>材料、通知与验收任务</small></article>
  </div>
  <section class="card"><header><h2>当前可办事项</h2></header><div class="card-body">
    <div v-if="todos.length" class="todo-list"><RouterLink v-for="todo in todos" :key="todo.type+String(todo.targetId||'')" :to="todo.route" class="todo-item"><span><strong>{{todo.title}}</strong><small>{{todo.description}}</small></span><b>{{todo.count}}</b></RouterLink></div>
    <div v-else-if="auth.user?.accountType === 'ADMIN'" class="action-list"><RouterLink to="/admin/students">审核学生注册资料</RouterLink><RouterLink to="/admin/periods">创建与配置活动期次</RouterLink><RouterLink to="/admin/applications">审核学生报名</RouterLink><RouterLink to="/admin/matching">运行与发布匹配</RouterLink><RouterLink to="/admin/materials">审核材料与人工验收</RouterLink></div>
    <div v-else-if="auth.user?.accountType === 'SYSTEM_OWNER'" class="action-list"><RouterLink to="/owner/admins">创建或停用管理员账号</RouterLink></div>
    <div v-else class="action-list"><RouterLink to="/profile">查看或维护个人资料</RouterLink><RouterLink v-if="auth.user?.profileReviewStatus === 'APPROVED'" to="/applications">参加期次报名</RouterLink><RouterLink v-if="auth.user?.profileReviewStatus === 'APPROVED'" to="/matching">查看我的匹配</RouterLink><RouterLink v-if="auth.user?.profileReviewStatus === 'APPROVED'" to="/materials">提交帮扶材料</RouterLink></div>
  </div></section>
</template>
