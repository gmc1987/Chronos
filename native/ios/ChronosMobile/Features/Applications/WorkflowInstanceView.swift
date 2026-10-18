import SwiftUI

struct WorkflowInstanceView: View {
    let instanceID: String
    @EnvironmentObject private var model: AppModel
    @State private var bundle: [String: JSONValue]?
    @State private var formValues: [String: [String: JSONValue]] = [:]
    @State private var comment = ""
    @State private var error: String?
    var body: some View {
        Form {
            if let bundle {
                ForEach(Array((bundle["forms"]?.arrayValue ?? []).enumerated()), id: \.offset) { _, form in
                    if let f = form.objectValue, let formID = f["formId"]?.stringValue {
                        Section(f["formName"]?.stringValue ?? "流程表单") {
                            DynamicWorkflowFields(fields: f["fields"]?.arrayValue ?? [], values: valuesBinding(formID))
                        }
                    }
                }
                let history = bundle["history"]?.arrayValue ?? []
                Section("审批记录") {
                    ForEach(Array(history.enumerated()), id: \.offset) { _, row in
                        NativeRecordCard(record: row)
                    }
                }
                if let task = bundle["currentTask"]?.objectValue { Section(task["taskKind"]?.stringValue == "STARTER_REWORK" ? "修改后重新提交" : "处理意见") {
                    TextField("请输入处理意见", text: $comment, axis: .vertical)
                    if task["taskKind"]?.stringValue == "STARTER_REWORK" {
                        Button("重新提交") { Task { await complete(.resubmit) } }
                    } else {
                        if bundle["operations"]?.objectValue?["approve"] != .bool(false) {
                            Button("同意") { Task { await complete(.approve) } }
                        }
                        if bundle["operations"]?.objectValue?["reject"] != .bool(false) {
                            Button("驳回", role: .destructive) { Task { await complete(.reject) } }
                        }
                    }
                }}
                if let error { Text(error).foregroundStyle(.red) }
            } else if let error { InlineErrorView(message: error, retry: { Task { await load() } }) }
            else { ProgressView("正在加载流程…") }
        }.navigationTitle(bundle?["flow"]?.objectValue?["flowName"]?.stringValue ?? "流程详情").task { await load() }
    }
    private func load() async {
        do { bundle = try await model.session.getJSON("/workflow-instances/\(instanceID)/forms").objectValue; formValues = [:]
            for form in bundle?["forms"]?.arrayValue ?? [] {
                guard let object = form.objectValue, let formID = object["formId"]?.stringValue else { continue }
                formValues[formID] = object["data"]?.objectValue ?? [:]
            }
            error = nil
        } catch { self.error = error.localizedDescription }
    }
    private enum Action { case approve, reject, resubmit }
    private func complete(_ action: Action) async {
        guard let id = bundle?["currentTask"]?.objectValue?["id"]?.stringValue else { return }
        do {
            for form in bundle?["forms"]?.arrayValue ?? [] {
                guard let object = form.objectValue, let formID = object["formId"]?.stringValue else { continue }
                let editableKeys = Set((object["fields"]?.arrayValue ?? []).compactMap { field -> String? in
                    guard let value = field.objectValue, value["permission"]?.stringValue == "EDIT" else { return nil }
                    return value["fieldKey"]?.stringValue
                })
                guard !editableKeys.isEmpty else { continue }
                let editableData = (formValues[formID] ?? [:]).filter { editableKeys.contains($0.key) }
                _ = try await model.session.sendJSON("/workflow-instances/\(instanceID)/forms/\(formID)", method: "PUT",
                    body: .object(["data": .object(editableData), "draft": .bool(false)]))
            }
            let path: String
            let body: JSONValue
            switch action {
            case .approve:
                path = "/workflow-tasks/\(id)/complete"
                body = .object(["approved": .bool(true), "comment": .string(comment)])
            case .reject:
                path = "/workflow-tasks/\(id)/reject"
                body = .object(["comment": .string(comment)])
            case .resubmit:
                path = "/workflow-tasks/\(id)/resubmit"
                body = .object(["comment": .string(comment)])
            }
            _ = try await model.session.sendJSON(path, body: body)
            await load()
        }
        catch { self.error = error.localizedDescription }
    }
    private func valuesBinding(_ formID: String) -> Binding<[String: JSONValue]> {
        Binding(get: { formValues[formID] ?? [:] }, set: { formValues[formID] = $0 })
    }
}
