<template>
  <div class="app-root">
    <el-header v-if="isLoggedIn" class="app-header">
      <div class="logo" @click="$router.push('/chat')">法催平台智能客服</div>
      <el-menu mode="horizontal" :default-active="route.path" router class="nav-menu">
        <el-menu-item index="/chat">答疑</el-menu-item>
        <el-menu-item v-if="role === 'SRE'" index="/diagnose">排障</el-menu-item>
      </el-menu>
      <div class="header-right">
        <el-tag size="small" :type="roleTagType">{{ roleLabel }}</el-tag>
        <span class="user-name">{{ name }}</span>
        <el-button link type="danger" @click="logout">退出</el-button>
      </div>
    </el-header>
    <router-view class="app-content" />
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuth, clearAuth } from './stores/auth'

const router = useRouter()
const route = useRoute()
const { isLoggedIn, role, name, roleLabel } = useAuth()

const roleTagType = computed(() => {
  if (role.value === 'STAFF') return 'warning'
  if (role.value === 'SRE') return 'danger'
  return 'success'
})

function logout() {
  clearAuth()
  router.push('/login')
}
</script>

<style>
body {
  margin: 0;
  font-family: 'Helvetica Neue', Helvetica, 'PingFang SC', 'Microsoft YaHei', sans-serif;
}
#app,
.app-root {
  height: 100vh;
}
.app-root {
  display: flex;
  flex-direction: column;
}
.app-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #e4e7ed;
  background: #fff;
}
.app-header .logo {
  font-size: 18px;
  font-weight: bold;
  color: #409eff;
  cursor: pointer;
}
.nav-menu {
  flex: 1;
  margin: 0 24px;
  border-bottom: none;
}
.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}
.user-name {
  color: #606266;
}
.app-content {
  flex: 1;
  min-height: 0;
}
</style>
