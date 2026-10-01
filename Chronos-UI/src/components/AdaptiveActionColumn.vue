<template>
  <el-table-column
    v-bind="attrs"
    :label="label"
    :width="columnWidth"
    :class-name="`${className || ''} adaptive-action-column`.trim()"
  >
    <template #default="scope">
      <div :ref="observeRow" class="adaptive-action-buttons">
        <slot v-bind="scope" />
      </div>
    </template>
  </el-table-column>
</template>

<script setup>
import { nextTick, onBeforeUnmount, onUpdated, ref, useAttrs } from 'vue'

defineOptions({ inheritAttrs: false })
defineProps({
  label: { type: String, default: '操作' },
  // Existing fixed widths are accepted for compatibility; visible buttons determine the width.
  width: [String, Number],
  minWidth: [String, Number],
  className: String,
})

const attrs = useAttrs()
const columnWidth = ref(80)
const rows = new Set()
let frame = 0

const measure = () => {
  frame = 0
  let width = 80
  for (const row of rows) {
    if (!row.isConnected) {
      observer?.unobserve(row)
      rows.delete(row)
      continue
    }
    width = Math.max(width, Math.ceil(row.getBoundingClientRect().width + 28))
  }
  if (columnWidth.value !== width) columnWidth.value = width
}

const scheduleMeasure = () => {
  if (!frame) frame = requestAnimationFrame(measure)
}

const observer = typeof ResizeObserver === 'undefined'
  ? null
  : new ResizeObserver(scheduleMeasure)

const observeRow = element => {
  if (!element || rows.has(element)) return
  rows.add(element)
  observer?.observe(element)
  scheduleMeasure()
}

onUpdated(() => nextTick(scheduleMeasure))
onBeforeUnmount(() => {
  if (frame) cancelAnimationFrame(frame)
  observer?.disconnect()
  rows.clear()
})
</script>
