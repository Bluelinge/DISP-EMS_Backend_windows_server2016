<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useMesStore } from '../store'
import { api, apiMode, ApiError } from '../api/request'
import { clearSavedProjectorScreen, findSavedProjectorScreen, getProjectorScreens, getSavedProjectorScreen, getWindowManagementPermission, openProjectorWindow, saveProjectorScreen } from '../utils/projectorWindow'

const router = useRouter()
const store = useMesStore()
const productInput = ref(null)
const scanning = ref(false)
const scanCompleted = ref(false)
const selectedProcessId = ref('')
const projectorScreens = ref([])
const selectedProjectorScreenKey = ref('')
const projectorDialogVisible = ref(false)
const pendingStart = ref(false)
const projectorStatus = ref('checking')
let workstationRefreshTimer = null
const station = computed(() => store.currentStation)
const manual = computed(() => station.value.captureMode === 'manual')
const recognized = computed(() => !!String(store.state.productCode || '').trim())
const availableProcesses = computed(() => store.state.processFiles.filter(file => !store.state.deleted.processes.includes(file.id) && (file.steps || []).some(step => Number(step.stationId || 1) === station.value.id)))
const matchedProcesses = computed(() => !recognized.value ? [] : (store.state.selectedModel ? availableProcesses.value.filter(file => file.model === store.state.selectedModel) : availableProcesses.value))
const readyToStart = computed(() => recognized.value && !!store.state.selectedModel && !!store.state.steelNo && !!selectedProcessId.value)
const projectorCandidates = computed(() => projectorScreens.value.filter(screen => !screen.isCurrent))
const selectedProjectorScreen = computed(() => projectorCandidates.value.find(screen => screen.key === selectedProjectorScreenKey.value) || null)
const projectorStatusText = computed(() => {
  if (selectedProjectorScreen.value) return `投影屏：${selectedProjectorScreen.value.name}`
  if (getSavedProjectorScreen() && projectorStatus.value === 'ready') return '投影屏已断开，需重新设置'
  if (projectorStatus.value === 'unsupported') return '当前浏览器不支持副屏投影'
  if (projectorStatus.value === 'denied') return '未授权管理多屏窗口'
  return '未设置投影屏'
})

function restoreProcess() {
  const remembered = store.rememberedProcess(store.state.selectedModel)
  const selection = matchedProcesses.value.some(file => file.id === remembered) ? remembered : (matchedProcesses.value.length === 1 ? matchedProcesses.value[0].id : '')
  selectedProcessId.value = selection
  if (selection) store.activateProcess(selection)
}
watch([matchedProcesses, () => store.state.selectedModel, () => station.value.id], restoreProcess, { immediate: true })
function focusProduct() { nextTick(() => productInput.value?.focus()) }
async function refreshProjectorScreens() {
  const permission = await getWindowManagementPermission()
  if (permission !== 'granted') { projectorStatus.value = permission; return { status: permission, screens: [] } }
  const result = await getProjectorScreens()
  projectorStatus.value = result.status
  projectorScreens.value = result.screens || []
  const saved = findSavedProjectorScreen(projectorScreens.value)
  if (saved && !saved.isCurrent) selectedProjectorScreenKey.value = saved.key
  else if (getSavedProjectorScreen()) clearSavedProjectorScreen()
  return result
}
function workstationFromApi(item) {
  return {
    ...item,
    id: Number(item.id),
    stationNo: Number(item.stationNo),
    captureMode: Number(item.captureMode) === 0 ? 'manual' : 'auto',
    rowVersion: Number(item.rowVersion),
  }
}
async function refreshWorkstations({ silent = false } = {}) {
  try {
    const list = await api.workstations()
    const workstations = (Array.isArray(list) ? list : []).map(workstationFromApi)
    if (!workstations.length) return
    store.state.workstations = workstations
    if (!workstations.some(item => item.id === Number(store.state.selectedStationId))) {
      store.state.selectedStationId = workstations[0].id
    }
  } catch (error) {
    if (!silent) throw error
  }
}
onMounted(async () => {
  if (apiMode) {
    try {
      const [processes, templates, videoPage] = await Promise.all([api.processes(), api.templates(), api.videos({ page: 1, pageSize: 100 }), refreshWorkstations()])
      store.state.templates = (templates || []).map(item => ({ ...item, id: String(item.id), isDefault: Number(item.isDefault) === 1 || item.isDefault === true, elements: Array.isArray(item.elements) ? item.elements : [], rowVersion: Number(item.rowVersion) }))
      store.state.videos = (videoPage?.items || []).map(item => ({ ...item, id: String(item.id), categoryId: String(item.categoryId), sessionPreview: false }))
      store.replaceServerProcesses(processes)
      workstationRefreshTimer = window.setInterval(() => refreshWorkstations({ silent: true }), 5000)
    } catch (error) { ElMessage.error(error instanceof ApiError ? error.message : '无法加载已发布工艺') }
  }
  focusProduct(); refreshProjectorScreens()
})
onBeforeUnmount(() => {
  if (workstationRefreshTimer) window.clearInterval(workstationRefreshTimer)
})
async function recognize() {
  const code = String(store.state.productCode || '').trim()
  if (!code) { ElMessage.warning('请先输入或扫描产品编码'); return focusProduct() }
  scanCompleted.value = false
  scanning.value = true
  window.setTimeout(async () => {
    scanning.value = false
    if (manual.value) {
      store.state.selectedModel = ''
      store.state.steelNo = ''
      scanCompleted.value = true
      ElMessage.success('扫码成功，请选择产品型号并填写钢印编号')
      return
    }
    try {
      // 后端是工位交接的唯一数据源：刷新或换到另一台电脑后仍能继续同一在制任务。
      const binding = apiMode ? await api.taskBinding(code) : store.applyBinding(code)
      if (!binding) {
        store.state.selectedModel = ''
        store.state.steelNo = ''
        ElMessage.warning('未找到该产品编码的进行中任务，请由手动录入工位先建立信息')
        return
      }
      store.state.selectedModel = binding.model
      store.state.steelNo = binding.steelNo
      if (apiMode) store.saveBinding({ ...binding, taskId: binding.taskId })
      scanCompleted.value = true
      ElMessage.success(`已读取最新绑定：${binding.model} / ${binding.steelNo}`)
    } catch (error) {
      store.state.selectedModel = ''
      store.state.steelNo = ''
      ElMessage.error(error instanceof ApiError ? error.message : '读取产品绑定失败，请稍后重试')
    }
  }, 180)
}
function resetScan() { scanCompleted.value = false; store.state.productCode = ''; store.state.selectedModel = ''; store.state.steelNo = ''; selectedProcessId.value = ''; focusProduct() }
function selectProcess(id) { selectedProcessId.value = id; store.activateProcess(id); store.rememberProcess(id) }
function validateStart() {
  if (!recognized.value) { ElMessage.warning('请先扫码识别产品'); return false }
  if (!store.state.selectedModel || !store.state.steelNo) { ElMessage.warning(manual.value ? '请选择产品型号并填写钢印编号' : '请先读取产品绑定信息'); return false }
  const selected = matchedProcesses.value.find(file => file.id === selectedProcessId.value)
  if (!selected) { ElMessage.warning('请选择当前工位可执行的工艺文件'); return false }
  store.activateProcess(selected.id)
  store.rememberProcess(selected.id)
  return true
}
async function startTaskWithProjector(screen = null) {
  let id
  try {
    if (apiMode) {
      const processSnapshot = store.state.processSteps.map(step => ({
        ...step,
        stationId: Number(step.stationId || 1),
        stationName: store.state.workstations.find(item => Number(item.id) === Number(step.stationId || 1))?.name || `工位${store.stationNoForId(step.stationId || 1)}`,
      }))
      const task = await api.createTask({ productCode: store.state.productCode, model: store.state.selectedModel, steelNo: store.state.steelNo, processSnapshot })
      store.adoptServerTask(task)
      id = task.id
    } else id = store.startTask()
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : '创建任务失败，请稍后重试')
    return
  }
  if (id) {
    const result = openProjectorWindow(id, screen)
    if (result.status === 'blocked') ElMessage.warning('投影窗口被浏览器拦截，可在作业页点击“打开投影窗口”重试')
    else if (result.status === 'fallback') ElMessage.info('未使用副屏投影，已按普通投影窗口打开')
    router.push(`/op/work/${id}`)
  }
  else ElMessage.warning('当前产品无法开始作业，请确认工艺和绑定信息')
}
async function openProjectorSettings(startAfter = false) {
  pendingStart.value = startAfter
  const result = await getProjectorScreens()
  projectorStatus.value = result.status
  projectorScreens.value = result.screens || []
  if (result.status !== 'ready') {
    if (startAfter) startTaskWithProjector()
    else ElMessage.warning(result.status === 'denied' ? '未授权管理多屏窗口，无法设置副屏投影' : '当前浏览器不支持副屏投影设置')
    return
  }
  if (!projectorCandidates.value.length) {
    if (startAfter) startTaskWithProjector()
    else ElMessage.warning('未检测到可用副屏，请连接扩展显示器后重试')
    return
  }
  const saved = findSavedProjectorScreen(projectorScreens.value)
  selectedProjectorScreenKey.value = saved && !saved.isCurrent ? saved.key : projectorCandidates.value[0].key
  projectorDialogVisible.value = true
}
function confirmProjectorScreen() {
  const screen = selectedProjectorScreen.value
  if (!screen) return ElMessage.warning('请选择一块副屏')
  saveProjectorScreen(screen)
  projectorDialogVisible.value = false
  projectorStatus.value = 'ready'
  if (pendingStart.value) {
    pendingStart.value = false
    startTaskWithProjector(screen)
  } else ElMessage.success(`已将 ${screen.name} 设为投影屏`)
}
function start() {
  if (!validateStart()) return
  if (projectorStatus.value === 'ready' && selectedProjectorScreen.value) {
    startTaskWithProjector(selectedProjectorScreen.value)
    return
  }
  if (['unsupported', 'denied', 'unavailable'].includes(projectorStatus.value)) {
    startTaskWithProjector()
    return
  }
  openProjectorSettings(true)
}
</script>

<template>
  <main class="select-page atmosphere">
    <div class="select-heading"><div><h1>开始一项新的装配任务</h1><p>{{ station.name }} · {{ manual ? '扫码后手动填写产品型号与钢印编号' : '扫码后自动读取最新绑定信息' }}</p></div><div class="projector-setting"><span>{{ projectorStatusText }}</span><button class="ghost-button" @click="openProjectorSettings(false)"><Monitor /> 投影屏设置</button></div></div>
    <div class="select-grid">
      <section class="panel scan-panel">
        <div class="panel-head"><div><h2>产品识别</h2></div><span class="panel-meta">{{ station.name }} / {{ manual ? '手动录入' : '自动获取' }}</span></div>
        <div class="scan-zone" :class="{ recognized: scanCompleted, scanning }" @click="scanCompleted ? resetScan() : focusProduct()"><div v-if="!scanCompleted" class="scan-motion" aria-hidden="true"><div class="scan-motion-track"><div class="scan-motion-visual"><img class="scan-motion-box" src="/scan-assets/box.png" alt="" /><img class="scan-motion-line" src="/scan-assets/line.png" alt="" /></div></div></div><div class="scan-corners"><span></span><span></span><span></span><span></span></div><Aim v-if="!scanCompleted" class="scan-symbol" /><CircleCheck v-else class="scan-symbol check" /><span class="scan-caption">{{ scanning ? '正在识别产品…' : scanCompleted ? '识别成功 · 产品编码' : '扫码枪输入后按 Enter 自动识别' }}</span><strong v-if="scanCompleted" class="scan-code">{{ store.state.productCode }}</strong><strong v-else class="scan-placeholder">等待扫码</strong><span class="scan-action">{{ scanCompleted ? '点击重新扫码' : '产品编码输入框已就绪' }}</span></div>
        <div class="scan-fields">
          <label><span>产品编码</span><input ref="productInput" v-model="store.state.productCode" aria-label="产品编码" placeholder="扫码或手动录入产品编码" @keyup.enter="recognize" /></label>
          <label><span>产品型号</span><el-select v-model="store.state.selectedModel" :disabled="!manual" placeholder="请选择产品型号"><el-option v-for="model in store.productModels" :key="model" :label="model" :value="model" /></el-select></label>
          <label><span>钢印编号</span><input v-model="store.state.steelNo" :readonly="!manual" :placeholder="manual ? '请输入钢印编号' : '扫码后自动读取'" /></label>
        </div>
      </section>
      <section class="panel process-panel">
        <div class="panel-head"><div><h2>工艺匹配</h2></div><span class="panel-meta">{{ store.state.selectedModel ? `型号：${store.state.selectedModel}` : '当前工位全部工艺' }}</span></div>
        <div class="match-banner" :class="{ muted: !recognized || !matchedProcesses.length }"><span class="status-dot" :class="matchedProcesses.length ? 'success' : 'warning'"></span>{{ !recognized ? '扫码后显示当前工位可用工艺' : matchedProcesses.length ? `当前工位可操作 ${matchedProcesses.length} 份工艺` : '当前工位没有可执行的匹配工艺' }}</div>
        <div v-if="matchedProcesses.length" class="process-option-list"><button v-for="file in matchedProcesses" :key="file.id" class="process-option" :class="{ selected: selectedProcessId===file.id }" @click="selectProcess(file.id)"><div class="process-option-top"><span class="process-code">{{ file.model }}</span><span v-if="selectedProcessId===file.id" class="selected-label">已选择</span></div><h3>{{ file.title }}</h3><p>{{ file.sourceDocument }}</p><div class="process-facts"><span><Files /> {{ file.steps.filter(step=>Number(step.stationId||1)===station.id).length }} 道本工位工序</span><span><Star /> {{ file.steps.filter(step=>step.importance).length }} 道重要工序</span></div></button></div>
        <div v-else class="empty-parameters"><DocumentChecked /><strong>{{ recognized ? '暂无可用工艺' : '等待产品识别' }}</strong><span>未选择型号时列出本工位全部工艺；选择型号后按型号精确筛选。</span></div>
        <div class="process-note"><InfoFilled /><span>开始任务后会冻结工艺快照；后续工位只可录入各自所属步骤。</span></div>
      </section>
    </div>
    <footer class="select-footer"><button class="primary-button" :disabled="!readyToStart" @click="start">开始作业 <ArrowRight /></button></footer>
    <el-dialog v-model="projectorDialogVisible" title="设置投影屏" width="520px" :close-on-click-modal="false" @closed="pendingStart=false">
      <p class="projector-dialog-note">选择用于投影的副屏。设置会仅保存在当前浏览器；开始作业后投影窗口将自动铺满该屏幕的可用工作区。</p>
      <el-radio-group v-model="selectedProjectorScreenKey" class="projector-screen-options">
        <el-radio v-for="screen in projectorCandidates" :key="screen.key" :value="screen.key" class="projector-screen-option"><span><strong>{{ screen.name }}</strong><small>{{ screen.width }} × {{ screen.height }} · 副屏</small></span></el-radio>
      </el-radio-group>
      <template #footer><button class="ghost-button" @click="projectorDialogVisible=false">取消</button><button class="primary-button" @click="confirmProjectorScreen">保存并继续</button></template>
    </el-dialog>
  </main>
</template>
