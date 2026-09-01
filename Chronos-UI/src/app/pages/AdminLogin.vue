<template>
  <div class="login-page">
    <div class="login-card">
      <div class="brand">
        <div class="logo"></div>
        <div class="title">Chronos 管理后台</div>
        <div class="subtitle">账号密码登录</div>
      </div>
      <form class="form" @submit.prevent="submit">
        <label class="field">
          <span>账号</span>
          <input v-model="username" type="text" placeholder="请输入账号" autocomplete="username" />
        </label>
        <label class="field">
          <span>密码</span>
          <input v-model="password" type="password" placeholder="请输入密码" autocomplete="current-password" />
        </label>
        <div class="actions">
          <label class="remember">
            <input v-model="remember" type="checkbox" />
            记住我
          </label>
          <a class="forgot" href="javascript:void(0)">忘记密码？</a>
        </div>
        <p v-if="error" class="error">{{ error }}</p>
        <button class="submit" type="submit" :disabled="loading">
          {{ loading ? '登录中...' : '登录' }}
        </button>
      </form>
      <div class="tips">推荐使用 Chrome / Edge 浏览器访问</div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { adminLogin } from '../../api/admin'
import { saveAdminTokens, saveAdminMeta } from '../../store/auth'

const router = useRouter()
const username = ref('')
const password = ref('')
const remember = ref(true)
const loading = ref(false)
const error = ref('')

const submit = async () => {
  error.value = ''
  if (!username.value || !password.value) {
    error.value = '请输入账号与密码'
    return
  }
  loading.value = true
  try {
    const res = await adminLogin({ username: username.value, password: password.value })
    if (res?.code === '200' || res?.code === '201') {
      saveAdminTokens(res.data, remember.value, username.value)
      saveAdminMeta({ roles: res.data.roles, permissions: res.data.permissions, menus: res.data.menus, mustChangePassword: res.data.mustChangePassword }, remember.value)
      router.replace(res.data.mustChangePassword ? '/account/change-password' : '/admin/overview')
    } else {
      error.value = res?.msg || '登录失败'
    }
  } catch (err) {
    error.value = err instanceof Error ? err.message : '登录失败'
  } finally {
    loading.value = false
  }
}
</script>
