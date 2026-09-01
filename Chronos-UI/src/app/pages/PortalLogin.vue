<template>
  <div class="portal-login">
    <section class="portal-login-intro">
      <div class="portal-login-brand">CHRONOS</div>
      <h1>医院智慧协同办公平台</h1>
      <p>统一入口连接流程、办公、公文、知识与 AI，让每一项工作清晰抵达。</p>
      <div class="portal-login-points">
        <span>统一待办</span><span>智能办公</span><span>安全协同</span>
      </div>
    </section>
    <form class="portal-login-card" @submit.prevent="submit">
      <div class="portal-login-card__title">欢迎回来</div>
      <div class="portal-login-card__sub">使用医院统一账号登录</div>
      <label><span>账号</span><input v-model.trim="username" autocomplete="username" placeholder="请输入账号" /></label>
      <label><span>密码</span><input v-model="password" type="password" autocomplete="current-password" placeholder="请输入密码" /></label>
      <label class="portal-remember"><input v-model="remember" type="checkbox" />保持登录</label>
      <p v-if="error" class="portal-login-error">{{ error }}</p>
      <button :disabled="loading">{{ loading ? '正在登录…' : '登录统一门户' }}</button>
      <a href="/admin/login">管理员入口</a>
    </form>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { adminLogin } from '../../api/admin'
import { saveAdminMeta, saveAdminTokens } from '../../store/auth'

const router = useRouter()
const username = ref('')
const password = ref('')
const remember = ref(true)
const loading = ref(false)
const error = ref('')

const submit = async () => {
  if (!username.value || !password.value) { error.value = '请输入账号和密码'; return }
  loading.value = true; error.value = ''
  try {
    const res = await adminLogin({ username: username.value, password: password.value })
    if (res?.code !== '200' && res?.code !== '201') throw new Error(res?.msg || '登录失败')
    saveAdminTokens(res.data, remember.value, username.value)
    saveAdminMeta({ roles: res.data.roles, permissions: res.data.permissions, menus: res.data.menus, mustChangePassword: res.data.mustChangePassword }, remember.value)
    router.replace(res.data.mustChangePassword ? '/account/change-password' : '/portal')
  } catch (e) { error.value = e instanceof Error ? e.message : '登录失败' }
  finally { loading.value = false }
}
</script>
