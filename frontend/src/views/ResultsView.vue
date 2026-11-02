<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { apiErrorMessage, http } from '@/services/http'
import type { ApiResponse, StudentGradeResult } from '@/types/api'
import { labelOf } from '@/utils/labels'
const items=ref<StudentGradeResult[]>([]),error=ref(''),loading=ref(true)
onMounted(async()=>{try{items.value=(await http.get<ApiResponse<StudentGradeResult[]>>('/grades/mine')).data.data}catch(e){error.value=apiErrorMessage(e)}finally{loading.value=false}})
function signed(value?:number){if(value==null)return'待导入';return `${value>0?'+':''}${value.toFixed(2)}`}
</script>
<template><div class="notice"><strong>隐私说明</strong><p>这里只展示你本人的单学期平均学分绩点，不展示成绩排名、排名变化或其他同学数据。</p></div><div v-if="error" class="notice danger">{{error}}</div><div v-if="loading" class="loading-state">正在加载成果…</div><div v-else-if="!items.length" class="empty-state card"><h3>暂无成绩成果</h3><p>管理员完成帮扶前或帮扶后成绩导入后，这里会显示你的绩点变化。</p></div><article v-for="item in items" :key="item.periodId" class="card"><header><div><span class="tag">{{labelOf(item.role)}}</span><h2>{{item.periodName}}</h2></div></header><div class="card-body result-metrics"><div><small>帮扶前平均学分绩点</small><strong>{{item.beforeTermGpa?.toFixed(2)??'待导入'}}</strong></div><div><small>帮扶后平均学分绩点</small><strong>{{item.afterTermGpa?.toFixed(2)??'待导入'}}</strong></div><div :class="{'positive-change':(item.change??0)>0,'negative-change':(item.change??0)<0}"><small>绩点变化</small><strong>{{signed(item.change)}}</strong></div></div></article></template>
