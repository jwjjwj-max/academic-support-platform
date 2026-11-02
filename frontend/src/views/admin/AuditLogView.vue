<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { apiErrorMessage, http } from '@/services/http'
import type { ApiResponse, AuditLog } from '@/types/api'
const items=ref<AuditLog[]>([]),error=ref('')
onMounted(async()=>{try{items.value=(await http.get<ApiResponse<AuditLog[]>>('/admin/audit-logs')).data.data}catch(e){error.value=apiErrorMessage(e)}})
</script>
<template><div v-if="error" class="notice danger">{{error}}</div><article class="card"><header><div><h2>关键操作日志</h2><p>最近100条审核、驳回、重提、匹配和验收留痕。</p></div></header><div class="table-wrap"><table><thead><tr><th>时间</th><th>操作人</th><th>动作</th><th>对象</th><th>原因</th></tr></thead><tbody><tr v-for="item in items" :key="item.id"><td>{{new Date(item.createdAt).toLocaleString('zh-CN')}}</td><td>{{item.actorLogin}}</td><td>{{item.action}}</td><td>{{item.targetType}} {{item.targetId}}</td><td>{{item.reason||'—'}}</td></tr></tbody></table></div></article></template>
