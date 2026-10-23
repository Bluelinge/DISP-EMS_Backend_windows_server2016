<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useMesStore } from '../store'
import { api, apiMode, ApiError } from '../api/request'
import { formatRangeReference } from '../utils/parameterRange'
import { firstOptionValue, isBlankParameterValue, isParameterValueInvalid } from '../utils/parameterValidation'

const route = useRoute(), router = useRouter()
const store = useMesStore()
async function loadTask(id) {
  if (!id) return
  try {
    if (apiMode) store.adoptServerTask(await api.task(id))
    else store.selectTask(id)
  } catch (error) { ElMessage.error(error instanceof ApiError ? error.message : '无法读取任务') }
}
watch(() => route.params.taskId, loadTask, { immediate: true })
const task = computed(() => store.state.task)
const steelEditing = ref(false)
const finished = computed(() => task.value?.status === '已完成')
const finalStage = computed(() => !finished.value && store.finalSummaryPending)
const summaryAccessible = computed(() => finished.value || store.canOpenParameterSummary)
const summaryEditable = computed(() => !finished.value && store.canOpenParameterSummary)
const items = ref([])
const touchedParameters = ref({})
const steelNo = ref('')
const title = computed(() => finished.value ? '任务已完成' : finalStage.value ? '最终参数汇总' : `${store.currentStation.name} 参数汇总`)
const parameterReferenceText = parameter => {
  const reference = String(parameter?.referenceValue || '').trim()
  if (Number(parameter?.type) === 2) {
    return formatRangeReference(reference)
  }
  if (Number(parameter?.type) === 1) {
    const option = (parameter.options || reference.split(/\r?\n/)).map(item => String(item).trim()).find(Boolean)
    return `合格参考项：${option || '—'}`
  }
  return `参考：${reference || '—'}`
}
const unfilledCount = computed(() => items.value.flatMap(item => item.parameters || []).filter(parameter => isBlankParameterValue(parameter.operatorValue)).length)
function loadSummary() {
  touchedParameters.value = {}
  if (!task.value) { items.value = []; steelNo.value = ''; return }
  const summaryItems = finalStage.value ? store.completionItems() : store.stationCompletionItems()
  items.value = summaryItems.map(item => ({
    ...item,
    parameters: item.parameters.map(parameter => ({
      ...parameter,
      operatorValue: summaryEditable.value && Number(parameter.type) === 1 && isBlankParameterValue(parameter.operatorValue)
        ? firstOptionValue(parameter)
        : parameter.operatorValue,
    })),
  }))
  steelNo.value = task.value.steelNo || ''
}
watch([() => task.value?.id, finalStage], loadSummary, { immediate: true })
function markParameterTouched(stepNo, index) {
  touchedParameters.value[`${stepNo}:${index}`] = true
}
function parameterInvalid(item, parameter) {
  return !!touchedParameters.value[`${item.stepNo}:${parameter.index}`]
    && isParameterValueInvalid(parameter, parameter.operatorValue)
}
async function saveSteelNo() {
  if (!summaryEditable.value) return ElMessage.warning('当前汇总不可修改')
  if (!steelNo.value.trim()) return ElMessage.warning('钢印编号不能为空')
  try {
    if (apiMode) store.adoptServerTask(await api.updateTaskSteelNo(task.value.id, steelNo.value.trim()))
    else if (!store.updateTaskSteelNo(steelNo.value)) return
    ElMessage.success('钢印编号已更新，后续工位将读取最新绑定')
    steelEditing.value = false
  } catch (error) { ElMessage.error(error instanceof ApiError ? error.message : '更新钢印编号失败') }
}
function nextTask() { store.prepareNextTask(); router.push('/op/select') }
async function confirm() {
  if (!summaryEditable.value) return ElMessage.warning('当前工位尚不能确认参数汇总')
  if (unfilledCount.value) return ElMessage.warning(`还有 ${unfilledCount.value} 项参数未填写，填写完成后才能${finalStage.value ? '提交任务' : '交接下一工位'}`)
  if (!finalStage.value) {
    let isFinal
    try {
      if (apiMode) {
        const updated = await api.confirmTaskStation(task.value.id, store.currentStation.id, items.value)
        store.adoptServerTask(updated)
        isFinal = store.currentStation.id === store.finalStationId
      } else isFinal = store.confirmStation(items.value)
    } catch (error) { return ElMessage.error(error instanceof ApiError ? error.message : '确认交接失败，请稍后重试') }
    ElMessage.success(`${store.currentStation.name} 参数汇总已确认`)
    if (isFinal) { items.value = store.completionItems(); return }
    router.push('/op/select')
    return
  }
  try {
    if (apiMode) store.adoptServerTask(await api.completeTask(task.value.id, items.value))
    else store.completeTask(items.value)
    ElMessage.success('任务已完成')
  } catch (error) { ElMessage.error(error instanceof ApiError ? error.message : '完成任务失败，请稍后重试') }
}
</script>

<template>
  <main v-if="task && !summaryAccessible" class="empty-screen">
    <DocumentChecked />
    <strong>暂不能进入参数汇总</strong>
    <span>请先完成前序工位的全部步骤，并等待前序工位确认交接。</span>
    <button class="primary-button" @click="router.push(`/op/work/${task.id}`)">返回作业</button>
  </main>
  <main v-else-if="task" class="complete-page">
    <div class="page-body">
      <div class="complete-heading">
        <div><h1>{{ title }}</h1><p>{{ finished ? '任务已归档，点击“录入下一件”可用新的产品编码创建新的在制绑定。' : finalStage ? '汇总全部工位的参数记录；全部参数填写完成后才可确认完成整条装配任务。' : '本工位全部步骤均已展示；全部参数填写完成后才可确认交接。' }}</p></div>
        <div class="complete-status"><CircleCheck /><span>{{ finished ? '已归档' : finalStage ? '最终确认' : '待交接' }}</span></div>
      </div>
      <div class="complete-layout">
        <section class="panel complete-summary">
          <div class="panel-head"><div><h2>任务概览</h2></div><span class="panel-meta">{{ task.id }}</span></div>
          <dl>
            <div><dt>减振器型号</dt><dd>{{ task.model }}</dd></div>
            <div><dt>产品编码</dt><dd>{{ task.productCode }}</dd></div>
            <div><dt>钢印编号</dt><dd v-if="!steelEditing">{{ task.steelNo }} <button v-if="summaryEditable" class="table-action" @click="steelEditing=true">修改钢印编号</button></dd><dd v-else class="steel-edit"><input v-model="steelNo" aria-label="钢印编号"/><button class="table-action" @click="saveSteelNo">保存</button></dd></div>
            <div><dt>当前工位</dt><dd>{{ finalStage ? '最终汇总' : store.currentStation.name }}</dd></div>
            <div><dt>汇总步骤</dt><dd>{{ items.length }} 项</dd></div>
            <div><dt>未填写参数</dt><dd>{{ unfilledCount }} 项</dd></div>
          </dl>
          <div class="summary-note"><InfoFilled /> {{ finished ? '当前任务已归档；下一件产品会重新扫码并建立新的绑定。' : '钢印编号修改会写入审计，并同步更新该产品编码的最新绑定。' }}</div>
        </section>
        <section class="panel completion-form">
          <div class="panel-head"><div><h2>参数汇总表单</h2></div><span class="panel-meta">{{ summaryEditable ? '参数需全部填写' : '已归档，只读查看' }}</span></div>
          <div v-if="items.length" class="completion-items">
            <div v-for="item in items" :key="item.stepNo" class="completion-item">
              <div class="completion-step"><span>{{ String(item.stepNo).padStart(2,'0') }}</span><strong>{{ item.title }}</strong><small>工位{{ store.stationNoForId(item.stationId) }}</small></div>
              <div v-if="item.parameters.length" class="completion-params">
                <label v-for="p in item.parameters" :key="p.index"><span>{{ p.name }}<small>{{ parameterReferenceText(p) }}</small></span><el-select v-if="Number(p.type) === 1" v-model="p.operatorValue" :disabled="!summaryEditable" :class="{ 'is-invalid': parameterInvalid(item, p) }" :aria-invalid="parameterInvalid(item, p)" placeholder="未填写" @blur="markParameterTouched(item.stepNo, p.index)"><el-option v-for="option in p.options" :key="option" :label="option" :value="option" /></el-select><input v-else v-model="p.operatorValue" :disabled="!summaryEditable" :class="{ 'is-invalid': parameterInvalid(item, p) }" :aria-invalid="parameterInvalid(item, p)" placeholder="请输入" @blur="markParameterTouched(item.stepNo, p.index)" /></label>
              </div>
              <div v-else class="completion-empty">本步骤无参数</div>
            </div>
          </div>
          <div v-else class="empty-parameters"><DocumentChecked /><strong>本工位没有可汇总步骤</strong></div>
        </section>
      </div>
    </div>
    <footer class="complete-footer">
      <div class="footer-status"><span class="status-dot" :class="finished?'success':unfilledCount?'warning':'success'"></span>{{ finished ? '任务已归档，可开始录入下一件' : unfilledCount ? `还有 ${unfilledCount} 项参数待填写，完成后才能${finalStage ? '提交任务' : '交接下一工位'}` : '参数均已填写，可确认提交' }}</div>
      <div class="footer-actions"><button v-if="summaryEditable&&!finalStage" class="ghost-button" @click="router.push(`/op/work/${task.id}`)">返回作业</button><button v-if="finished" class="primary-button success-button" @click="nextTask">录入下一件 <ArrowRight /></button><button v-else-if="summaryEditable" class="primary-button success-button" :disabled="!!unfilledCount" @click="confirm">{{ finalStage ? '确认完成任务' : '确认并交接下一工位' }} <Check /></button></div>
    </footer>
  </main>
  <main v-else class="empty-screen"><strong>尚无任务记录</strong><button class="primary-button" @click="router.push('/op/select')">返回工位</button></main>
</template>
