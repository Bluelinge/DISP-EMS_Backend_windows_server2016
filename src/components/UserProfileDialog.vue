<script setup>
/**
 * 个人信息弹窗（两端口顶栏「登录人」按钮共用）
 * 展示当前登录人在「人员管理」中的档案，并支持自助修改密码。
 */
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useMesStore } from '../store'
import { api, apiMode, ApiError, endSession } from '../api/request'

const props = defineProps({ modelValue: { type: Boolean, default: false } })
const emit = defineEmits(['update:modelValue'])
const store = useMesStore()
const router = useRouter()
const form = ref({ current: '', next: '', confirm: '' })
const isSaving = ref(false)

const person = computed(() => store.state.people.find(p => p.id === store.state.currentUserId) || null)

function close() { emit('update:modelValue', false) }

// 打开时清空密码栏，避免残留上一次输入
function onOpen() { form.value = { current: '', next: '', confirm: '' } }

async function changePassword() {
  const me = person.value
  if (!me) return ElMessage.warning('未找到当前登录人档案')
  const { current, next, confirm } = form.value
  if (!current || !next || !confirm) return ElMessage.warning('请完整填写原密码、新密码和确认密码')
  if (next.length < 6) return ElMessage.warning('新密码至少 6 位')
  if (next !== confirm) return ElMessage.warning('两次输入的新密码不一致')
  if (next === current) return ElMessage.warning('新密码不能与原密码相同')
  if (isSaving.value) return
  isSaving.value = true
  try {
    if (apiMode) {
      await api.changePassword({ currentPassword: current, newPassword: next })
      // 后端会立即废止该用户的全部会话；同步清除浏览器令牌并回到登录页。
      try { await endSession() } catch {}
      store.logout()
      close()
      form.value = { current: '', next: '', confirm: '' }
      ElMessage.success('密码已更新，请使用新密码重新登录')
      await router.replace('/login')
      return
    }
    if (current !== me.password) return ElMessage.error('原密码不正确')
    store.updatePersonPassword(me.id, next)
  } catch (error) {
    return ElMessage.error(error instanceof ApiError ? error.message : '修改密码失败，请稍后重试')
  } finally {
    isSaving.value = false
  }
  form.value = { current: '', next: '', confirm: '' }
  ElMessage.success('密码已更新，下次登录请使用新密码')
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    title="个人信息"
    width="520px"
    class="mes-dialog"
    @update:model-value="emit('update:modelValue', $event)"
    @open="onOpen"
  >
    <div class="profile-info">
      <div><span>工号</span><strong>{{ person?.no || '-' }}</strong></div>
      <div><span>姓名</span><strong>{{ person?.name || '-' }}</strong></div>
      <div><span>角色</span><strong>{{ person?.role || '-' }}</strong></div>
      <div><span>部门</span><strong>{{ person?.dept || '-' }}</strong></div>
      <div><span>手机号</span><strong>{{ person?.phone || '-' }}</strong></div>
      <div><span>状态</span><strong>{{ person?.status || '-' }}</strong></div>
    </div>
    <div class="profile-section-title"><strong>修改密码</strong><span>修改后下次登录生效</span></div>
    <div class="dialog-label">原密码<input v-model="form.current" type="password" autocomplete="off" placeholder="请输入当前密码" /></div>
    <div class="dialog-label">新密码<input v-model="form.next" type="password" autocomplete="new-password" placeholder="至少 6 位" /></div>
    <div class="dialog-label">确认新密码<input v-model="form.confirm" type="password" autocomplete="new-password" placeholder="再次输入新密码" @keyup.enter="changePassword" /></div>
    <template #footer>
      <button class="ghost-button" @click="close">关闭</button>
      <button class="admin-primary" :disabled="isSaving" @click="changePassword">{{ isSaving ? '正在保存…' : '保存新密码' }}</button>
    </template>
  </el-dialog>
</template>
