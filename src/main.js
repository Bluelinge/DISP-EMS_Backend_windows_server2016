import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { createRouter, createWebHashHistory } from 'vue-router'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'
import * as ElementIcons from '@element-plus/icons-vue'
import App from './App.vue'
import Login from './views/Login.vue'
import SelectProcess from './views/SelectProcess.vue'
import Work from './views/Work.vue'
import Complete from './views/Complete.vue'
import Projector from './views/Projector.vue'
import Admin from './views/Admin.vue'
import { useMesStore } from './store'
// 骤点 B 端设计系统：先注入 tokens + components，再加载项目 style.css 以便局部覆盖
import './design/tokens.css'
import './design/components.css'
import './style.css'

// Element Plus 主色统一为骤点界面主色 #0077FA
import { ElMessage, ElMessageBox } from 'element-plus'

// Element Plus 中文 locale：日期选择弹窗月份固定用两位数字（08、09）
const syncDotZhCn = {
  ...zhCn,
  el: {
    ...zhCn.el,
    datepicker: {
      ...zhCn.el.datepicker,
      month1: '01',
      month2: '02',
      month3: '03',
      month4: '04',
      month5: '05',
      month6: '06',
      month7: '07',
      month8: '08',
      month9: '09',
      month10: '10',
      month11: '11',
      month12: '12',
    },
  },
}

const routes = [
  { path: '/', redirect: '/login' },
  { path: '/login', component: Login },
  { path: '/op/select', component: SelectProcess },
  { path: '/op/work/:taskId', component: Work },
  { path: '/op/work/:taskId/complete', component: Complete },
  { path: '/op/projector', component: Projector },
  { path: '/admin/processes', component: Admin },
  { path: '/admin/processes/:id', component: Admin },
  { path: '/admin/processes/:id/projector-preview', component: Admin },
  { path: '/admin/processes/:id/projector-preview/live', component: Projector },
  { path: '/admin/videos', component: Admin },
  { path: '/admin/icons', component: Admin },
  { path: '/admin/projector/templates', component: Admin },
  { path: '/admin/projector/templates/:id', component: Admin },
  { path: '/admin/projector/templates/:id/preview', component: Projector },
  { path: '/admin/tasks', component: Admin },
  { path: '/admin/tasks/:id/audit', component: Admin },
  { path: '/admin/dicts', redirect: '/admin/processes' },
  { path: '/admin/users', component: Admin },
  { path: '/admin/workstations', component: Admin },
  { path: '/admin/stats', component: Admin },
  { path: '/:pathMatch(.*)*', redirect: '/login' },
]

const app = createApp(App)
Object.entries(ElementIcons).forEach(([name, component]) => app.component(name, component))
const pinia = createPinia()
const router = createRouter({ history: createWebHashHistory(), routes })
router.beforeEach(to => {
  const role = useMesStore(pinia).state.role
  if (to.path !== '/login' && !role) return '/login'
  if (to.path === '/login' && role) return role === 'operator' ? '/op/select' : '/admin/processes'
  // 操作员仅可进入工位端；管理员可进入管理端全部模块
  if (to.path.startsWith('/op/select') || to.path.startsWith('/op/work')) {
    if (role === 'admin') return '/admin/processes'
  }
  if (!to.path.startsWith('/admin')) return true
  if (role === 'operator') return '/login'
  return true
})
app.use(pinia)
app.use(ElementPlus, { locale: syncDotZhCn })
app.use(router)
app.mount('#app')
