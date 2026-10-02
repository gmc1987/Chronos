<template>
  <div class="weekly-schedule">
    <div class="weekly-schedule__heading">
      <div>
        <strong>{{ calendar.termName || '本周课表' }}</strong>
        <small>{{ weekStart }} 至 {{ weekEnd }}</small>
      </div>
      <button type="button" @click="router.push('/portal/education/schedule')">完整课表 →</button>
    </div>

    <div v-if="loading" class="weekly-schedule__state">正在加载个人课表…</div>
    <div v-else-if="error" class="weekly-schedule__state weekly-schedule__state--error">
      {{ error }}
      <button type="button" @click="load">重试</button>
    </div>
    <div v-else class="weekly-schedule__grid">
      <section
        v-for="day in weekDays"
        :key="day.date"
        :class="{ 'is-today': day.date === today }"
      >
        <header>
          <strong>{{ day.label }}</strong>
          <span>{{ day.date.slice(5) }}</span>
        </header>
        <div v-if="!lessonsByDate[day.date]?.length" class="weekly-schedule__empty">无课</div>
        <button
          v-for="lesson in lessonsByDate[day.date] || []"
          :key="lesson.occurrenceKey"
          type="button"
          :class="[
            'weekly-lesson',
            `weekly-lesson--${String(lesson.occurrenceStatus || '').toLowerCase()}`,
            { 'is-preparing': lesson.occurrenceKey === preparingLesson?.occurrenceKey },
          ]"
          @click="router.push('/portal/education/schedule')"
        >
          <span>
            {{ lesson.startTime ? `${lesson.startTime}-${lesson.endTime}` : `第 ${lesson.effectivePeriodNo} 节` }}
            <b v-if="lesson.occurrenceKey === preparingLesson?.occurrenceKey">待上课</b>
          </span>
          <strong>{{ lesson.entry.courseName }}</strong>
          <small>{{ lesson.entry.teachingClassName }}</small>
          <small>{{ lesson.entry.classroomName || '教室待定' }}</small>
        </button>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { portalEducationScheduleCalendar } from '../../../api/portal'

const router = useRouter()
const loading = ref(true)
const error = ref('')
const calendar = ref({})

const formatDate = value => {
  const year = value.getFullYear()
  const month = String(value.getMonth() + 1).padStart(2, '0')
  const day = String(value.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}
const now = new Date()
const today = formatDate(now)
const monday = new Date(now.getFullYear(), now.getMonth(), now.getDate())
monday.setDate(monday.getDate() + (monday.getDay() === 0 ? -6 : 1 - monday.getDay()))
const weekStart = formatDate(monday)
const sunday = new Date(monday)
sunday.setDate(sunday.getDate() + 6)
const weekEnd = formatDate(sunday)
const weekDays = Array.from({ length: 7 }, (_, index) => {
  const date = new Date(monday)
  date.setDate(date.getDate() + index)
  return { date: formatDate(date), label: `周${'一二三四五六日'[index]}` }
})
const activeLessons = computed(() => (calendar.value.occurrences || [])
  .filter(item => !['CANCELLED', 'MOVED_OUT'].includes(item.occurrenceStatus))
  .sort((left, right) => lessonStart(left).getTime() - lessonStart(right).getTime()))
const lessonsByDate = computed(() => (calendar.value.occurrences || []).reduce((result, item) => {
  if (!result[item.date]) result[item.date] = []
  result[item.date].push(item)
  result[item.date].sort((left, right) => left.effectivePeriodNo - right.effectivePeriodNo)
  return result
}, {}))
const lessonStart = lesson => new Date(
  `${lesson.date}T${lesson.startTime || '23:59:59'}`,
)
const preparingLesson = computed(() => activeLessons.value.find(
  lesson => lessonStart(lesson).getTime() >= Date.now(),
))

const load = async () => {
  loading.value = true
  error.value = ''
  try {
    const studentId = localStorage.getItem('chronos.portal.education.studentId') || ''
    let response
    try {
      response = await portalEducationScheduleCalendar(studentId, weekStart, weekEnd)
    } catch (requestError) {
      if (!studentId) throw requestError
      localStorage.removeItem('chronos.portal.education.studentId')
      response = await portalEducationScheduleCalendar('', weekStart, weekEnd)
    }
    calendar.value = response.data || {}
  } catch (e) {
    error.value = e instanceof Error ? e.message : '个人课表加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.weekly-schedule { display:grid; gap:12px; }
.weekly-schedule__heading { display:flex; align-items:center; justify-content:space-between; gap:12px; }
.weekly-schedule__heading > div { display:grid; gap:3px; }
.weekly-schedule__heading strong { color:#29444f; font-size:14px; }
.weekly-schedule__heading small { color:#8a989e; font-size:11px; }
.weekly-schedule__heading button,
.weekly-schedule__state button { border:0; background:transparent; color:#147765; cursor:pointer; }
.weekly-schedule__grid { display:grid; grid-template-columns:repeat(7, minmax(118px, 1fr)); gap:8px; overflow-x:auto; }
.weekly-schedule__grid > section { min-height:142px; padding:8px; border:1px solid #edf0f1; border-radius:10px; background:#fafcfc; }
.weekly-schedule__grid > section.is-today { border-color:#71b9ad; background:#f3fbf9; }
.weekly-schedule__grid header { display:flex; justify-content:space-between; margin-bottom:7px; color:#71838a; font-size:11px; }
.weekly-schedule__empty,
.weekly-schedule__state { display:grid; min-height:110px; place-content:center; color:#9aa5aa; font-size:12px; }
.weekly-schedule__state--error { gap:7px; color:#bd503b; }
.weekly-lesson { width:100%; display:grid; gap:2px; margin-bottom:6px; padding:7px; border:0; border-left:3px solid #82a5ad; border-radius:7px; background:#fff; color:#71838a; text-align:left; cursor:pointer; }
.weekly-lesson > span { display:flex; align-items:center; justify-content:space-between; gap:4px; font-size:10px; }
.weekly-lesson > span b { border-radius:8px; padding:2px 5px; background:#f1993a; color:#fff; font-size:9px; }
.weekly-lesson > strong { overflow:hidden; color:#2f4751; font-size:12px; text-overflow:ellipsis; white-space:nowrap; }
.weekly-lesson > small { overflow:hidden; font-size:10px; text-overflow:ellipsis; white-space:nowrap; }
.weekly-lesson.is-preparing { border-left-color:#f1993a; background:#fff8ed; box-shadow:0 0 0 1px #f4c98f; }
.weekly-lesson--cancelled,
.weekly-lesson--moved_out { opacity:.5; text-decoration:line-through; }
@media (max-width:1100px) { .weekly-schedule__grid { grid-template-columns:repeat(7, 132px); } }
</style>
