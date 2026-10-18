import SwiftUI

struct ApplicationsView: View {
    let applications: [PortalApplication]
    var body: some View {
        NavigationStack {
            List(applications) { app in
                NavigationLink { ApplicationDestination(application: app) } label: {
                    HStack(spacing: 12) {
                        Text(String(app.name.prefix(1))).font(.headline).foregroundStyle(.white)
                            .frame(width: 42, height: 42)
                            .background(ChronosPalette.green, in: RoundedRectangle(cornerRadius: 12))
                        VStack(alignment: .leading, spacing: 3) {
                            Text(app.name).font(.headline)
                            Text(app.description ?? "服务端授权应用").font(.caption)
                                .foregroundStyle(.secondary).lineLimit(2)
                        }
                    }
                }
            }.overlay {
                if applications.isEmpty { EmptyStateView(icon: "square.grid.2x2", text: "当前账号暂无可用应用") }
            }.navigationTitle("应用")
        }
    }
}

private enum NativeFeature: String {
    case workflow = "WORKFLOW_TASKS", collaboration = "COLLABORATION", documents = "DOCUMENTS"
    case files = "FILES", meetings = "MEETINGS", trips = "BUSINESS_TRIPS"
    case knowledge = "KNOWLEDGE", ai = "AI_CENTER", teaching = "TEACHING_CENTER"

    init(_ app: PortalApplication) {
        let fallback: [String: Self] = ["workflow": .workflow, "oa": .collaboration,
            "document": .documents, "file": .files, "EDUCATION_MEETINGS": .meetings,
            "STAFF_BUSINESS_TRIP": .trips, "knowledge": .knowledge, "ai": .ai,
            "teaching-center": .teaching]
        self = Self(rawValue: app.nativeFeatureCode ?? "") ?? fallback[app.code ?? ""] ?? .collaboration
    }
}

@ViewBuilder func ApplicationDestination(application: PortalApplication) -> some View {
    if application.presentationMode == "EXTERNAL" || application.openMode == "EXTERNAL" {
        ExternalApplicationView(application: application)
    } else {
        switch NativeFeature(application) {
        case .workflow: WorkflowCenterView()
        case .collaboration: NativeModuleList(title: "协同办公", modules: collaborationModules)
        case .documents: NativeRecordsView(title: "公文中心", path: "/portal/collaboration/documents/my", empty: "暂无公文")
        case .files: NativeRecordsView(title: "文件中心", path: "/portal/collaboration/files/shares", empty: "暂无共享文件")
        case .meetings: NativeRecordsView(title: "我的会议", path: "/portal/education/meetings", empty: "暂无会议")
        case .trips: NativeRecordsView(title: "我的出差", path: "/portal/collaboration/business-trips", empty: "暂无出差申请")
        case .knowledge: NativeRecordsView(title: "知识中心", path: "/admin/knowledge-bases?page=0&size=50", empty: "暂无知识库")
        case .ai: NativeRecordsView(title: "AI 智能中心", path: "/agent/specs/list?page=0&size=50", empty: "暂无智能体")
        case .teaching: NativeModuleList(title: "教学中心", modules: teachingModules)
        }
    }
}

private struct ExternalApplicationView: View {
    let application: PortalApplication
    @Environment(\.openURL) private var openURL
    var body: some View {
        ContentUnavailableView { Label(application.name, systemImage: "arrow.up.right.square") }
        description: { Text("该功能由外部系统提供，将使用系统浏览器打开。") }
        actions: {
            Button("打开外部应用") { if let url = URL(string: application.routePath) { openURL(url) } }
                .buttonStyle(.borderedProminent)
        }.navigationTitle(application.name)
    }
}

private struct NativeModule: Identifiable {
    let id: String, title: String, detail: String, icon: String, path: String
}

private let collaborationModules = [
    NativeModule(id: "requests", title: "用车与用印", detail: "申请与执行状态", icon: "car", path: "/portal/collaboration/requests/my"),
    NativeModule(id: "trips", title: "出差", detail: "行程、预算和审批", icon: "suitcase", path: "/portal/collaboration/business-trips"),
    NativeModule(id: "documents", title: "公文", detail: "收发、签发和归档", icon: "doc.text", path: "/portal/collaboration/documents/my"),
    NativeModule(id: "files", title: "共享文件", detail: "统一文件共享", icon: "folder", path: "/portal/collaboration/files/shares"),
    NativeModule(id: "contacts", title: "通讯录", detail: "教职工联系方式", icon: "person.2", path: "/portal/collaboration/contacts?keyword=")]

private let teachingModules = [
    ("plans", "教学计划", "list.bullet.clipboard"), ("lessons", "教案", "doc.richtext"),
    ("preparations", "集体备课", "person.3"), ("courseware", "课件", "rectangle.on.rectangle"),
    ("materials", "教学材料", "books.vertical"), ("questions", "题库", "questionmark.square"),
    ("knowledge-points", "知识点", "point.3.connected.trianglepath.dotted"),
    ("research", "教研", "person.2.wave.2"), ("mistakes", "错题沉淀", "exclamationmark.bubble")]
    .map { NativeModule(id: $0.0, title: $0.1, detail: "查看和管理\($0.1)", icon: $0.2,
        path: "/education/teaching-center/api/\($0.0)?page=0&size=50") }

private struct NativeModuleList: View {
    let title: String, modules: [NativeModule]
    var body: some View {
        List(modules) { module in
            NavigationLink { NativeRecordsView(title: module.title, path: module.path, empty: "暂无\(module.title)数据") } label: {
                Label { VStack(alignment: .leading) { Text(module.title).font(.headline); Text(module.detail).font(.caption).foregroundStyle(.secondary) } }
                icon: { Image(systemName: module.icon).foregroundStyle(ChronosPalette.green) }
            }
        }.navigationTitle(title)
    }
}

private struct NativeRecordsView: View {
    let title: String, path: String, empty: String
    @EnvironmentObject private var model: AppModel
    @State private var rows: [JSONValue] = []
    @State private var loading = true
    @State private var error: String?
    var body: some View {
        Group {
            if loading { ProgressView("正在加载…") }
            else if let error { InlineErrorView(message: error, retry: { Task { await load() } }) }
            else if rows.isEmpty { EmptyStateView(icon: "tray", text: empty) }
            else { List(Array(rows.enumerated()), id: \.offset) { _, row in
                NavigationLink { NativeRecordDetailView(title: title, record: row) } label: { NativeRecordCard(record: row) }
            }.listStyle(.insetGrouped) }
        }.navigationTitle(title).task { await load() }.refreshable { await load() }
    }
    private func load() async {
        loading = true; error = nil
        do { rows = records(from: try await model.session.getJSON(path)) }
        catch { self.error = error.localizedDescription }
        loading = false
    }
}

private struct NativeRecordDetailView: View {
    let title: String
    let record: JSONValue
    var body: some View {
        List {
            ForEach(Array(detailFields(record).enumerated()), id: \.offset) { _, field in
                VStack(alignment: .leading, spacing: 5) {
                    Text(field.0).font(.caption).foregroundStyle(.secondary)
                    Text(field.1).textSelection(.enabled)
                }.padding(.vertical, 3)
            }
        }.navigationTitle(title + "详情")
    }
}

private struct NativeRecordCard: View {
    let record: JSONValue
    var body: some View {
        let fields = displayFields(record)
        VStack(alignment: .leading, spacing: 7) {
            Text(fields.first?.1 ?? "业务记录").font(.headline)
            ForEach(Array(fields.dropFirst().prefix(6)), id: \.0) { key, value in
                HStack(alignment: .top) { Text(fieldLabel(key)).foregroundStyle(.secondary).frame(width: 82, alignment: .leading); Text(value) }.font(.caption)
            }
        }.padding(.vertical, 4)
    }
}

private enum WorkflowTab: String, CaseIterable, Identifiable {
    case pending = "待办", handled = "已办", initiated = "我发起的", start = "发起"
    var id: String { rawValue }
}

private struct WorkflowCenterView: View {
    @State private var tab: WorkflowTab = .pending
    var body: some View {
        VStack(spacing: 0) {
            Picker("流程", selection: $tab) { ForEach(WorkflowTab.allCases) { Text($0.rawValue).tag($0) } }.pickerStyle(.segmented).padding()
            switch tab {
            case .pending: WorkflowRecordsView(path: "/workflow-tasks/pending?page=0&size=50", empty: "暂无待办任务")
            case .handled: WorkflowRecordsView(path: "/workflow-tasks/handled?page=0&size=50", empty: "暂无已办任务")
            case .initiated: WorkflowRecordsView(path: "/workflow-instances/initiated?page=0&size=50", empty: "暂无发起记录")
            case .start: AvailableWorkflowsView()
            }
        }.navigationTitle("流程中心")
    }
}

private struct WorkflowRecordsView: View {
    let path: String, empty: String
    @EnvironmentObject private var model: AppModel
    @State private var rows: [JSONValue] = []
    @State private var error: String?
    var body: some View {
        Group {
            if let error { InlineErrorView(message: error, retry: { Task { await load() } }) }
            else if rows.isEmpty { EmptyStateView(icon: "checkmark.circle", text: empty) }
            else { List(Array(rows.enumerated()), id: \.offset) { _, row in
                if let id = row.objectValue?["instanceId"]?.stringValue ?? row.objectValue?["id"]?.stringValue {
                    NavigationLink { WorkflowInstanceView(instanceID: id) } label: { NativeRecordCard(record: row) }
                }
            }.listStyle(.insetGrouped) }
        }.task { await load() }.refreshable { await load() }
    }
    private func load() async { do { rows = records(from: try await model.session.getJSON(path)); error = nil } catch { self.error = error.localizedDescription } }
}

private struct AvailableWorkflowsView: View {
    @EnvironmentObject private var model: AppModel
    @State private var rows: [JSONValue] = []
    @State private var error: String?
    var body: some View {
        Group {
            if let error { InlineErrorView(message: error, retry: { Task { await load() } }) }
            else if rows.isEmpty { ProgressView("正在加载可发起流程…") }
            else { List(Array(rows.enumerated()), id: \.offset) { _, row in
                if let id = row.objectValue?["id"]?.stringValue { NavigationLink { WorkflowStartView(flowID: id) } label: { NativeRecordCard(record: row) } }
            }.listStyle(.insetGrouped) }
        }.task { await load() }
    }
    private func load() async { do { rows = records(from: try await model.session.getJSON("/workflows/available")); error = nil } catch { self.error = error.localizedDescription } }
}

private struct WorkflowStartView: View {
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

private struct WorkflowInstanceView: View {
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

private struct DynamicWorkflowFields: View {
    let fields: [JSONValue]
    @Binding var values: [String: JSONValue]
    var body: some View { ForEach(Array(fields.enumerated()), id: \.offset) { _, field in
        if let f = field.objectValue, let key = f["fieldKey"]?.stringValue { DynamicWorkflowField(field: f, value: binding(key)) }
    }}
    private func binding(_ key: String) -> Binding<JSONValue> { Binding(get: { values[key] ?? .string("") }, set: { values[key] = $0 }) }
}

private struct DynamicWorkflowField: View {
    let field: [String: JSONValue]
    @Binding var value: JSONValue
    private var label: String { field["fieldLabel"]?.stringValue ?? "字段" }
    private var type: String { field["fieldType"]?.stringValue ?? "TEXT" }
    private var readonly: Bool { field["permission"]?.stringValue == "READ" }
    private var options: [(String, String)] {
        guard let raw = field["optionsJson"]?.stringValue, let data = raw.data(using: .utf8),
              let rows = try? JSONSerialization.jsonObject(with: data) as? [[String: Any]] else { return [] }
        return rows.compactMap { guard let value = $0["value"] else { return nil }; return (String(describing: value), String(describing: $0["label"] ?? value)) }
    }
    var body: some View {
        switch type {
        case "BOOLEAN": Toggle(label, isOn: Binding(get: { value == .bool(true) }, set: { value = .bool($0) })).disabled(readonly)
        case "SELECT", "RADIO": Picker(label, selection: textBinding) { Text("请选择").tag(""); ForEach(options, id: \.0) { Text($0.1).tag($0.0) } }.disabled(readonly)
        case "CHECKBOX": VStack(alignment: .leading) { Text(label).font(.caption).foregroundStyle(.secondary); ForEach(options, id: \.0) { item in Toggle(item.1, isOn: checkBinding(item.0)).disabled(readonly) } }
        case "TEXTAREA": VStack(alignment: .leading) { Text(label).font(.caption).foregroundStyle(.secondary); TextEditor(text: textBinding).frame(minHeight: 90).disabled(readonly) }
        case "FILE": LabeledContent(label, value: (value.arrayValue?.isEmpty == false) ? "\(value.arrayValue!.count) 个附件" : "暂无附件")
        default: TextField(label + (field["required"] == .bool(true) ? " *" : ""), text: textBinding).keyboardType(type == "NUMBER" ? .decimalPad : .default).disabled(readonly)
        }
    }
    private var textBinding: Binding<String> { Binding(get: { value.stringValue ?? "" }, set: { value = .string($0) }) }
    private func checkBinding(_ option: String) -> Binding<Bool> { Binding(get: { value.arrayValue?.contains(.string(option)) == true }, set: { selected in
        var list = value.arrayValue ?? []; if selected && !list.contains(.string(option)) { list.append(.string(option)) }; if !selected { list.removeAll { $0 == .string(option) } }; value = .array(list)
    }) }
}

private func records(from value: JSONValue) -> [JSONValue] {
    if let rows = value.arrayValue { return rows }; guard let object = value.objectValue else { return [] }
    for key in ["content", "items", "records", "data"] { if let rows = object[key]?.arrayValue { return rows } }; return [value]
}
private func displayFields(_ value: JSONValue) -> [(String, String)] {
    guard var object = value.objectValue else { return [("value", value.stringValue ?? "-")] }
    if let meeting = object["meeting"]?.objectValue { object.merge(meeting) { _, new in new } }
    let order = ["flowName", "title", "name", "purpose", "documentTitle", "fileName", "status", "nodeName", "initiator", "startTime", "endTime", "createTime", "description"] + object.keys.sorted()
    var result: [(String, String)] = []
    for key in order where !result.contains(where: { $0.0 == key }) { if let text = object[key]?.stringValue, !text.isEmpty { result.append((key, text)) } }
    return result.isEmpty ? [("value", "业务记录")] : result
}
private func fieldLabel(_ key: String) -> String { ["status":"状态", "nodeName":"节点", "initiator":"发起人", "startTime":"开始", "endTime":"结束", "createTime":"创建时间", "description":"说明", "purpose":"事由"][key] ?? key }
private func jsonString(_ values: [String: JSONValue]) -> String { guard let data = try? JSONEncoder().encode(values) else { return "{}" }; return String(data: data, encoding: .utf8) ?? "{}" }
private func detailFields(_ value: JSONValue, prefix: String = "") -> [(String, String)] {
    switch value {
    case .object(let object):
        return object.keys.sorted().flatMap { key in detailFields(object[key] ?? .null, prefix: prefix.isEmpty ? fieldLabel(key) : "\(prefix) · \(fieldLabel(key))") }
    case .array(let array):
        if array.isEmpty { return [(prefix, "暂无")] }
        return array.enumerated().flatMap { detailFields($0.element, prefix: "\(prefix) \($0.offset + 1)") }
    case .null: return [(prefix, "—")]
    default: return [(prefix, value.stringValue ?? "—")]
    }
}
