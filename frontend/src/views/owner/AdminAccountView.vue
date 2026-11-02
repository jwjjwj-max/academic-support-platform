<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { http, apiErrorMessage } from '@/services/http'
import type { AdminAccount, ApiResponse } from '@/types/api'

const admins = ref<AdminAccount[]>([]), loading = ref(true), error = ref(''), submitting = ref(false), showCreate = ref(false)
const form = reactive({ loginName: '', displayName: '', initialPassword: '' })
async function load() { loading.value = true; try { admins.value = (await http.get<ApiResponse<AdminAccount[]>>('/owner/admins')).data.data } catch (e) { error.value = apiErrorMessage(e) } finally { loading.value = false } }
async function create() { submitting.value = true; try { await http.post('/owner/admins', form); showCreate.value = false; Object.assign(form, { loginName: '', displayName: '', initialPassword: '' }); await load() } catch (e) { error.value = apiErrorMessage(e) } finally { submitting.value = false } }
async function toggle(item: AdminAccount) { const next = item.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'; const reason = window.prompt(`请输入${next === 'DISABLED' ? '停用' : '启用'}原因`); if (!reason?.trim()) return; try { await http.put(`/owner/admins/${item.id}/status`, { status: next, reason }); await load() } catch (e) { error.value = apiErrorMessage(e) } }
onMounted(load)
</script>

<template>
  <div class="page-actions"><button class="button primary" @click="showCreate = true">创建管理员</button></div><div v-if="error" class="notice danger">{{ error }}</div>
  <section class="card"><header><div><h2>管理员账号</h2><p>每名工作人员使用独立账号；停用后其已有登录令牌立即失效。</p></div></header><div v-if="loading" class="loading-state">正在加载…</div><div v-else-if="!admins.length" class="empty-state"><h3>暂无管理员</h3><p>创建首个日常业务管理员账号。</p></div><div v-else class="table-wrap"><table><thead><tr><th>姓名</th><th>登录账号</th><th>状态</th><th>创建时间</th><th>最近登录</th><th>操作</th></tr></thead><tbody><tr v-for="item in admins" :key="item.id"><td><strong>{{ item.displayName }}</strong></td><td>{{ item.loginName }}</td><td><span class="status-badge" :class="item.status.toLowerCase()">{{ item.status === 'ACTIVE' ? '启用' : '停用' }}</span></td><td>{{ new Date(item.createdAt).toLocaleString() }}</td><td>{{ item.lastLoginAt ? new Date(item.lastLoginAt).toLocaleString() : '尚未登录' }}</td><td><button class="button small" :class="item.status === 'ACTIVE' ? 'danger' : 'secondary'" @click="toggle(item)">{{ item.status === 'ACTIVE' ? '停用' : '启用' }}</button></td></tr></tbody></table></div></section>
  <dialog :open="showCreate" class="modal"><form @submit.prevent="create"><header><h2>创建管理员账号</h2></header><div class="modal-body"><label>登录账号<input v-model.trim="form.loginName" required minlength="4" /></label><label>管理员姓名<input v-model.trim="form.displayName" required /></label><label>初始密码<input v-model="form.initialPassword" type="password" minlength="10" required /><small>至少 10 位，包含字母和数字；请通过安全渠道告知本人。</small></label></div><footer><button type="button" class="button ghost" @click="showCreate = false">取消</button><button class="button primary" :disabled="submitting">创建账号</button></footer></form></dialog>
</template>

