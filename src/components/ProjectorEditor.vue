<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useMesStore } from '../store'
import { api, apiMode, ApiError } from '../api/request'
import ProjectionCanvas from './ProjectionCanvas.vue'
const props=defineProps({mode:{type:String,default:'template'},templateId:{type:String,default:''},stepNo:{type:Number,default:1}})
const store=useMesStore(),clone=v=>JSON.parse(JSON.stringify(v)),template=computed(()=>store.state.templates.find(t=>String(t.id)===String(props.templateId))),step=computed(()=>store.steps.find(s=>s.stepNo===props.stepNo)),projection=computed(()=>store.state.stepProjection[props.stepNo]||{}),sourceElements=computed(()=>props.mode==='template'?template.value?.elements:store.projectionElements(props.stepNo)),elements=ref([]),activeId=ref(''),selected=computed(()=>elements.value.find(el=>el.id===activeId.value)),allIcons=computed(()=>[...new Set([...store.iconNames,...store.state.customIcons.map(i=>i.name)])].filter(n=>!store.state.deleted.icons.includes(n)))
const bindings=[['none','不绑定（纯文本）'],['stepTitle','当前工步名称  {{stepTitle}}'],['stepNo','当前步骤号  {{stepNo}}'],['paramName1','第 1 组参数名称  {{paramName1}}'],['paramValue1','第 1 组参数值  {{paramValue1}}'],['paramName2','第 2 组参数名称  {{paramName2}}'],['paramValue2','第 2 组参数值  {{paramValue2}}'],['paramName3','第 3 组参数名称  {{paramName3}}'],['paramValue3','第 3 组参数值  {{paramValue3}}'],['progress','进度（已完成 / 总步骤）  {{progress}}'],['model','减振器型号  {{model}}'],['steelNo','钢印号  {{steelNo}}'],['taskNo','任务号  {{taskNo}}'],['operator','操作员姓名  {{operator}}']]
const token=key=>key==='none'?'':`{{${key}}}`
// 模板名称：编辑模板页可直接重命名（新建模板时由列表页弹窗预填）
const nameModel=ref(template.value?.name||'')
watch(()=>props.templateId,()=>{nameModel.value=template.value?.name||''})
async function rename(){if(!template.value)return;const v=nameModel.value.trim();if(!v){nameModel.value=template.value.name;return}if(v!==template.value.name){template.value.name=v;await save(true)}}
function load(){elements.value=clone(sourceElements.value||[]).map(el=>({...el,bindField:el.bindField||'none'}));activeId.value=elements.value[0]?.id||''}
watch([()=>props.mode,()=>props.templateId,()=>props.stepNo,sourceElements],load,{immediate:true,deep:true})
function add(type){const id='E-'+Date.now().toString().slice(-7);elements.value.push(type==='icon'?{id,type,iconName:allIcons.value[0]||'文书',x:500,y:430,scale:1,rotation:0,blink:false,zIndex:elements.value.length+1}:{id,type,text:'',bindField:'none',x:500,y:430,fontSize:48,color:'#EFF2F0',scale:1,rotation:0,blink:false,zIndex:elements.value.length+1});activeId.value=id}
function setBinding(value){selected.value.bindField=value;if(value!=='none')selected.value.text=token(value)}
function move({id,x,y}){const item=elements.value.find(el=>el.id===id);if(item){item.x=x;item.y=y}}
function remove(){elements.value=elements.value.filter(el=>el.id!==activeId.value);activeId.value=elements.value[0]?.id||''}
async function save(silent=false){
  try {
    if(props.mode==='template'){
      if(!template.value)return
      if(apiMode){
        const saved=await api.updateTemplate(template.value.id,{name:nameModel.value.trim()||template.value.name,description:template.value.description||'',elements:clone(elements.value),expectedVersion:template.value.rowVersion})
        const index=store.state.templates.findIndex(item=>String(item.id)===String(saved.id))
        const next={...saved,id:String(saved.id),isDefault:Number(saved.isDefault)===1||saved.isDefault===true,elements:Array.isArray(saved.elements)?saved.elements:[],rowVersion:Number(saved.rowVersion)}
        if(index>=0)store.state.templates[index]=next
      } else { template.value.elements=clone(elements.value);store.persist() }
      if(!silent)ElMessage.success('投影模板已保存；使用该模板的步骤会同步显示新样式')
    }else{
      if(apiMode){
        const process=store.state.processFiles.find(item=>String(item.id)===String(store.state.processRecord.id))
        if(!process)throw new ApiError('当前工艺不存在，请刷新后重试')
        store.replaceServerProcess(await api.updateProcessProjection(process.id,props.stepNo,{expectedVersion:process.rowVersion,templateId:projection.value.templateId||store.defaultTemplate?.id||'',elements:clone(elements.value),inheritsTemplate:false}))
      } else store.updateProjection(props.stepNo,elements.value)
      if(!silent)ElMessage.success('步骤投影已保存，操作端投影将同步更新')
    }
  }catch(error){if(!silent)ElMessage.error(error instanceof ApiError?error.message:'投影保存失败，请稍后重试')}
}
function preview(){save(true);const url=props.mode==='template'?'/admin/projector/templates/'+props.templateId+'/preview?templateId='+props.templateId+'&step=1':'/admin/processes/1/projector-preview/live?step='+props.stepNo;window.open(location.origin+location.pathname+'#'+url,'_blank')}
</script>
<template><div class="editor-shell"><div class="editor-toolbar"><div><span class="status-dot success"></span><input v-if="mode==='template'" v-model="nameModel" class="template-name-input" maxlength="64" placeholder="模板名称" @blur="rename" @keyup.enter="rename"/><strong v-else>第 {{stepNo}} 步 · 投影快照</strong><span>1920 × 1080 画布</span></div><div><button class="ghost-button" @click="add('icon')"><Picture/> 添加投影图标</button><button class="ghost-button" @click="add('text')"><EditPen/> 添加文字</button><button class="ghost-button" @click="preview"><Monitor/> 预览</button><button class="admin-primary" @click="save"><Check/> 保存{{mode==='template'?'模板':'步骤投影'}}</button></div></div><div class="editor-layout"><section class="editor-canvas-card"><div class="editor-canvas-title"><span>投影画布</span><span>拖动元素调整位置</span></div><ProjectionCanvas :elements="elements" :step="step" :model="store.state.processRecord.model" :active-id="activeId" editable @select="activeId=$event" @move="move"/><div class="editor-canvas-bottom"><span>安全显示区域 · 16:9</span><span>{{elements.length}} 个元素</span></div></section><aside class="editor-sidebar"><div class="editor-side-title"><strong>元素图层</strong><span>{{elements.length}} 项</span></div><div class="element-list"><button v-for="el in elements" :key="el.id" :class="{active:el.id===activeId}" @click="activeId=el.id"><span class="element-type-icon"><Picture v-if="el.type==='icon'"/><EditPen v-else/></span><span>{{el.type==='icon'?el.iconName:el.text||'纯文本'}}</span><small>{{el.zIndex}}</small></button></div><template v-if="selected"><div class="editor-side-title properties-title"><strong>元素属性</strong><button @click="remove">移除</button></div><div class="property-form"><label v-if="selected.type==='icon'">投影图标<el-select v-model="selected.iconName"><el-option v-for="name in allIcons" :key="name" :label="name" :value="name"/></el-select></label><template v-else><label>绑定字段<el-select :model-value="selected.bindField||'none'" @update:model-value="setBinding"><el-option v-for="item in bindings" :key="item[0]" :label="item[1]" :value="item[0]"/></el-select></label><label v-if="(selected.bindField||'none')==='none'">纯文本<input v-model="selected.text" placeholder="输入投影文字"/></label></template><div class="property-grid"><label>X 位置<input v-model.number="selected.x" type="number" min="0" max="1919"/></label><label>Y 位置<input v-model.number="selected.y" type="number" min="0" max="1079"/></label><label>缩放<input v-model.number="selected.scale" type="number" step="0.1" min="0.2" max="4"/></label><label>旋转 °<input v-model.number="selected.rotation" type="number" min="-180" max="180"/></label><label v-if="selected.type==='text'">字号 px<input v-model.number="selected.fontSize" type="number" min="16" max="160"/></label><label>层级<input v-model.number="selected.zIndex" type="number" min="1" max="99"/></label></div><label v-if="selected.type==='text'">文字颜色<input v-model="selected.color" type="color" class="color-input"/></label><label class="blink-toggle"><span>闪烁提示</span><el-switch v-model="selected.blink"/></label></div></template></aside></div></div></template>
