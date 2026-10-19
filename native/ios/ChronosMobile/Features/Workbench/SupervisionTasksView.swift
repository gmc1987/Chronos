import SwiftUI

struct SupervisionTasksView: View {
    @EnvironmentObject private var model: AppModel
    @State private var tasks: [SupervisionTask] = []
    @State private var proofs: [String: String] = [:]
    @State private var loading = true
    @State private var error: String?
    @State private var scanTarget: SupervisionScanTarget?

    var body: some View {
        List {
            if loading { ProgressView().frame(maxWidth: .infinity) }
            if let error { Text(error).foregroundStyle(.red) }
            if tasks.isEmpty && !loading { Text("当前没有督导任务").foregroundStyle(.secondary) }
            ForEach(tasks) { task in
                Section("督导任务 · \(task.status)") {
                    LabeledContent("教师", value: task.teacherId)
                    LabeledContent("排课记录", value: task.scheduleEntryId)
                    if task.status == "PENDING" {
                        Button("接受任务") { Task { await accept(task) } }
                    }
                    if task.status == "PENDING" || task.status == "ACCEPTED" {
                        TextField("扫描教师二维码后填入核验码", text: proofBinding(task.id), axis: .vertical)
                            .lineLimit(3...6)
                        Button("扫描教师二维码") {
                            scanTarget = SupervisionScanTarget(id: task.id)
                        }
                        Button("核验并签到") { Task { await checkIn(task) } }
                    }
                    if task.status == "CHECKED_IN" {
                        NavigationLink("填写并提交督导评价") {
                            SupervisionEvaluationView(task: task)
                        }
                    }
                    if task.status == "SUBMITTED" {
                        Button("完成督导任务") { Task { await complete(task) } }
                    }
                }
            }
        }
        .navigationTitle("督导任务")
        .task { await load() }
        .refreshable { await load() }
        .sheet(item: $scanTarget) { target in
            QRCodeScannerView { value in
                proofs[target.id] = value
                scanTarget = nil
            }
        }
    }

    private func load() async {
        loading = true
        do {
            tasks = try await model.session.get("/portal/education/supervision/tasks", as: [SupervisionTask].self)
            error = nil
        } catch { self.error = error.localizedDescription }
        loading = false
    }

    private func accept(_ task: SupervisionTask) async {
        do {
            _ = try await model.session.sendJSON("/portal/education/supervision/tasks/\(task.id)/accept")
            await load()
        } catch { self.error = error.localizedDescription }
    }

    private func checkIn(_ task: SupervisionTask) async {
        let token = proofs[task.id, default: ""].trimmingCharacters(in: .whitespacesAndNewlines)
        guard !token.isEmpty else { error = "请先扫描或填写教师核验码"; return }
        do {
            _ = try await model.session.sendJSON(
                "/portal/education/supervision/tasks/\(task.id)/check-in",
                body: .object(["proof": .object([
                    "providerId": .string("TEACHER_QR_V1"),
                    "signedProof": .string(token)
                ])])
            )
            proofs[task.id] = ""
            await load()
        } catch { self.error = error.localizedDescription }
    }

    private func complete(_ task: SupervisionTask) async {
        do {
            _ = try await model.session.sendJSON("/portal/education/supervision/tasks/\(task.id)/complete")
            await load()
        } catch { self.error = error.localizedDescription }
    }

    private func proofBinding(_ id: String) -> Binding<String> {
        Binding(get: { proofs[id, default: ""] }, set: { proofs[id] = $0 })
    }
}

private struct SupervisionScanTarget: Identifiable {
    let id: String
}

struct SupervisionTask: Decodable, Identifiable {
    let id: String
    let teacherId: String
    let scheduleEntryId: String
    let status: String
}
