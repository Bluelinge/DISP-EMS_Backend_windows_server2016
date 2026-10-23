<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useMesStore } from './store'
import UserProfileDialog from './components/UserProfileDialog.vue'
import { endSession } from './api/request'

const route = useRoute()
const router = useRouter()
const isLogin = computed(() => route.path === '/login')
const isAdmin = computed(() => route.path.startsWith('/admin'))
const isProjector = computed(() => route.path.startsWith('/op/projector'))
const isWork = computed(() => /^\/op\/work\/[^/]+$/.test(route.path))
const store = useMesStore()
const profileDialog = ref(false)

async function logout() { await endSession(); store.logout(); router.replace('/login') }
</script>

<template>
  <div class="app-root" :class="{ 'is-login': isLogin, 'is-projector': isProjector, 'is-work': isWork }">
    <header v-if="!isLogin && !isAdmin && !isProjector" class="topbar">
      <button class="brand-link" @click="router.push('/op/select')" aria-label="返回工艺选择"><img class="brand-logo" src="/syncdot-logo.svg" alt="SyncDot" /><span>减振器装配作业系统</span><strong>DISP-MES</strong></button>
      <div class="topbar-right"><button class="topbar-user" @click="profileDialog=true"><UserFilled /><span>{{store.state.operatorName}}</span></button><button class="plain-icon" title="退出登录" aria-label="退出登录" @click="logout"><SwitchButton /></button></div>
    </header>
    <UserProfileDialog v-model="profileDialog" />
    <router-view />
  </div>
</template>
