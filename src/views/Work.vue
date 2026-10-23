<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Monitor, StarFilled } from '@element-plus/icons-vue'
import { useMesStore } from '../store'
import { api, apiMode, ApiError } from '../api/request'
import { openProjectorWindow } from '../utils/projectorWindow'
import { resolveVideoBinding } from '../utils/videoBinding'
import { formatRangeReference } from '../utils/parameterRange'
import { firstOptionValue, isParameterValueInvalid } from '../utils/parameterValidation'

const route = useRoute(), router = useRouter()
const store = useMesStore()
async function loadTask(id) {
  if (!id) return
  try {
    if (apiMode) store.adoptServerTask(await api.task(id))
    else store.selectTask(id)
  } catch (error) { ElMessage.error(error instanceof ApiError ? error.message : '无法读取任务') }
}
async function loadVideoResources() {
  if (!apiMode) return
  try {
    const page = await api.videos({ page: 1, pageSize: 100 })
    store.state.videos = (page?.items || []).map(item => ({
      ...item,
      id: String(item.id),
      categoryId: String(item.categoryId),
      sessionPreview: false,
    }))
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : '无法加载视频资源')
  }
}
watch(() => route.params.taskId, loadTask, { immediate: true })
const task = computed(() => store.state.task)
const step = computed(() => store.currentStep)
const activeSteps = computed(() => store.activeSteps)
const editable = computed(() => step.value && store.canEditStep(step.value.stepNo))
const station = computed(() => store.currentStation)
const stationBlocked = computed(() => !store.previousStationsConfirmed)
const canOpenParameterSummary = computed(() => !!task.value && (!store.stationConfirmed || store.finalSummaryPending))
const videoPlaybackFailed = ref(false)
const videoPlayback = ref({ url: '', loading: false, message: '' })
const resolvedVideo = ref(null)
const videoResolutionMessage = ref('')
const configuredVideoName = computed(() => {
  const name = String(step.value?.videoName || '').trim()
  return name && name.toUpperCase() !== 'NONE' ? name : ''
})
const boundVideo = computed(() => {
  if (!configuredVideoName.value) return null
  return resolveVideoBinding(
    configuredVideoName.value,
    store.state.videos,
    store.state.deleted.videos,
  ).video || resolvedVideo.value
})
const playableVideo = computed(() => {
  const video = boundVideo.value
  if (videoPlaybackFailed.value || !video) return null
  if (apiMode) return videoPlayback.value.url ? { ...video, previewUrl: videoPlayback.value.url } : null
  return video.sessionPreview && video.previewUrl ? video : null
})
async function loadVideoPlayback() {
  const video = boundVideo.value
  videoPlaybackFailed.value = false
  videoPlayback.value = { url: '', loading: false, message: '' }
  if (!video || !apiMode) return
  const id = String(video.id)
  videoPlayback.value = { url: '', loading: true, message: '' }
  try {
    const playback = await api.playback(id)
    if (String(boundVideo.value?.id) !== id) return
    if (Number(playback.playable) !== 1 || !playback.url) {
      videoPlayback.value = { url: '', loading: false, message: '视频正在解析或当前版本不可播放，请稍后重试。' }
      return
    }
    videoPlayback.value = { url: playback.url, loading: false, message: '' }
  } catch (error) {
    if (String(boundVideo.value?.id) !== id) return
    videoPlayback.value = { url: '', loading: false, message: error instanceof ApiError ? error.message : '无法获取视频播放地址，请稍后重试。' }
  }
}
async function resolveVideoResource() {
  const name = configuredVideoName.value
  resolvedVideo.value = null
  videoResolutionMessage.value = ''
  if (!name || !apiMode) return
  const local = resolveVideoBinding(name, store.state.videos, store.state.deleted.videos).video
  if (local) return
  try {
    const video = await api.resolveVideo(name)
    if (configuredVideoName.value !== name) return
    resolvedVideo.value = { ...video, id: String(video.id), categoryId: String(video.categoryId), sessionPreview: false }
  } catch (error) {
    if (configuredVideoName.value !== name) return
    videoResolutionMessage.value = error instanceof ApiError ? error.message : '无法解析步骤绑定的视频资源，请稍后重试。'
  }
}
const values = ref({})
const touchedParameters = ref({})
const ribbonNodes = ref(null)
const parameterTypeLabel = parameter => Number(parameter?.type) === 1 ? '单选参数' : Number(parameter?.type) === 2 ? '范围值' : '手动输入'
const parameterReferenceText = parameter => {
  const reference = String(parameter?.referenceValue || '').trim()
  if (Number(parameter?.type) === 2) {
    return formatRangeReference(reference)
  }
  if (Number(parameter?.type) === 1) {
    const option = (parameter.options || reference.split(/\r?\n/)).map(item => String(item).trim()).find(Boolean)
    return `合格参考项：${option || '—'}`
  }
  return `参考值：${reference || '—'}`
}
watch([() => task.value?.id, () => step.value?.stepNo, editable, () => step.value?.parameters], ([, no, canEdit]) => {
  touchedParameters.value = {}
  const nextValues = { ...(task.value?.logs?.[no]?.values || {}) }
  if (canEdit) {
    for (const parameter of step.value?.parameters || []) {
      const key = String(parameter.index)
      if (Number(parameter.type) === 1 && !String(nextValues[key] ?? '').trim()) {
        nextValues[key] = firstOptionValue(parameter)
      }
    }
  }
  values.value = nextValues
}, { immediate: true })
function markParameterTouched(index) {
  touchedParameters.value[String(index)] = true
}
function parameterInvalid(parameter) {
  return !!touchedParameters.value[String(parameter.index)] && isParameterValueInvalid(parameter, values.value[parameter.index])
}
watch(configuredVideoName, resolveVideoResource, { immediate: true })
watch(() => boundVideo.value?.id, loadVideoPlayback, { immediate: true })
function centerCurrentStep() { const wrap = ribbonNodes.value; const current = wrap && [...wrap.querySelectorAll('.step-node')].find(node => node.classList.contains('current')); if (current) wrap.scrollLeft = Math.max(0, Math.min(current.offsetLeft - wrap.clientWidth / 2 + current.offsetWidth / 2, wrap.scrollWidth - wrap.clientWidth)) }
watch(() => step.value?.stepNo, () => nextTick(centerCurrentStep))
onMounted(async () => { await loadVideoResources(); nextTick(centerCurrentStep); openProjectorWindow(task.value?.id) })
function go(item) {
  if (Number(item.stationId || 1) !== station.value.id) return store.setViewedStep(item.stepNo)
  if (apiMode && !task.value?.logs?.[item.stepNo] && item.stepNo !== step.value?.stepNo) return ElMessage.warning('生产模式下请按顺序保存本站步骤，不能跳过未完成步骤')
  store.jumpToStep(item.stepNo)
}
function goByNo(stepNo) { const item = activeSteps.value.find(entry => entry.stepNo === Number(stepNo)); if (item) go(item) }
function handleVideoError() { videoPlaybackFailed.value = true; videoPlayback.value = { ...videoPlayback.value, loading: false, message: '浏览器无法播放该视频，请确认视频编码格式。' } }
async function save() {
  if (!editable.value) {
    if (stationBlocked.value) return ElMessage.warning('请等待上一工位完成并确认交接')
    if (store.nextStationStep) { store.setViewedStep(store.nextStationStep.stepNo); return ElMessage.info('已返回本工位待完成步骤') }
    if (!store.stationConfirmed) return router.push(`/op/work/${task.value.id}/complete`)
    return ElMessage.warning('本工位已完成并完成交接')
  }
  if (apiMode) {
    try {
      const updated = await api.saveTaskStep(task.value.id, step.value.stepNo, values.value)
      store.adoptServerTask(updated)
      const stationComplete = store.stationSteps.every(item => !!store.state.task?.logs?.[item.stepNo])
      if (stationComplete) router.push(`/op/work/${task.value.id}/complete`)
      else {
        const next = store.nextStationStep
        if (next) store.setViewedStep(next.stepNo)
        ElMessage.success('步骤已保存，进入本工位下一步')
      }
    } catch (error) {
      ElMessage.error(error instanceof ApiError ? error.message : '保存步骤失败，请稍后重试')
    }
    return
  }
  const result = store.recordStep(values.value)
  if (!result.ok) return ElMessage.warning('当前步骤不可录入')
  if (result.stationComplete) router.push(`/op/work/${task.value.id}/complete`)
  else ElMessage.success('步骤已保存，进入本工位下一步')
}
function projector() { openProjectorWindow(task.value?.id) }
function openParameterSummary() {
  if (!store.previousStationsConfirmed) return ElMessage.warning('请先完成前序工位的全部步骤，并等待前序工位确认交接。')
  if (!store.stationSummaryReady && !store.stationConfirmed) {
    if (apiMode) return ElMessage.warning('请先保存当前工位全部步骤后再进入参数汇总')
    store.skipStationToParameterSummary()
  }
  router.push(`/op/work/${task.value.id}/complete`)
}
function handleKeydown(event) { if (event.key === 'Enter' && !event.repeat && !event.isComposing && editable.value) { event.preventDefault(); save() } }
onMounted(() => window.addEventListener('keydown', handleKeydown))
onBeforeUnmount(() => window.removeEventListener('keydown', handleKeydown))
</script>

<template>
  <main v-if="task && step" class="work-page">
    <div class="step-ribbon"><div class="ribbon-inner"><div class="ribbon-nodes" ref="ribbonNodes"><button v-for="item in activeSteps" :key="item.stepNo" class="step-node" :class="{ current:item.stepNo===step.stepNo, done:task.logs[item.stepNo]?.actionCode===0, foreign:Number(item.stationId||1)!==station.id }" :title="item.title" @click="go(item)"><span>{{ String(item.stepNo).padStart(2,'0') }}</span><small>{{ item.title }}</small></button></div></div></div>
    <div class="work-content">
      <div class="work-heading"><div><div class="work-title-row"><span class="station-tag">{{ station.name }}</span><span v-if="Number(step.stationId||1)!==station.id" class="normal-tag">其它工位 · 仅查看</span><span v-else-if="step.importance" class="important-tag"><StarFilled /> 重要工序</span><span v-else class="normal-tag">普通工序</span></div><h1>{{ step.title }}</h1></div><div class="work-heading-actions"><button v-if="canOpenParameterSummary" class="ghost-button" @click="openParameterSummary"><DocumentChecked /> 参数汇总</button><button class="ghost-button work-projector-button" @click="projector"><Monitor /> 打开投影窗口 <TopRight /></button></div></div>
      <div v-if="stationBlocked" class="asset-note warning"><WarningFilled /> 当前工位需等待上一工位完成参数汇总并确认交接。</div>
      <div class="work-subbar"><div class="work-assembly-progress"><span>本工位进度</span><strong>{{ store.stationSteps.filter(item=>task.logs[item.stepNo]).length }}<small> / {{ store.stationSteps.length }}</small></strong></div><div class="work-jump"><span>跳转 / 查看工序</span><el-select :model-value="step.stepNo" popper-class="work-jump-popper" style="width:160px" @update:model-value="goByNo"><el-option v-for="item in activeSteps" :key="item.stepNo" :label="`第 ${item.stepNo} 步 · 工位${store.stationNoForId(item.stationId)}`" :value="item.stepNo"><div class="jump-opt"><span>第 {{ item.stepNo }} 步 · 工位{{ store.stationNoForId(item.stationId) }}</span><StarFilled v-if="item.importance" class="jump-star" /></div></el-option></el-select><small>可回到本站已完成步骤修改参数</small></div></div>
      <div class="work-ab-layout"><section class="panel work-video"><div class="panel-head"><div><h2>作业视频</h2></div><span class="panel-meta">工艺绑定资源</span></div><div class="video-frame"><video v-if="playableVideo" :key="`${playableVideo.id}:${playableVideo.previewUrl}`" class="work-video-player" :src="playableVideo.previewUrl" controls playsinline @error="handleVideoError"/><div v-else class="video-stage"><VideoCamera /><strong>{{ boundVideo?.name || configuredVideoName || '该步骤未配置作业视频' }}</strong><span>{{ configuredVideoName ? (boundVideo ? (videoPlayback.loading ? '正在获取视频播放地址…' : videoPlayback.message || '视频地址不可用，请稍后重试。') : videoResolutionMessage || '正在解析步骤绑定的视频资源…') : '可在工艺文件步骤编辑中绑定有效视频' }}</span></div></div></section><section class="panel work-parameters"><div class="panel-head"><div><h2>内容</h2></div><span class="panel-meta">{{ editable ? '可录入' : '只读' }}</span></div><section class="work-step-content"><div class="work-step-content-body">{{ String(step.content ?? '').trim() || '未填写步骤内容' }}</div></section><section class="work-parameter-section"><h3>工艺参数</h3><div v-if="step.parameters.length" class="parameter-list"><label v-for="p in step.parameters" :key="p.index" class="parameter-row"><div class="parameter-info"><div class="parameter-top"><span class="parameter-index">P{{ p.index }}</span><span class="parameter-type">{{ parameterTypeLabel(p) }}</span></div><strong class="parameter-name">{{ p.name }}</strong></div><div class="parameter-control"><span class="parameter-ref">{{ parameterReferenceText(p) }}</span><el-select v-if="Number(p.type) === 1" v-model="values[p.index]" :disabled="!editable" :class="{ 'is-invalid': parameterInvalid(p) }" :aria-invalid="parameterInvalid(p)" placeholder="请选择" @blur="markParameterTouched(p.index)"><el-option v-for="option in p.options" :key="option" :label="option" :value="option" /></el-select><input v-else v-model="values[p.index]" :disabled="!editable" :class="{ 'is-invalid': parameterInvalid(p) }" :aria-invalid="parameterInvalid(p)" placeholder="请输入" @blur="markParameterTouched(p.index)" /></div></label></div><div v-else class="empty-parameters"><DocumentChecked /><strong>本步骤无需填写参数</strong><span>{{ editable ? '核对作业内容后可保存本步骤' : '当前为查看模式' }}</span></div></section></section></div>
    </div>
    <footer class="work-footer"><div class="footer-actions"><button class="primary-button" :disabled="stationBlocked || store.stationConfirmed" @click="save">{{ editable ? '保存并继续' : '下一步' }} <ArrowRight /></button></div></footer>
  </main>
  <main v-else class="empty-screen"><strong>尚无进行中的任务</strong><button class="primary-button" @click="router.push('/op/select')">选择工艺</button></main>
</template>
