<template>
  <div class="page">
    <header>
      <div>
        <h2>走班排课</h2>
        <p>按教学班安排教师、教室、星期、节次和授课周次，保存时自动检查冲突。</p>
      </div>
      <div class="scheduling-header-actions">
        <SchedulingModeSwitch :semester-code="semesterCode" />
        <el-select v-model="semesterCode" placeholder="选择学期" @change="loadAll">
          <el-option v-for="term in terms" :key="term.id" :label="term.termName" :value="term.termCode" />
        </el-select>
      </div>
    </header>

    <el-tabs v-model="activeTab">
      <el-tab-pane v-if="!reviewOnly" label="周课表" name="schedule">
        <div class="schedule-toolbar">
          <div class="dimension-filter">
            <el-select v-model="scheduleDimension" class="dimension-select" @change="changeDimension">
              <el-option label="全校课表" value="ALL" />
              <el-option label="教师课表" value="TEACHER" />
              <el-option label="教学班课表" value="TEACHING_CLASS" />
              <el-option label="行政班课表" value="ADMIN_CLASS" />
              <el-option label="学生课表" value="STUDENT" />
              <el-option label="教室课表" value="CLASSROOM" />
            </el-select>
            <el-select
              v-if="scheduleDimension === 'STUDENT'"
              v-model="scheduleTargetId"
              class="target-select"
              filterable
              remote
              clearable
              :remote-method="searchStudentOptions"
              :loading="studentOptionsLoading"
              :placeholder="studentOptionsError ? '学生加载失败，请重试' : '搜索姓名或学号选择学生'"
              @visible-change="onStudentSelectVisible"
              @change="changeScheduleTarget">
              <el-option
                v-for="item in studentOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value" />
              <template #footer>
                <div class="student-option-footer">
                  <span>共 {{ studentTotal }} 人</span>
                  <el-pagination
                    v-model:current-page="studentPage"
                    :page-size="10"
                    :total="studentTotal"
                    :pager-count="5"
                    size="small"
                    layout="prev, pager, next"
                    @current-change="loadStudentOptions" />
                </div>
              </template>
            </el-select>
            <el-select
              v-else-if="scheduleDimension !== 'ALL'"
              v-model="scheduleTargetId"
              class="target-select"
              filterable
              clearable
              :loading="dimensionOptionsLoading"
              :disabled="dimensionOptionsLoading || dimensionOptionsError"
              :placeholder="scheduleTargetPlaceholder"
              @change="loadSchedule">
              <el-option
                v-for="item in dimensionOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value" />
            </el-select>
            <el-button v-if="studentOptionsError && scheduleDimension === 'STUDENT'" link type="danger" @click="loadStudentOptions">学生加载失败，重试</el-button>
            <el-button v-if="dimensionOptionsError && scheduleDimension !== 'ALL' && scheduleDimension !== 'STUDENT'" link type="danger" @click="loadDimensionOptions">选项加载失败，重试</el-button>
          </div>
          <div class="schedule-actions">
            <el-radio-group v-model="scheduleView" class="view-switch">
              <el-radio-button value="grid">网格</el-radio-button>
              <el-radio-button value="list">列表</el-radio-button>
            </el-radio-group>
            <el-button v-permission="['education:scheduling:create', 'education:scheduling:manage']" @click="downloadImportTemplate">下载导入模板</el-button>
            <el-button v-permission="['education:scheduling:create', 'education:scheduling:manage']" type="warning" @click="chooseImportFile">导入课表</el-button>
            <el-button v-permission="['education:scheduling:view', 'education:scheduling:manage']" @click="exportSchedule">导出当前课表</el-button>
            <el-button v-permission="['education:scheduling:manage']" type="primary" :loading="candidateGenerating" @click="generateFullSchedule">按规则全量自动排课</el-button>
            <el-button type="success" @click="publishVersion">发布当前课表</el-button>
            <el-button type="primary" @click="openEntry()">新增排课</el-button>
            <input ref="scheduleFileInput" type="file" accept=".xlsx" hidden @change="importSchedule" />
          </div>
        </div>
        <el-empty
          v-if="scheduleDimension !== 'ALL' && !scheduleTargetId"
          description="选择教师、班级、学生或教室后查看课表" />
        <ScheduleGrid v-if="(scheduleDimension === 'ALL' || scheduleTargetId) && scheduleView === 'grid'" :entries="schedule" :periods="schedulePeriods" @edit="openEntry" @move="moveEntry" />
        <el-table v-else-if="scheduleDimension === 'ALL' || scheduleTargetId" :data="schedule">
          <el-table-column label="时间" width="160">
            <template #default="scope">周{{ dayName(scope.row.dayOfWeek) }} 第 {{ scope.row.periodNo }} 节</template>
          </el-table-column>
          <el-table-column prop="courseName" label="课程" />
          <el-table-column prop="teachingClassName" label="教学班" />
          <el-table-column label="组班方式" width="100">
            <template #default="scope">{{ scope.row.offeringMode === 'COMBINED' ? '合班' : '普通' }}</template>
          </el-table-column>
          <el-table-column prop="teacherName" label="教师" width="120" />
          <el-table-column prop="classroomName" label="教室" width="140" />
          <el-table-column label="周次" width="120">
            <template #default="scope">{{ scope.row.startWeek }}–{{ scope.row.endWeek }} 周</template>
          </el-table-column>
          <AdaptiveActionColumn label="操作" width="140">
            <template #default="scope">
              <el-button link @click="openEntry(scope.row)">编辑</el-button>
              <el-button link type="danger" @click="removeEntry(scope.row)">删除</el-button>
            </template>
          </AdaptiveActionColumn>
        </el-table>
      </el-tab-pane>

      <el-tab-pane v-if="!reviewOnly" label="日期课表" name="date-schedule">
        <div class="toolbar"><el-date-picker v-model="occurrenceDate" value-format="YYYY-MM-DD" @change="loadOccurrences" /><el-button @click="loadDateSchedule">刷新</el-button></div>
        <el-table :data="occurrences" border><el-table-column label="时间" width="110"><template #default="s">第 {{ s.row.effectivePeriodNo }} 节</template></el-table-column><el-table-column label="课程"><template #default="s">{{ s.row.entry.courseName }}</template></el-table-column><el-table-column label="教学班"><template #default="s">{{ s.row.entry.teachingClassName }}</template></el-table-column><el-table-column label="教师"><template #default="s">{{ s.row.entry.teacherName }}</template></el-table-column><el-table-column label="状态" width="110"><template #default="s"><el-tag :type="occurrenceStatusType(s.row.occurrenceStatus)">{{ occurrenceStatusName(s.row.occurrenceStatus) }}</el-tag></template></el-table-column><el-table-column prop="reason" label="变更原因" /><AdaptiveActionColumn label="操作" width="100"><template #default="s"><el-button link type="primary" @click="openDateException(s.row)">日期调整</el-button></template></AdaptiveActionColumn></el-table>
        <h3>节假日待补课（{{ pendingMakeups.length }}）</h3>
        <el-alert type="info" :closable="false" title="非教学日的原课次不会自动挤占后续课表；选择提前或顺延的目标日期、节次后按实际日期检查冲突。" />
        <el-table :data="pendingMakeups" border empty-text="当前没有待补课次">
          <el-table-column prop="sourceDate" label="原上课日期" width="130" />
          <el-table-column label="课程" min-width="150"><template #default="s">{{ s.row.entry.courseName }}</template></el-table-column>
          <el-table-column label="教学班" min-width="180"><template #default="s">{{ s.row.entry.teachingClassName }}</template></el-table-column>
          <el-table-column label="教师" width="120"><template #default="s">{{ s.row.entry.teacherName }}</template></el-table-column>
          <el-table-column label="原节次" width="90"><template #default="s">第 {{ s.row.entry.periodNo }} 节</template></el-table-column>
          <AdaptiveActionColumn label="操作" width="170"><template #default="s"><el-button link type="primary" @click="openMakeup(s.row)">安排补课</el-button><el-button link @click="openWaiveMakeup(s.row)">确认不补</el-button></template></AdaptiveActionColumn>
        </el-table>
        <h3>教师请假待代课（{{ pendingSubstitutions.length }}）</h3>
        <el-table :data="pendingSubstitutions" border empty-text="当前没有待安排代课的课次">
          <el-table-column prop="date" label="上课日期" width="130" />
          <el-table-column label="课程" min-width="150"><template #default="s">{{ s.row.occurrence.entry.courseName }}</template></el-table-column>
          <el-table-column label="教学班" min-width="180"><template #default="s">{{ s.row.occurrence.entry.teachingClassName }}</template></el-table-column>
          <el-table-column label="请假教师" width="120"><template #default="s">{{ s.row.occurrence.entry.teacherName }}</template></el-table-column>
          <el-table-column label="节次" width="90"><template #default="s">第 {{ s.row.occurrence.effectivePeriodNo }} 节</template></el-table-column>
          <AdaptiveActionColumn label="操作" width="150"><template #default="s"><el-button v-if="s.row.occurrence.occurrenceStatus === 'SCHEDULED'" link type="primary" @click="openSubstitution(s.row)">安排代课</el-button><span v-else>先处理原日期调整</span></template></AdaptiveActionColumn>
        </el-table>
        <h3>日期调整历史</h3>
        <el-table :data="dateExceptionHistory" border>
          <el-table-column prop="sourceDate" label="原日期" width="120" />
          <el-table-column prop="exceptionType" label="类型" width="100" />
          <el-table-column prop="targetDate" label="目标日期" width="120" />
          <el-table-column prop="targetPeriodNo" label="目标节次" width="100" />
          <el-table-column prop="reason" label="原因" />
          <el-table-column prop="status" label="状态" width="100" />
          <AdaptiveActionColumn label="操作" width="100">
            <template #default="scope">
              <el-button v-if="scope.row.status === 'ACTIVE'" link type="danger" @click="cancelDateException(scope.row)">撤销</el-button>
              <el-button v-else link type="primary" @click="restoreDateException(scope.row)">恢复</el-button>
            </template>
          </AdaptiveActionColumn>
        </el-table>
      </el-tab-pane>

      <el-tab-pane v-if="!reviewOnly" label="发布版本" name="versions">
        <div class="toolbar"><el-button @click="loadVersions">刷新</el-button></div>
        <el-table :data="versions">
          <el-table-column prop="versionNo" label="版本" width="90">
            <template #default="scope">V{{ scope.row.versionNo }}</template>
          </el-table-column>
          <el-table-column prop="entryCount" label="课表项" width="100" />
          <el-table-column prop="publishedBy" label="发布人" width="130" />
          <el-table-column label="发布时间" min-width="180">
            <template #default="scope">{{ formatTime(scope.row.publishedAt) }}</template>
          </el-table-column>
          <el-table-column label="来源" width="120">
            <template #default="scope">{{ scope.row.sourceVersionNo ? `回滚自 V${scope.row.sourceVersionNo}` : '当前草稿' }}</template>
          </el-table-column>
          <AdaptiveActionColumn label="操作" width="100">
            <template #default="scope"><el-button link type="warning" @click="rollbackVersion(scope.row)">回滚</el-button></template>
          </AdaptiveActionColumn>
        </el-table>
      </el-tab-pane>

      <el-tab-pane v-if="!reviewOnly" label="质量分析" name="quality">
        <div class="toolbar"><el-button @click="loadQualityAnalysis">重新分析</el-button></div>
        <div class="quality-summary">
          <el-statistic title="排课项" :value="quality.summary?.entryCount || 0" />
          <el-statistic title="已排课时" :value="quality.summary?.scheduledLessons || 0" />
          <el-statistic title="教师数" :value="quality.summary?.teacherCount || 0" />
          <el-statistic title="教室数" :value="quality.summary?.classroomCount || 0" />
          <el-statistic title="风险项" :value="quality.summary?.riskCount || 0" />
        </div>
        <h3>教师负载</h3>
        <el-table :data="quality.teacherLoads || []" border>
          <el-table-column prop="teacherName" label="教师" />
          <el-table-column label="周课时"><template #default="s">{{ s.row.weeklyLessons }} / {{ s.row.weeklyLimit }}</template></el-table-column>
          <el-table-column label="日峰值"><template #default="s">{{ s.row.peakDailyLessons }} / {{ s.row.dailyLimit }}</template></el-table-column>
          <el-table-column label="最长连堂"><template #default="s">{{ s.row.longestConsecutive }} / {{ s.row.consecutiveLimit }}</template></el-table-column>
          <el-table-column prop="campusSwitchDays" label="跨校区天数" />
          <el-table-column prop="loadRate" label="负载率"><template #default="s">{{ s.row.loadRate }}%</template></el-table-column>
          <el-table-column label="状态"><template #default="s"><el-tag :type="s.row.overloaded ? 'danger' : 'success'">{{ s.row.overloaded ? '超限' : '正常' }}</el-tag></template></el-table-column>
        </el-table>
        <h3>教室利用率</h3>
        <el-table :data="quality.roomUtilization || []" border>
          <el-table-column prop="classroomName" label="教室" />
          <el-table-column prop="occupiedLessons" label="学期占用课时" />
          <el-table-column prop="availableLessons" label="学期可用课时" />
          <el-table-column prop="utilizationRate" label="利用率"><template #default="s">{{ s.row.utilizationRate }}%</template></el-table-column>
        </el-table>
        <h3>风险清单</h3>
        <el-empty v-if="!quality.risks?.length" description="当前课表未发现质量风险" />
        <el-table v-else :data="quality.risks" border>
          <el-table-column prop="level" label="等级" width="100" />
          <el-table-column prop="type" label="类型" width="180" />
          <el-table-column prop="message" label="说明" />
        </el-table>
      </el-tab-pane>

      <el-tab-pane v-if="!reviewOnly" label="规则配置" name="policy">
        <el-alert
          title="规则按学期生效。教师档案设置了个人上限时优先使用个人值，未设置时使用本页默认值。"
          type="info"
          :closable="false"
          show-icon />
        <el-form class="policy-form" label-width="180px">
          <h3>全量自动排课范围</h3>
          <el-form-item label="每周上课天数"><el-input-number v-model="policy.teachingDaysPerWeek" :min="1" :max="7" /></el-form-item>
          <el-form-item label="每日排课节数"><el-input-number v-model="policy.periodsPerDay" :min="1" :max="20" /></el-form-item>
          <el-alert title="一键全量排课使用本页规则和学期周数，自动覆盖本学期全部启用的教学任务。" type="info" :closable="false" show-icon />
          <h3>教师默认硬约束</h3>
          <el-form-item label="每周课时上限"><el-input-number v-model="policy.defaultMaxWeeklyLessons" :min="1" :max="100" /></el-form-item>
          <el-form-item label="每日课时上限"><el-input-number v-model="policy.defaultMaxDailyLessons" :min="1" :max="20" /></el-form-item>
          <el-form-item label="连续授课上限"><el-input-number v-model="policy.defaultMaxConsecutiveLessons" :min="1" :max="10" /></el-form-item>
          <el-form-item label="同课程单日集中阈值"><el-input-number v-model="policy.courseConcentrationThreshold" :min="1" :max="20" /></el-form-item>
          <h3>候选方案评分权重</h3>
          <el-form-item label="已排课时奖励"><el-input-number v-model="policy.scheduledLessonReward" :min="0" :max="100000" /></el-form-item>
          <el-form-item label="偏好时段奖励"><el-input-number v-model="policy.preferredSlotReward" :min="0" :max="100000" /></el-form-item>
          <el-form-item label="同课程同日惩罚"><el-input-number v-model="policy.sameCourseDayPenalty" :min="0" :max="100000" /></el-form-item>
          <el-form-item label="教师日负载惩罚"><el-input-number v-model="policy.teacherLoadPenalty" :min="0" :max="100000" /></el-form-item>
          <el-form-item label="连续授课惩罚"><el-input-number v-model="policy.consecutivePenalty" :min="0" :max="100000" /></el-form-item>
          <el-form-item label="跨校区切换惩罚"><el-input-number v-model="policy.campusSwitchPenalty" :min="0" :max="100000" /></el-form-item>
          <el-form-item label="教师空档惩罚"><el-input-number v-model="policy.teacherGapPenalty" :min="0" :max="100000" /></el-form-item>
          <el-form-item label="跨校区最少间隔节次"><el-input-number v-model="policy.minimumCampusTravelPeriods" :min="0" :max="10" /></el-form-item>
          <el-form-item label="未排课时惩罚"><el-input-number v-model="policy.unscheduledLessonPenalty" :min="0" :max="100000" /></el-form-item>
          <h3>发布门禁</h3>
          <el-form-item label="阻止时间硬冲突发布"><el-switch v-model="policy.blockHardConflicts" /></el-form-item>
          <el-form-item label="阻止教学任务未排满发布"><el-switch v-model="policy.blockIncompleteOfferings" /></el-form-item>
          <el-form-item label="阻止教师课时超限发布"><el-switch v-model="policy.blockTeacherOverload" /></el-form-item>
          <el-form-item><el-button v-permission="['education:scheduling:manage']" type="primary" @click="savePolicy">保存本学期规则</el-button></el-form-item>
        </el-form>
      </el-tab-pane>

      <el-tab-pane label="自动排课候选" name="candidates">
        <div class="toolbar">
          <el-button @click="loadCandidates">刷新</el-button>
          <el-button v-permission="['education:scheduling:manage']" type="primary" :loading="candidateGenerating" @click="generateFullSchedule">按规则全量自动排课</el-button>
          <el-button
            v-permission="['education:scheduling:manage']"
            @click="openCandidateDialog">
            自定义生成 / 局部重排
          </el-button>
          <el-button :disabled="candidateSelection.length < 2" @click="compareCandidates">
            对比方案（{{ candidateSelection.length }}）
          </el-button>
        </div>
        <el-alert
          title="候选方案生成不会直接修改当前课表。预览差异并确认应用后，才会替换当前草稿。"
          type="info"
          :closable="false"
          show-icon
        />
        <h3>后台生成任务</h3>
        <el-table :data="generationJobs" size="small" border>
          <el-table-column prop="requestedBy" label="提交人" width="130" />
          <el-table-column label="状态" width="110"><template #default="s"><el-tag :type="jobStatusType(s.row.status)">{{ jobStatusName(s.row.status) }}</el-tag></template></el-table-column>
          <el-table-column label="进度" min-width="180"><template #default="s"><el-progress :percentage="s.row.progress || 0" /></template></el-table-column>
          <el-table-column prop="errorMessage" label="失败/取消原因" min-width="220" show-overflow-tooltip />
          <AdaptiveActionColumn label="操作" width="90"><template #default="s"><el-button v-if="['QUEUED', 'RUNNING'].includes(s.row.status)" v-permission="['education:scheduling:manage']" link type="danger" @click="cancelGenerationJob(s.row)">取消</el-button></template></AdaptiveActionColumn>
        </el-table>
        <h3>候选方案</h3>
        <el-empty v-if="!candidates.length" description="暂无自动排课候选方案" />
        <el-table v-else :data="candidates" class="candidate-table" @selection-change="candidateSelection = $event">
          <el-table-column
            type="selection"
            width="46"
            :selectable="row => candidateSelection.some(item => item.id === row.id) || candidateSelection.length < 5" />
          <el-table-column prop="planName" label="方案名称" min-width="180" />
          <el-table-column label="模式" width="100">
            <template #default="scope">
              {{ scope.row.generationMode === 'LOCAL' ? '局部重排' : '全量排课' }}
            </template>
          </el-table-column>
          <el-table-column label="评分" width="150">
            <template #default="scope">
              <el-popover placement="right" width="300" trigger="hover">
                <template #reference><el-button link type="primary">{{ scope.row.totalScore }} 分</el-button></template>
                <div class="score-detail">
                  <span>已排课时：{{ scope.row.metrics?.scheduledLessons || 0 }}</span>
                  <span>教师偏好命中：{{ scope.row.metrics?.preferredSlotHits || 0 }}</span>
                  <span>同课程同日惩罚：{{ scope.row.metrics?.sameCourseDayPenalty || 0 }}</span>
                  <span>教师日负载惩罚：{{ scope.row.metrics?.teacherLoadPenalty || 0 }}</span>
                  <span>连续授课惩罚：{{ scope.row.metrics?.consecutivePenalty || 0 }}</span>
                  <span>跨校区切换惩罚：{{ scope.row.metrics?.campusSwitchPenalty || 0 }}</span>
                  <span>教师空档惩罚：{{ scope.row.metrics?.teacherGapPenalty || 0 }}</span>
                  <span>未排课时：{{ scope.row.metrics?.unscheduledLessons || 0 }}</span>
                </div>
              </el-popover>
            </template>
          </el-table-column>
          <el-table-column prop="entryCount" label="课表项" width="90" />
          <el-table-column prop="unscheduledLessons" label="未排课时" width="100">
            <template #default="scope">
              <el-tag :type="scope.row.unscheduledLessons ? 'danger' : 'success'">
                {{ scope.row.unscheduledLessons }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="协作状态" width="120">
            <template #default="scope"><el-tag>{{ reviewStatusName(scope.row.reviewStatus) }}</el-tag></template>
          </el-table-column>
          <el-table-column prop="ownerUsername" label="负责人" width="130" />
          <el-table-column label="生成时间" min-width="170">
            <template #default="scope">{{ formatTime(scope.row.generatedAt) }}</template>
          </el-table-column>
          <AdaptiveActionColumn label="操作" width="420" fixed="right">
            <template #default="scope">
              <el-button link @click="showCandidateDiff(scope.row)">预览差异</el-button>
              <template v-if="scope.row.status === 'CANDIDATE'">
                <el-button v-if="['DRAFT', 'REJECTED'].includes(scope.row.reviewStatus)" v-permission="['education:scheduling:manage']" link @click="editCandidateGovernance(scope.row)">协作信息</el-button>
                <el-button v-if="['DRAFT', 'REJECTED'].includes(scope.row.reviewStatus)" v-permission="['education:scheduling:manage']" link type="warning" @click="submitCandidateReview(scope.row)">提交审核</el-button>
                <el-button v-if="scope.row.reviewStatus === 'SUBMITTED' && scope.row.ownerUsername !== getAdminUsername()" v-permission="['education:scheduling:review', 'education:scheduling:manage']" link type="success" @click="reviewCandidate(scope.row, true)">通过</el-button>
                <el-button v-if="scope.row.reviewStatus === 'SUBMITTED' && scope.row.ownerUsername !== getAdminUsername()" v-permission="['education:scheduling:review', 'education:scheduling:manage']" link type="danger" @click="reviewCandidate(scope.row, false)">驳回</el-button>
                <span v-if="scope.row.reviewStatus === 'SUBMITTED' && scope.row.ownerUsername === getAdminUsername()">请其他审核员处理</span>
                <el-button
                  v-permission="['education:scheduling:manage']"
                  link
                  type="primary"
                  :disabled="scope.row.unscheduledLessons > 0 || scope.row.reviewStatus !== 'APPROVED'"
                  @click="applyCandidate(scope.row)">
                  应用
                </el-button>
                <el-button
                  v-permission="['education:scheduling:manage']"
                  link
                  type="danger"
                  @click="discardCandidate(scope.row)">
                  废弃
                </el-button>
              </template>
            </template>
          </AdaptiveActionColumn>
        </el-table>
      </el-tab-pane>

      <el-tab-pane v-if="!reviewOnly" label="教学任务" name="offerings">
        <div class="toolbar"><el-button type="primary" @click="openOffering()">新增教学任务</el-button><el-button @click="router.push('/admin/education/teaching-class-members')">教学班成员</el-button></div>
        <el-table :data="offerings">
          <el-table-column prop="offeringCode" label="教学班编码" />
          <el-table-column prop="courseName" label="课程" />
          <el-table-column prop="teachingClassName" label="教学班" />
          <el-table-column prop="teacherName" label="教师" />
          <el-table-column prop="studentCount" label="人数" width="90" />
          <el-table-column prop="weeklyLessons" label="周课时" width="90" />
          <AdaptiveActionColumn label="操作" width="230">
            <template #default="scope">
              <el-button link @click="openOffering(scope.row)">编辑</el-button>
              <el-button link type="primary" @click="openCombined(scope.row)">合班设置</el-button>
              <el-button link type="danger" @click="removeOffering(scope.row)">删除</el-button>
            </template>
          </AdaptiveActionColumn>
        </el-table>
        <el-pagination
          v-model:current-page="offeringPage"
          v-model:page-size="offeringPageSize"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          :total="offeringTotal"
          @size-change="changeOfferingPageSize"
          @current-change="loadOfferingsPage"
        />
      </el-tab-pane>

      <el-tab-pane v-if="!reviewOnly" label="教室" name="classrooms">
        <div class="toolbar"><el-button type="primary" @click="openClassroom()">新增教室</el-button></div>
        <el-table :data="classrooms">
          <el-table-column prop="roomCode" label="编码" />
          <el-table-column prop="roomName" label="名称" />
          <el-table-column prop="buildingName" label="教学楼" />
          <el-table-column prop="roomType" label="类型" />
          <el-table-column prop="capacity" label="容量" width="90" />
          <AdaptiveActionColumn label="操作" width="140">
            <template #default="scope">
              <el-button link @click="openClassroom(scope.row)">编辑</el-button>
              <el-button link type="danger" @click="removeClassroom(scope.row)">删除</el-button>
            </template>
          </AdaptiveActionColumn>
        </el-table>
        <el-pagination
          v-model:current-page="classroomPage"
          v-model:page-size="classroomPageSize"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          :total="classroomTotal"
          @size-change="changeClassroomPageSize"
          @current-change="loadClassroomsPage"
        />
      </el-tab-pane>

      <el-tab-pane v-if="!reviewOnly" label="教师时间约束" name="constraints">
        <div class="toolbar"><el-button type="primary" @click="openConstraint()">新增时间约束</el-button></div>
        <el-table :data="teacherConstraints" border>
          <el-table-column label="教师" min-width="150"><template #default="s">{{ teacherName(s.row.teacherId) }}</template></el-table-column>
          <el-table-column label="时间" width="160"><template #default="s">星期{{ dayName(s.row.dayOfWeek) }} 第 {{ s.row.periodNo }} 节</template></el-table-column>
          <el-table-column label="约束类型" width="110"><template #default="s"><el-tag :type="s.row.constraintType === 'FORBIDDEN' ? 'danger' : 'success'">{{ s.row.constraintType === 'FORBIDDEN' ? '禁止排课' : '优先安排' }}</el-tag></template></el-table-column>
          <el-table-column prop="weight" label="权重" width="80" /><el-table-column prop="reason" label="原因" min-width="180" />
          <AdaptiveActionColumn label="操作" width="130"><template #default="s"><el-button link type="primary" @click="openConstraint(s.row)">编辑</el-button><el-button link type="danger" @click="removeConstraint(s.row)">删除</el-button></template></AdaptiveActionColumn>
        </el-table>
      </el-tab-pane>

      <el-tab-pane v-if="!reviewOnly" label="教室不可用时段" name="room-constraints">
        <div class="toolbar"><el-button type="primary" @click="openRoomConstraint()">新增不可用时段</el-button></div>
        <el-table :data="roomConstraints" border><el-table-column label="教室"><template #default="s">{{ classroomName(s.row.classroomId) }}</template></el-table-column><el-table-column label="星期" width="90"><template #default="s">星期{{ dayName(s.row.dayOfWeek) }}</template></el-table-column><el-table-column label="节次" width="120"><template #default="s">第 {{ s.row.startPeriod }}–{{ s.row.endPeriod }} 节</template></el-table-column><el-table-column prop="reason" label="原因" /><AdaptiveActionColumn label="操作" width="130"><template #default="s"><el-button link type="primary" @click="openRoomConstraint(s.row)">编辑</el-button><el-button link type="danger" @click="removeRoomConstraint(s.row)">删除</el-button></template></AdaptiveActionColumn></el-table>
      </el-tab-pane>

	  <el-tab-pane v-if="!reviewOnly" label="调课回写异常" name="incidents">
		<div class="incident-toolbar">
		  <div class="incident-filters">
			<el-select v-model="incidentStatus" class="incident-status" @change="searchIncidents">
			  <el-option label="待处理" value="FAILED" />
			  <el-option label="已处理" value="APPLIED" />
			  <el-option label="全部" value="ALL" />
			</el-select>
			<el-select
			  v-model="incidentAdjustmentType"
			  class="incident-type"
			  clearable
			  placeholder="调整类型"
			  @change="searchIncidents">
			  <el-option label="调课" value="MOVE" />
			  <el-option label="停课" value="CANCEL" />
			  <el-option label="代课" value="SUBSTITUTE" />
			  <el-option label="补课" value="MAKEUP" />
			</el-select>
			<el-input
			  v-model="incidentKeyword"
			  class="incident-keyword"
			  clearable
			  placeholder="流程、业务编号、课表项或原因"
			  @keyup.enter="searchIncidents" />
			<el-button type="primary" @click="searchIncidents">查询</el-button>
		  </div>
		  <div>
			<el-button
			  type="warning"
			  :disabled="!incidentSelection.length"
			  :loading="incidentBatchRetrying"
			  @click="batchRetryIncidents">
			  批量重放（{{ incidentSelection.length }}）
			</el-button>
			<el-button @click="loadIncidents">刷新</el-button>
		  </div>
		</div>
		<el-empty v-if="!incidents.length" description="暂无待处理异常" />
		<el-table v-else :data="incidents" @selection-change="incidentSelection = $event">
		  <el-table-column type="selection" width="46" :selectable="row => row.status === 'FAILED'" />
		  <el-table-column prop="workflowInstanceId" label="流程实例" min-width="210" />
		  <el-table-column prop="adjustmentType" label="调整类型" width="110" />
		  <el-table-column label="状态" width="100">
			<template #default="scope">
			  <el-tag :type="scope.row.status === 'APPLIED' ? 'success' : 'danger'">
				{{ scope.row.status === 'APPLIED' ? '已处理' : '待处理' }}
			  </el-tag>
			</template>
		  </el-table-column>
		  <el-table-column prop="message" label="失败原因" min-width="220" show-overflow-tooltip />
		  <el-table-column prop="retryCount" label="重试次数" width="100" />
		  <AdaptiveActionColumn label="操作" width="100">
			<template #default="scope">
			  <el-button
				v-if="scope.row.status === 'FAILED'"
				link
				type="primary"
				@click="retryIncident(scope.row)">
				重试
			  </el-button>
			</template>
		  </AdaptiveActionColumn>
		</el-table>
		<el-pagination
		  v-model:current-page="incidentPage"
		  v-model:page-size="incidentPageSize"
		  :page-sizes="[10, 20, 50, 100]"
		  layout="total, sizes, prev, pager, next"
		  :total="incidentTotal"
		  @size-change="searchIncidents"
		  @current-change="loadIncidents" />
	  </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="entryDialog" title="课表安排" width="600px">
      <el-form label-width="90px">
        <el-form-item label="教学任务"><el-select v-model="entryForm.offeringId" filterable @change="selectEntryOffering"><el-option v-for="item in offeringOptions" :key="item.id" :label="`${item.courseName} / ${item.teachingClassName} / ${item.teacherName}`" :value="item.id" /></el-select></el-form-item>
        <el-form-item label="教室"><el-select v-model="entryForm.classroomId" filterable><el-option v-for="item in classroomOptions" :key="item.id" :label="`${item.roomName}（${item.capacity}人）`" :value="item.id" /></el-select></el-form-item>
        <el-form-item label="星期"><el-select v-model="entryForm.dayOfWeek"><el-option v-for="day in 7" :key="day" :label="`星期${dayName(day)}`" :value="day" /></el-select></el-form-item>
        <el-form-item label="节次">
          <el-select v-if="availablePeriods.length" v-model="entryForm.periodNo">
            <el-option v-for="item in availablePeriods" :key="item.id" :label="`${item.periodName}（${item.startTime}-${item.endTime}）`" :value="item.periodNo" />
          </el-select>
          <el-input-number v-else v-model="entryForm.periodNo" :min="1" :max="20" />
        </el-form-item>
        <el-form-item label="连堂节数"><el-input-number v-model="entryForm.durationPeriods" :min="1" :max="4" /></el-form-item>
        <el-form-item label="周模式"><el-select v-model="entryForm.weekPattern"><el-option label="每周" value="ALL" /><el-option label="单周" value="ODD" /><el-option label="双周" value="EVEN" /></el-select></el-form-item>
        <el-form-item label="授课周次"><el-input-number v-model="entryForm.startWeek" :min="1" /><span class="separator">至</span><el-input-number v-model="entryForm.endWeek" :min="1" /></el-form-item>
        <el-form-item label="锁定课程"><el-switch v-model="entryForm.locked" /><span class="form-tip">锁定后自动排课不会移动该课程</span></el-form-item>
      </el-form>
      <template #footer><el-button @click="entryDialog = false">取消</el-button><el-button type="primary" @click="saveEntry">保存并校验</el-button></template>
    </el-dialog>

    <el-dialog v-model="combinedDialog" title="合班公共课设置" width="640px">
      <el-alert type="info" :closable="false" title="选择至少两个同校区行政班；保存后把在籍学生同步到当前教学任务。已有课表时会检查教室容量和学生冲突，失败则整次回滚。" />
      <el-form label-width="110px" class="combined-form">
        <el-form-item label="教学任务">{{ combinedOffering?.teachingClassName }} / {{ combinedOffering?.courseName }}</el-form-item>
        <el-form-item label="来源行政班" required>
          <el-select v-model="combinedClassIds" multiple filterable collapse-tags placeholder="选择两个或更多行政班" style="width:100%">
            <el-option
              v-for="item in availableCombinedClasses"
              :key="item.id"
              :label="item.className"
              :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="当前人数">{{ combinedInfo?.studentCount ?? 0 }}</el-form-item>
        <el-form-item v-if="combinedInfo?.syncRequired" label="同步提示">
          <el-tag type="warning">行政班人数已变化，预计 {{ combinedInfo.expectedStudentCount }} 人</el-tag>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="combinedDialog = false">取消</el-button>
        <el-button v-if="combinedOffering?.offeringMode === 'COMBINED'" :loading="combinedSaving" @click="syncCombined">只同步成员</el-button>
        <el-button type="primary" :loading="combinedSaving" @click="saveCombined">保存来源班级并同步</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="offeringDialog" title="教学任务" width="620px">
      <el-form label-width="110px">
        <el-form-item label="教学班编码"><el-input v-model="offeringForm.offeringCode" /></el-form-item>
        <el-form-item label="课程">
          <el-select v-model="offeringForm.courseCode" filterable @change="selectCourse">
            <el-option v-for="course in courses" :key="course.id" :label="`${course.courseName}（${course.courseCode}）`" :value="course.courseCode" />
          </el-select>
        </el-form-item>
        <el-form-item label="教学班名称"><el-input v-model="offeringForm.teachingClassName" /></el-form-item>
        <el-form-item label="任课教师">
          <el-select v-model="offeringForm.teacherId" filterable @change="selectTeacher">
            <el-option v-for="teacher in teachers" :key="teacher.id" :label="`${teacher.teacherName}（${teacher.teacherNo}）`" :value="teacher.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="学生人数"><el-input-number v-model="offeringForm.studentCount" :min="1" :disabled="offeringForm.offeringMode === 'COMBINED'" /><span v-if="offeringForm.offeringMode === 'COMBINED'">由来源行政班同步</span></el-form-item>
        <el-form-item label="每周课时"><el-input-number v-model="offeringForm.weeklyLessons" :min="1" /></el-form-item>
        <el-form-item label="连堂节数"><el-input-number v-model="offeringForm.preferredDurationPeriods" :min="1" :max="4" /></el-form-item>
        <el-form-item label="授课周模式"><el-select v-model="offeringForm.weekPattern"><el-option label="每周" value="ALL" /><el-option label="单周" value="ODD" /><el-option label="双周" value="EVEN" /></el-select></el-form-item>
        <el-form-item label="教室类型要求"><el-select v-model="offeringForm.requiredRoomType" clearable><el-option v-for="item in roomTypes" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
        <el-form-item label="设备要求"><el-input v-model="offeringForm.requiredEquipmentCodes" placeholder="多个设备编码使用英文逗号分隔" /></el-form-item>
        <el-form-item label="所属校区"><el-select v-model="offeringForm.campusId" filterable><el-option v-for="item in campuses" :key="item.id" :label="orgName(item)" :value="item.id" /></el-select></el-form-item>
      </el-form>
      <template #footer><el-button @click="offeringDialog = false">取消</el-button><el-button type="primary" @click="saveOffering">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="classroomDialog" title="教室" width="560px">
      <el-form label-width="90px">
        <el-form-item label="教室编码"><el-input v-model="classroomForm.roomCode" /></el-form-item>
        <el-form-item label="教室名称"><el-input v-model="classroomForm.roomName" /></el-form-item>
        <el-form-item label="教学楼"><el-input v-model="classroomForm.buildingName" /></el-form-item>
        <el-form-item label="所属校区"><el-select v-model="classroomForm.campusId" filterable><el-option v-for="item in campuses" :key="item.id" :label="orgName(item)" :value="item.id" /></el-select></el-form-item>
        <el-form-item label="教室类型"><el-select v-model="classroomForm.roomType"><el-option v-for="item in roomTypes" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
        <el-form-item label="设备编码"><el-input v-model="classroomForm.equipmentCodes" placeholder="例如 PROJECTOR,COMPUTER" /></el-form-item>
        <el-form-item label="容量"><el-input-number v-model="classroomForm.capacity" :min="1" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="classroomDialog = false">取消</el-button><el-button type="primary" @click="saveClassroom">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="candidateDialog" title="生成自动排课候选方案" width="660px">
      <el-form label-width="110px">
        <el-form-item label="方案名称">
          <el-input v-model="candidateForm.planName" maxlength="80" />
        </el-form-item>
        <el-form-item label="生成模式">
          <el-radio-group v-model="candidateForm.mode">
            <el-radio value="FULL">全量排课</el-radio>
            <el-radio value="LOCAL">局部重排</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="candidateForm.mode === 'LOCAL'" label="教学任务" required>
          <el-select
            v-model="candidateForm.selectedOfferingIds"
            multiple
            filterable
            collapse-tags
            class="full-width"
            placeholder="选择需要局部重排的教学任务">
            <el-option
              v-for="item in offeringOptions"
              :key="item.id"
              :label="`${item.courseName} / ${item.teachingClassName} / ${item.teacherName}`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="候选数量">
          <el-input-number v-model="candidateForm.candidateCount" :min="1" :max="5" />
        </el-form-item>
        <el-form-item label="排课日范围">
          <el-input-number v-model="candidateForm.weekdays" :min="1" :max="7" />
          <span class="unit-label">天/周</span>
        </el-form-item>
        <el-form-item label="每日节次">
          <el-input-number v-model="candidateForm.periodsPerDay" :min="1" :max="20" />
        </el-form-item>
        <el-form-item label="授课周次">
          <el-input-number v-model="candidateForm.startWeek" :min="1" />
          <span class="separator">至</span>
          <el-input-number v-model="candidateForm.endWeek" :min="1" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="candidateDialog = false">取消</el-button>
        <el-button type="primary" :loading="candidateGenerating" @click="generateCandidates">
          生成候选
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="constraintDialog" :title="constraintForm.id ? '编辑教师时间约束' : '新增教师时间约束'" width="580px">
      <el-form label-width="100px">
        <el-form-item label="教师"><el-select v-model="constraintForm.teacherId" filterable><el-option v-for="item in teachers" :key="item.id" :label="`${item.teacherName}（${item.teacherNo}）`" :value="item.id" /></el-select></el-form-item>
        <el-form-item label="星期"><el-select v-model="constraintForm.dayOfWeek"><el-option v-for="day in 7" :key="day" :label="`星期${dayName(day)}`" :value="day" /></el-select></el-form-item>
        <el-form-item label="节次"><el-select v-if="allBellPeriods.length" v-model="constraintForm.periodNo"><el-option v-for="item in allBellPeriods" :key="item.periodNo" :label="item.periodName" :value="item.periodNo" /></el-select><el-input-number v-else v-model="constraintForm.periodNo" :min="1" :max="20" /></el-form-item>
        <el-form-item label="约束类型"><el-radio-group v-model="constraintForm.constraintType"><el-radio value="FORBIDDEN">禁止排课</el-radio><el-radio value="PREFERRED">优先安排</el-radio></el-radio-group></el-form-item>
        <el-form-item label="权重"><el-input-number v-model="constraintForm.weight" :min="1" :max="100" /></el-form-item>
        <el-form-item label="原因"><el-input v-model="constraintForm.reason" type="textarea" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="constraintDialog = false">取消</el-button><el-button type="primary" @click="saveConstraint">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="roomConstraintDialog" :title="roomConstraintForm.id ? '编辑教室不可用时段' : '新增教室不可用时段'" width="580px">
      <el-form label-width="100px"><el-form-item label="教室"><el-select v-model="roomConstraintForm.classroomId" filterable><el-option v-for="item in classroomOptions" :key="item.id" :label="item.roomName" :value="item.id" /></el-select></el-form-item><el-form-item label="星期"><el-select v-model="roomConstraintForm.dayOfWeek"><el-option v-for="day in 7" :key="day" :label="`星期${dayName(day)}`" :value="day" /></el-select></el-form-item><el-form-item label="节次范围"><el-input-number v-model="roomConstraintForm.startPeriod" :min="1" /><span class="separator">至</span><el-input-number v-model="roomConstraintForm.endPeriod" :min="1" /></el-form-item><el-form-item label="原因"><el-input v-model="roomConstraintForm.reason" type="textarea" /></el-form-item></el-form>
      <template #footer><el-button @click="roomConstraintDialog = false">取消</el-button><el-button type="primary" @click="saveRoomConstraint">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="dateExceptionDialog" title="课程日期调整" width="620px">
      <el-alert v-if="dateExceptionError" :title="dateExceptionError" type="error" show-icon :closable="false" class="date-exception-error" />
      <el-form label-width="100px"><el-form-item label="调整类型"><el-radio-group v-model="dateExceptionForm.exceptionType"><el-radio value="MOVE">调课</el-radio><el-radio value="CANCEL">停课</el-radio><el-radio value="SUBSTITUTE">代课</el-radio><el-radio value="MAKEUP">补课</el-radio></el-radio-group></el-form-item><template v-if="['MOVE', 'MAKEUP'].includes(dateExceptionForm.exceptionType)"><el-form-item label="目标日期"><el-date-picker v-model="dateExceptionForm.targetDate" value-format="YYYY-MM-DD" /></el-form-item><el-form-item label="目标节次"><el-input-number v-model="dateExceptionForm.targetPeriodNo" :min="1" /></el-form-item><el-form-item label="目标教室"><el-select v-model="dateExceptionForm.targetClassroomId" clearable filterable><el-option v-for="item in classroomOptions" :key="item.id" :label="item.roomName" :value="item.id" /></el-select></el-form-item></template><el-form-item v-if="dateExceptionForm.exceptionType === 'SUBSTITUTE'" label="代课教师"><el-select v-model="dateExceptionForm.substituteTeacherId" filterable><el-option v-for="item in teachers" :key="item.id" :label="item.teacherName" :value="item.id" /></el-select></el-form-item><el-form-item label="变更原因"><el-input v-model="dateExceptionForm.reason" type="textarea" /></el-form-item></el-form>
      <template #footer><el-button @click="dateExceptionDialog = false">取消</el-button><el-button type="primary" @click="saveDateException">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="diffDialog" :title="diffTitle" width="860px">
      <div v-if="currentDiff" class="diff-summary">
        <el-tag type="success">新增 {{ currentDiff.added }}</el-tag>
        <el-tag type="warning">移动 {{ currentDiff.moved }}</el-tag>
        <el-tag type="danger">移除 {{ currentDiff.removed }}</el-tag>
        <el-tag type="info">未变化 {{ currentDiff.unchanged }}</el-tag>
      </div>
      <el-table :data="currentDiff?.items || []" max-height="480">
        <el-table-column label="变化" width="90">
          <template #default="scope">{{ changeTypeName(scope.row.changeType) }}</template>
        </el-table-column>
        <el-table-column prop="courseName" label="课程" min-width="130" />
        <el-table-column prop="teachingClassName" label="教学班" min-width="150" />
        <el-table-column prop="teacherName" label="教师" width="110" />
        <el-table-column prop="beforeSlot" label="变更前" min-width="150" />
        <el-table-column prop="afterSlot" label="变更后" min-width="150" />
      </el-table>
      <template #footer>
        <el-button @click="diffDialog = false">关闭</el-button>
      </template>
    </el-dialog>
    <el-dialog v-model="candidateCompareDialog" title="候选方案横向对比" width="1080px">
      <el-table :data="candidateComparison" border>
        <el-table-column prop="planName" label="方案" min-width="180" fixed />
        <el-table-column prop="totalScore" label="总分" width="90" />
        <el-table-column prop="metrics.scheduledLessons" label="已排课时" width="100" />
        <el-table-column prop="metrics.unscheduledLessons" label="未排课时" width="100" />
        <el-table-column prop="metrics.preferredSlotHits" label="偏好命中" width="100" />
        <el-table-column prop="metrics.sameCourseDayPenalty" label="课程集中" width="100" />
        <el-table-column prop="metrics.teacherLoadPenalty" label="教师负载" width="100" />
        <el-table-column prop="metrics.consecutivePenalty" label="连续授课" width="100" />
        <el-table-column prop="metrics.campusSwitchPenalty" label="跨校区" width="100" />
        <el-table-column prop="metrics.teacherGapPenalty" label="教师空档" width="100" />
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import ScheduleGrid from '../components/ScheduleGrid.vue'
import SchedulingModeSwitch from '../components/SchedulingModeSwitch.vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getAdminUsername, hasAdminPermission } from '../../../store/auth'
import {
  applyScheduleCandidate,
  batchRetryCourseAdjustmentIncidents,
  createClassroom,
  createCourseOffering,
  configureCombinedOffering,
  createScheduleEntry,
  deleteClassroom,
  deleteCourseOffering,
  deleteCombinedOffering,
  deleteScheduleEntry,
  downloadScheduleImportTemplate,
  importClassSchedule,
  exportClassSchedule,
  getScheduleQualityAnalysis,
  getCombinedOffering,
  getSchedulePolicy,
  compareScheduleCandidates,
  discardScheduleCandidate,
  submitScheduleGenerationJob,
  listScheduleGenerationJobs,
  cancelScheduleGenerationJob,
  updateScheduleCandidateGovernance,
  submitScheduleCandidateReview,
  reviewScheduleCandidate,
  listClassrooms,
  listClassSchedule,
  listAcademicTerms,
  listCourseCatalog,
  listCourseOfferings,
  listCourseAdjustmentIncidents,
  listScheduleVersions,
  listScheduleCandidates,
  previewScheduleCandidate,
  previewSchedulePublication,
  publishScheduleVersion,
  saveSchedulePolicy,
  syncCombinedOffering,
  retryCourseAdjustmentIncident,
  rollbackScheduleVersion,
  updateClassroom,
  updateCourseOffering,
  updateScheduleEntry,
  dictionaryOptions,
  createTeacherTimeConstraint,
  createClassroomUnavailableSlot,
  createScheduleDateException,
  deleteClassroomUnavailableSlot,
  deleteTeacherTimeConstraint,
  listBellSchedules,
  listTeacherTimeConstraints,
  listClassroomUnavailableSlots,
  listScheduleOccurrences,
  listSchedulePendingMakeups,
  listSchedulePendingSubstitutions,
  listScheduleDimensionOptions,
  listScheduleStudentOptions,
  listScheduleDateExceptionHistory,
  cancelScheduleDateException,
  restoreScheduleDateException,
  orgList,
  updateTeacherTimeConstraint,
  updateClassroomUnavailableSlot,
} from '../../../api/admin'

const route = useRoute()
const reviewOnly = hasAdminPermission('education:scheduling:review') && !hasAdminPermission('education:scheduling:manage')
const semesterCode = ref(typeof route.query.semesterCode === 'string' ? route.query.semesterCode : '')
const scheduleFileInput = ref(null)
const router = useRouter()
const activeTab = ref(reviewOnly ? 'candidates' : ['schedule', 'date-schedule', 'versions', 'quality', 'policy', 'candidates'].includes(route.query.schedulingTab)
  ? route.query.schedulingTab : 'schedule')
const scheduleView = ref('grid')
const offerings = ref([])
const classrooms = ref([])
const offeringOptions = ref([])
const classroomOptions = ref([])
const terms = ref([])
const courses = ref([])
const teachers = ref([])
const students = ref([])
const studentPage = ref(1)
const studentTotal = ref(0)
const studentKeyword = ref('')
const studentOptionsLoading = ref(false)
const studentOptionsError = ref(false)
const selectedStudent = ref(null)
let studentSearchTimer
let studentOptionsSequence = 0
const administrativeClasses = ref([])
const roomTypes = ref([])
const campuses = ref([])
const bellSchedules = ref([])
const teacherConstraints = ref([])
const roomConstraints = ref([])
const occurrences = ref([])
const dateExceptionHistory = ref([])
const occurrenceDate = ref(new Date().toISOString().slice(0, 10))
const schedule = ref([])
const quality = ref({})
const policy = reactive({})
const scheduleDimension = ref('ALL')
const scheduleTargetId = ref('')
const dimensionOptionsLoading = ref(false)
const dimensionOptionsError = ref(false)
const incidents = ref([])
const incidentSelection = ref([])
const incidentStatus = ref('FAILED')
const incidentAdjustmentType = ref('')
const incidentKeyword = ref('')
const incidentPage = ref(1)
const incidentPageSize = ref(20)
const incidentTotal = ref(0)
const incidentBatchRetrying = ref(false)
const versions = ref([])
const candidates = ref([])
const generationJobs = ref([])
let generationJobTimer
const candidateSelection = ref([])
const candidateComparison = ref([])
const candidateCompareDialog = ref(false)
const offeringPage = ref(1)
const offeringPageSize = ref(10)
const offeringTotal = ref(0)
const classroomPage = ref(1)
const classroomPageSize = ref(10)
const classroomTotal = ref(0)
const entryDialog = ref(false)
const offeringDialog = ref(false)
const combinedDialog = ref(false)
const combinedSaving = ref(false)
const combinedOffering = ref(null)
const combinedInfo = ref(null)
const combinedClassIds = ref([])
const classroomDialog = ref(false)
const candidateDialog = ref(false)
const candidateGenerating = ref(false)
const constraintDialog = ref(false)
const roomConstraintDialog = ref(false)
const dateExceptionDialog = ref(false)
const dateExceptionError = ref('')
const diffDialog = ref(false)
const diffTitle = ref('方案差异')
const currentDiff = ref(null)
const entryForm = reactive({})
const offeringForm = reactive({})
const classroomForm = reactive({})
const candidateForm = reactive({})
const constraintForm = reactive({})
const roomConstraintForm = reactive({})
const dateExceptionForm = reactive({})
const pendingMakeups = ref([])
const pendingSubstitutions = ref([])
let loadSequence = 0
let dimensionOptionsSequence = 0
let scheduleSequence = 0
const orgName = item => item.organizationName || item.orgName || item.name || item.id
const selectedTerm = computed(() => terms.value.find(item => item.termCode === semesterCode.value))
const availableCombinedClasses = computed(() => administrativeClasses.value.filter(item =>
  item.status === 'ACTIVE' && item.campusId === combinedOffering.value?.campusId))
const selectedClassroom = computed(() => classroomOptions.value.find(item => item.id === entryForm.classroomId))
const availablePeriods = computed(() => {
  const schedule = bellSchedules.value.find(item =>
    item.schedule.defaultSchedule && item.schedule.campusId === selectedClassroom.value?.campusId)
  return (schedule?.periods || []).filter(item => item.schedulable)
})
const allBellPeriods = computed(() => {
  const values = new Map()
  bellSchedules.value.flatMap(item => item.periods || []).filter(item => item.schedulable).forEach(item => values.set(item.periodNo, item))
  return [...values.values()].sort((left, right) => left.periodNo - right.periodNo)
})
const schedulePeriods = computed(() => {
  if (allBellPeriods.value.length) return allBellPeriods.value
  const maximum = Math.max(8, ...schedule.value.map(item => item.periodNo + (item.durationPeriods || 1) - 1))
  return Array.from({ length: maximum }, (_, index) => ({ periodNo: index + 1, periodName: `第 ${index + 1} 节` }))
})
const teacherName = id => teachers.value.find(item => item.id === id)?.teacherName || id
const classroomName = id => classroomOptions.value.find(item => item.id === id)?.roomName || id
const occurrenceStatusName = status => ({ SCHEDULED: '正常', CANCELLED: '停课', MOVED_OUT: '已调出', MOVED_IN: '已调入', SUBSTITUTED: '代课', MAKEUP: '补课' }[status] || status)
const occurrenceStatusType = status => ({ CANCELLED: 'danger', MOVED_OUT: 'warning', MOVED_IN: 'success', SUBSTITUTED: 'warning', MAKEUP: 'success' }[status] || 'primary')
const dayName = (day) => ['一', '二', '三', '四', '五', '六', '日'][day - 1]
const formatTime = value => value ? new Date(value).toLocaleString() : '-'
const dimensionOptions = computed(() => {
  if (scheduleDimension.value === 'TEACHER') {
    return teachers.value.map(item => ({ label: `${item.teacherName}（${item.teacherNo}）`, value: item.id }))
  }
  if (scheduleDimension.value === 'TEACHING_CLASS') {
    return offeringOptions.value.map(item => ({ label: `${item.teachingClassName} / ${item.courseName}`, value: item.id }))
  }
  if (scheduleDimension.value === 'ADMIN_CLASS') {
    return administrativeClasses.value.map(item => ({ label: item.className, value: item.id }))
  }
  if (scheduleDimension.value === 'CLASSROOM') {
    return classroomOptions.value.map(item => ({ label: item.roomName, value: item.id }))
  }
  return []
})
const studentOptions = computed(() => {
  const options = students.value.map(item => ({ label: `${item.studentName}（${item.studentNo}）`, value: item.id }))
  if (selectedStudent.value && !options.some(item => item.value === selectedStudent.value.id)) {
    options.unshift({
      label: `${selectedStudent.value.studentName}（${selectedStudent.value.studentNo}）`,
      value: selectedStudent.value.id,
    })
  }
  return options
})
const scheduleTargetPlaceholder = computed(() => ({
  TEACHER: '请选择教师',
  TEACHING_CLASS: '请选择教学班',
  ADMIN_CLASS: '请选择行政班',
  STUDENT: '请选择学生',
  CLASSROOM: '请选择教室',
})[scheduleDimension.value] || '请选择查询对象')
const reset = (target, value) => { Object.keys(target).forEach(key => delete target[key]); Object.assign(target, value) }
const responseData = response => response?.data
const responseList = response => {
  const data = responseData(response)
  return Array.isArray(data) ? data : data?.content || []
}
const loadResult = (results, index, fallback) => {
  const result = results[index]
  return result?.status === 'fulfilled' ? result.value : fallback
}
const resetStudentOptions = () => {
  ++studentOptionsSequence
  window.clearTimeout(studentSearchTimer)
  students.value = []
  studentPage.value = 1
  studentTotal.value = 0
  studentKeyword.value = ''
  studentOptionsLoading.value = false
  studentOptionsError.value = false
  selectedStudent.value = null
}
const loadStudentOptions = async () => {
  if (scheduleDimension.value !== 'STUDENT' || !semesterCode.value) return
  const sequence = ++studentOptionsSequence
  const semester = semesterCode.value
  studentOptionsLoading.value = true
  studentOptionsError.value = false
  try {
    const response = await listScheduleStudentOptions({
      keyword: studentKeyword.value,
      page: studentPage.value - 1,
      size: 10,
    })
    if (sequence !== studentOptionsSequence || semester !== semesterCode.value) return
    students.value = response.data?.content || []
    studentTotal.value = response.data?.totalElements || 0
  } catch (error) {
    if (sequence !== studentOptionsSequence || semester !== semesterCode.value) return
    studentOptionsError.value = true
    ElMessage.error(`学生选项加载失败：${error.message}`)
  } finally {
    if (sequence === studentOptionsSequence) studentOptionsLoading.value = false
  }
}
const searchStudentOptions = query => {
  ++studentOptionsSequence
  window.clearTimeout(studentSearchTimer)
  studentKeyword.value = query.trim()
  studentPage.value = 1
  studentOptionsLoading.value = true
  studentSearchTimer = window.setTimeout(loadStudentOptions, 250)
}
const onStudentSelectVisible = visible => {
  if (visible && !students.value.length && !studentOptionsLoading.value) loadStudentOptions()
}
const changeScheduleTarget = () => {
  selectedStudent.value = students.value.find(item => item.id === scheduleTargetId.value) || null
  loadSchedule()
}
const clearSemesterData = () => {
  ++dimensionOptionsSequence
  ++scheduleSequence
  dimensionOptionsLoading.value = false
  dimensionOptionsError.value = false
  schedule.value = []
  offerings.value = []
  classrooms.value = []
  courses.value = []
  versions.value = []
  candidates.value = []
  generationJobs.value = []
  teacherConstraints.value = []
  roomConstraints.value = []
  occurrences.value = []
  pendingMakeups.value = []
  pendingSubstitutions.value = []
  dateExceptionHistory.value = []
  quality.value = {}
  reset(policy, {})
  campuses.value = []
  roomTypes.value = []
  teachers.value = []
  resetStudentOptions()
  administrativeClasses.value = []
  offeringOptions.value = []
  classroomOptions.value = []
  bellSchedules.value = []
  offeringTotal.value = 0
  classroomTotal.value = 0
  scheduleTargetId.value = ''
}
const loadDimensionOptions = async () => {
  const sequence = ++dimensionOptionsSequence
  const semester = semesterCode.value
  dimensionOptionsLoading.value = true
  dimensionOptionsError.value = false
  try {
    const response = await listScheduleDimensionOptions(semester)
    if (sequence !== dimensionOptionsSequence || semester !== semesterCode.value) return
    const dimensions = responseData(response) || {}
    if (!['teachers', 'administrativeClasses', 'teachingClasses', 'classrooms']
      .every(key => Array.isArray(dimensions[key]))) {
      throw new Error('接口返回的查询对象格式不正确')
    }
    teachers.value = dimensions.teachers || []
    administrativeClasses.value = dimensions.administrativeClasses || []
    offeringOptions.value = dimensions.teachingClasses || []
    classroomOptions.value = dimensions.classrooms || []
  } catch (error) {
    if (sequence !== dimensionOptionsSequence || semester !== semesterCode.value) return
    dimensionOptionsError.value = true
    ElMessage.error(`课表查询对象加载失败：${error.message}`)
  } finally {
    if (sequence === dimensionOptionsSequence) dimensionOptionsLoading.value = false
  }
}
const loadAll = async () => {
  const sequence = ++loadSequence
  ++dimensionOptionsSequence
  ++scheduleSequence
  scheduleTargetId.value = ''
  schedule.value = []
  teachers.value = []
  resetStudentOptions()
  administrativeClasses.value = []
  offeringOptions.value = []
  classroomOptions.value = []
  dimensionOptionsLoading.value = false
  dimensionOptionsError.value = false
  let termResponse
  try {
    termResponse = await listAcademicTerms()
  } catch (error) {
    if (sequence !== loadSequence) return
    terms.value = []
    semesterCode.value = ''
    clearSemesterData()
    ElMessage.error(`学期加载失败：${error.message}`)
    return
  }
  if (sequence !== loadSequence) return
  terms.value = responseList(termResponse)
  const preferredTerm = terms.value.find(item => item.currentTerm) || terms.value[0]
  if (!terms.value.some(item => item.termCode === semesterCode.value)) {
    semesterCode.value = preferredTerm?.termCode || ''
  }
  if (!semesterCode.value) {
    clearSemesterData()
    return
  }
  if (reviewOnly) {
    // 审核员只需候选方案及任务，不加载无权访问的管理配置。
    await loadCandidates()
    return
  }
  const currentTerm = terms.value.find(item => item.termCode === semesterCode.value)
  const dimensionOptionsRequest = loadDimensionOptions()
  const studentOptionsRequest = scheduleDimension.value === 'STUDENT'
    ? loadStudentOptions() : Promise.resolve()
  const results = await Promise.allSettled([
    listCourseCatalog(),
    dictionaryOptions('EDU_ROOM_TYPE'),
    listScheduleVersions(semesterCode.value),
    listScheduleCandidates(semesterCode.value),
    orgList({ page: 0, size: 200 }),
    currentTerm ? listBellSchedules(currentTerm.id) : Promise.resolve({ data: [] }),
    listTeacherTimeConstraints(semesterCode.value),
    listClassroomUnavailableSlots(semesterCode.value),
  ])
  const courseResponse = loadResult(results, 0, { data: [] })
  const roomTypeResponse = loadResult(results, 1, { data: [] })
  const versionResponse = loadResult(results, 2, { data: [] })
  const candidateResponse = loadResult(results, 3, { data: [] })
  const campusResponse = loadResult(results, 4, { data: [] })
  const bellScheduleResponse = loadResult(results, 5, { data: [] })
  const teacherConstraintResponse = loadResult(results, 6, { data: [] })
  const roomConstraintResponse = loadResult(results, 7, { data: [] })
  if (sequence !== loadSequence) return
  courses.value = responseList(courseResponse)
  roomTypes.value = responseList(roomTypeResponse).map(item => ({ label: item.dictName, value: item.dictValue }))
  versions.value = responseList(versionResponse)
  candidates.value = responseList(candidateResponse)
  const organizations = campusResponse.data?.content || campusResponse.data || []
  campuses.value = organizations.filter(item => ['CAMPUS', 'SCHOOL'].includes(item.organizationType || item.orgType))
  bellSchedules.value = responseList(bellScheduleResponse)
  teacherConstraints.value = responseList(teacherConstraintResponse)
  roomConstraints.value = responseList(roomConstraintResponse)
  offeringPage.value = 1
  classroomPage.value = 1
  await Promise.allSettled([
    dimensionOptionsRequest,
    studentOptionsRequest,
    loadOfferingsPage(),
    loadClassroomsPage(),
    loadSchedule(),
    loadDateSchedule(),
    loadQualityAnalysis(),
    loadPolicy(),
  ])
}
const loadOfferingsPage = async () => {
  const response = await listCourseOfferings(semesterCode.value, {
    page: offeringPage.value - 1,
    size: offeringPageSize.value,
  })
  offerings.value = response.data?.content || response.data || []
  offeringTotal.value = response.data?.totalElements ?? offerings.value.length
}
const loadClassroomsPage = async () => {
  const response = await listClassrooms({
    page: classroomPage.value - 1,
    size: classroomPageSize.value,
  })
  classrooms.value = response.data?.content || response.data || []
  classroomTotal.value = response.data?.totalElements ?? classrooms.value.length
}
const changeOfferingPageSize = () => { offeringPage.value = 1; loadOfferingsPage() }
const changeClassroomPageSize = () => { classroomPage.value = 1; loadClassroomsPage() }
const loadSchedule = async () => {
  const sequence = ++scheduleSequence
  if (scheduleDimension.value !== 'ALL' && !scheduleTargetId.value) {
    schedule.value = []
    return
  }
  const semester = semesterCode.value
  const dimension = scheduleDimension.value
  const target = scheduleTargetId.value
  const response = await listClassSchedule(
    semester,
    dimension,
    target || undefined,
  )
  if (sequence === scheduleSequence && semester === semesterCode.value
    && dimension === scheduleDimension.value && target === scheduleTargetId.value) {
    schedule.value = response.data || []
  }
}
const loadQualityAnalysis = async () => {
  quality.value = (await getScheduleQualityAnalysis(semesterCode.value)).data || {}
}
const loadPolicy = async () => {
  reset(policy, (await getSchedulePolicy(semesterCode.value)).data || {})
}
const savePolicy = async () => {
  const response = await saveSchedulePolicy({ ...policy, semesterCode: semesterCode.value })
  reset(policy, response.data || {})
  ElMessage.success('本学期排课规则已保存')
  await loadQualityAnalysis()
}
const loadOccurrences = async () => {
  occurrences.value = occurrenceDate.value
    ? (await listScheduleOccurrences(semesterCode.value, occurrenceDate.value)).data || []
    : []
}
const loadDateExceptionHistory = async () => {
  dateExceptionHistory.value = (await listScheduleDateExceptionHistory(semesterCode.value)).data || []
}
const loadPendingMakeups = async () => {
  pendingMakeups.value = (await listSchedulePendingMakeups(semesterCode.value)).data || []
}
const loadPendingSubstitutions = async () => {
  pendingSubstitutions.value = (await listSchedulePendingSubstitutions(semesterCode.value)).data || []
}
const loadDateSchedule = async () => Promise.all([loadOccurrences(), loadDateExceptionHistory(), loadPendingMakeups(), loadPendingSubstitutions()])
const changeDimension = async () => {
  scheduleTargetId.value = ''
  schedule.value = []
  resetStudentOptions()
  await Promise.allSettled([
    scheduleDimension.value === 'STUDENT' ? loadStudentOptions() : loadDimensionOptions(),
    loadSchedule(),
  ])
}
const openEntry = (row) => { reset(entryForm, row ? { ...row } : { dayOfWeek: 1, periodNo: 1, durationPeriods: 1, weekPattern: 'ALL', startWeek: 1, endWeek: selectedTerm.value?.weekCount || 20, locked: false }); entryDialog.value = true }
const saveEntry = async () => { const payload = { ...entryForm, semesterCode: semesterCode.value }; await (entryForm.id ? updateScheduleEntry(entryForm.id, payload) : createScheduleEntry(payload)); entryDialog.value = false; ElMessage.success('课表已保存'); await loadAll() }
const moveEntry = async (entry, target) => {
  if (entry.dayOfWeek === target.dayOfWeek && entry.periodNo === target.periodNo) return
  await updateScheduleEntry(entry.id, { ...entry, ...target, semesterCode: semesterCode.value })
  ElMessage.success('课程已移动并通过冲突校验')
  await loadSchedule()
}
const selectEntryOffering = id => {
  const offering = offeringOptions.value.find(item => item.id === id)
  if (!offering) return
  entryForm.durationPeriods = offering.preferredDurationPeriods || 1
  entryForm.weekPattern = offering.weekPattern || 'ALL'
  const matchingRoom = classroomOptions.value.find(item =>
    (!offering.campusId || item.campusId === offering.campusId) &&
    (!offering.requiredRoomType || item.roomType === offering.requiredRoomType) &&
    item.capacity >= offering.studentCount)
  if (matchingRoom) entryForm.classroomId = matchingRoom.id
}
const removeEntry = async (row) => { await ElMessageBox.confirm('确认删除该课表安排？', '删除'); await deleteScheduleEntry(row.id); await loadAll() }
const saveBlob = (blob, filename) => {
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = filename
  anchor.click()
  URL.revokeObjectURL(url)
}
const downloadImportTemplate = async () => saveBlob(
  await downloadScheduleImportTemplate(),
  '课表导入模板.xlsx',
)
const chooseImportFile = () => scheduleFileInput.value?.click()
const importSchedule = async event => {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file) return
  const check = (await importClassSchedule(semesterCode.value, file, true)).data || {}
  if (!check.valid) {
    await ElMessageBox.alert((check.errors || []).join('\n'), '课表校验失败')
    return
  }
  await ElMessageBox.confirm(
    `校验通过，共 ${check.successCount} 条课表。确认正式导入？`,
    '导入课表',
  )
  await importClassSchedule(semesterCode.value, file, false)
  ElMessage.success('课表导入成功')
  await loadAll()
}
const exportSchedule = async () => saveBlob(
  await exportClassSchedule(
    semesterCode.value,
    scheduleDimension.value,
    scheduleTargetId.value || undefined,
  ),
  `课表-${semesterCode.value}.xlsx`,
)
const openOffering = (row) => { reset(offeringForm, row ? { ...row } : { studentCount: 30, weeklyLessons: 2, preferredDurationPeriods: 1, weekPattern: 'ALL', status: 'ACTIVE' }); offeringDialog.value = true }
const selectCourse = code => {
  offeringForm.courseName = courses.value.find(item => item.courseCode === code)?.courseName || ''
}
const selectTeacher = id => {
  offeringForm.teacherName = teachers.value.find(item => item.id === id)?.teacherName || ''
}
const saveOffering = async () => { const payload = { ...offeringForm, semesterCode: semesterCode.value }; await (offeringForm.id ? updateCourseOffering(offeringForm.id, payload) : createCourseOffering(payload)); offeringDialog.value = false; ElMessage.success('教学任务已保存'); await loadAll() }
const openCombined = async row => {
  combinedOffering.value = row
  try {
    const response = await getCombinedOffering(row.id)
    combinedInfo.value = response.data
    combinedClassIds.value = [...(response.data?.administrativeClassIds || [])]
    combinedDialog.value = true
  } catch (error) { ElMessage.error(error.message) }
}
const saveCombined = async () => {
  if (combinedClassIds.value.length < 2) return ElMessage.warning('请选择至少两个来源行政班')
  combinedSaving.value = true
  try {
    const response = await configureCombinedOffering(combinedOffering.value.id, combinedClassIds.value)
    combinedInfo.value = response.data
    combinedDialog.value = false
    ElMessage.success(`合班成员已同步，共 ${response.data.studentCount} 人`)
    await loadAll()
  } catch (error) { ElMessage.error(error.message) }
  finally { combinedSaving.value = false }
}
const syncCombined = async () => {
  combinedSaving.value = true
  try {
    const response = await syncCombinedOffering(combinedOffering.value.id)
    combinedInfo.value = response.data
    ElMessage.success(`合班成员已同步，共 ${response.data.studentCount} 人`)
    await loadAll()
  } catch (error) { ElMessage.error(error.message) }
  finally { combinedSaving.value = false }
}
const removeOffering = async (row) => { await ElMessageBox.confirm('确认删除该教学任务？', '删除'); await (row.offeringMode === 'COMBINED' ? deleteCombinedOffering(row.id) : deleteCourseOffering(row.id)); await loadAll() }
const openClassroom = (row) => { reset(classroomForm, row ? { ...row } : { capacity: 40, roomType: roomTypes.value[0]?.value || '', enabled: true }); classroomDialog.value = true }
const saveClassroom = async () => { await (classroomForm.id ? updateClassroom(classroomForm.id, classroomForm) : createClassroom(classroomForm)); classroomDialog.value = false; ElMessage.success('教室已保存'); await loadAll() }
const removeClassroom = async (row) => { await ElMessageBox.confirm('确认删除该教室？', '删除'); await deleteClassroom(row.id); await loadAll() }
const openConstraint = row => { reset(constraintForm, row ? { ...row } : { dayOfWeek: 1, periodNo: 1, constraintType: 'FORBIDDEN', weight: 10 }); constraintDialog.value = true }
const saveConstraint = async () => { const payload = { ...constraintForm, semesterCode: semesterCode.value }; await (constraintForm.id ? updateTeacherTimeConstraint(constraintForm.id, payload) : createTeacherTimeConstraint(payload)); constraintDialog.value = false; ElMessage.success('教师时间约束已保存'); await loadAll() }
const removeConstraint = async row => { await ElMessageBox.confirm('确认删除该教师时间约束？', '删除'); await deleteTeacherTimeConstraint(row.id); await loadAll() }
const openRoomConstraint = row => { reset(roomConstraintForm, row ? { ...row } : { dayOfWeek: 1, startPeriod: 1, endPeriod: 1, status: 'ACTIVE' }); roomConstraintDialog.value = true }
const saveRoomConstraint = async () => { const payload = { ...roomConstraintForm, semesterCode: semesterCode.value }; await (roomConstraintForm.id ? updateClassroomUnavailableSlot(roomConstraintForm.id, payload) : createClassroomUnavailableSlot(payload)); roomConstraintDialog.value = false; ElMessage.success('教室不可用时段已保存'); await loadAll() }
const removeRoomConstraint = async row => { await ElMessageBox.confirm('确认删除该教室不可用时段？', '删除'); await deleteClassroomUnavailableSlot(row.id); await loadAll() }
const openDateException = occurrence => {
  dateExceptionError.value = ''
  reset(dateExceptionForm, {
    semesterCode: semesterCode.value,
    sourceEntryId: occurrence.entry.id,
    sourceDate: occurrence.date,
    exceptionType: 'MOVE',
    targetDate: occurrence.date,
    targetPeriodNo: occurrence.effectivePeriodNo,
    targetClassroomId: occurrence.effectiveClassroomId,
    reason: '',
  })
  dateExceptionDialog.value = true
}
const openMakeup = item => {
  dateExceptionError.value = ''
  reset(dateExceptionForm, {
    semesterCode: semesterCode.value,
    sourceEntryId: item.entry.id,
    sourceDate: item.sourceDate,
    exceptionType: 'MAKEUP',
    targetDate: null,
    targetPeriodNo: item.entry.periodNo,
    targetClassroomId: item.entry.classroomId,
    reason: '节假日停课补课',
  })
  dateExceptionDialog.value = true
}
const openWaiveMakeup = item => {
  dateExceptionError.value = ''
  reset(dateExceptionForm, {
    semesterCode: semesterCode.value,
    sourceEntryId: item.entry.id,
    sourceDate: item.sourceDate,
    exceptionType: 'CANCEL',
    targetDate: null,
    targetPeriodNo: null,
    targetClassroomId: null,
    reason: '节假日停课，学校确认不补课',
  })
  dateExceptionDialog.value = true
}
const openSubstitution = item => {
  dateExceptionError.value = ''
  reset(dateExceptionForm, {
    semesterCode: semesterCode.value,
    sourceEntryId: item.occurrence.entry.id,
    sourceDate: item.date,
    exceptionType: 'SUBSTITUTE',
    targetDate: null,
    targetPeriodNo: null,
    targetClassroomId: null,
    substituteTeacherId: null,
    reason: `教师请假代课（请假记录：${item.leaveId}）`,
  })
  dateExceptionDialog.value = true
}
const saveDateException = async () => {
  dateExceptionError.value = ''
  try {
    await createScheduleDateException(dateExceptionForm)
    dateExceptionDialog.value = false
    ElMessage.success('日期课表调整已保存')
    await loadDateSchedule()
  } catch (error) {
    dateExceptionError.value = error.response?.data?.msg || error.message || '日期课表调整失败'
  }
}
const cancelDateException = async row => {
  await ElMessageBox.confirm('撤销后将恢复原日期课表，是否继续？', '撤销日期调整')
  await cancelScheduleDateException(row.id)
  ElMessage.success('日期调整已撤销')
  await loadDateSchedule()
}
const restoreDateException = async row => {
  await restoreScheduleDateException(row.id)
  ElMessage.success('日期调整已恢复')
  await loadDateSchedule()
}
const loadIncidents = async () => {
  const response = await listCourseAdjustmentIncidents({
    status: incidentStatus.value,
    adjustmentType: incidentAdjustmentType.value || undefined,
    keyword: incidentKeyword.value || undefined,
    page: incidentPage.value - 1,
    size: incidentPageSize.value,
  })
  incidents.value = response.data?.content || []
  incidentTotal.value = response.data?.totalElements || 0
  incidentSelection.value = []
}
const searchIncidents = async () => {
  incidentPage.value = 1
  await loadIncidents()
}
const retryIncident = async (row) => {
  const response = await retryCourseAdjustmentIncident(row.id)
  const result = response.data
  if (result?.status === 'APPLIED') ElMessage.success('重放成功，课表已更新')
  else ElMessage.error(result?.message || '重放失败')
  await loadIncidents()
  await loadAll()
}
const batchRetryIncidents = async () => {
  await ElMessageBox.confirm(
    `确认重放选中的 ${incidentSelection.value.length} 条调课事故？每条记录会独立处理。`,
    '批量重放',
    { type: 'warning' },
  )
  incidentBatchRetrying.value = true
  try {
    const response = await batchRetryCourseAdjustmentIncidents(
      incidentSelection.value.map(item => item.id),
    )
    const result = response.data
    if (result.failed) {
      ElMessage.warning(`批量重放完成：成功 ${result.succeeded} 条，失败 ${result.failed} 条`)
    } else {
      ElMessage.success(`批量重放成功，共处理 ${result.succeeded} 条`)
    }
    await Promise.all([loadIncidents(), loadAll()])
  } finally {
    incidentBatchRetrying.value = false
  }
}
const loadVersions = async () => {
  versions.value = (await listScheduleVersions(semesterCode.value)).data || []
}
const loadCandidates = async () => {
  const [candidateResponse, jobResponse] = await Promise.all([
    listScheduleCandidates(semesterCode.value),
    listScheduleGenerationJobs(semesterCode.value),
  ])
  candidates.value = candidateResponse.data || []
  generationJobs.value = jobResponse.data || []
}
const compareCandidates = async () => {
  candidateComparison.value = (await compareScheduleCandidates(
    candidateSelection.value.map(item => item.id),
  )).data || []
  candidateCompareDialog.value = true
}
const openCandidateDialog = () => {
  const currentTerm = terms.value.find(item => item.termCode === semesterCode.value)
  reset(candidateForm, {
    planName: `${semesterCode.value}-自动排课`,
    mode: 'FULL',
    selectedOfferingIds: [],
    candidateCount: 3,
    weekdays: policy.teachingDaysPerWeek || 5,
    periodsPerDay: policy.periodsPerDay || 8,
    startWeek: 1,
    endWeek: currentTerm?.weekCount || 20,
  })
  candidateDialog.value = true
}
const generateFullSchedule = async () => {
  if (!semesterCode.value) return ElMessage.warning('请先选择学期')
  if (!offeringTotal.value) {
    activeTab.value = 'offerings'
    return ElMessage.warning('本学期尚无教学任务，请先配置教学任务')
  }
  candidateGenerating.value = true
  try {
    const jobs = (await listScheduleGenerationJobs(semesterCode.value)).data || []
    if (jobs.some(job => ['QUEUED', 'RUNNING'].includes(job.status))) {
      generationJobs.value = jobs
      activeTab.value = 'candidates'
      return ElMessage.warning('本学期已有自动排课任务正在运行，请查看任务进度')
    }
    try {
      await ElMessageBox.confirm(
        '将按本学期规则、教师及教室约束，对全部启用的教学任务生成 3 个候选方案。生成过程不会修改当前课表；审核通过后还需确认应用。',
        '按规则全量自动排课',
        { confirmButtonText: '开始排课', type: 'warning' },
      )
    } catch {
      return
    }
    await submitScheduleGenerationJob({
      semesterCode: semesterCode.value,
      planName: `${semesterCode.value}-全量自动排课-${Date.now()}`,
      mode: 'FULL',
      selectedOfferingIds: [],
      candidateCount: 3,
      startWeek: 1,
    })
    activeTab.value = 'candidates'
    ElMessage.success('全量排课任务已提交，候选方案生成后可预览、审核并应用')
    await loadCandidates()
  } finally {
    candidateGenerating.value = false
  }
}
const generateCandidates = async () => {
  if (candidateForm.mode === 'LOCAL' && !candidateForm.selectedOfferingIds.length) {
    ElMessage.warning('局部重排至少选择一个教学任务')
    return
  }
  candidateGenerating.value = true
  try {
    await submitScheduleGenerationJob({
      ...candidateForm,
      semesterCode: semesterCode.value,
    })
    candidateDialog.value = false
    ElMessage.success('排课任务已提交，可在后台任务列表查看进度')
    await loadCandidates()
  } finally {
    candidateGenerating.value = false
  }
}
const cancelGenerationJob = async row => {
  await ElMessageBox.confirm('确认取消该自动排课任务？', '取消任务')
  await cancelScheduleGenerationJob(row.id)
  await loadCandidates()
}
const jobStatusName = status => ({ QUEUED: '排队中', RUNNING: '执行中', SUCCEEDED: '已完成', FAILED: '失败', CANCELLED: '已取消' }[status] || status)
const jobStatusType = status => ({ SUCCEEDED: 'success', FAILED: 'danger', CANCELLED: 'info', RUNNING: 'warning' }[status] || 'primary')
const reviewStatusName = status => ({ DRAFT: '草稿', SUBMITTED: '待审核', APPROVED: '审核通过', REJECTED: '已驳回' }[status] || status)
const editCandidateGovernance = async row => {
  const owner = await ElMessageBox.prompt(
    '请输入方案负责人账号',
    '协作信息',
    { inputValue: row.ownerUsername },
  ).then(value => value.value)
  const remark = await ElMessageBox.prompt(
    '请输入协作备注',
    '协作信息',
    { inputValue: row.collaborationRemark || '', inputType: 'textarea' },
  ).then(value => value.value)
  await updateScheduleCandidateGovernance(row.id, { ownerUsername: owner, remark })
  ElMessage.success('协作信息已更新')
  await loadCandidates()
}
const submitCandidateReview = async row => {
  await ElMessageBox.confirm(
    '提交后需要由另一名排课管理员审核，是否继续？',
    '提交审核',
  )
  await submitScheduleCandidateReview(row.id)
  await loadCandidates()
}
const reviewCandidate = async (row, approved) => {
  let comment
  if (!approved) {
    comment = await ElMessageBox.prompt(
      '请输入驳回原因',
      '驳回方案',
      { inputType: 'textarea' },
    ).then(value => value.value)
  }
  await reviewScheduleCandidate(row.id, approved, comment)
  ElMessage.success(approved ? '方案审核通过' : '方案已驳回')
  await loadCandidates()
}
const showCandidateDiff = async (row) => {
  currentDiff.value = (await previewScheduleCandidate(row.id)).data
  diffTitle.value = `${row.planName}－方案差异`
  diffDialog.value = true
}
const applyCandidate = async (row) => {
  const diff = (await previewScheduleCandidate(row.id)).data
  await ElMessageBox.confirm(
    `确认应用“${row.planName}”？将新增 ${diff.added}、移动 ${diff.moved}、移除 ${diff.removed} 条课表安排。`,
    '应用候选方案',
    { type: 'warning' },
  )
  await applyScheduleCandidate(row.id)
  ElMessage.success('候选方案已应用到当前课表草稿')
  await loadAll()
}
const discardCandidate = async (row) => {
  await ElMessageBox.confirm(`确认废弃“${row.planName}”？`, '废弃候选方案')
  await discardScheduleCandidate(row.id)
  ElMessage.success('候选方案已废弃')
  await loadCandidates()
}
const candidateStatus = (status) => ({
  CANDIDATE: '待应用',
  APPLIED: '已应用',
  DISCARDED: '已废弃',
})[status] || status
const changeTypeName = (type) => ({
  ADDED: '新增',
  MOVED: '移动',
  REMOVED: '移除',
})[type] || type
const publishVersion = async () => {
  const diff = (await previewSchedulePublication(semesterCode.value)).data
  await ElMessageBox.confirm(
    `发布后会生成不可变版本快照。本次将新增 ${diff.added}、移动 ${diff.moved}、移除 ${diff.removed} 条安排，确认发布？`,
    '发布课表',
    { type: 'warning' },
  )
  await publishScheduleVersion(semesterCode.value)
  ElMessage.success('课表版本已发布')
  await loadVersions()
}
const rollbackVersion = async row => {
  await ElMessageBox.confirm(`回滚到 V${row.versionNo}？系统会保留历史并生成一个新版本。`, '回滚课表', { type: 'warning' })
  await rollbackScheduleVersion(row.id)
  ElMessage.success('课表已回滚并生成新版本')
  await Promise.all([loadAll(), loadVersions()])
}
onMounted(async () => {
  await (reviewOnly ? loadAll() : Promise.all([loadAll(), loadIncidents()]))
  const { dateAction, sourceEntryId, sourceDate } = route.query
  if (activeTab.value === 'date-schedule' && typeof sourceEntryId === 'string'
    && typeof sourceDate === 'string') {
    const item = dateAction === 'MAKEUP'
      ? pendingMakeups.value.find(row => row.entry.id === sourceEntryId && row.sourceDate === sourceDate)
      : dateAction === 'SUBSTITUTE'
        ? pendingSubstitutions.value.find(row => row.occurrence.entry.id === sourceEntryId && row.date === sourceDate)
        : null
    if (item && dateAction === 'MAKEUP') openMakeup(item)
    else if (item && item.occurrence.occurrenceStatus === 'SCHEDULED') openSubstitution(item)
    else if (dateAction === 'MAKEUP' || dateAction === 'SUBSTITUTE') {
      ElMessage.info('该日期课次已变化，请从最新待处理清单重新选择')
    }
  }
  generationJobTimer = window.setInterval(() => {
    if (activeTab.value === 'candidates'
      && generationJobs.value.some(job => ['QUEUED', 'RUNNING'].includes(job.status))) {
      loadCandidates()
    }
  }, 3000)
})
onUnmounted(() => {
  window.clearInterval(generationJobTimer)
  window.clearTimeout(studentSearchTimer)
})
</script>

<style scoped>
.date-exception-error { margin-bottom: 16px; }
.page {
  padding: 24px;
}
header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 18px;
}
header h2 {
  margin: 0 0 6px;
}
header p {
  margin: 0;
  color: #84909a;
}
.scheduling-header-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 16px;
}
@media (max-width: 800px) {
  header { flex-wrap: wrap; }
  .scheduling-header-actions { justify-content: flex-start; }
}
header .el-input {
  width: 260px;
}
.toolbar {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-bottom: 12px;
}
.incident-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}
.incident-filters {
  display: flex;
  align-items: center;
  gap: 8px;
}
.incident-status,
.incident-type {
  width: 120px;
}
.incident-keyword {
  width: 280px;
}
.schedule-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 16px;
  margin-bottom: 12px;
}
.dimension-filter {
  display: flex;
  align-items: center;
  gap: 10px;
}
.schedule-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 10px;
}
.schedule-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}
.view-switch {
  margin-right: 8px;
  flex-shrink: 0;
}
.dimension-select {
  width: 140px;
}
.target-select {
  width: 280px;
}
.student-option-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  min-width: 280px;
  white-space: nowrap;
}
.separator {
  margin: 0 12px;
  color: #84909a;
}
.unit-label {
  margin-left: 10px;
  color: #84909a;
}
.full-width {
  width: 100%;
}
.candidate-table {
  margin-top: 12px;
}
.diff-summary {
  display: flex;
  gap: 10px;
  margin-bottom: 14px;
}
.score-detail {
  display: grid;
  gap: 8px;
}
.quality-summary {
  display: grid;
  grid-template-columns: repeat(5, minmax(120px, 1fr));
  gap: 12px;
  margin-bottom: 20px;
}
.quality-summary :deep(.el-statistic) {
  padding: 16px;
  background: #f6f9fa;
  border-radius: 8px;
}
</style>
