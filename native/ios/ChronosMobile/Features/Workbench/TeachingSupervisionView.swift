import SwiftUI
import UIKit

struct TeachingSupervisionView: View {
    @EnvironmentObject private var model: AppModel
    @State private var tasks: [TeachingSupervisionTask] = []
    @State private var loading = true
    @State private var error: String?
    @State private var copied = false

    var body: some View {
        List {
            if loading { ProgressView().frame(maxWidth: .infinity) }
            if let error { Text(error).foregroundStyle(.red) }
            if copied { Label("核验码已复制，请交给现场督导使用", systemImage: "checkmark.circle.fill").foregroundStyle(.green) }
            if tasks.isEmpty && !loading { Text("当前没有涉及本人的督导任务").foregroundStyle(.secondary) }
            ForEach(tasks) { task in
                Section("督导任务 · \(task.status)") {
                    LabeledContent("排课记录", value: task.scheduleEntryId)
                    Button("生成一次性签到核验码") { Task { await issueProof(task) } }
                }
            }
        }
        .navigationTitle("课堂督导核验")
        .task { await load() }
        .refreshable { await load() }
    }

    private func load() async {
        loading = true
        do {
            tasks = try await model.session.get(
                "/portal/education/supervision/teaching-tasks",
                as: [TeachingSupervisionTask].self
            )
            error = nil
        } catch { self.error = error.localizedDescription }
        loading = false
    }

    private func issueProof(_ task: TeachingSupervisionTask) async {
        do {
            let value = try await model.session.sendJSON(
                "/portal/education/supervision/teaching-tasks/\(task.id)/proof"
            )
            guard let token = value.objectValue?["signedProof"]?.stringValue, !token.isEmpty else {
                error = "服务端未返回有效核验码"; return
            }
            UIPasteboard.general.string = token
            copied = true
            error = nil
        } catch { self.error = error.localizedDescription }
    }
}

private struct TeachingSupervisionTask: Decodable, Identifiable {
    let id: String
    let scheduleEntryId: String
    let status: String
}
