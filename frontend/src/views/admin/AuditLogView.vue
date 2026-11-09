<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { apiErrorMessage, http } from '@/services/http'
import type { ApiResponse, AuditLog } from '@/types/api'
import { labelOf } from '@/utils/labels'
const items=ref<AuditLog[]>([]),error=ref('')
onMounted(async()=>{try{items.value=(await http.get<ApiResponse<AuditLog[]>>('/admin/audit-logs')).data.data}catch(e){error.value=apiErrorMessage(e)}})

function correctionDetails(item: AuditLog) {
  const fields = {
    grade: '报名年级', major: '报名专业', cumulativeGpa: '报名累计平均绩点',
    subjects: '帮扶科目', statementText: '报名自述', applicationStatus: '报名状态',
  }
  try {
    const before = JSON.parse(item.beforeSnapshot || '{}')
    const after = JSON.parse(item.afterSnapshot || '{}')
    const changes = Object.entries(fields).map(([key, name]) => ({ name, before: before[key], after: after[key] }))
    for (const [key, name] of [['grade', '账号年级'], ['major', '账号专业']]) {
      changes.push({ name, before: before.studentProfile?.[key], after: after.studentProfile?.[key] })
    }
    return changes.filter(change => JSON.stringify(change.before) !== JSON.stringify(change.after))
  } catch {
    return []
  }
}

function fieldValue(value: unknown) {
  if (Array.isArray(value)) return value.join('、')
  if (typeof value === 'string') return labelOf(value)
  return value ?? '—'
}
</script>
<template>
  <div v-if="error" class="notice danger">{{ error }}</div>
  <article class="card">
    <header><div><h2>关键操作日志</h2><p>最近 100 条操作记录，包括报名资料更正。</p></div></header>
    <div class="table-wrap">
      <table>
        <thead><tr><th>时间</th><th>操作人</th><th>动作</th><th>对象</th><th>原因与详情</th></tr></thead>
        <tbody>
          <tr v-for="item in items" :key="item.id">
            <td>{{ new Date(item.createdAt).toLocaleString('zh-CN') }}</td>
            <td>{{ item.actorLogin }}</td>
            <td>{{ item.action === 'APPLICATION_CORRECTION' ? '报名资料更正' : item.action }}</td>
            <td>{{ item.targetType }} {{ item.targetId }}</td>
            <td>
              {{ item.reason || '—' }}
              <details v-if="item.action === 'APPLICATION_CORRECTION'">
                <summary>查看修改前后</summary>
                <ul class="compact-list correction-values">
                  <li v-for="field in correctionDetails(item)" :key="field.name">
                    <strong>{{ field.name }}</strong>
                    <small>更正前 · {{ fieldValue(field.before) }}</small>
                    <small>更正后 · {{ fieldValue(field.after) }}</small>
                  </li>
                </ul>
              </details>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </article>
</template>

<style scoped>
.correction-values { min-width: 150px; max-width: 360px; white-space: pre-wrap; overflow-wrap: anywhere; }
</style>
