<template>
  <div class="login-page">
    <el-card class="login-card">
      <div class="login-title">法催平台智能客服</div>
      <div class="login-subtitle">请选择身份进入</div>
      <el-tabs v-model="activeTab" stretch>
        <el-tab-pane label="催员登录" name="staff">
          <el-form :model="staffForm" @submit.prevent>
            <el-form-item>
              <el-input v-model="staffForm.username" placeholder="账号" size="large" />
            </el-form-item>
            <el-form-item>
              <el-input
                v-model="staffForm.password"
                type="password"
                placeholder="密码"
                size="large"
                show-password
                @keyup.enter="staffLogin"
              />
            </el-form-item>
            <el-button type="primary" class="login-btn" size="large" :loading="loading" @click="staffLogin">
              登录
            </el-button>
          </el-form>
        </el-tab-pane>
        <el-tab-pane label="债务人验证" name="debtor">
          <el-form :model="debtorForm" @submit.prevent>
            <el-form-item>
              <el-input
                v-model="debtorForm.identifier"
                placeholder="案件号或身份证号"
                size="large"
                @keyup.enter="debtorVerify"
              />
            </el-form-item>
            <el-button type="primary" class="login-btn" size="large" :loading="loading" @click="debtorVerify">
              验证进入
            </el-button>
          </el-form>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { login, verifyDebtor } from '../api/auth'
import { setAuth } from '../stores/auth'

const router = useRouter()
const activeTab = ref('staff')
const loading = ref(false)
const staffForm = reactive({ username: '', password: '' })
const debtorForm = reactive({ identifier: '' })

async function staffLogin() {
  if (!staffForm.username || !staffForm.password) {
    ElMessage.warning('请输入账号和密码')
    return
  }
  loading.value = true
  try {
    const { data } = await login(staffForm)
    setAuth(data)
    router.push('/chat')
  } catch (e) {
    // 错误提示由 http 拦截器统一处理
  } finally {
    loading.value = false
  }
}

async function debtorVerify() {
  if (!debtorForm.identifier) {
    ElMessage.warning('请输入案件号或身份证号')
    return
  }
  loading.value = true
  try {
    const { data } = await verifyDebtor(debtorForm)
    setAuth(data)
    router.push('/chat')
  } catch (e) {
    // 错误提示由 http 拦截器统一处理
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #e0ecff 0%, #f5f7fa 100%);
}
.login-card {
  width: 400px;
  padding: 12px 8px;
}
.login-title {
  font-size: 22px;
  font-weight: bold;
  color: #409eff;
  text-align: center;
}
.login-subtitle {
  color: #909399;
  text-align: center;
  margin: 8px 0 16px;
}
.login-btn {
  width: 100%;
}
</style>
