import { defineStore } from 'pinia'
import { computed, ref, watch } from 'vue'
import processSource from './data/process.json'

const KEY = 'disp-mes-ui-prototype-v2'
const iconNames = ['大屏', '图片', '文书', '用户切换', '退出']
const productModels = ['DISPEN9148-40', 'DISPEN9241-40', 'DISPEN9242-40', 'DISPEN9243-40']
const clone = value => JSON.parse(JSON.stringify(value))
const stationId = value => Number(value) || 1
const normalizeParameters = parameters => (Array.isArray(parameters) ? parameters : []).slice(0, 3).map((parameter, index) => {
  const candidateType = Number(parameter.type)
  const type = [0, 1, 2].includes(candidateType) ? candidateType : 0
  const options = type === 1
    ? (Array.isArray(parameter.options) ? parameter.options : String(parameter.options || parameter.referenceValue || '').split(/\r?\n/)).map(item => String(item).trim()).filter(Boolean)
    : []
  return { ...parameter, index: index + 1, type, referenceValue: String(parameter.referenceValue || '').trim(), options, sourceTypeMissing: !!parameter.sourceTypeMissing }
})
// 旧版本地缓存和服务端工艺都可能存在 parameters 为空字符串的历史数据。
// 归一化函数放在 store 作用域，供缓存恢复与服务端工艺同步共同使用。
const normalizeSteps = steps => (Array.isArray(steps) ? steps : []).map(step => ({ ...step, content: String(step.content ?? ''), stationId: stationId(step.stationId), parameters: normalizeParameters(step.parameters) }))
const processSteps = () => clone(processSource.steps).map(step => {
  const no = step.stepNo
  const id = no <= 6 ? 1 : no <= 9 ? 2 : no <= 11 ? 3 : no <= 19 ? 4 : no <= 25 ? 5 : no <= 32 ? 6 : 7
  return { ...step, stationId: id }
})
const initialWorkstations = () => Array.from({ length: 7 }, (_, index) => ({ id: index + 1, stationNo: index + 1, name: `工位${index + 1}`, captureMode: index === 0 ? 'manual' : 'auto' }))
const initialVideoCategories = () => [
  { id: 'VC-DAMPER', name: '油压减振器', parentId: null, sortOrder: 1 },
  { id: 'VC-9243', name: 'DISPEN9243-40', parentId: 'VC-DAMPER', sortOrder: 1 },
  { id: 'VC-9242', name: 'DISPEN9242-40', parentId: 'VC-DAMPER', sortOrder: 2 },
  { id: 'VC-9241', name: 'DISPEN9241-40', parentId: 'VC-DAMPER', sortOrder: 3 },
  { id: 'VC-9148', name: 'DISPEN9148-40', parentId: 'VC-DAMPER', sortOrder: 4 },
  { id: 'VC-GENERAL', name: '通用', parentId: 'VC-DAMPER', sortOrder: 5 },
  { id: 'VC-OTHER', name: '其他', parentId: null, sortOrder: 2 },
]
const legacyVideoCategoryId = category => category === '其他' ? 'VC-OTHER' : 'VC-GENERAL'

const initialTemplates = () => [
  { id: 'T-001', name: '默认工序引导', isDefault: true, width: 1920, height: 1080, description: '系统默认模板，所有导入工艺步骤自动绑定', elements: [
    { id: 'E-01', type: 'icon', iconName: '文书', x: 190, y: 300, scale: 1, rotation: 0, blink: false, zIndex: 1 },
    { id: 'E-02', type: 'text', text: '{{stepNo}}', bindField: 'stepNo', x: 460, y: 286, fontSize: 80, color: '#59C2DA', zIndex: 2 },
    { id: 'E-03', type: 'text', text: '{{stepTitle}}', bindField: 'stepTitle', x: 460, y: 410, fontSize: 60, color: '#EFF2F0', zIndex: 2 },
    { id: 'E-04', type: 'text', text: '{{model}} · {{progress}}', bindField: 'none', x: 460, y: 545, fontSize: 30, color: '#94A3B8', zIndex: 2 }
  ] },
  { id: 'T-002', name: '参数点检提示', isDefault: false, width: 1920, height: 1080, description: '关键参数与点检结果突出显示', elements: [] }
]
const projectionForSteps = steps => Object.fromEntries(steps.map(step => [step.stepNo, { templateId: 'T-001', elements: [], inheritsTemplate: true }]))
const initialProcessFile = () => {
  const steps = processSteps()
  return { id: 'P-001', model: processSource.model, title: '油压减振器总组装作业', version: 'v1', sourceWorkbook: processSource.sourceWorkbook, sourceDocument: processSource.sourceDocument, steps, stepProjection: projectionForSteps(steps), isDemo: true }
}
const resolveTaskLiveStationId = (task, processFiles = [], workstations = []) => {
  const snapshot = task?.processSnapshot?.length ? task.processSnapshot : processFiles.find(file => file.id === task?.processId)?.steps || []
  const stationNumber = id => Number(workstations.find(item => Number(item.id) === Number(id))?.stationNo || id) || 1
  const stationIds = [...new Set(snapshot.map(step => stationId(step.stationId)))].sort((left, right) => stationNumber(left) - stationNumber(right))
  if (!stationIds.length) return null
  return stationIds.find(id => !task.stationConfirmations?.[id]) || stationIds.at(-1)
}

function initialState() {
  const processFile = initialProcessFile()
  return {
    role: null, currentUserId: null, currentUserRoleCode: null, operatorName: '', selectedStationId: 1,
    productCode: '', selectedModel: '', steelNo: '', task: null, activeTaskId: null, inProgressTasks: {}, taskHistory: [], productBindings: {}, steelBindings: {}, stationProcessSelections: {},
    workstations: initialWorkstations(),
    processRecord: { id: processFile.id, model: processFile.model, title: processFile.title, version: processFile.version, sourceWorkbook: processFile.sourceWorkbook, sourceDocument: processFile.sourceDocument },
    processSteps: clone(processFile.steps), processFiles: [processFile], deleted: { processes: [], icons: [], videos: [], templates: [], dicts: [], people: [] }, customIcons: [], templates: initialTemplates(), stepProjection: clone(processFile.stepProjection),
    videoCategories: initialVideoCategories(),
    videos: [{ id: 'V-001', name: '减振器总成装配示范.mp4', categoryId: 'VC-GENERAL', duration: '08:24', batch: 'B-20260914-01', sessionPreview: false }],
    people: [{ id: 'U-001', no: 'OP-018', name: '李工', role: '操作员', dept: '总装车间', phone: '138****2018', status: '在岗', password: '123456' }, { id: 'U-002', no: 'ADMIN-001', name: '王工', role: '超级管理员', dept: '生产管理部', phone: '136****0001', status: '在岗', password: '123456' }, { id: 'U-003', no: 'MA-001', name: '张主任', role: '管理员', dept: '生产管理部', phone: '136****0002', status: '在岗', password: '000000' }],
    dicts: [{ type: 'user_role', code: 0, name: '操作员', status: '启用' }, { type: 'user_role', code: 1, name: '管理员', status: '启用' }]
  }
}

function loadState() {
  try {
    const saved = JSON.parse(localStorage.getItem(KEY) || 'null')
    const fresh = initialState()
    if (!saved) return fresh
    const { workstationIdSequence: _legacyWorkstationIdSequence, ...savedState } = saved
    const productBindings = saved.productBindings || {}
    const steelBindings = saved.steelBindings || Object.fromEntries(Object.values(productBindings).filter(binding => binding?.steelNo).map(binding => [binding.steelNo, { ...binding }]))
    const inProgressTasks = saved.inProgressTasks || (saved.task?.status === '进行中' ? { [saved.task.id]: saved.task } : {})
    const activeTaskId = inProgressTasks[saved.activeTaskId] ? saved.activeTaskId : saved.task?.status === '进行中' && inProgressTasks[saved.task.id] ? saved.task.id : null
    const task = activeTaskId ? inProgressTasks[activeTaskId] : saved.task?.status === '已完成' ? saved.task : null
    const workstations = (saved.workstations?.length ? saved.workstations : fresh.workstations).map((item, index) => ({
      id: Number.isInteger(Number(item.id)) && Number(item.id) > 0 ? Number(item.id) : index + 1,
      stationNo: Number.isInteger(Number(item.stationNo)) && Number(item.stationNo) > 0 ? Number(item.stationNo) : index + 1,
      name: item.name || `工位${index + 1}`,
      captureMode: item.captureMode === 'manual' ? 'manual' : 'auto',
      rowVersion: Number.isInteger(Number(item.rowVersion)) ? Number(item.rowVersion) : undefined,
    })).sort((a, b) => a.stationNo - b.stationNo || a.id - b.id)
    const videoCategories = Array.isArray(saved.videoCategories) && saved.videoCategories.length
      ? saved.videoCategories.map((item, index) => ({ id: String(item.id), name: String(item.name || '').trim(), parentId: item.parentId || null, sortOrder: Number(item.sortOrder) || index + 1 })).filter(item => item.id && item.name)
      : fresh.videoCategories
    const categoryIds = new Set(videoCategories.map(item => item.id))
    const videos = (saved.videos?.length ? saved.videos : fresh.videos).map(video => ({ ...video, categoryId: categoryIds.has(video.categoryId) ? video.categoryId : legacyVideoCategoryId(video.category) }))
    return {
      ...fresh, ...savedState,
      processFiles: (saved.processFiles?.length ? saved.processFiles : fresh.processFiles).map(file => ({ ...file, steps: normalizeSteps(file.steps) })),
      processSteps: normalizeSteps(saved.processSteps || fresh.processSteps),
      workstations,
      task, activeTaskId, inProgressTasks, productBindings, steelBindings, stationProcessSelections: saved.stationProcessSelections || {}, deleted: { ...fresh.deleted, ...saved.deleted },
      templates: (saved.templates || fresh.templates).map(template => ({ ...template, isDefault: template.id === 'T-001' || !!template.isDefault })),
      people: (saved.people || fresh.people).map(person => ({ ...person, phone: person.phone || '', password: person.password || '123456' })),
      videoCategories, videos
    }
  } catch { return initialState() }
}

export const useMesStore = defineStore('mes', () => {
  const state = ref(loadState())
  const steps = computed(() => state.value.processSteps)
  const activeSteps = computed(() => state.value.task?.processSnapshot || state.value.processSteps)
  const currentStep = computed(() => state.value.task ? activeSteps.value[state.value.task.currentStepIndex] : null)
  const currentStation = computed(() => state.value.workstations.find(item => item.id === state.value.selectedStationId) || state.value.workstations[0])
  const stationNoForId = id => Number(state.value.workstations.find(item => Number(item.id) === Number(id))?.stationNo || id) || 1
  const processStationIds = computed(() => [...new Set(activeSteps.value.map(step => stationId(step.stationId)))])
  const stationSteps = computed(() => activeSteps.value.filter(step => stationId(step.stationId) === currentStation.value?.id))
  const stationConfirmed = computed(() => !!state.value.task?.stationConfirmations?.[currentStation.value?.id])
  const previousStationsConfirmed = computed(() => processStationIds.value
    .filter(id => stationNoForId(id) < stationNoForId(currentStation.value?.id))
    .every(id => !!state.value.task?.stationConfirmations?.[id]))
  const finalStationId = computed(() => [...processStationIds.value].sort((left, right) => stationNoForId(left) - stationNoForId(right)).at(-1) || 0)
  const isFinalStation = computed(() => !!state.value.task && currentStation.value?.id === finalStationId.value)
  const stationSummaryReady = computed(() => {
    const task = state.value.task
    return !!task && stationSteps.value.length > 0 && stationSteps.value.every(step => !!task.logs?.[step.stepNo])
  })
  const finalSummaryPending = computed(() => !!state.value.task && state.value.task.status === '进行中' && isFinalStation.value && stationConfirmed.value)
  const canOpenParameterSummary = computed(() => stationSummaryReady.value && previousStationsConfirmed.value && (!stationConfirmed.value || finalSummaryPending.value))
  const nextStationStep = computed(() => stationSteps.value.find(step => state.value.task && !state.value.task.logs?.[step.stepNo]) || null)
  const liveStationTasks = computed(() => {
    const tasks = Object.values(state.value.inProgressTasks || {})
    const current = state.value.task
    if (current?.status === '进行中' && !tasks.some(task => task.id === current.id)) tasks.push(current)
    return tasks.filter(task => task.status === '进行中').map(task => ({ ...task, liveStationId: resolveTaskLiveStationId(task, state.value.processFiles, state.value.workstations) })).filter(task => task.liveStationId)
  })
  const completedLogs = computed(() => Object.values(state.value.task?.logs || {}).filter(log => log.actionCode === 0).sort((a, b) => a.stepNo - b.stepNo))
  const auditLogs = computed(() => Object.values(state.value.task?.logs || {}).filter(log => log.actionCode > 0).sort((a, b) => a.stepNo - b.stepNo))
  const sourceMissingCount = computed(() => steps.value.flatMap(step => step.parameters || []).filter(parameter => parameter.sourceTypeMissing).length)
  const processAvailable = computed(() => !!state.value.processRecord?.id && !state.value.deleted.processes.includes(state.value.processRecord.id))
  const defaultTemplate = computed(() => state.value.templates.find(template => template.isDefault && !state.value.deleted.templates.includes(template.id)))
  // 继承模板的步骤始终读取模板最新元素；单独编辑过的步骤使用自己的元素快照。
  function projectionElements(stepNo, projections = state.value.stepProjection) {
    const projection = projections?.[stepNo] || projections?.[String(stepNo)]
    const template = state.value.templates.find(item => String(item.id) === String(projection?.templateId))
    if (projection?.inheritsTemplate && template) return template.elements || []
    return projection?.elements || []
  }

  function persist() { localStorage.setItem(KEY, JSON.stringify(state.value)) }
  watch(state, persist, { deep: true })
  window.addEventListener('storage', event => { if (event.key === KEY && event.newValue) state.value = loadState() })
  function reset() { state.value = initialState() }
  function syncFromStorage() { state.value = loadState() }
  function selectWorkstation(id) { const found = state.value.workstations.find(item => item.id === Number(id)); if (found) state.value.selectedStationId = found.id }
  function updateWorkstation(id, value) { const item = state.value.workstations.find(entry => entry.id === Number(id)); if (!item) return false; item.name = String(value.name || item.name).trim() || item.name; item.captureMode = value.captureMode === 'manual' ? 'manual' : 'auto'; return true }
  function addWorkstation() { const usedNumbers = new Set(state.value.workstations.map(item => Number(item.stationNo || item.id))); let stationNo = 1; while (usedNumbers.has(stationNo)) stationNo += 1; const workstation = { id: stationNo, stationNo, name: `工位${stationNo}`, captureMode: 'auto' }; state.value.workstations.push(workstation); state.value.workstations.sort((a, b) => Number(a.stationNo || a.id) - Number(b.stationNo || b.id)); return workstation }
  function deleteWorkstation(id) {
    const stationIdToDelete = Number(id), workstation = state.value.workstations.find(item => item.id === stationIdToDelete)
    if (!workstation) return { ok: false, message: '工位不存在' }
    if (stationIdToDelete === 1) return { ok: false, message: '工位 1 是默认归属工位，不能删除' }
    const runningTasks = [...Object.values(state.value.inProgressTasks || {}), ...(state.value.task?.status === '进行中' ? [state.value.task] : [])]
    if (runningTasks.some(task => (task.processSnapshot || []).some(step => stationId(step.stationId) === stationIdToDelete))) return { ok: false, message: '有在制任务的冻结工艺引用该工位，完成任务后才能删除' }
    let reassignedCount = 0
    const reassignSteps = steps => (steps || []).map(step => {
      if (stationId(step.stationId) !== stationIdToDelete) return step
      reassignedCount += 1
      return { ...step, stationId: 1 }
    })
    state.value.processSteps = reassignSteps(state.value.processSteps)
    state.value.processFiles = state.value.processFiles.map(file => ({ ...file, steps: reassignSteps(file.steps) }))
    state.value.workstations = state.value.workstations.filter(item => item.id !== stationIdToDelete)
    Object.keys(state.value.stationProcessSelections).filter(key => key.startsWith(`${stationIdToDelete}:`)).forEach(key => delete state.value.stationProcessSelections[key])
    if (state.value.selectedStationId === stationIdToDelete) state.value.selectedStationId = 1
    return { ok: true, reassignedCount }
  }
  function selectTask(id) { const task = state.value.inProgressTasks[String(id)] || state.value.taskHistory.find(item => item.id === String(id)) || (state.value.task?.id === String(id) ? state.value.task : null); if (!task) return false; state.value.task = task; state.value.activeTaskId = task.status === '进行中' ? task.id : null; return true }
  // 后端返回的是冻结任务快照；这一个入口供操作端、SSE 和管理端统一写回 Pinia。
  function adoptServerTask(task) {
    if (!task?.id) return false
    const normalized = { ...task, id: String(task.id), processId: task.processId || state.value.processRecord?.id }
    if (normalized.status === '已完成') {
      const index = state.value.taskHistory.findIndex(item => String(item.id) === normalized.id)
      if (index >= 0) state.value.taskHistory[index] = normalized
      else state.value.taskHistory.unshift(normalized)
      delete state.value.inProgressTasks[normalized.id]
      state.value.activeTaskId = null
    } else {
      state.value.inProgressTasks[normalized.id] = normalized
      state.value.activeTaskId = normalized.id
    }
    state.value.task = normalized
    return true
  }
  function replaceServerProcess(process) {
    if (!process?.id) return false
    const item = { ...process, id: String(process.id), steps: normalizeSteps(process.steps || []), stepProjection: process.stepProjection || projectionForSteps(process.steps || []) }
    const index = state.value.processFiles.findIndex(file => String(file.id) === item.id || file.model === item.model)
    if (index >= 0) state.value.processFiles[index] = item
    else state.value.processFiles.push(item)
    state.value.deleted.processes = state.value.deleted.processes.filter(id => String(id) !== item.id)
    activateProcess(item.id)
    return true
  }
  function replaceServerProcesses(processes) {
    if (!Array.isArray(processes)) return false
    state.value.processFiles = processes.map(process => ({ ...process, id: String(process.id), steps: normalizeSteps(process.steps || []), stepProjection: process.stepProjection || projectionForSteps(process.steps || []) }))
    state.value.deleted.processes = []
    const current = state.value.processFiles.find(file => String(file.id) === String(state.value.processRecord?.id)) || state.value.processFiles[0]
    if (current) activateProcess(current.id)
    return true
  }
  function getBinding(productCode) { return state.value.productBindings[String(productCode || '').trim()] || null }
  function getSteelBinding(steelNo) { return state.value.steelBindings[String(steelNo || '').trim()] || null }
  function applyBinding(productCode) { const binding = getBinding(productCode); if (!binding) return null; state.value.selectedModel = binding.model; state.value.steelNo = binding.steelNo; return binding }
  function saveBinding({ productCode, model, steelNo, taskId }) { const code = String(productCode || '').trim(), steel = String(steelNo || '').trim(); if (!code || !model || !steel) return null; const record = { productCode: code, model, steelNo: steel, taskId: taskId || getSteelBinding(steel)?.taskId || getBinding(code)?.taskId || null, updatedAt: new Date().toISOString(), updatedByStationId: currentStation.value.id }; state.value.productBindings[code] = record; state.value.steelBindings[steel] = record; return record }
  function replaceTaskProductCode(task, nextProductCode) { const next = String(nextProductCode || '').trim(); if (!task || !next) return false; const previous = task.productCode; if (previous !== next) { task.productCodeAudit = task.productCodeAudit || []; task.productCodeAudit.push({ previousProductCode: previous, productCode: next, changedAt: new Date().toISOString(), stationId: currentStation.value.id, operatorName: state.value.operatorName }); if (state.value.productBindings[previous]?.taskId === task.id) delete state.value.productBindings[previous]; task.productCode = next } saveBinding({ productCode: task.productCode, model: task.model, steelNo: task.steelNo, taskId: task.id }); return true }
  function syncActiveProcess() { const index = state.value.processFiles.findIndex(file => file.id === state.value.processRecord.id); if (index >= 0) state.value.processFiles[index] = { ...state.value.processFiles[index], ...clone(state.value.processRecord), steps: clone(state.value.processSteps), stepProjection: clone(state.value.stepProjection) } }
  function activateProcess(id) { const file = state.value.processFiles.find(item => item.id === id && !state.value.deleted.processes.includes(item.id)); if (!file) return false; state.value.processRecord = { id: file.id, model: file.model, title: file.title, version: file.version, sourceWorkbook: file.sourceWorkbook, sourceDocument: file.sourceDocument }; state.value.processSteps = clone(file.steps || []); state.value.stepProjection = clone(file.stepProjection || projectionForSteps(file.steps || [])); return true }
  function rememberProcess(id, model = state.value.selectedModel) { if (id && model) state.value.stationProcessSelections[`${currentStation.value.id}:${model}`] = id }
  function rememberedProcess(model = state.value.selectedModel) { return state.value.stationProcessSelections[`${currentStation.value.id}:${model}`] || '' }
  function importProcess(payload) { const existing = state.value.processFiles.find(file => file.model === payload.model); const id = existing?.id || `P-${Date.now().toString().slice(-8)}`, workstationsById = new Map(state.value.workstations.map(item => [Number(item.id), item])), workstationsByNo = new Map(state.value.workstations.map(item => [Number(item.stationNo || item.id), item])), defaultStationId = Number(workstationsByNo.get(1)?.id || 1); const importedSteps = (payload.steps || []).map(step => { const requestedStation = stationId(step.stationId), workstation = workstationsById.get(requestedStation) || workstationsByNo.get(requestedStation); return { ...clone(step), stationId: Number(workstation?.id || defaultStationId), parameters: normalizeParameters(step.parameters) } }); const entry = { id, model: payload.model, title: payload.title || `${payload.model} 装配作业`, version: payload.version || 'v1', sourceWorkbook: payload.sourceWorkbook, sourceDocument: payload.sourceDocument, steps: importedSteps, stepProjection: Object.fromEntries(importedSteps.map(step => [step.stepNo, { templateId: defaultTemplate.value?.id || 'T-001', elements: [], inheritsTemplate: true }])), isDemo: false, importedAt: new Date().toISOString() }; if (existing) state.value.processFiles = state.value.processFiles.map(file => file.id === id ? entry : file); else state.value.processFiles.push(entry); state.value.deleted.processes = state.value.deleted.processes.filter(processId => processId !== id); activateProcess(id); return entry }
  function login(role) { state.value.role = role; const person = state.value.people.find(item => item.status === '在岗' && item.role === (role === 'operator' ? '操作员' : '超级管理员')); state.value.operatorName = person?.name || ''; state.value.currentUserId = person?.id || ''; state.value.currentUserRoleCode = role === 'operator' ? 0 : 2 }
  function logout() { state.value.role = null; state.value.currentUserId = null; state.value.currentUserRoleCode = null; state.value.operatorName = '' }
  function loginByCredentials(no, password) { const person = state.value.people.find(item => item.no === String(no).trim() && !state.value.deleted.people.includes(item.id) && item.status === '在岗'); if (!person || person.password !== password) return null; state.value.role = person.role === '操作员' ? 'operator' : 'admin'; state.value.operatorName = person.name; state.value.currentUserId = person.id; state.value.currentUserRoleCode = person.role === '操作员' ? 0 : person.role === '超级管理员' ? 2 : 1; return state.value.role }
  function resetPersonPassword(id) { const person = state.value.people.find(item => item.id === id); if (!person) return null; const password = `MES@${person.no.replace(/\D/g, '').slice(-4).padStart(4, '0')}`; person.password = password; return password }
  function updatePersonPassword(id, password) { const person = state.value.people.find(item => item.id === id); if (!person) return false; person.password = password; return true }
  function startTask() {
    if (!processAvailable.value || !state.value.productCode || !state.value.selectedModel || !state.value.steelNo) return null
    const steelBinding = getSteelBinding(state.value.steelNo)
    const activeTask = (steelBinding?.taskId && state.value.inProgressTasks[steelBinding.taskId]) || Object.values(state.value.inProgressTasks).find(task => task.steelNo === state.value.steelNo)
    if (activeTask) { if (activeTask.model !== state.value.selectedModel) return null; state.value.task = activeTask; state.value.activeTaskId = activeTask.id; replaceTaskProductCode(activeTask, state.value.productCode); const target = nextStationStep.value; if (target) activeTask.currentStepIndex = activeSteps.value.findIndex(step => step.stepNo === target.stepNo); return activeTask.id }
    const operator = state.value.people.find(person => person.id === state.value.currentUserId)
    const processSnapshot = clone(state.value.processSteps).map(step => ({ ...step, stationName: state.value.workstations.find(workstation => workstation.id === stationId(step.stationId))?.name || `工位${stationNoForId(step.stationId)}` }))
    const task = { id: `TASK-${Date.now().toString().slice(-8)}`, productCode: state.value.productCode, steelNo: state.value.steelNo, model: state.value.selectedModel, processId: state.value.processRecord.id, operatorId: operator?.id || '', operatorName: operator?.name || state.value.operatorName, status: '进行中', currentStepIndex: 0, startedAt: new Date().toISOString(), logs: {}, projection: clone(state.value.stepProjection), processSnapshot, stationConfirmations: {}, steelNoAudit: [], productCodeAudit: [], completionForm: null }
    state.value.inProgressTasks[task.id] = task; state.value.task = task; state.value.activeTaskId = task.id; saveBinding({ ...task, taskId: task.id }); return task.id
  }
  function setViewedStep(stepNo) { const index = activeSteps.value.findIndex(step => step.stepNo === Number(stepNo)); if (state.value.task && index >= 0) state.value.task.currentStepIndex = index }
  function jumpToStep(stepNo) {
    const task = state.value.task
    const target = activeSteps.value.find(step => step.stepNo === Number(stepNo))
    if (!task || !target) return false
    if (stationId(target.stationId) !== currentStation.value.id) { setViewedStep(target.stepNo); return false }
    if (stationConfirmed.value) { setViewedStep(target.stepNo); return false }
    const targetIndex = stationSteps.value.findIndex(step => step.stepNo === target.stepNo)
    const nextIndex = stationSteps.value.findIndex(step => !task.logs[step.stepNo])
    // 允许回到本站任何已处理步骤改写参数；只有向后跳时才为中间步骤补跳过审计。
    if (nextIndex >= 0 && targetIndex > nextIndex) stationSteps.value.slice(nextIndex, targetIndex).filter(step => !task.logs[step.stepNo]).forEach(step => {
      task.logs[step.stepNo] = { stepNo: step.stepNo, title: step.title, stationId: currentStation.value.id, stationName: currentStation.value.name, actionCode: step.importance ? 2 : 1, values: {}, time: new Date().toISOString(), reason: '工位内跳转', operatorName: state.value.operatorName }
    })
    setViewedStep(target.stepNo)
    return true
  }
  function skipStationToParameterSummary() {
    const task = state.value.task
    if (!task || stationConfirmed.value || !stationSteps.value.length) return false
    const skippedAt = new Date().toISOString()
    stationSteps.value.filter(step => !task.logs?.[step.stepNo]).forEach(step => {
      task.logs[step.stepNo] = { stepNo: step.stepNo, title: step.title, stationId: currentStation.value.id, stationName: currentStation.value.name, actionCode: step.importance ? 2 : 1, values: {}, time: skippedAt, reason: '进入参数汇总', operatorName: state.value.operatorName }
    })
    const lastStep = stationSteps.value.at(-1)
    if (lastStep) setViewedStep(lastStep.stepNo)
    return true
  }
  function canEditStep(stepNo) { const step = activeSteps.value.find(item => item.stepNo === Number(stepNo)); return !!state.value.task && !!step && stationId(step.stationId) === currentStation.value.id && previousStationsConfirmed.value && !stationConfirmed.value }
  function recordStep(values = {}) { const task = state.value.task; const step = currentStep.value; if (!task || !step || !canEditStep(step.stepNo)) return { ok: false, stationComplete: false }; const previous = task.logs[step.stepNo]; task.logs[step.stepNo] = { ...previous, stepNo: step.stepNo, title: step.title, stationId: currentStation.value.id, stationName: currentStation.value.name, actionCode: 0, values: { ...values }, time: new Date().toISOString(), operatorName: state.value.operatorName }; const next = stationSteps.value.find(item => !task.logs[item.stepNo]); if (next) { setViewedStep(next.stepNo); return { ok: true, stationComplete: false } } return { ok: true, stationComplete: true, finalStation: currentStation.value.id === finalStationId.value } }
  function stationCompletionItems(station = currentStation.value.id) { return activeSteps.value.filter(step => stationId(step.stationId) === Number(station)).map(step => { const log = state.value.task?.logs?.[step.stepNo]; return { stepNo: step.stepNo, title: step.title, stationId: stationId(step.stationId), parameters: (step.parameters || []).map(parameter => ({ ...parameter, operatorValue: log?.values?.[parameter.index] ?? '', unfilled: !String(log?.values?.[parameter.index] ?? '').trim() })) } }) }
  function completionItems() { return activeSteps.value.map(step => { const log = state.value.task?.logs?.[step.stepNo]; return { stepNo: step.stepNo, title: step.title, stationId: stationId(step.stationId), parameters: (step.parameters || []).map(parameter => ({ ...parameter, operatorValue: log?.values?.[parameter.index] ?? '', unfilled: !String(log?.values?.[parameter.index] ?? '').trim() })) } }) }
  function updateCompletionValues(items) { items.forEach(item => { const log = state.value.task?.logs?.[item.stepNo]; if (log) log.values = Object.fromEntries((item.parameters || []).map(parameter => [parameter.index, parameter.operatorValue ?? ''])) }) }
  function confirmStation(items) { const task = state.value.task; if (!task) return false; updateCompletionValues(items); const id = currentStation.value.id; task.stationConfirmations[id] = { stationId: id, stationName: currentStation.value.name, confirmedAt: new Date().toISOString(), operatorName: state.value.operatorName, items: clone(items) }; return id === finalStationId.value }
  function updateTaskSteelNo(nextSteelNo) { const task = state.value.task; const next = String(nextSteelNo || '').trim(); if (!task || !next || next === task.steelNo) return false; const previous = task.steelNo; task.steelNoAudit.push({ previousSteelNo: previous, steelNo: next, changedAt: new Date().toISOString(), stationId: currentStation.value.id, stationName: currentStation.value.name, operatorName: state.value.operatorName }); if (state.value.steelBindings[previous]?.taskId === task.id) delete state.value.steelBindings[previous]; task.steelNo = next; state.value.steelNo = next; saveBinding({ productCode: task.productCode, model: task.model, steelNo: next, taskId: task.id }); return true }
  function completeTask(items) { const task = state.value.task; if (!task) return; updateCompletionValues(items); const endedAt = new Date().toISOString(); task.completionForm = { savedAt: endedAt, operatorName: state.value.operatorName, items: clone(items) }; task.status = '已完成'; task.endedAt = endedAt; task.durationSec = Math.max(1, Math.round((new Date(endedAt) - new Date(task.startedAt)) / 1000)); task.isDemo = false; const historyIndex = state.value.taskHistory.findIndex(item => item.id === task.id); if (historyIndex >= 0) state.value.taskHistory[historyIndex] = clone(task); else state.value.taskHistory.unshift(clone(task)); delete state.value.inProgressTasks[task.id]; delete state.value.productBindings[task.productCode]; delete state.value.steelBindings[task.steelNo]; state.value.activeTaskId = null; state.value.productCode = ''; state.value.selectedModel = ''; state.value.steelNo = '' }
  function prepareNextTask() { if (state.value.task?.status === '已完成') { state.value.task = null; state.value.activeTaskId = null } state.value.productCode = ''; state.value.selectedModel = ''; state.value.steelNo = '' }
  function updateStep(stepNo, value) { const index = state.value.processSteps.findIndex(step => step.stepNo === stepNo); if (index < 0) return; const parameters = normalizeParameters(value.parameters).map(parameter => ({ ...parameter, sourceTypeMissing: false })); state.value.processSteps[index] = { ...state.value.processSteps[index], ...value, importance: Number(value.importance), stationId: stationId(value.stationId), parameters }; syncActiveProcess() }
  function applyTemplate(stepNo, templateId) { const template = state.value.templates.find(item => String(item.id) === String(templateId)); if (template) { state.value.stepProjection[stepNo] = { templateId: String(templateId), elements: [], inheritsTemplate: true }; syncActiveProcess() } }
  function updateProjection(stepNo, elements) { state.value.stepProjection[stepNo] = { ...(state.value.stepProjection[stepNo] || { templateId: null }), elements: clone(elements), inheritsTemplate: false }; syncActiveProcess() }
  function addTemplate(name) { const id = `T-${String(state.value.templates.length + 1).padStart(3, '0')}`; state.value.templates.push({ id, name: name || '新建投影模板', isDefault: false, width: 1920, height: 1080, description: '未填写说明', elements: [] }); return id }
  function canDeleteTemplate(id) { return !state.value.templates.find(item => item.id === id)?.isDefault }
  function createVideoCategory({ name, parentId = null }) { const label = String(name || '').trim(), parent = parentId ? state.value.videoCategories.find(item => item.id === parentId) : null; if (!label) return { ok: false, message: '请填写分类名称' }; if (parentId && !parent) return { ok: false, message: '父级分类不存在' }; if (parent?.parentId) return { ok: false, message: '视频分类最多两级' }; if (state.value.videoCategories.some(item => item.parentId === (parentId || null) && item.name === label)) return { ok: false, message: '同级分类名称不能重复' }; const siblings = state.value.videoCategories.filter(item => item.parentId === (parentId || null)); const category = { id: `VC-${Date.now()}-${siblings.length + 1}`, name: label, parentId: parentId || null, sortOrder: Math.max(0, ...siblings.map(item => Number(item.sortOrder) || 0)) + 1 }; state.value.videoCategories.push(category); return { ok: true, category } }
  function renameVideoCategory(id, name) { const category = state.value.videoCategories.find(item => item.id === id), label = String(name || '').trim(); if (!category) return { ok: false, message: '分类不存在' }; if (!label) return { ok: false, message: '请填写分类名称' }; if (state.value.videoCategories.some(item => item.id !== id && item.parentId === category.parentId && item.name === label)) return { ok: false, message: '同级分类名称不能重复' }; category.name = label; return { ok: true, category } }
  function deleteVideoCategory(id) { const category = state.value.videoCategories.find(item => item.id === id); if (!category) return { ok: false, message: '分类不存在' }; const childCount = state.value.videoCategories.filter(item => item.parentId === id).length; if (childCount) return { ok: false, message: `该一级分类下还有 ${childCount} 个二级分类，请先删除二级分类` }; const videoCount = state.value.videos.filter(video => video.categoryId === id && !state.value.deleted.videos.includes(video.id)).length; if (videoCount) return { ok: false, message: `该分类下有 ${videoCount} 个视频，请先转移或删除视频` }; state.value.videoCategories = state.value.videoCategories.filter(item => item.id !== id); return { ok: true } }
  function deleteResource(type, id) { const deleted = state.value.deleted[type]; if (!Array.isArray(deleted)) return { ok: false, message: '不支持删除该资源' }; if (type === 'templates' && !canDeleteTemplate(id)) return { ok: false, message: '默认模板不可删除，只能编辑' }; if (deleted.includes(id)) return { ok: false, message: '资源已被删除' }; deleted.push(id); return { ok: true } }
  function iconUrl(name) { return state.value.customIcons.find(item => item.name === name)?.dataUrl || `/projector-icons/${encodeURIComponent(name)}.png` }
  function saveIconAsset({ originalName = '', name, dataUrl = '' }) { const label = String(name || '').trim(); if (!label) return { ok: false, message: '请填写素材名称' }; const index = state.value.customIcons.findIndex(item => item.name === originalName || item.name === label); const record = { name: label, dataUrl: dataUrl || iconUrl(originalName || label), updatedAt: new Date().toISOString() }; if (index >= 0) state.value.customIcons[index] = { ...state.value.customIcons[index], ...record }; else state.value.customIcons.push(record); return { ok: true } }

  return { state, process: processSource, steps, activeSteps, currentStep, currentStation, stationNoForId, stationSteps, stationConfirmed, previousStationsConfirmed, finalStationId, isFinalStation, stationSummaryReady, finalSummaryPending, canOpenParameterSummary, nextStationStep, liveStationTasks, completedLogs, auditLogs, sourceMissingCount, processAvailable, defaultTemplate, projectionElements, iconNames, productModels, persist, reset, syncFromStorage, selectWorkstation, selectTask, adoptServerTask, replaceServerProcess, replaceServerProcesses, updateWorkstation, addWorkstation, deleteWorkstation, getBinding, getSteelBinding, applyBinding, saveBinding, activateProcess, rememberProcess, rememberedProcess, importProcess, login, logout, loginByCredentials, resetPersonPassword, updatePersonPassword, startTask, setViewedStep, jumpToStep, skipStationToParameterSummary, canEditStep, recordStep, stationCompletionItems, completionItems, confirmStation, updateTaskSteelNo, completeTask, prepareNextTask, updateStep, applyTemplate, updateProjection, addTemplate, canDeleteTemplate, createVideoCategory, renameVideoCategory, deleteVideoCategory, deleteResource, iconUrl, saveIconAsset }
})
