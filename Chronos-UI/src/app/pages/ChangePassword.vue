<template>
  <div class="password-page">
    <form class="password-card" @submit.prevent="submit">
      <div class="brand">CHRONOS</div>
      <h1>首次登录修改密码</h1>
      <p>当前密码为临时密码。设置新密码后，请重新登录统一门户。</p>
      <label><span>当前密码</span><input v-model="form.oldPassword" type="password" autocomplete="current-password" /></label>
      <label><span>新密码</span><input v-model="form.newPassword" type="password" autocomplete="new-password" placeholder="至少10位，包含大小写字母和数字" /></label>
      <label><span>确认新密码</span><input v-model="confirmPassword" type="password" autocomplete="new-password" /></label>
      <div v-if="error" class="error">{{ error }}</div>
      <button :disabled="loading">{{ loading ? '正在提交…' : '修改密码并重新登录' }}</button>
    </form>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { changeOwnPassword } from '../../api/admin'
import { clearAdminTokens } from '../../store/auth'
const router=useRouter(),loading=ref(false),error=ref(''),confirmPassword=ref('')
const form=reactive({oldPassword:'',newPassword:''})
const submit=async()=>{error.value='';if(!form.oldPassword||!form.newPassword){error.value='请完整填写密码';return}if(form.newPassword!==confirmPassword.value){error.value='两次输入的新密码不一致';return}loading.value=true;try{await changeOwnPassword(form);clearAdminTokens();await router.replace('/login')}catch(e){error.value=e instanceof Error?e.message:'密码修改失败'}finally{loading.value=false}}
</script>

<style scoped>
.password-page{min-height:100vh;display:grid;place-items:center;background:linear-gradient(135deg,#eef8f7,#f7fafb);padding:24px}.password-card{width:min(440px,100%);box-sizing:border-box;padding:36px;border:1px solid #dce8e7;border-radius:22px;background:#fff;box-shadow:0 20px 60px rgba(25,70,68,.12)}.brand{color:#168b83;font-weight:800;letter-spacing:3px}h1{font-size:25px;margin:12px 0 8px;color:#173b3a}p{margin:0 0 24px;color:#728482;line-height:1.6}label{display:block;margin:16px 0}label span{display:block;margin-bottom:7px;color:#405b59;font-size:14px}input{box-sizing:border-box;width:100%;padding:12px;border:1px solid #cfdcda;border-radius:10px;outline:none}input:focus{border-color:#168b83}button{width:100%;margin-top:12px;padding:13px;border:0;border-radius:10px;background:#168b83;color:white;font-weight:700;cursor:pointer}.error{color:#d84b4b;font-size:13px}
</style>
