<script setup lang="ts">
import { privateFileBlob } from '@/services/private-files'

import { onMounted, ref } from 'vue';import { apiErrorMessage,http } from '@/services/http';import type{ApiResponse,MoralFile}from'@/types/api'
const items=ref<MoralFile[]>([]),error=ref('');onMounted(async()=>{try{items.value=(await http.get<ApiResponse<MoralFile[]>>('/moral-files')).data.data}catch(e){error.value=apiErrorMessage(e)}})
async function download(item:MoralFile){try{const blob=await privateFileBlob(item.downloadUrl);const u=URL.createObjectURL(blob);const a=document.createElement('a');a.href=u;a.download=item.originalFilename;a.click();setTimeout(()=>URL.revokeObjectURL(u),60000)}catch(e){error.value=apiErrorMessage(e)}}
</script>
<template><div class="notice"><strong>文件说明</strong><p>德育素质分由管理员上传发布，平台不自动计算。你只能查看已发布文件。</p></div><div v-if="error" class="notice danger">{{error}}</div><div v-if="!items.length" class="empty-state card"><h3>暂无已发布文件</h3></div><article v-for="item in items" :key="item.id" class="card"><div class="card-body file-row"><div><span class="tag">{{item.periodName}}</span><h2>{{item.title}}</h2><p class="muted">{{item.originalFilename}} · {{(item.fileSize/1024).toFixed(1)}} KB · {{new Date(item.publishedAt!).toLocaleString('zh-CN')}}</p></div><button class="button primary" @click="download(item)">下载文件</button></div></article></template>
