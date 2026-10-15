import SwiftUI

@main
struct ChronosMobileApp: App {
    @StateObject private var model = AppModel()
    var body: some Scene {
        WindowGroup { RootView().environmentObject(model) }
    }
}

@MainActor final class AppModel: ObservableObject {
    let session = ChronosSession()
    @Published var loggedIn = false
    @Published var bootstrap: PortalBootstrap?
    @Published var workbenchKind: WorkbenchKind = .staff
    @Published var error: String?
    @Published var isLoggingIn = false
    @Published var isBootstrapping = false

    init() {
        Task {
            if await session.restorePersisted() != nil {
                loggedIn = true
                await reloadBootstrap()
            }
        }
    }

    func login(username: String, password: String) async {
        guard !isLoggingIn else { return }
        isLoggingIn = true
        error = nil
        defer { isLoggingIn = false }
        do {
            _ = try await session.login(username: username, password: password)
            loggedIn = true
            await reloadBootstrap()
        } catch let loginError { error = loginError.localizedDescription }
    }

    func reloadBootstrap() async {
        guard !isBootstrapping else { return }
        isBootstrapping = true
        error = nil
        defer { isBootstrapping = false }
        do {
            let value = try await session.get("/portal/bootstrap", as: PortalBootstrap.self)
            bootstrap = value
            workbenchKind = WorkbenchResolver.resolve(bootstrap: value)
        } catch {
            self.error = error.localizedDescription
        }
    }

    func logout() {
        Task { await session.revoke() }
        loggedIn = false
        bootstrap = nil
        workbenchKind = .staff
        error = nil
        UserDefaults.standard.removeObject(forKey: "chronos.mobile.timetable.expanded")
    }
}

struct RootView: View {
    @EnvironmentObject var model: AppModel
    var body: some View {
        if model.loggedIn { PortalView() } else { LoginView() }
    }
}

struct LoginView: View {
    @EnvironmentObject var model: AppModel
    @State private var username = ""
    @State private var password = ""
    @State private var passwordVisible = false
    @FocusState private var focusedField: LoginField?

    private let portalGreen = Color(red: 8 / 255, green: 123 / 255, blue: 104 / 255)
    private let portalDark = Color(red: 6 / 255, green: 61 / 255, blue: 57 / 255)

    var body: some View {
        GeometryReader { geometry in
            ZStack {
                Color(red: 244 / 255, green: 247 / 255, blue: 248 / 255)
                    .ignoresSafeArea()

                Circle()
                    .fill(portalGreen.opacity(0.08))
                    .frame(width: 300, height: 300)
                    .blur(radius: 2)
                    .offset(x: geometry.size.width * 0.42, y: -geometry.size.height * 0.42)

                Circle()
                    .fill(portalGreen.opacity(0.05))
                    .frame(width: 230, height: 230)
                    .offset(x: -geometry.size.width * 0.42, y: geometry.size.height * 0.43)

                ScrollView {
                    VStack(spacing: 0) {
                        brandSection
                            .padding(.top, max(32, geometry.safeAreaInsets.top + 24))

                        loginCard
                            .padding(.top, 34)

                        footer
                            .padding(.top, 28)
                            .padding(.bottom, max(24, geometry.safeAreaInsets.bottom + 16))
                    }
                    .padding(.horizontal, 24)
                    .frame(maxWidth: 520)
                    .frame(
                        minHeight: geometry.size.height,
                        alignment: geometry.size.height < 700 ? .top : .center
                    )
                }
                .scrollDismissesKeyboard(.interactively)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
        .preferredColorScheme(.light)
    }

    private var brandSection: some View {
        VStack(spacing: 16) {
            ZStack {
                RoundedRectangle(cornerRadius: 19, style: .continuous)
                    .fill(
                        LinearGradient(
                            colors: [portalGreen, portalDark],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        )
                    )
                    .frame(width: 68, height: 68)
                    .shadow(color: portalGreen.opacity(0.25), radius: 20, y: 10)

                Text("C")
                    .font(.system(size: 34, weight: .black, design: .rounded))
                    .foregroundStyle(.white)
            }

            VStack(spacing: 7) {
                Text("CHRONOS")
                    .font(.system(size: 13, weight: .bold, design: .rounded))
                    .tracking(4)
                    .foregroundStyle(portalGreen)

                Text("连接校园每一项工作")
                    .font(.system(size: 25, weight: .bold))
                    .foregroundStyle(Color(red: 23 / 255, green: 32 / 255, blue: 42 / 255))

                Text("统一待办 · 智能办公 · 安全协同")
                    .font(.system(size: 14))
                    .foregroundStyle(Color(red: 113 / 255, green: 128 / 255, blue: 138 / 255))
            }
        }
        .frame(maxWidth: .infinity)
    }

    private var loginCard: some View {
        VStack(alignment: .leading, spacing: 22) {
            VStack(alignment: .leading, spacing: 7) {
                Text("欢迎回来")
                    .font(.system(size: 27, weight: .bold))
                    .foregroundStyle(Color(red: 23 / 255, green: 32 / 255, blue: 42 / 255))
                Text("登录 Chronos 统一门户")
                    .font(.system(size: 14))
                    .foregroundStyle(Color(red: 122 / 255, green: 135 / 255, blue: 145 / 255))
            }

            VStack(spacing: 15) {
                loginField(
                    title: "账号",
                    icon: "person",
                    text: $username,
                    field: .username
                )
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .textContentType(.username)
                .submitLabel(.next)
                .onSubmit { focusedField = .password }

                passwordField
            }

            if let error = model.error {
                Label(error, systemImage: "exclamationmark.circle.fill")
                    .font(.system(size: 13))
                    .foregroundStyle(Color(red: 183 / 255, green: 61 / 255, blue: 61 / 255))
                    .fixedSize(horizontal: false, vertical: true)
            }

            Button(action: submit) {
                HStack(spacing: 10) {
                    if model.isLoggingIn {
                        ProgressView()
                            .tint(.white)
                    }
                    Text(model.isLoggingIn ? "正在登录…" : "登录统一门户")
                        .font(.system(size: 16, weight: .bold))
                    if !model.isLoggingIn {
                        Image(systemName: "arrow.right")
                            .font(.system(size: 14, weight: .bold))
                    }
                }
                .frame(maxWidth: .infinity)
                .frame(height: 54)
                .foregroundStyle(.white)
                .background(
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .fill(canSubmit ? portalGreen : Color.gray.opacity(0.35))
                )
                .shadow(color: canSubmit ? portalGreen.opacity(0.22) : .clear, radius: 14, y: 7)
            }
            .buttonStyle(.plain)
            .disabled(!canSubmit || model.isLoggingIn)
        }
        .padding(24)
        .background(
            RoundedRectangle(cornerRadius: 24, style: .continuous)
                .fill(.white)
                .shadow(color: Color.black.opacity(0.07), radius: 28, y: 12)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 24, style: .continuous)
                .stroke(Color(red: 225 / 255, green: 231 / 255, blue: 235 / 255), lineWidth: 1)
        )
    }

    private func loginField(
        title: String,
        icon: String,
        text: Binding<String>,
        field: LoginField
    ) -> some View {
        HStack(spacing: 12) {
            Image(systemName: icon)
                .font(.system(size: 16, weight: .medium))
                .foregroundStyle(focusedField == field ? portalGreen : Color.gray)
                .frame(width: 22)
            TextField(title, text: text)
                .focused($focusedField, equals: field)
                .font(.system(size: 16))
                .foregroundStyle(Color(red: 23 / 255, green: 32 / 255, blue: 42 / 255))
        }
        .padding(.horizontal, 16)
        .frame(height: 54)
        .background(fieldBackground(isFocused: focusedField == field))
    }

    private var passwordField: some View {
        HStack(spacing: 12) {
            Image(systemName: "lock")
                .font(.system(size: 16, weight: .medium))
                .foregroundStyle(focusedField == .password ? portalGreen : Color.gray)
                .frame(width: 22)

            Group {
                if passwordVisible {
                    TextField("密码", text: $password)
                } else {
                    SecureField("密码", text: $password)
                }
            }
            .focused($focusedField, equals: .password)
            .font(.system(size: 16))
            .foregroundStyle(Color(red: 23 / 255, green: 32 / 255, blue: 42 / 255))
            .textContentType(.password)
            .submitLabel(.go)
            .onSubmit(submit)

            Button {
                passwordVisible.toggle()
            } label: {
                Image(systemName: passwordVisible ? "eye.slash" : "eye")
                    .foregroundStyle(Color.gray)
                    .frame(width: 28, height: 40)
            }
            .buttonStyle(.plain)
            .accessibilityLabel(passwordVisible ? "隐藏密码" : "显示密码")
        }
        .padding(.horizontal, 16)
        .frame(height: 54)
        .background(fieldBackground(isFocused: focusedField == .password))
    }

    private func fieldBackground(isFocused: Bool) -> some View {
        RoundedRectangle(cornerRadius: 13, style: .continuous)
            .fill(Color(red: 248 / 255, green: 250 / 255, blue: 251 / 255))
            .overlay(
                RoundedRectangle(cornerRadius: 13, style: .continuous)
                    .stroke(
                        isFocused ? portalGreen : Color(red: 218 / 255, green: 226 / 255, blue: 229 / 255),
                        lineWidth: isFocused ? 1.5 : 1
                    )
            )
    }

    private var footer: some View {
        VStack(spacing: 5) {
            Label("安全连接由 Chronos 统一身份认证提供", systemImage: "checkmark.shield")
                .font(.system(size: 12))
                .foregroundStyle(Color(red: 122 / 255, green: 135 / 255, blue: 145 / 255))
            Text("© 2026 Chronos")
                .font(.system(size: 11))
                .foregroundStyle(Color(red: 160 / 255, green: 170 / 255, blue: 177 / 255))
        }
        .frame(maxWidth: .infinity)
    }

    private var canSubmit: Bool {
        !username.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && !password.isEmpty
    }

    private func submit() {
        guard canSubmit, !model.isLoggingIn else { return }
        focusedField = nil
        Task {
            await model.login(
                username: username.trimmingCharacters(in: .whitespacesAndNewlines),
                password: password
            )
        }
    }
}

private enum LoginField: Hashable {
    case username
    case password
}

struct PortalView: View {
    @EnvironmentObject var model: AppModel

    var body: some View {
        Group {
            if let bootstrap = model.bootstrap {
                tabs(bootstrap)
            } else {
                BootstrapStateView()
            }
        }
        .tint(ChronosPalette.green)
        .preferredColorScheme(.light)
    }

    @ViewBuilder
    private func tabs(_ bootstrap: PortalBootstrap) -> some View {
        switch model.workbenchKind {
        case .student:
            TabView {
                StudentWorkbenchView(bootstrap: bootstrap)
                    .tabItem { Label("工作台", systemImage: "rectangle.grid.2x2") }
                GradesListView()
                    .tabItem { Label("成绩", systemImage: "graduationcap") }
                ExamsListView()
                    .tabItem { Label("考试", systemImage: "doc.text") }
                PublicationsListView(title: "通知", type: "NOTICE")
                    .tabItem { Label("通知", systemImage: "bell") }
                ProfileView(bootstrap: bootstrap, kind: .student)
                    .tabItem { Label("我的", systemImage: "person") }
            }
        case .teacher, .staff, .management:
            TabView {
                RoleWorkbenchView(bootstrap: bootstrap, kind: model.workbenchKind)
                    .tabItem { Label("工作台", systemImage: "rectangle.grid.2x2") }
                ApplicationsView(applications: bootstrap.applications)
                    .tabItem { Label("应用", systemImage: "square.grid.2x2") }
                PublicationsListView(title: "公告", type: "ANNOUNCEMENT")
                    .tabItem { Label("公告", systemImage: "megaphone") }
                ProfileView(bootstrap: bootstrap, kind: model.workbenchKind)
                    .tabItem { Label("我的", systemImage: "person") }
            }
        }
    }
}

private enum ChronosPalette {
    static let green = Color(red: 8 / 255, green: 123 / 255, blue: 104 / 255)
    static let dark = Color(red: 6 / 255, green: 61 / 255, blue: 57 / 255)
    static let background = Color(red: 244 / 255, green: 247 / 255, blue: 248 / 255)
    static let text = Color(red: 23 / 255, green: 32 / 255, blue: 42 / 255)
    static let secondary = Color(red: 113 / 255, green: 128 / 255, blue: 138 / 255)
    static let orange = Color(red: 232 / 255, green: 139 / 255, blue: 48 / 255)
}

private struct BootstrapStateView: View {
    @EnvironmentObject var model: AppModel

    var body: some View {
        ZStack {
            ChronosPalette.background.ignoresSafeArea()
            VStack(spacing: 16) {
                if model.isBootstrapping {
                    ProgressView()
                    Text("正在初始化工作台…")
                        .foregroundStyle(ChronosPalette.secondary)
                } else {
                    Image(systemName: "exclamationmark.arrow.triangle.2.circlepath")
                        .font(.system(size: 34))
                        .foregroundStyle(ChronosPalette.orange)
                    Text(model.error ?? "工作台初始化失败")
                        .multilineTextAlignment(.center)
                    Button("重新加载") {
                        Task { await model.reloadBootstrap() }
                    }
                    .buttonStyle(.borderedProminent)
                    Button("退出登录", role: .destructive, action: model.logout)
                }
            }
            .padding(28)
        }
    }
}

private struct StudentWorkbenchView: View {
    let bootstrap: PortalBootstrap
    @EnvironmentObject var model: AppModel

    var body: some View {
        NavigationStack {
            ScrollView {
                LazyVStack(spacing: 16) {
                    IdentityHeader(
                        eyebrow: "学生工作台",
                        name: bootstrap.user.displayName ?? bootstrap.user.username,
                        detail: bootstrap.contributions["DATA"]?.data?.termName ?? "当前学期",
                        icon: "graduationcap.fill"
                    )
                    TimetableCard()
                    LearningCapabilitiesView(applications: bootstrap.applications)
                }
                .padding()
            }
            .background(ChronosPalette.background)
            .navigationTitle("工作台")
            .refreshable { await model.reloadBootstrap() }
        }
    }
}

private struct RoleWorkbenchView: View {
    let bootstrap: PortalBootstrap
    let kind: WorkbenchKind
    @EnvironmentObject var model: AppModel

    var body: some View {
        NavigationStack {
            ScrollView {
                LazyVStack(spacing: 16) {
                    IdentityHeader(
                        eyebrow: title,
                        name: bootstrap.user.displayName ?? bootstrap.user.username,
                        detail: bootstrap.user.positionName ?? todayText,
                        icon: headerIcon
                    )
                    if kind == .teacher {
                        TimetableCard()
                    }
                    SummaryContributionCard(
                        title: "我的待办",
                        icon: "checklist",
                        contribution: bootstrap.contributions["WORKFLOW"],
                        emptyMessage: "当前没有待处理事项"
                    )
                    RecentMeetingsCard()
                    if kind == .management {
                        SummaryContributionCard(
                            title: "督办事项",
                            icon: "exclamationmark.bubble",
                            contribution: bootstrap.contributions["SUPERVISION"],
                            emptyMessage: "当前没有运行中的督办事项"
                        )
                        MetricsCard(contribution: bootstrap.contributions["DATA"])
                    } else {
                        CapabilityGroupCard(
                            title: "协同办公",
                            icon: "person.2.wave.2",
                            applications: collaborationApplications,
                            emptyMessage: "协同办公移动摘要建设中"
                        )
                        if kind == .teacher {
                            CapabilityGroupCard(
                                title: "教学中心",
                                icon: "books.vertical",
                                applications: teachingApplications,
                                emptyMessage: "教学能力将按服务端授权逐步开放"
                            )
                        } else {
                            SummaryContributionCard(
                                title: "公告摘要",
                                icon: "megaphone",
                                contribution: bootstrap.contributions["ANNOUNCEMENT"],
                                emptyMessage: "当前没有可见公告"
                            )
                        }
                    }
                }
                .padding()
            }
            .background(ChronosPalette.background)
            .navigationTitle("工作台")
            .refreshable { await model.reloadBootstrap() }
        }
    }

    private var title: String {
        switch kind {
        case .teacher: return "教师工作台"
        case .staff: return "教职工工作台"
        case .management: return "管理工作台"
        case .student: return "学生工作台"
        }
    }

    private var headerIcon: String {
        switch kind {
        case .teacher: return "person.crop.rectangle.stack.fill"
        case .management: return "building.columns.fill"
        default: return "person.text.rectangle.fill"
        }
    }

    private var collaborationApplications: [PortalApplication] {
        bootstrap.applications.filter {
            appMatches($0, ["请假", "出差", "用车", "用印", "公文", "文件", "会议", "workflow"])
        }
    }

    private var teachingApplications: [PortalApplication] {
        bootstrap.applications.filter {
            appMatches($0, ["课件", "题库", "错题", "教学", "education", "course"])
        }
    }
}

private struct IdentityHeader: View {
    let eyebrow: String
    let name: String
    let detail: String
    let icon: String

    var body: some View {
        HStack(spacing: 15) {
            Image(systemName: icon)
                .font(.system(size: 25, weight: .semibold))
                .foregroundStyle(.white)
                .frame(width: 54, height: 54)
                .background(ChronosPalette.green, in: RoundedRectangle(cornerRadius: 16))
            VStack(alignment: .leading, spacing: 5) {
                Text(eyebrow.uppercased())
                    .font(.caption.weight(.bold))
                    .tracking(1.4)
                    .foregroundStyle(ChronosPalette.green)
                Text(name)
                    .font(.title2.bold())
                    .foregroundStyle(ChronosPalette.text)
                Text("\(detail) · \(todayText)")
                    .font(.subheadline)
                    .foregroundStyle(ChronosPalette.secondary)
            }
            Spacer()
        }
        .padding(18)
        .cardStyle()
    }
}

private struct TimetableCard: View {
    @EnvironmentObject var model: AppModel
    @AppStorage("chronos.mobile.timetable.expanded") private var expanded = true
    @State private var weekOffset = 0
    @State private var state: ScheduleLoadState = .loading
    @State private var selectedLesson: ScheduleOccurrence?

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

    private var weekControls: some View {
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
    private var scheduleContent: some View {
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
    private var collapsedSummary: some View {
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

    private func timetableGrid(_ calendar: ScheduleCalendar) -> some View {
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

    private func gridHeader(_ text: String, width: CGFloat, highlighted: Bool) -> some View {
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

    private func lessonCell(_ lesson: ScheduleOccurrence) -> some View {
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

    private var weekDays: [(date: String, label: String)] {
        (0..<7).compactMap { index in
            guard let date = Calendar.current.date(byAdding: .day, value: index, to: weekStart) else {
                return nil
            }
            return (isoDate(date), "周" + "一二三四五六日".map(String.init)[index])
        }
    }

    private var weekStart: Date {
        let calendar = Calendar(identifier: .gregorian)
        let today = calendar.startOfDay(for: Date())
        let weekday = calendar.component(.weekday, from: today)
        let daysFromMonday = weekday == 1 ? -6 : 2 - weekday
        let monday = calendar.date(byAdding: .day, value: daysFromMonday, to: today) ?? today
        return calendar.date(byAdding: .weekOfYear, value: weekOffset, to: monday) ?? monday
    }

    private var weekRangeLabel: String {
        let end = Calendar.current.date(byAdding: .day, value: 6, to: weekStart) ?? weekStart
        return "\(isoDate(weekStart)) 至 \(isoDate(end))"
    }

    private func load() async {
        state = .loading
        let end = Calendar.current.date(byAdding: .day, value: 6, to: weekStart) ?? weekStart
        let path = "/portal/education/schedule/calendar?startDate=\(isoDate(weekStart))&endDate=\(isoDate(end))"
        do {
            let calendar = try await model.session.get(path, as: ScheduleCalendar.self)
            state = .loaded(calendar)
        } catch {
            state = .error(error.localizedDescription)
        }
    }

    private func todayLessons(_ calendar: ScheduleCalendar) -> [ScheduleOccurrence] {
        calendar.occurrences
            .filter { $0.date == isoDate(Date()) && !isCancelled($0.occurrenceStatus) }
            .sorted { $0.effectivePeriodNo < $1.effectivePeriodNo }
    }

    private func isPast(_ lesson: ScheduleOccurrence) -> Bool {
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

private enum ScheduleLoadState {
    case loading
    case loaded(ScheduleCalendar)
    case error(String)
}

private struct LessonDetailView: View {
    let lesson: ScheduleOccurrence
    @Environment(\.dismiss) private var dismiss

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

private struct LearningCapabilitiesView: View {
    let applications: [PortalApplication]

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("学习中心").font(.headline)
            ForEach(capabilities, id: \.title) { capability in
                NavigationLink {
                    if let application = capability.application {
                        ApplicationDestination(application: application)
                    } else {
                        ConstructionView(title: capability.title)
                    }
                } label: {
                    HStack(spacing: 13) {
                        Image(systemName: capability.icon)
                            .foregroundStyle(capability.application == nil ? .gray : ChronosPalette.green)
                            .frame(width: 34, height: 34)
                            .background(
                                (capability.application == nil ? Color.gray : ChronosPalette.green).opacity(0.1),
                                in: RoundedRectangle(cornerRadius: 10)
                            )
                        VStack(alignment: .leading, spacing: 3) {
                            Text(capability.title).foregroundStyle(ChronosPalette.text)
                            Text(capability.application?.description ?? "建设中，暂未获得服务端授权应用")
                                .font(.caption)
                                .foregroundStyle(ChronosPalette.secondary)
                                .lineLimit(2)
                        }
                        Spacer()
                        Text(capability.application == nil ? "建设中" : "进入")
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(capability.application == nil ? .gray : ChronosPalette.green)
                    }
                    .padding(.vertical, 4)
                }
            }
        }
        .padding(16)
        .cardStyle()
    }

    private var capabilities: [(title: String, icon: String, application: PortalApplication?)] {
        [
            ("课件", "doc.richtext", findApplication(["课件", "courseware", "material"])),
            ("错题沉淀", "xmark.circle", findApplication(["错题", "wrong"])),
            ("题库", "questionmark.square", findApplication(["题库", "question", "bank"]))
        ]
    }

    private func findApplication(_ markers: [String]) -> PortalApplication? {
        applications.first { appMatches($0, markers) }
    }
}

private struct SummaryContributionCard: View {
    let title: String
    let icon: String
    let contribution: PortalContribution?
    let emptyMessage: String

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            CardTitle(title: title, icon: icon)
            if let contribution, contribution.available, let data = contribution.data {
                if let total = data.total {
                    HStack(alignment: .firstTextBaseline, spacing: 7) {
                        Text("\(total)").font(.system(size: 30, weight: .bold))
                        Text("项").foregroundStyle(ChronosPalette.secondary)
                        Spacer()
                        if let overdue = data.overdue, overdue > 0 {
                            Text("\(overdue) 项逾期")
                                .font(.caption.weight(.semibold))
                                .foregroundStyle(.red)
                        }
                    }
                }
                let items = data.items ?? []
                if items.isEmpty {
                    Text(emptyMessage)
                        .font(.subheadline)
                        .foregroundStyle(ChronosPalette.secondary)
                        .frame(maxWidth: .infinity, minHeight: 54, alignment: .center)
                } else {
                    ForEach(items.prefix(3), id: \.stableID) { item in
                        VStack(alignment: .leading, spacing: 3) {
                            Text(item.title ?? item.flowName ?? "待处理事项")
                                .font(.subheadline.weight(.semibold))
                            Text(item.summary ?? item.nodeName ?? item.businessKey ?? "查看详情请前往 Web 门户")
                                .font(.caption)
                                .foregroundStyle(ChronosPalette.secondary)
                                .lineLimit(1)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.top, 8)
                        .overlay(alignment: .top) { Divider() }
                    }
                }
            } else {
                ConstructionInlineView(message: contribution?.message ?? "移动端摘要建设中")
            }
        }
        .padding(16)
        .cardStyle()
    }
}

private struct MetricsCard: View {
    let contribution: PortalContribution?

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            CardTitle(title: "数据概览", icon: "chart.bar.xaxis")
            if let metrics = contribution?.data?.metrics, !metrics.isEmpty {
                LazyVGrid(columns: [.init(.flexible()), .init(.flexible())], spacing: 10) {
                    ForEach(metrics.prefix(6)) { metric in
                        VStack(spacing: 4) {
                            Text("\(metric.value)").font(.title2.bold())
                            Text(metric.label)
                                .font(.caption)
                                .foregroundStyle(ChronosPalette.secondary)
                        }
                        .frame(maxWidth: .infinity)
                        .padding(12)
                        .background(ChronosPalette.background, in: RoundedRectangle(cornerRadius: 12))
                    }
                }
            } else {
                ConstructionInlineView(message: "当前账号暂无授权指标")
            }
        }
        .padding(16)
        .cardStyle()
    }
}

private struct RecentMeetingsCard: View {
    @EnvironmentObject var model: AppModel
    @State private var state: MeetingLoadState = .loading

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            CardTitle(title: "近期会议", icon: "person.3")
            switch state {
            case .loading:
                ProgressView("正在加载会议…").frame(maxWidth: .infinity, minHeight: 70)
            case .error(let message):
                InlineErrorView(message: message) { Task { await load() } }
            case .loaded(let meetings):
                if meetings.isEmpty {
                    Text("未来暂无需要参加的会议")
                        .foregroundStyle(ChronosPalette.secondary)
                        .frame(maxWidth: .infinity, minHeight: 54, alignment: .center)
                } else {
                    ForEach(meetings.prefix(3)) { value in
                        VStack(alignment: .leading, spacing: 4) {
                            Text(value.meeting.title).font(.subheadline.weight(.semibold))
                            Text("\(displayDateTime(value.meeting.startTime)) · \(value.currentParticipant?.responseStatus ?? "待确认")")
                                .font(.caption)
                                .foregroundStyle(ChronosPalette.secondary)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.top, 8)
                        .overlay(alignment: .top) { Divider() }
                    }
                }
            }
        }
        .padding(16)
        .cardStyle()
        .task { await load() }
    }

    private func load() async {
        state = .loading
        do {
            let values = try await model.session.get(
                "/portal/education/meetings",
                as: [MeetingView].self
            )
            let upcoming = values
                .filter { $0.meeting.status != "CANCELLED" }
                .sorted { $0.meeting.startTime < $1.meeting.startTime }
            state = .loaded(upcoming)
        } catch {
            state = .error(error.localizedDescription)
        }
    }
}

private enum MeetingLoadState {
    case loading
    case loaded([MeetingView])
    case error(String)
}

private struct CapabilityGroupCard: View {
    let title: String
    let icon: String
    let applications: [PortalApplication]
    let emptyMessage: String

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            CardTitle(title: title, icon: icon)
            if applications.isEmpty {
                ConstructionInlineView(message: emptyMessage)
            } else {
                ForEach(applications.prefix(5)) { application in
                    NavigationLink {
                        ApplicationDestination(application: application)
                    } label: {
                        HStack {
                            Text(application.name).foregroundStyle(ChronosPalette.text)
                            Spacer()
                            Image(systemName: "chevron.right")
                                .font(.caption)
                                .foregroundStyle(ChronosPalette.secondary)
                        }
                        .padding(.vertical, 5)
                    }
                }
            }
        }
        .padding(16)
        .cardStyle()
    }
}

private struct ApplicationsView: View {
    let applications: [PortalApplication]

    var body: some View {
        NavigationStack {
            Group {
                if applications.isEmpty {
                    EmptyStateView(icon: "square.grid.2x2", text: "当前账号暂无可用应用")
                } else {
                    List(applications) { application in
                        NavigationLink {
                            ApplicationDestination(application: application)
                        } label: {
                            HStack(spacing: 12) {
                                Text(String(application.name.prefix(1)))
                                    .font(.headline)
                                    .foregroundStyle(.white)
                                    .frame(width: 40, height: 40)
                                    .background(ChronosPalette.green, in: RoundedRectangle(cornerRadius: 11))
                                VStack(alignment: .leading, spacing: 3) {
                                    Text(application.name)
                                    Text(application.description ?? "服务端授权应用")
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                        .lineLimit(2)
                                }
                            }
                        }
                    }
                    .listStyle(.insetGrouped)
                }
            }
            .navigationTitle("应用")
        }
    }
}

@ViewBuilder
private func ApplicationDestination(application: PortalApplication) -> some View {
    let route = application.routePath.lowercased()
    if route.contains("/grades") {
        GradesListView()
    } else if route.contains("/my-exams") {
        ExamsListView()
    } else if route.contains("/schedule") {
        NavigationStack {
            ScrollView { TimetableCard().padding() }
                .background(ChronosPalette.background)
                .navigationTitle(application.name)
        }
    } else if route.contains("/publications") {
        PublicationsListView(title: application.name, type: nil)
    } else {
        ConstructionView(
            title: application.name,
            message: application.openMode == "EXTERNAL"
                ? "该应用为外部系统，请通过受控 Web 门户访问。"
                : "该应用已获授权，原生页面尚在建设中，请前往 Web 门户处理。"
        )
    }
}

private struct GradesListView: View {
    @EnvironmentObject var model: AppModel
    @State private var state: ListLoadState<GradeView> = .loading

    var body: some View {
        NavigationStack {
            LoadableList(
                state: state,
                emptyIcon: "graduationcap",
                emptyText: "当前学期暂无已发布成绩",
                retry: { Task { await load() } }
            ) { grade in
                HStack {
                    VStack(alignment: .leading, spacing: 5) {
                        Text(grade.courseName).font(.headline)
                        Text(grade.offeringName ?? grade.semesterCode ?? "课程成绩")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                    Spacer()
                    VStack(alignment: .trailing, spacing: 3) {
                        Text(decimalText(grade.totalScore))
                            .font(.title3.bold())
                            .foregroundStyle(grade.passed == false ? .red : ChronosPalette.green)
                        Text(grade.gradeLevel ?? (grade.passed == false ? "未通过" : "已发布"))
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }
                .padding(.vertical, 4)
            }
            .navigationTitle("成绩")
            .task { await load() }
            .refreshable { await load() }
        }
    }

    private func load() async {
        state = .loading
        do {
            state = .loaded(try await model.session.get(
                "/portal/education/grades",
                as: [GradeView].self
            ))
        } catch {
            state = .error(error.localizedDescription)
        }
    }
}

private struct ExamsListView: View {
    @EnvironmentObject var model: AppModel
    @State private var state: ListLoadState<ExamView> = .loading

    var body: some View {
        NavigationStack {
            LoadableList(
                state: state,
                emptyIcon: "doc.text",
                emptyText: "当前没有已发布考试安排",
                retry: { Task { await load() } }
            ) { exam in
                VStack(alignment: .leading, spacing: 6) {
                    HStack {
                        Text(exam.subjectName).font(.headline)
                        Spacer()
                        Text(exam.examDate)
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(ChronosPalette.green)
                    }
                    Text(exam.planName)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                    Text("\(exam.startTime)-\(exam.endTime) · \(exam.classroomName ?? "考场待定") · 座位 \(exam.seatNo.map(String.init) ?? "待定")")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                .padding(.vertical, 4)
            }
            .navigationTitle("考试")
            .task { await load() }
            .refreshable { await load() }
        }
    }

    private func load() async {
        state = .loading
        do {
            state = .loaded(try await model.session.get(
                "/portal/education/exam/my-exams",
                as: [ExamView].self
            ))
        } catch {
            state = .error(error.localizedDescription)
        }
    }
}

private struct PublicationsListView: View {
    let title: String
    let type: String?
    @EnvironmentObject var model: AppModel
    @State private var state: ListLoadState<PublicationView> = .loading

    var body: some View {
        NavigationStack {
            LoadableList(
                state: state,
                emptyIcon: "megaphone",
                emptyText: title == "通知" ? "当前没有可见通知" : "当前没有可见公告",
                retry: { Task { await load() } }
            ) { publication in
                VStack(alignment: .leading, spacing: 6) {
                    HStack(alignment: .top) {
                        if publication.pinned == true {
                            Text("置顶")
                                .font(.system(size: 10, weight: .bold))
                                .foregroundStyle(.white)
                                .padding(.horizontal, 6)
                                .padding(.vertical, 3)
                                .background(ChronosPalette.orange, in: Capsule())
                        }
                        Text(publication.title)
                            .font(.headline)
                        Spacer()
                        if !publication.read {
                            Circle().fill(ChronosPalette.green).frame(width: 8, height: 8)
                        }
                    }
                    Text(publication.summary ?? "暂无摘要")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .lineLimit(2)
                    Text(displayDateTime(publication.publishedAt))
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                .padding(.vertical, 4)
            }
            .navigationTitle(title)
            .task { await load() }
            .refreshable { await load() }
        }
    }

    private func load() async {
        state = .loading
        let typeQuery = type.map { "&type=\($0)" } ?? ""
        do {
            let page = try await model.session.get(
                "/publications?page=0&size=30\(typeQuery)",
                as: PublicationPage.self
            )
            state = .loaded(page.content)
        } catch {
            state = .error(error.localizedDescription)
        }
    }
}

private struct ProfileView: View {
    let bootstrap: PortalBootstrap
    let kind: WorkbenchKind
    @EnvironmentObject var model: AppModel

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    LabeledContent(
                        "姓名",
                        value: bootstrap.user.displayName ?? bootstrap.user.username
                    )
                    LabeledContent("账号", value: bootstrap.user.username)
                    LabeledContent("当前工作台", value: kind.displayName)
                    if let employeeId = bootstrap.user.employeeId, !employeeId.isEmpty {
                        LabeledContent("工号", value: employeeId)
                    }
                } header: {
                    Text("个人资料")
                } footer: {
                    Text("工作台由服务端身份、角色、权限和授权应用共同决定，不提供身份切换。")
                }
                Section("设置") {
                    Label("消息设置", systemImage: "bell.badge")
                    Label("隐私与安全", systemImage: "lock.shield")
                    LabeledContent("App 版本", value: "0.1.0")
                }
                Section {
                    Button("退出登录", role: .destructive, action: model.logout)
                }
            }
            .navigationTitle("我的")
        }
    }
}

private enum ListLoadState<Value> {
    case loading
    case loaded([Value])
    case error(String)
}

private struct LoadableList<Value, Row: View>: View {
    let state: ListLoadState<Value>
    let emptyIcon: String
    let emptyText: String
    let retry: () -> Void
    @ViewBuilder let row: (Value) -> Row

    var body: some View {
        switch state {
        case .loading:
            ProgressView("加载中…")
        case .error(let message):
            InlineErrorView(message: message, retry: retry)
                .padding()
        case .loaded(let values):
            if values.isEmpty {
                EmptyStateView(icon: emptyIcon, text: emptyText)
            } else {
                List {
                    ForEach(Array(values.enumerated()), id: \.offset) { _, value in
                        row(value)
                    }
                }
                .listStyle(.insetGrouped)
            }
        }
    }
}

private struct CardTitle: View {
    let title: String
    let icon: String

    var body: some View {
        Label(title, systemImage: icon)
            .font(.headline)
            .foregroundStyle(ChronosPalette.text)
    }
}

private struct InlineErrorView: View {
    let message: String
    let retry: () -> Void

    var body: some View {
        VStack(spacing: 9) {
            Label(message, systemImage: "exclamationmark.triangle")
                .font(.subheadline)
                .foregroundStyle(.red)
                .multilineTextAlignment(.center)
            Button("重试", action: retry)
                .font(.subheadline.weight(.semibold))
        }
        .frame(maxWidth: .infinity, minHeight: 90)
    }
}

private struct EmptyStateView: View {
    let icon: String
    let text: String

    var body: some View {
        ContentUnavailableView(text, systemImage: icon)
    }
}

private struct ConstructionInlineView: View {
    let message: String

    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: "hammer")
            Text(message)
        }
        .font(.subheadline)
        .foregroundStyle(ChronosPalette.secondary)
        .frame(maxWidth: .infinity, minHeight: 64, alignment: .center)
    }
}

private struct ConstructionView: View {
    let title: String
    var message = "功能建设中，暂未接入可用的移动端接口。"

    var body: some View {
        EmptyStateView(icon: "hammer", text: message)
            .navigationTitle(title)
    }
}

private extension View {
    func cardStyle() -> some View {
        background(.white, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
            .overlay {
                RoundedRectangle(cornerRadius: 18, style: .continuous)
                    .stroke(Color.black.opacity(0.045), lineWidth: 1)
            }
            .shadow(color: Color.black.opacity(0.045), radius: 12, y: 5)
    }
}

private extension WorkbenchKind {
    var displayName: String {
        switch self {
        case .student: return "学生"
        case .teacher: return "教师"
        case .staff: return "教职工"
        case .management: return "管理"
        }
    }
}

private extension ISO8601DateFormatter {
    static let chronosDateTime: ISO8601DateFormatter = {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime]
        return formatter
    }()
}

private let todayText: String = {
    let formatter = DateFormatter()
    formatter.locale = Locale(identifier: "zh_CN")
    formatter.dateFormat = "M月d日 EEEE"
    return formatter.string(from: Date())
}()

private func isoDate(_ date: Date) -> String {
    let formatter = DateFormatter()
    formatter.calendar = Calendar(identifier: .gregorian)
    formatter.locale = Locale(identifier: "en_US_POSIX")
    formatter.dateFormat = "yyyy-MM-dd"
    return formatter.string(from: date)
}

private func timeText(_ lesson: ScheduleOccurrence) -> String {
    if let start = lesson.startTime, !start.isEmpty,
       let end = lesson.endTime, !end.isEmpty {
        return "\(String(start.prefix(5)))-\(String(end.prefix(5)))"
    }
    return "第 \(lesson.effectivePeriodNo) 节"
}

private func statusText(_ status: String) -> String {
    switch status.uppercased() {
    case "CANCELLED": return "停课"
    case "MOVED_OUT": return "已调出"
    case "MOVED_IN": return "调课"
    case "SUBSTITUTED": return "代课"
    case "ADDED": return "临时课程"
    default: return "正常"
    }
}

private func statusColor(_ status: String) -> Color {
    switch status.uppercased() {
    case "CANCELLED", "MOVED_OUT": return .red
    case "MOVED_IN", "SUBSTITUTED", "ADDED": return ChronosPalette.orange
    default: return ChronosPalette.green
    }
}

private func isCancelled(_ status: String) -> Bool {
    ["CANCELLED", "MOVED_OUT"].contains(status.uppercased())
}

private func decimalText(_ value: Decimal?) -> String {
    guard let value else { return "--" }
    return NSDecimalNumber(decimal: value).stringValue
}

private func displayDateTime(_ value: String?) -> String {
    guard let value, !value.isEmpty else { return "时间待定" }
    return value
        .replacingOccurrences(of: "T", with: " ")
        .prefix(16)
        .description
}

private func appMatches(_ application: PortalApplication, _ markers: [String]) -> Bool {
    let haystack = [
        application.name,
        application.code ?? "",
        application.description ?? "",
        application.routePath
    ]
    .joined(separator: " ")
    .lowercased()
    return markers.contains { haystack.contains($0.lowercased()) }
}
