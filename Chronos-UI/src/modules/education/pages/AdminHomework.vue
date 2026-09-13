<template><section class="redirect"><h1>作业中心</h1><p>作业布置、提交与批改请前往作业中心。</p><el-button type="primary" @click="go">进入作业中心</el-button></section></template>
<script setup>import { onMounted } from 'vue';import { useRouter } from 'vue-router';const router=useRouter();const go=()=>router.push('/portal/tasks');onMounted(go)</script>
<style scoped>.redirect{padding:32px}.redirect p{color:#667085}</style>
