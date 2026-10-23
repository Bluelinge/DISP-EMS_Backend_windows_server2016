<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useMesStore } from '../store'
import { api, apiMode } from '../api/request'
import ProjectionCanvas from '../components/ProjectionCanvas.vue'
const route=useRoute(),store=useMesStore(),tick=ref(0),templateId=computed(()=>route.query.templateId||(route.path.includes('/projector-preview/live')?'':route.params.id)),stepNo=computed(()=>Number(route.query.step||1)),taskId=computed(()=>String(route.query.taskId||'')),template=computed(()=>store.state.templates.find(t=>t.id===templateId.value)),isTemplatePreview=computed(()=>!!templateId.value),isStepPreview=computed(()=>route.path.includes('/projector-preview/live'))
const task=computed(()=>{tick.value;try{const data=JSON.parse(localStorage.getItem('disp-mes-ui-prototype-v2')||'{}'),id=taskId.value;return id?data.inProgressTasks?.[id]||data.taskHistory?.find(item=>item.id===id)||data.task:data.task||store.state.task}catch{return store.state.task}})
const step=computed(()=>{const preview=isTemplatePreview.value||isStepPreview.value,list=preview?store.steps:(task.value?.processSnapshot||store.steps);if(preview)return list.find(s=>s.stepNo===stepNo.value)||store.steps[0];return list[task.value?.currentStepIndex ?? 0]||store.currentStep})
const liveProcess=computed(()=>store.state.processFiles.find(file=>file.model===task.value?.model)||store.state.processFiles.find(file=>String(file.id)===String(store.state.processRecord?.id)))
const elements=computed(()=>isTemplatePreview.value?template.value?.elements||[]:isStepPreview.value?store.projectionElements(stepNo.value):step.value?store.projectionElements(step.value.stepNo,liveProcess.value?.stepProjection):[])
const projectionTask=computed(()=>isTemplatePreview.value||isStepPreview.value?null:task.value)
let syncTimer
async function refreshDefinitions(){
  if(!apiMode)return
  try{
    const [processes,templates,latestTask]=await Promise.all([api.processes(),api.templates(),taskId.value?api.task(taskId.value):Promise.resolve(null)])
    store.state.templates=(templates||[]).map(item=>({...item,id:String(item.id),isDefault:Number(item.isDefault)===1||item.isDefault===true,elements:Array.isArray(item.elements)?item.elements:[],rowVersion:Number(item.rowVersion)}))
    store.replaceServerProcesses(processes)
    if(latestTask)store.adoptServerTask(latestTask)
  }catch{/* 投影窗口保留上一次已成功加载的画面，避免短暂网络波动清空画面。 */}
}
onMounted(()=>{refreshDefinitions();syncTimer=window.setInterval(()=>{tick.value++;refreshDefinitions()},2000)})
onBeforeUnmount(()=>window.clearInterval(syncTimer))
</script>
<template><main class="projector-page"><header class="projector-header"><div><span class="projector-brand">DISP-MES</span><i></i><span>{{isTemplatePreview?'模板投影预览':isStepPreview?'步骤投影预览':'实时投影引导'}}</span></div><div><span class="status-dot" :class="elements.length?'success':'warning'"></span>{{isTemplatePreview?template?.name:isStepPreview?'第 '+step?.stepNo+' 步 · 当前工艺快照':task?'任务 '+task.id+' · 第 '+step?.stepNo+' 步':'等待任务'}}</div></header><div v-if="elements.length||isTemplatePreview||isStepPreview||task" class="projector-screen"><ProjectionCanvas :elements="elements" :step="step" :model="projectionTask?.model||store.state.processRecord.model" :task="projectionTask"/></div><div v-else class="projector-wait"><Monitor/><strong>等待工位任务</strong><span>从操作端开始装配作业后，投影画面将在这里同步显示。</span></div><footer class="projector-footer"><span>{{projectionTask?.model||store.state.processRecord.model}}</span><span>1920 × 1080 · 投影预览</span></footer></main></template>
