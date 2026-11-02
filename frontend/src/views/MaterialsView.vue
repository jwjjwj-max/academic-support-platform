<script setup lang="ts">
import { uploadPrivate } from '@/services/private-files'
import { computed, onMounted, ref } from 'vue'
import ImagePreviewDialog from '@/components/ImagePreviewDialog.vue'
import { apiErrorMessage, http } from '@/services/http'
import { useAuthStore } from '@/stores/auth'
import type { ApiResponse, PairPhoto, PairReflection, SupportPairDetail } from '@/types/api'
import { labelOf, reflectionStatusLabel } from '@/utils/labels'

const auth=useAuthStore(), pairs=ref<SupportPairDetail[]>([]), loading=ref(true), error=ref(''), success=ref(''), busy=ref(false)
const uploadPair=ref<SupportPairDetail|null>(null), uploadPhoto=ref<PairPhoto|null>(null), file=ref<File|null>(null), description=ref('')
const reflectionPair=ref<SupportPairDetail|null>(null), reflection=ref('')
const reflectionDetail=ref<PairReflection|null>(null)
const previewPhoto=ref<PairPhoto|null>(null)
const reflectionCount=computed(()=>Array.from(reflection.value.trim()).length)
async function load(){loading.value=true;error.value='';try{pairs.value=(await http.get<ApiResponse<SupportPairDetail[]>>('/materials/mine')).data.data}catch(e){error.value=apiErrorMessage(e)}finally{loading.value=false}}
function openUpload(pair:SupportPairDetail,photo?:PairPhoto){uploadPair.value=pair;uploadPhoto.value=photo||null;file.value=null;description.value=photo?.description||''}
async function submitPhoto(){if(!uploadPair.value||!file.value)return;busy.value=true;const form=new FormData();form.append('file',file.value);form.append('description',description.value.trim());try{const path=uploadPhoto.value?`/materials/photos/${uploadPhoto.value.id}/resubmit`:`/materials/pairs/${uploadPair.value.id}/photos`;await uploadPrivate(path,form,{kind:'PHOTO',pairId:uploadPair.value.id,photoId:uploadPhoto.value?.id,description:description.value.trim()});success.value=uploadPhoto.value?'照片已重新提交，等待同伴确认。':'照片已提交，等待同伴确认真实性。';uploadPair.value=null;await load()}catch(e){error.value=apiErrorMessage(e)}finally{busy.value=false}}
async function confirm(photo:PairPhoto){busy.value=true;try{await http.post(`/materials/photos/${photo.id}/peer-confirm`);success.value='真实性确认完成，照片已进入管理员审核。';await load()}catch(e){error.value=apiErrorMessage(e)}finally{busy.value=false}}
function openReflection(pair:SupportPairDetail){reflectionPair.value=pair;reflection.value=pair.reflections.find(x=>x.authorId===auth.user?.id)?.content||''}
function openReflectionDetail(item:PairReflection){reflectionDetail.value=item}
async function submitReflection(){if(!reflectionPair.value)return;busy.value=true;try{await http.post(`/materials/pairs/${reflectionPair.value.id}/reflections`,{content:reflection.value.trim()});success.value='活动感悟已提交，管理员可查看。';reflectionPair.value=null;await load()}catch(e){error.value=apiErrorMessage(e)}finally{busy.value=false}}
function ownReflection(pair:SupportPairDetail){return pair.reflections.find(x=>x.authorId===auth.user?.id)}
onMounted(load)
</script>
<template>
  <div v-if="error" class="notice danger">{{ error }}</div><div v-if="success" class="notice success">{{ success }}</div>
  <div v-if="loading" class="loading-state">正在加载帮扶材料…</div>
  <div v-else-if="!pairs.length" class="empty-state card"><h3>暂无正式帮扶对</h3><p>双方确认结对后，帮扶材料会显示在这里。</p></div>
  <article v-for="pair in pairs" :key="pair.id" class="card material-pair"><header><div><span class="tag">{{ pair.periodName }}</span><h2>{{ pair.navigator.name }} 与 {{ pair.climber.name }}</h2><p>{{ pair.pairNo }} · {{ labelOf(pair.periodStatus) }} · {{ labelOf(pair.status) }}</p></div></header><div class="card-body">
    <div class="notice"><strong>照片要求：每组 {{ pair.photoMin }}—{{ pair.photoMax }} 张</strong><p v-if="pair.periodStatus==='ACCEPTANCE'">当前为验收阶段：可补交过程照片、提交活动感悟，也可重提被驳回照片；照片仍需同伴确认和管理员审核，感悟提交后管理员可查看。</p><p v-else-if="pair.periodStatus==='TRACKING'">照片须先由未上传的一方确认真实性，再由管理员审核；活动感悟提交后管理员可查看。材料提交后不可自行修改。</p><p v-else>当前阶段暂不开放学生提交材料；请在帮扶开展或验收窗口内完成提交。</p></div>
    <div class="section-heading"><h3>过程照片</h3><button v-if="pair.canUploadPhoto && pair.photos.filter(x=>x.status!=='REJECTED').length<pair.photoMax" class="button primary small" @click="openUpload(pair)">{{ pair.periodStatus==='ACCEPTANCE' ? '补交照片' : '上传照片' }}</button></div>
    <div v-if="!pair.photos.length" class="muted">尚未提交照片。</div><div v-else class="material-list"><div v-for="photo in pair.photos" :key="photo.id" class="material-item"><div><strong>{{ photo.originalFilename }}</strong><small>{{ photo.uploaderName }}提交 · 第{{ photo.revisionNo }}版 · {{ labelOf(photo.status) }}</small><p v-if="photo.description">{{ photo.description }}</p><p v-if="photo.rejectionReason" class="text-danger">驳回原因：{{ photo.rejectionReason }}</p></div><div class="table-actions"><button class="button ghost small" @click="previewPhoto=photo">站内预览</button><button v-if="photo.canPeerConfirm" class="button primary small" :disabled="busy" @click="confirm(photo)">确认真实</button><button v-if="photo.canResubmit" class="button secondary small" @click="openUpload(pair,photo)">修改重提</button></div></div></div>
    <div class="section-heading"><h3>我的活动感悟</h3><button v-if="pair.canSubmitReflection && (!ownReflection(pair)||ownReflection(pair)?.canResubmit)" class="button primary small" @click="openReflection(pair)">{{ ownReflection(pair) ? '修改重提' : '提交我的感悟' }}</button></div>
  <div class="reflection-grid"><button v-for="item in pair.reflections" :key="item.id" type="button" class="material-item reflection-card" :aria-label="`打开${item.authorName}的完整活动感悟`" aria-haspopup="dialog" @click="openReflectionDetail(item)"><div><strong>{{ item.authorName }} · {{ item.wordCount }}字</strong><small>{{ reflectionStatusLabel(item.status) }}</small><p class="reflection-preview">{{ item.content }}</p><small class="reflection-open-hint">点击查看完整感悟</small></div></button><div v-if="!pair.reflections.length" class="muted">你还没有提交活动感悟。</div></div>
    <div v-if="pair.status==='COMPLETED'" class="notice success">管理员已人工验收：{{ pair.acceptanceNote }}</div><div v-else-if="pair.status==='ENDED'" class="notice danger">该帮扶对已解除：{{ pair.endReason }}</div>
  </div></article>
  <dialog :open="Boolean(uploadPair)" class="modal"><form @submit.prevent="submitPhoto"><header><h2>{{ uploadPhoto ? '修改并重提照片' : '上传过程照片' }}</h2></header><div class="modal-body"><label>选择照片<input type="file" accept="image/jpeg,image/png,.jpg,.jpeg,.png" required @change="file=($event.target as HTMLInputElement).files?.[0]||null" /></label><label>照片说明（选填）<textarea v-model="description" maxlength="500" /></label><div class="notice">仅支持真实的 JPG、JPEG、PNG 内容，单张不超过 5MB。上传后不能自行删除或修改。</div></div><footer><button type="button" class="button ghost" @click="uploadPair=null">取消</button><button class="button primary" :disabled="busy||!file">提交照片</button></footer></form></dialog>
  <dialog :open="Boolean(reflectionPair)" class="modal"><form @submit.prevent="submitReflection"><header><h2>提交活动感悟</h2></header><div class="modal-body"><label>感悟内容（300—500字）<textarea v-model="reflection" rows="12" maxlength="500" /><small :class="{'text-danger':reflectionCount<300||reflectionCount>500}">当前 {{ reflectionCount }} 字</small></label><div class="notice">提交后将锁定，管理员可查看；如需更正，请联系管理员处理。</div></div><footer><button type="button" class="button ghost" @click="reflectionPair=null">取消</button><button class="button primary" :disabled="busy||reflectionCount<300||reflectionCount>500">提交感悟</button></footer></form></dialog>
  <dialog :open="Boolean(reflectionDetail)" class="modal reflection-detail-modal" aria-label="完整活动感悟" @click.self="reflectionDetail=null" @keydown.esc.window="reflectionDetail=null"><header><div><span class="eyebrow">活动感悟</span><h2>{{ reflectionDetail?.authorName }}的完整感悟</h2></div><button type="button" class="icon-ghost modal-close" aria-label="关闭完整活动感悟" @click="reflectionDetail=null">×</button></header><div v-if="reflectionDetail" class="modal-body"><p class="reflection-detail-meta">{{ reflectionDetail.wordCount }}字 · {{ reflectionStatusLabel(reflectionDetail.status) }}</p><p class="reflection-full">{{ reflectionDetail.content }}</p></div><footer><button type="button" class="button primary" @click="reflectionDetail=null">关闭</button></footer></dialog>
  <ImagePreviewDialog :photo="previewPhoto" @close="previewPhoto=null" />
</template>
