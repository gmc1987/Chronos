<template>
  <div class="rich-editor" :class="{ disabled }">
    <div v-if="!disabled" class="rich-editor__toolbar">
      <button type="button" title="加粗" @click="command('bold')"><b>B</b></button>
      <button type="button" title="斜体" @click="command('italic')"><i>I</i></button>
      <button type="button" title="下划线" @click="command('underline')"><u>U</u></button>
      <button type="button" @click="block('h2')">标题</button>
      <button type="button" @click="block('p')">正文</button>
      <button type="button" @click="command('insertUnorderedList')">项目符号</button>
      <button type="button" @click="command('insertOrderedList')">编号</button>
      <button type="button" @click="createLink">链接</button>
      <button type="button" @click="command('removeFormat')">清除格式</button>
    </div>
    <div
      ref="editor"
      class="rich-editor__content"
      :contenteditable="!disabled"
      @input="emitContent"
      @blur="emitContent"
    ></div>
  </div>
</template>

<script setup>
import { nextTick, onMounted, ref, watch } from 'vue'

const props = defineProps({
  modelValue: { type: String, default: '' },
  disabled: { type: Boolean, default: false },
})
const emit = defineEmits(['update:modelValue'])
const editor = ref(null)

const sync = async value => {
  await nextTick()
  if (editor.value && editor.value.innerHTML !== (value || '')) {
    editor.value.innerHTML = value || ''
  }
}
const emitContent = () => emit('update:modelValue', editor.value?.innerHTML || '')
const command = name => {
  editor.value?.focus()
  document.execCommand(name, false)
  emitContent()
}
const block = tag => {
  editor.value?.focus()
  document.execCommand('formatBlock', false, tag)
  emitContent()
}
const createLink = () => {
  const url = window.prompt('请输入 http:// 或 https:// 链接')
  if (!url || !/^https?:\/\//i.test(url)) return
  editor.value?.focus()
  document.execCommand('createLink', false, url)
  emitContent()
}

watch(() => props.modelValue, sync)
onMounted(() => sync(props.modelValue))
</script>

<style scoped>
.rich-editor { overflow: hidden; width: 100%; border: 1px solid #dcdfe6; border-radius: 4px; background: #fff; }
.rich-editor:focus-within { border-color: #409eff; }
.rich-editor.disabled { background: #f5f7fa; }
.rich-editor__toolbar { display: flex; flex-wrap: wrap; gap: 4px; padding: 7px; border-bottom: 1px solid #ebeef5; background: #fafafa; }
.rich-editor__toolbar button { min-width: 30px; padding: 4px 7px; border: 1px solid #dcdfe6; border-radius: 3px; background: #fff; color: #46515a; cursor: pointer; }
.rich-editor__content { min-height: 240px; padding: 12px 14px; overflow-wrap: anywhere; color: #303133; line-height: 1.75; outline: none; }
.rich-editor__content :deep(table) { width: 100%; border-collapse: collapse; }
.rich-editor__content :deep(td), .rich-editor__content :deep(th) { padding: 6px; border: 1px solid #dcdfe6; }
</style>
