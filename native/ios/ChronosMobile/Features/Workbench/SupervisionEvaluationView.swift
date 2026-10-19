import SwiftUI

struct SupervisionEvaluationView: View {
    let task: SupervisionTask
    @EnvironmentObject private var model: AppModel
    @Environment(\.dismiss) private var dismiss
    @State private var formID = ""
    @State private var fields: [JSONValue] = []
    @State private var values: [String: JSONValue] = [:]
    @State private var loading = true
    @State private var uploading = false
    @State private var submitting = false
    @State private var error: String?

    var body: some View {
        Form {
            if loading { ProgressView("正在加载评价表单…") }
            if let error { Text(error).foregroundStyle(.red) }
            DynamicWorkflowFields(
                fields: fields,
                businessID: task.id,
                onUploadingChange: { uploading = $0 },
                values: $values
            )
            if !fields.isEmpty {
                Button(submitting ? "正在提交…" : "提交评价并完成任务") {
                    Task { await submit() }
                }
                .disabled(submitting || uploading)
            }
        }
        .navigationTitle("督导评价")
        .task { await load() }
    }

    private func load() async {
        do {
            let forms = try await model.session.getJSON("/portal/education/supervision/evaluation-forms")
                .arrayValue ?? []
            guard let id = forms.first?.objectValue?["id"]?.stringValue else {
                error = "尚未发布督导评价表单"; loading = false; return
            }
            formID = id
            fields = try await model.session.getJSON(
                "/portal/education/supervision/evaluation-forms/\(id)/fields"
            ).arrayValue ?? []
            error = nil
        } catch { self.error = error.localizedDescription }
        loading = false
    }

    private func submit() async {
        for field in fields.compactMap(\.objectValue) where field["required"] == .bool(true) {
            guard let key = field["fieldKey"]?.stringValue else { continue }
            let value = values[key]
            if value == nil || value == .string("") || value == .array([]) {
                error = "请填写“\(field["fieldLabel"]?.stringValue ?? key)”"
                return
            }
        }
        submitting = true
        defer { submitting = false }
        do {
            _ = try await model.session.sendJSON(
                "/portal/education/supervision/tasks/\(task.id)/form",
                body: .object([
                    "formTemplateId": .string(formID),
                    "values": .object(values),
                    "draft": .bool(false)
                ])
            )
            _ = try await model.session.sendJSON(
                "/portal/education/supervision/tasks/\(task.id)/submit",
                body: .object(["formTemplateId": .string(formID)])
            )
            _ = try await model.session.sendJSON(
                "/portal/education/supervision/tasks/\(task.id)/complete"
            )
            dismiss()
        } catch { self.error = error.localizedDescription }
    }
}
