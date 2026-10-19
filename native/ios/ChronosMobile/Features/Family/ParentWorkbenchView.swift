import SwiftUI

struct ParentWorkbenchView: View {
    let bootstrap: PortalBootstrap
    @EnvironmentObject private var model: AppModel
    @State private var children: [FamilyChild] = []
    @State private var grades: [JSONValue] = []
    @State private var loading = true
    @State private var error: String?

    var body: some View {
        NavigationStack {
            ScrollView {
                LazyVStack(spacing: 16) {
                    IdentityHeader(
                        eyebrow: "家长工作台",
                        name: bootstrap.user.displayName ?? bootstrap.user.username,
                        detail: "子女学业与家校协同",
                        icon: "person.2.fill"
                    )
                    if loading { ProgressView("正在加载家庭信息…").padding() }
                    if let error { Text(error).foregroundStyle(.red).cardStyle() }
                    childrenCard
                    gradesCard
                    NavigationLink { FamilyNoticesView() } label: {
                        Label("家校通知与回执", systemImage: "bell.badge")
                            .font(.headline).frame(maxWidth: .infinity, alignment: .leading).cardStyle()
                    }.buttonStyle(.plain)
                    NavigationLink { FamilyFeedbackView(children: children) } label: {
                        Label("家长反馈", systemImage: "bubble.left.and.bubble.right")
                            .font(.headline).frame(maxWidth: .infinity, alignment: .leading).cardStyle()
                    }.buttonStyle(.plain)
                }.padding()
            }
            .background(ChronosPalette.background)
            .navigationTitle("工作台")
            .task { await load() }
            .refreshable { await load() }
        }
    }

    private var childrenCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("我的子女").font(.headline)
            if children.isEmpty && !loading { Text("当前账号尚未绑定有效学生").foregroundStyle(.secondary) }
            ForEach(children) { child in
                HStack {
                    Image(systemName: "graduationcap.fill").foregroundStyle(ChronosPalette.green)
                    VStack(alignment: .leading) {
                        Text(child.studentName).fontWeight(.semibold)
                        Text([child.studentNo, child.relationship].compactMap { $0 }.joined(separator: " · "))
                            .font(.caption).foregroundStyle(.secondary)
                    }
                    Spacer()
                    if child.primaryGuardian == true { Text("主要监护人").font(.caption).foregroundStyle(ChronosPalette.green) }
                }
            }
        }.cardStyle()
    }

    private var gradesCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("成绩概览").font(.headline)
            if grades.isEmpty && !loading { Text("当前没有已发布成绩").foregroundStyle(.secondary) }
            ForEach(Array(grades.prefix(5).enumerated()), id: \.offset) { _, grade in
                Text(gradeSummary(grade)).font(.subheadline)
                Divider()
            }
        }.cardStyle()
    }

    private func load() async {
        loading = true
        do {
            async let childValues = model.session.get(FamilyAPI.children, as: [FamilyChild].self)
            async let gradeValues = model.session.get(FamilyAPI.grades, as: [JSONValue].self)
            (children, grades) = try await (childValues, gradeValues)
            error = nil
        } catch { self.error = error.localizedDescription }
        loading = false
    }

    private func gradeSummary(_ grade: JSONValue) -> String {
        guard let value = grade.objectValue else { return grade.stringValue ?? "成绩记录" }
        let course = value["courseName"]?.stringValue ?? value["course"]?.stringValue ?? "课程"
        let score = value["score"]?.stringValue ?? value["finalScore"]?.stringValue ?? "待发布"
        return "\(course) · \(score)"
    }
}
