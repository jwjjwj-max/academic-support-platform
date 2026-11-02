<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { apiErrorMessage, http } from '@/services/http'
import type { ApiResponse, Period } from '@/types/api'
const periods=ref<Period[]>([]),form=reactive({periodId:'' as number|'',title:'',content:'',audienceType:'PERIOD_PARTICIPANTS'}),error=ref(''),success=ref(''),busy=ref(false)
onMounted(async()=>{periods.value=(await http.get<ApiResponse<Period[]>>('/periods')).data.data})
async function submit(){busy.value=true;try{await http.post('/admin/notifications',{...form,periodId:form.audienceType==='ALL_STUDENTS'?null:form.periodId});success.value='站内通知已发送并生成明确收件人记录。';form.title='';form.content=''}catch(e){error.value=apiErrorMessage(e)}finally{busy.value=false}}
</script>
<template><div v-if="error" class="notice danger">{{error}}</div><div v-if="success" class="notice success">{{success}}</div><article class="card"><header><div><h2>发布站内通知</h2><p>仅站内展示，不发送短信、邮件或微信消息。</p></div></header><form class="card-body form-grid" @submit.prevent="submit"><label>通知范围<select v-model="form.audienceType"><option value="PERIOD_PARTICIPANTS">本期审核通过参与者</option><option value="ALL_STUDENTS">全部学生</option></select></label><label v-if="form.audienceType==='PERIOD_PARTICIPANTS'">活动期次<select v-model="form.periodId" required><option disabled value="">请选择</option><option v-for="p in periods" :key="p.id" :value="p.id">{{p.name}}</option></select></label><label class="full-row">标题<input v-model="form.title" maxlength="200" required /></label><label class="full-row">内容<textarea v-model="form.content" maxlength="2000" rows="8" required /></label><div class="form-actions full-row"><button class="button primary" :disabled="busy">发布通知</button></div></form></article></template>
