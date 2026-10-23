<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useMesStore } from '../store'
import { version } from '../../package.json'
import { api, apiMode, ApiError, saveAuthToken } from '../api/request'

const router = useRouter()
const store = useMesStore()
const role = ref('operator')
const account = ref(apiMode ? '' : 'OP-018')
const password = ref(apiMode ? '' : '123456')
const isSubmitting = ref(false)
const workstations = computed(() => store.state.workstations)
const roles = [
  { id: 'operator', label: '操作员', desc: '扫码装配 · 参数录入', icon: 'UserFilled' },
  { id: 'admin', label: '管理员', desc: '工艺配置 · 生产统计', icon: 'Setting' },
]
function choose(value) {
  role.value = value
  account.value = apiMode ? '' : { operator: 'OP-018', admin: 'ADMIN-001' }[value]
  password.value = apiMode ? '' : { operator: '123456', admin: '123456' }[value]
}
onMounted(async () => {
  if (!apiMode) return
  try {
    const list = (await api.workstations()).map(item => ({
      ...item,
      id: Number(item.id),
      captureMode: Number(item.captureMode) === 0 ? 'manual' : 'auto',
    }))
    store.state.workstations = list
    if (!list.some(item => Number(item.id) === Number(store.state.selectedStationId))) store.state.selectedStationId = Number(list[0]?.id || 1)
  } catch {
    ElMessage.warning('无法连接后端服务，请检查 API、MySQL 和 Redis 配置')
  }
})
async function login() {
  if (apiMode) {
    if (isSubmitting.value) return
    isSubmitting.value = true
    try {
      // A login must begin with no inherited browser session, including an expired token.
      saveAuthToken('')
      const result = await api.login({
        no: account.value,
        password: password.value,
        portCode: role.value === 'operator' ? 0 : 1,
        workstationId: role.value === 'operator' ? Number(store.state.selectedStationId) : null,
      })
      saveAuthToken(result.accessToken)
      const loggedRole = Number(result.user.roleCode) === 0 ? 'operator' : 'admin'
      store.state.role = loggedRole
      store.state.currentUserId = result.user.id
      store.state.currentUserRoleCode = Number(result.user.roleCode)
      store.state.operatorName = result.user.name
      router.push(loggedRole === 'operator' ? '/op/select' : '/admin/processes')
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) ElMessage.error('工号或密码错误，请重新输入')
      else if (error instanceof ApiError && error.status === 403) ElMessage.error(role.value === 'admin' ? '该工号没有管理端权限，请使用管理员账号登录' : '该账号没有操作端权限，请使用操作员工号登录')
      else ElMessage.error(error instanceof ApiError ? error.message : '登录失败，请稍后重试')
    } finally {
      isSubmitting.value = false
    }
    return
  }
  const loggedRole = store.loginByCredentials(account.value, password.value)
  if (!loggedRole) return ElMessage.error('工号或密码错误')
  if (role.value === 'operator' && loggedRole !== 'operator') return ElMessage.warning('该工号请从管理员入口登录')
  if (role.value === 'admin' && loggedRole === 'operator') return ElMessage.warning('操作员工号请从操作员入口登录')
  if (loggedRole === 'operator' && !store.state.selectedStationId) return ElMessage.warning('请选择工位')
  router.push(loggedRole === 'operator' ? '/op/select' : '/admin/processes')
}
async function resetDemo() {
  try {
    await ElMessageBox.confirm('将清除本浏览器中的工位、产品绑定、在制任务和测试记录，并恢复初始演示数据。', '重置测试数据', { confirmButtonText: '确认重置', cancelButtonText: '取消', type: 'warning' })
    store.reset()
    ElMessage.success('测试数据已重置')
  } catch {}
}
</script>

<template>
  <main class="login-page atmosphere">
    <div class="login-grid"></div><div class="login-glow one"></div><div class="login-glow two"></div>
    <div class="login-page-brand"><img class="brand-lockup" src="/syncdot-logo-lockup.svg" alt="骤点科技 SyncDot Tech" /></div>
    <section class="login-intro">
      <!-- 参考图组合：双虚线圆环 + 十字虚线轴 + 细实线括线 + 四个折线角标；减振器居中于轴线交点 -->
      <div class="login-intro-geometry" aria-hidden="true">
        <span class="geo-axis-h"></span>
        <span class="geo-axis-v"></span>
        <span class="geo-corner tl"></span>
        <span class="geo-corner tr"></span>
        <span class="geo-corner bl"></span>
        <span class="geo-corner br"></span>
        <!--
          油压减振器线框（依 yyjzq.svg 造型）：
          上安装眼环（外圆 + r10.5 / r5.5 同心圆）→ 吊杆颈 → 圆角直筒缸体 → 吊杆颈 → 下安装眼环
          轮廓为实色白描边；中轴与刻度用虚线，中轴与几何虚线轴重合
        -->
        <svg class="geo-damper" viewBox="484 326 56 372" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
          <!-- 上安装眼环 -->
          <path d="M528,350.8c0,5.1-2.3,9.6-6,12.5c-2.7,2.2-6.2,3.5-10,3.5c-3.8,0-7.3-1.3-10-3.5c-3.7-2.9-6-7.4-6-12.5 c0-8.8,7.2-16,16-16C520.8,334.8,528,341.9,528,350.8z" stroke="#FFFFFF" stroke-width="2.6"/>
          <circle cx="512" cy="350.8" r="10.5" stroke="#FFFFFF" stroke-width="2.4"/>
          <circle cx="512" cy="350.8" r="5.5" stroke="#FFFFFF" stroke-width="2.4"/>
          <!-- 上吊杆颈 -->
          <path d="M525,379.8v26.8h-26v-26.8l3-3v-13.5c2.7,2.2,6.2,3.5,10,3.5c3.8,0,7.3-1.3,10-3.5v13.5L525,379.8z" stroke="#FFFFFF" stroke-width="2.6"/>
          <!-- 缸体（圆角直筒） -->
          <path d="M531.7,617.5h-39.4c-2.4,0-4.3-1.9-4.3-4.3V410.8c0-2.4,1.9-4.3,4.3-4.3h39.4c2.4,0,4.3,1.9,4.3,4.3v202.4 C536,615.6,534.1,617.5,531.7,617.5z" stroke="#FFFFFF" stroke-width="2.6"/>
          <!-- 下吊杆颈 -->
          <path d="M525,644.2v-26.8h-26v26.8l3,3v13.5c2.7-2.2,6.2-3.5,10-3.5c3.8,0,7.3,1.3,10,3.5v-13.5L525,644.2z" stroke="#FFFFFF" stroke-width="2.6"/>
          <!-- 下安装眼环 -->
          <path d="M528,673.2c0-5.1-2.3-9.6-6-12.5c-2.7-2.2-6.2-3.5-10-3.5c-3.8,0-7.3,1.3-10,3.5c-3.7,2.9-6,7.4-6,12.5 c0,8.8,7.2,16,16,16C520.8,689.2,528,682.1,528,673.2z" stroke="#FFFFFF" stroke-width="2.6"/>
          <circle cx="512" cy="673.2" r="10.5" stroke="#FFFFFF" stroke-width="2.4"/>
          <circle cx="512" cy="673.2" r="5.5" stroke="#FFFFFF" stroke-width="2.4"/>
          <!-- 纹理虚线：贯穿中轴 + 缸体内部刻度 -->
          <line x1="512" y1="326" x2="512" y2="698" stroke="#FFFFFF" stroke-width="1.4" stroke-dasharray="8 10" opacity=".65"/>
          <line x1="498" y1="446" x2="526" y2="446" stroke="#FFFFFF" stroke-width="1.3" stroke-dasharray="4 6" opacity=".5"/>
          <line x1="498" y1="512" x2="526" y2="512" stroke="#FFFFFF" stroke-width="1.3" stroke-dasharray="4 6" opacity=".5"/>
          <line x1="498" y1="578" x2="526" y2="578" stroke="#FFFFFF" stroke-width="1.3" stroke-dasharray="4 6" opacity=".5"/>
        </svg>
      </div>
      <h1><span class="disp-ems">DISP-EMS</span><em class="h1-line">光影引导</em><span class="h1-line damper-line">装配作业系统<img class="h1-damper" src="/yyjzq-clean.png" alt="" aria-hidden="true"/></span><span class="intro-sub">过程可视 · 参数可溯</span></h1>
    </section>
    <section class="login-card">
      <h2>登录工位</h2><p class="subcopy">选择工作身份，进入对应操作界面</p>
      <div class="role-switch" role="group" aria-label="工作身份">
        <button v-for="item in roles" :key="item.id" :class="{ active: role===item.id }" @click="choose(item.id)">
          <span class="role-ico"><component :is="item.icon" /></span>
          <span class="role-meta"><strong>{{ item.label }}</strong><small>{{ item.desc }}</small></span>
        </button>
      </div>
      <label class="field-label">工号</label><div class="login-field"><User /><input v-model="account" aria-label="工号" /></div>
      <label class="field-label">密码</label><div class="login-field"><Lock /><input v-model="password" type="password" aria-label="密码" @keyup.enter="login" /></div>
      <label v-if="role==='operator'" class="field-label">工位</label><div v-if="role==='operator'" class="login-field"><Location /><select :value="store.state.selectedStationId" aria-label="工位" @change="store.selectWorkstation($event.target.value)"><option v-for="station in workstations" :key="station.id" :value="station.id">{{ station.name }} · {{ station.captureMode==='manual'?'手动录入':'自动获取' }}</option></select></div>
      <button class="primary-button login-submit" :disabled="isSubmitting" @click="login">{{ isSubmitting ? '正在登录…' : '进入系统' }} <ArrowRight /></button>
      <button v-if="!apiMode" class="login-reset" type="button" @click="resetDemo">重置测试数据</button>
    </section>
    <footer class="login-footer"><span class="login-footer-brand">成都骤点科技有限公司 · SyncDot</span><span class="login-footer-version">v{{ version }}</span></footer>
  </main>
</template>
