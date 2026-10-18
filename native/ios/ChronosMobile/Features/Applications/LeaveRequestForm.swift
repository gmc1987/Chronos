import SwiftUI

struct LeaveRequestForm: View {
    let onSubmitted: () -> Void
    @EnvironmentObject private var model: AppModel
    @Environment(\.dismiss) private var dismiss
    @State private var leaveType = "PERSONAL"
    @State private var startDate = Date()
    @State private var endDate = Date()
    @State private var reason = ""
    @State private var submitting = false
    @State private var error: String?

    var body: some View {
        Form {
            Section("请假信息") {
                Picker("假别", selection: $leaveType) {
                    Text("事假").tag("PERSONAL")
                    Text("病假").tag("SICK")
                    Text("年假").tag("ANNUAL")
                }
                DatePicker("开始日期", selection: $startDate, displayedComponents: .date)
                DatePicker("结束日期", selection: $endDate, in: startDate..., displayedComponents: .date)
                TextField("请假原因", text: $reason, axis: .vertical)
            }
            if let error { Text(error).foregroundStyle(.red) }
            Button(submitting ? "正在提交…" : "提交请假申请") { Task { await submit() } }
                .disabled(submitting)
        }
        .navigationTitle("发起请假")
    }

    private func submit() async {
        let text = reason.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { error = "请填写请假原因"; return }
        submitting = true
        do {
            _ = try await model.session.sendJSON("/portal/education/leaves", body: .object([
                "leaveType": .string(leaveType), "startDate": .string(isoDate(startDate)),
                "endDate": .string(isoDate(endDate)), "reason": .string(text)]))
            onSubmitted(); dismiss()
        } catch { self.error = error.localizedDescription }
        submitting = false
    }
}
