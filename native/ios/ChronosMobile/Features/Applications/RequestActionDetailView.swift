import SwiftUI

enum RequestActionKind {
    case leave, businessTrip, office
}

/// Detail screen for staff requests with lifecycle actions allowed by the backend.
struct RequestActionDetailView: View {
    let title: String
    let record: JSONValue
    let kind: RequestActionKind
    let onChanged: () -> Void
    @EnvironmentObject private var model: AppModel
    @Environment(\.dismiss) private var dismiss
    @State private var reason = ""
    @State private var actualEndDate = Date()
    @State private var processing = false
    @State private var error: String?

    private var object: [String: JSONValue] { record.objectValue ?? [:] }
    private var id: String { object["id"]?.stringValue ?? "" }
    private var status: String { object["status"]?.stringValue ?? "" }

    var body: some View {
        Form {
            Section("申请信息") { NativeRecordCard(record: record) }
            if status == "PENDING" {
                Section("撤回申请") {
                    TextField("撤回原因（可选）", text: $reason, axis: .vertical)
                    Button("撤回", role: .destructive) { Task { await withdraw() } }
                        .disabled(processing)
                }
            }
            if status == "APPROVED" && kind == .businessTrip {
                Section("取消出差") {
                    TextField("取消原因", text: $reason, axis: .vertical)
                    Button("取消已批准的出差", role: .destructive) { Task { await cancelTrip() } }
                        .disabled(processing)
                }
            }
            if status == "APPROVED" && kind == .leave {
                Section("申请销假") {
                    TextField("销假原因", text: $reason, axis: .vertical)
                    DatePicker("实际结束日期", selection: $actualEndDate, displayedComponents: .date)
                    Button("提交销假申请") { Task { await cancelLeave() } }.disabled(processing)
                }
            }
            if let error { Text(error).foregroundStyle(.red) }
        }
        .navigationTitle(title)
    }

    private func withdraw() async {
        let path: String
        switch kind {
        case .leave: path = "/portal/education/leaves/\(id)/withdraw"
        case .businessTrip: path = "/portal/collaboration/business-trips/\(id)/withdraw"
        case .office: path = "/portal/collaboration/requests/\(id)/withdraw"
        }
        await perform(path, body: kind == .office ? .object([:]) : .object(["reason": .string(reason)]))
    }

    private func cancelTrip() async {
        guard !reason.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            error = "请填写取消原因"; return
        }
        await perform("/portal/collaboration/business-trips/\(id)/cancel",
                      body: .object(["reason": .string(reason)]))
    }

    private func cancelLeave() async {
        guard !reason.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            error = "请填写销假原因"; return
        }
        let formatter = DateFormatter(); formatter.dateFormat = "yyyy-MM-dd"
        await perform("/portal/education/leaves/\(id)/cancellation", body: .object([
            "reason": .string(reason), "actualEndDate": .string(formatter.string(from: actualEndDate))
        ]))
    }

    private func perform(_ path: String, body: JSONValue) async {
        guard !id.isEmpty else { error = "申请记录缺少标识"; return }
        processing = true
        defer { processing = false }
        do {
            _ = try await model.session.sendJSON(path, body: body)
            onChanged()
            dismiss()
        } catch { self.error = error.localizedDescription }
    }
}
