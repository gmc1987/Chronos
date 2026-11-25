<template><HomeworkMistakes v-if="homeworkCustomer" /><ErrorBook v-else /></template>
<script setup>
import ErrorBook from './ErrorBook.vue'
import HomeworkMistakes from './HomeworkMistakes.vue'
import { getDeliveryContext } from '../../../store/auth'

const { mode, modules } = getDeliveryContext()
const homeworkCustomer = mode === 'customer' && modules?.includes('EDU_HOMEWORK')
</script>
