<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { apiErrorMessage, http } from '@/services/http'
import type { ApiResponse, NotificationItem } from '@/types/api'
const items=ref<NotificationItem[]>([]),error=ref('')
async function load(){try{items.value=(await http.get<ApiResponse<NotificationItem[]>>('/notifications/mine')).data.data}catch(e){error.value=apiErrorMessage(e)}}
async function read(item:NotificationItem){if(!item.readAt){await http.post(`/notifications/${item.id}/read`);await load()}}
onMounted(load)
</script>
<template><div v-if="error" class="notice danger">{{error}}</div><div v-if="!items.length" class="empty-state card"><h3>暂无站内通知</h3></div><article v-for="item in items" :key="item.id" class="card notification-card" :class="{unread:!item.readAt}" @click="read(item)"><div class="card-body"><span class="tag">{{item.audienceLabel}}</span><h2>{{item.title}}</h2><p>{{item.content}}</p><small>{{new Date(item.createdAt).toLocaleString('zh-CN')}} · {{item.createdByName}}发布 · {{item.readAt?'已读':'未读'}}</small></div></article></template>
