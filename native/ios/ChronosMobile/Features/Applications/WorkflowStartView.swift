import SwiftUI

struct WorkflowStartView: View {
    let flowID: String
    @EnvironmentObject private var model: AppModel
    @Environment(\.dismiss) private var dismiss
    @State private var schema: [String: JSONValue]?
    @State private var values: [String: JSONValue] = [:]
    @State private var businessKey = ""
    @State private var error: String?
    @State private var submitting = false
    var body: some View {
        Form {
            if let schema {
                Section("申请信息") { TextField("业务编号（可选）", text: $businessKey); DynamicWorkflowFields(fields: schema["fields"]?.arrayValue ?? [], values: $values) }
                if let error { Text(error).foregroundStyle(.red) }
                Button(submitting ? "正在提交…" : "提交申请") { Task { await submit() } }.disabled(submitting)
            } else if let error { InlineErrorView(message: error, retry: { Task { await load() } }) }
            else { ProgressView("正在加载表单…") }
        }.navigationTitle(schema?["flowName"]?.stringValue ?? "发起流程").task { await load() }
    }
    private func load() async { do { schema = try await model.session.getJSON("/workflows/\(flowID)/start-form").objectValue; error = nil } catch { self.error = error.localizedDescription } }
    private func submit() async {
        guard let schema else { return }
        let missing = (schema["fields"]?.arrayValue ?? []).compactMap { field -> String? in
            guard let f = field.objectValue, f["required"] == .bool(true), let key = f["fieldKey"]?.stringValue,
                  values[key]?.stringValue?.isEmpty != false else { return nil }
            return f["fieldLabel"]?.stringValue ?? key
        }
        guard missing.isEmpty else { error = "请填写：\(missing.joined(separator: "、"))"; return }
        submitting = true
        do {
            _ = try await model.session.sendJSON("/workflows/\(flowID)/start", body: .object([
                "idempotencyKey": .string(UUID().uuidString), "businessKey": .string(businessKey),
                "formData": .object(values), "variablesJson": .string(jsonString(values))]))
            dismiss()
        } catch { self.error = error.localizedDescription }
        submitting = false
    }
}
