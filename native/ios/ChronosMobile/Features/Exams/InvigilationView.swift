import SwiftUI

struct InvigilationView: View {
    @EnvironmentObject private var model: AppModel
    @State private var duties: [ExamDuty] = []
    @State private var reasons: [String: String] = [:]
    @State private var loading = true
    @State private var error: String?

    var body: some View {
        List {
            if loading { ProgressView().frame(maxWidth: .infinity) }
            if let error { Text(error).foregroundStyle(.red) }
            if duties.isEmpty && !loading { Text("当前没有监考安排").foregroundStyle(.secondary) }
            ForEach(duties) { duty in
                Section {
                    LabeledContent("时间", value: "\(duty.examDate ?? "—") \(duty.startTime ?? "")-\(duty.endTime ?? "")")
                    LabeledContent("考场", value: duty.classroomName ?? "待定")
                    LabeledContent("状态", value: duty.assignmentStatus ?? duty.status ?? "—")
                    if duty.acknowledgedAt == nil {
                        Button("确认监考任务") { Task { await send(duty, action: "acknowledge") } }
                    }
                    if duty.checkedInAt == nil {
                        Button("到场签到") { Task { await send(duty, action: "check-in") } }
                    }
                    TextField("无法监考的原因", text: reasonBinding(duty.id), axis: .vertical)
                        .lineLimit(3...6)
                    Button("申请调换监考") { Task { await requestChange(duty) } }
                } header: {
                    Text(duty.subjectName ?? "考试监考")
                }
            }
        }
        .navigationTitle("我的监考")
        .task { await load() }
        .refreshable { await load() }
    }

    private func load() async {
        loading = true
        do {
            duties = try await model.session.get("/portal/education/exam/my-invigilations", as: [ExamDuty].self)
            error = nil
        } catch { self.error = error.localizedDescription }
        loading = false
    }

    private func send(_ duty: ExamDuty, action: String) async {
        do {
            _ = try await model.session.sendJSON("/portal/education/exam/my-invigilations/\(duty.id)/\(action)")
            await load()
        } catch { self.error = error.localizedDescription }
    }

    private func requestChange(_ duty: ExamDuty) async {
        let reason = reasons[duty.id, default: ""].trimmingCharacters(in: .whitespacesAndNewlines)
        guard !reason.isEmpty else { error = "请填写调换原因"; return }
        do {
            _ = try await model.session.sendJSON(
                "/portal/education/exam/my-invigilations/\(duty.id)/change-requests",
                body: .object(["reason": .string(reason)])
            )
            reasons[duty.id] = ""
            await load()
        } catch { self.error = error.localizedDescription }
    }

    private func reasonBinding(_ id: String) -> Binding<String> {
        Binding(get: { reasons[id, default: ""] }, set: { reasons[id] = $0 })
    }
}

private struct ExamDuty: Decodable, Identifiable {
    let assignmentId: String
    let subjectName: String?
    let examDate: String?
    let startTime: String?
    let endTime: String?
    let classroomName: String?
    let status: String?
    let assignmentStatus: String?
    let acknowledgedAt: String?
    let checkedInAt: String?
    var id: String { assignmentId }
}
