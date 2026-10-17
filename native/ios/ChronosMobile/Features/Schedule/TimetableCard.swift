import SwiftUI

struct TimetableCard: View {
    @EnvironmentObject var model: AppModel
    @AppStorage("chronos.mobile.timetable.expanded") var expanded = true
    @State var weekOffset = 0
    @State var state: ScheduleLoadState = .loading
    @State var selectedLesson: ScheduleOccurrence?

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack {
                VStack(alignment: .leading, spacing: 3) {
                    Text("二维周课表").font(.headline)
                    Text(weekRangeLabel)
                        .font(.caption)
                        .foregroundStyle(ChronosPalette.secondary)
                }
                Spacer()
                Button(expanded ? "收起" : "展开") {
                    withAnimation(.easeInOut(duration: 0.2)) { expanded.toggle() }
                }
                .font(.subheadline.weight(.semibold))
            }

            if expanded {
                weekControls
                scheduleContent
            } else {
                collapsedSummary
            }
        }
        .padding(16)
        .cardStyle()
        .task(id: weekOffset) { await load() }
        .sheet(item: $selectedLesson) { lesson in
            LessonDetailView(lesson: lesson)
                .presentationDetents([.medium])
        }
    }

    var weekControls: some View {
        HStack {
            Button { weekOffset -= 1 } label: {
                Label("上一周", systemImage: "chevron.left")
            }
            Spacer()
            Button("本周") { weekOffset = 0 }
                .disabled(weekOffset == 0)
            Spacer()
            Button { weekOffset += 1 } label: {
                Label("下一周", systemImage: "chevron.right")
                    .labelStyle(.titleAndIcon)
            }
        }
        .font(.caption.weight(.semibold))
    }

    @ViewBuilder
    var scheduleContent: some View {
        switch state {
        case .loading:
            HStack {
                Spacer()
                ProgressView("正在加载个人课表…")
                Spacer()
            }
            .frame(minHeight: 130)
        case .error(let message):
            InlineErrorView(message: message) { Task { await load() } }
        case .loaded(let calendar):
            if calendar.occurrences.isEmpty {
                EmptyStateView(icon: "calendar.badge.minus", text: "本周暂无课程")
            } else {
                timetableGrid(calendar)
            }
        }
    }

    @ViewBuilder
    var collapsedSummary: some View {
        switch state {
        case .loaded(let calendar):
            let lessons = todayLessons(calendar)
            if let next = lessons.first(where: { !isPast($0) }) ?? lessons.first {
                HStack {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("今日 \(lessons.count) 节课")
                            .font(.subheadline.weight(.semibold))
                        Text("下一节：\(next.entry.courseName) \(timeText(next))")
                            .font(.caption)
                            .foregroundStyle(ChronosPalette.secondary)
                    }
                    Spacer()
                    Image(systemName: "chevron.down")
                        .foregroundStyle(ChronosPalette.green)
                }
            } else {
                Label("今日无课程", systemImage: "cup.and.saucer")
                    .foregroundStyle(ChronosPalette.secondary)
            }
        case .loading:
            ProgressView("正在加载今日课程…")
        case .error(let message):
            InlineErrorView(message: message) { Task { await load() } }
        }
    }

    func timetableGrid(_ calendar: ScheduleCalendar) -> some View {
        let days = weekDays
        let periods = Array(Set(calendar.occurrences.map(\.effectivePeriodNo))).sorted()
        return ScrollView(.horizontal, showsIndicators: true) {
            VStack(alignment: .leading, spacing: 6) {
                HStack(spacing: 6) {
                    gridHeader("节次", width: 70, highlighted: false)
                    ForEach(days, id: \.date) { day in
                        gridHeader(
                            "\(day.label)\n\(String(day.date.dropFirst(5)))",
                            width: 126,
                            highlighted: day.date == isoDate(Date())
                        )
                    }
                }
                ForEach(periods, id: \.self) { period in
                    HStack(alignment: .top, spacing: 6) {
                        VStack(spacing: 4) {
                            Text("第 \(period) 节").font(.caption.bold())
                            if let lesson = calendar.occurrences.first(where: {
                                $0.effectivePeriodNo == period && !($0.startTime ?? "").isEmpty
                            }) {
                                Text(timeText(lesson))
                                    .font(.system(size: 9))
                                    .foregroundStyle(ChronosPalette.secondary)
                                    .multilineTextAlignment(.center)
                            }
                        }
                        .frame(width: 70, height: 98)
                        .background(ChronosPalette.background, in: RoundedRectangle(cornerRadius: 9))

                        ForEach(days, id: \.date) { day in
                            let lessons = calendar.occurrences.filter {
                                $0.date == day.date && $0.effectivePeriodNo == period
                            }
                            Group {
                                if let lesson = lessons.first {
                                    Button { selectedLesson = lesson } label: {
                                        lessonCell(lesson)
                                    }
                                    .buttonStyle(.plain)
                                } else {
                                    Color.clear
                                }
                            }
                            .frame(width: 126, height: 98)
                            .background(
                                day.date == isoDate(Date())
                                    ? ChronosPalette.green.opacity(0.05)
                                    : Color(red: 250 / 255, green: 252 / 255, blue: 252 / 255),
                                in: RoundedRectangle(cornerRadius: 9)
                            )
                        }
                    }
                }
            }
        }
    }

    func gridHeader(_ text: String, width: CGFloat, highlighted: Bool) -> some View {
        Text(text)
            .font(.caption.weight(.semibold))
            .multilineTextAlignment(.center)
            .foregroundStyle(highlighted ? ChronosPalette.green : ChronosPalette.secondary)
            .frame(width: width, height: 42)
            .background(
                highlighted ? ChronosPalette.green.opacity(0.09) : ChronosPalette.background,
                in: RoundedRectangle(cornerRadius: 9)
            )
    }

    func lessonCell(_ lesson: ScheduleOccurrence) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(lesson.entry.courseName)
                .font(.caption.bold())
                .foregroundStyle(ChronosPalette.text)
                .lineLimit(2)
            Text(lesson.entry.classroomName ?? "教室待定")
                .font(.system(size: 10))
                .foregroundStyle(ChronosPalette.secondary)
                .lineLimit(1)
            Text(statusText(lesson.occurrenceStatus))
                .font(.system(size: 9, weight: .semibold))
                .foregroundStyle(statusColor(lesson.occurrenceStatus))
        }
        .padding(8)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .background(statusColor(lesson.occurrenceStatus).opacity(0.09), in: RoundedRectangle(cornerRadius: 9))
        .overlay(alignment: .leading) {
            RoundedRectangle(cornerRadius: 2)
                .fill(statusColor(lesson.occurrenceStatus))
                .frame(width: 3)
                .padding(.vertical, 7)
        }
    }

    var weekDays: [(date: String, label: String)] {
        (0..<7).compactMap { index in
            guard let date = Calendar.current.date(byAdding: .day, value: index, to: weekStart) else {
                return nil
            }
            return (isoDate(date), "周" + "一二三四五六日".map(String.init)[index])
        }
    }

    var weekStart: Date {
        let calendar = Calendar(identifier: .gregorian)
        let today = calendar.startOfDay(for: Date())
        let weekday = calendar.component(.weekday, from: today)
        let daysFromMonday = weekday == 1 ? -6 : 2 - weekday
        let monday = calendar.date(byAdding: .day, value: daysFromMonday, to: today) ?? today
        return calendar.date(byAdding: .weekOfYear, value: weekOffset, to: monday) ?? monday
    }

    var weekRangeLabel: String {
        let end = Calendar.current.date(byAdding: .day, value: 6, to: weekStart) ?? weekStart
        return "\(isoDate(weekStart)) 至 \(isoDate(end))"
    }

    func load() async {
        state = .loading
        let end = Calendar.current.date(byAdding: .day, value: 6, to: weekStart) ?? weekStart
        let path = ScheduleAPI.calendar(
            startDate: isoDate(weekStart),
            endDate: isoDate(end)
        )
        do {
            let calendar = try await model.session.get(path, as: ScheduleCalendar.self)
            state = .loaded(calendar)
        } catch {
            state = .error(error.localizedDescription)
        }
    }

    func todayLessons(_ calendar: ScheduleCalendar) -> [ScheduleOccurrence] {
        calendar.occurrences
            .filter { $0.date == isoDate(Date()) && !isCancelled($0.occurrenceStatus) }
            .sorted { $0.effectivePeriodNo < $1.effectivePeriodNo }
    }

    func isPast(_ lesson: ScheduleOccurrence) -> Bool {
        guard lesson.date == isoDate(Date()),
              let endTime = lesson.endTime,
              let date = ISO8601DateFormatter.chronosDateTime.date(
                from: "\(lesson.date)T\(endTime)"
              ) else {
            return false
        }
        return date < Date()
    }
}

enum ScheduleLoadState {
    case loading
    case loaded(ScheduleCalendar)
    case error(String)
}

struct LessonDetailView: View {
    let lesson: ScheduleOccurrence
    @Environment(\.dismiss) var dismiss

    var body: some View {
        NavigationStack {
            List {
                LabeledContent("课程", value: lesson.entry.courseName)
                LabeledContent("日期", value: lesson.date)
                LabeledContent("时间", value: timeText(lesson))
                LabeledContent("教师", value: lesson.effectiveTeacherName ?? lesson.entry.teacherName ?? "待定")
                LabeledContent("教室", value: lesson.entry.classroomName ?? "待定")
                LabeledContent("教学班", value: lesson.entry.teachingClassName ?? "未提供")
                LabeledContent("状态", value: statusText(lesson.occurrenceStatus))
                if let reason = lesson.reason, !reason.isEmpty {
                    Section("调整说明") { Text(reason) }
                }
            }
            .navigationTitle("课程详情")
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("完成") { dismiss() }
                }
            }
        }
    }
}
