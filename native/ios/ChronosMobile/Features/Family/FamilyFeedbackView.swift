import SwiftUI

struct FamilyFeedbackView: View {
    let children: [FamilyChild]
    @EnvironmentObject private var model: AppModel
    @State private var studentID = ""
    @State private var title = ""
    @State private var content = ""
    @State private var message: String?
    @State private var submitting = false

    var body: some View {
        Form {
            Picker("学生", selection: $studentID) {
                Text("请选择").tag("")
                ForEach(children) { Text($0.studentName).tag($0.id) }
            }
            TextField("反馈主题", text: $title)
            TextField("反馈内容", text: $content, axis: .vertical).lineLimit(5...10)
            if let message { Text(message).foregroundStyle(message == "反馈已提交" ? .green : .red) }
            Button(submitting ? "正在提交…" : "提交反馈") { Task { await submit() } }
                .disabled(submitting || studentID.isEmpty || title.isEmpty || content.isEmpty)
        }.navigationTitle("家长反馈")
    }

    private func submit() async {
        submitting = true
        do {
            _ = try await model.session.sendJSON(FamilyAPI.feedback, body: .object([
                "studentId": .string(studentID), "title": .string(title), "content": .string(content)
            ]))
            message = "反馈已提交"; title = ""; content = ""
        } catch { message = error.localizedDescription }
        submitting = false
    }
}
