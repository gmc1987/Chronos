<template>
  <div class="schedule-grid-wrap">
    <table class="schedule-grid">
      <thead><tr><th class="period-column">节次</th><th v-for="day in days" :key="day">星期{{ dayName(day) }}</th></tr></thead>
      <tbody>
        <tr v-for="period in periods" :key="period.periodNo">
          <th class="period-column"><strong>{{ period.periodName }}</strong><small v-if="period.startTime">{{ shortTime(period.startTime) }}–{{ shortTime(period.endTime) }}</small></th>
          <td v-for="day in days" :key="`${day}-${period.periodNo}`" :class="{ 'drop-active': dragOver === `${day}-${period.periodNo}` }" @dragover.prevent="dragOver = `${day}-${period.periodNo}`" @dragleave="dragOver = ''" @drop="drop(day, period.periodNo)">
            <article v-for="entry in entriesAt(day, period.periodNo)" :key="entry.id" class="course-card" :class="[`week-${entry.weekPattern?.toLowerCase() || 'all'}`, { locked: entry.locked }]" :draggable="!readonly && !entry.locked" @dragstart="startDrag(entry)" @dragend="clearDrag" @dblclick="$emit('edit', entry)">
              <div><strong>{{ entry.courseName }}</strong><el-icon v-if="entry.locked"><Lock /></el-icon></div>
              <span>{{ entry.teachingClassName }}</span><span>{{ entry.teacherName }} · {{ entry.classroomName }}</span>
              <small>{{ weekLabel(entry) }}<template v-if="entry.durationPeriods > 1"> · 连堂 {{ entry.durationPeriods }} 节</template></small>
            </article>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { Lock } from '@element-plus/icons-vue'

const props = defineProps({ entries: { type: Array, default: () => [] }, periods: { type: Array, default: () => [] }, readonly: Boolean })
const emit = defineEmits(['edit', 'move'])
const days = [1, 2, 3, 4, 5, 6, 7]; const dragging = ref(null); const dragOver = ref('')
const dayName = day => ['一', '二', '三', '四', '五', '六', '日'][day - 1]
const shortTime = value => String(value || '').slice(0, 5)
const entriesAt = (day, periodNo) => props.entries.filter(item => item.dayOfWeek === day && item.periodNo === periodNo)
const weekLabel = item => `${item.startWeek}–${item.endWeek}周${item.weekPattern === 'ODD' ? ' 单周' : item.weekPattern === 'EVEN' ? ' 双周' : ''}`
const startDrag = entry => { dragging.value = entry }
const clearDrag = () => { dragging.value = null; dragOver.value = '' }
const drop = (dayOfWeek, periodNo) => { if (dragging.value && !props.readonly) emit('move', dragging.value, { dayOfWeek, periodNo }); clearDrag() }
</script>

<style scoped>
.schedule-grid-wrap { overflow: auto; border: 1px solid #dcdfe6; border-radius: 6px; }
.schedule-grid { width: 100%; min-width: 1120px; border-collapse: collapse; table-layout: fixed; }
th, td { border-right: 1px solid #ebeef5; border-bottom: 1px solid #ebeef5; padding: 8px; vertical-align: top; }
thead th { position: sticky; top: 0; z-index: 2; background: #f5f7fa; color: #606266; text-align: center; }
.period-column { width: 96px; background: #fafafa; text-align: center; }
.period-column strong, .period-column small { display: block; }
.period-column small { margin-top: 4px; color: #909399; font-weight: normal; }
td { min-height: 96px; height: 96px; background: #fff; transition: background .15s; }
td.drop-active { background: #ecf5ff; }
.course-card { display: grid; gap: 3px; margin-bottom: 5px; padding: 7px 8px; border-left: 4px solid #409eff; border-radius: 4px; background: #ecf5ff; cursor: grab; }
.course-card > div { display: flex; justify-content: space-between; gap: 4px; }
.course-card span, .course-card small { overflow: hidden; color: #606266; font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
.course-card.week-odd { border-left-color: #e6a23c; background: #fdf6ec; }
.course-card.week-even { border-left-color: #67c23a; background: #f0f9eb; }
.course-card.locked { border-left-color: #909399; background: #f4f4f5; cursor: not-allowed; }
</style>
