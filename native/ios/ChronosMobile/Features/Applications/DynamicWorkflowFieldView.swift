import SwiftUI
import UniformTypeIdentifiers
import UIKit

struct DynamicWorkflowField: View {
    let field: [String: JSONValue]
    let businessID: String?
    let onUploadingChange: (Bool) -> Void
    @Binding var value: JSONValue
    @EnvironmentObject private var model: AppModel
    @State private var loadedOptions: [(String, String)] = []
    @State private var optionError: String?
    private var label: String { field["fieldLabel"]?.stringValue ?? "字段" }
    private var type: String { field["fieldType"]?.stringValue ?? "TEXT" }
    private var readonly: Bool {
        guard let permission = field["permission"]?.stringValue else { return false }
        return permission != "EDIT"
    }
    private var inlineOptions: [(String, String)] {
        guard let raw = field["optionsJson"]?.stringValue, let data = raw.data(using: .utf8),
              let rows = try? JSONSerialization.jsonObject(with: data) as? [[String: Any]] else { return [] }
        return rows.compactMap { guard let value = $0["value"] else { return nil }; return (String(describing: value), String(describing: $0["label"] ?? value)) }
    }
    private var options: [(String, String)] { loadedOptions.isEmpty ? inlineOptions : loadedOptions }
    var body: some View {
        switch type {
        case "BOOLEAN": Toggle(label, isOn: Binding(get: { value == .bool(true) }, set: { value = .bool($0) })).disabled(readonly)
        case "SELECT", "RADIO": VStack(alignment: .leading) {
            Picker(label, selection: textBinding) { Text("请选择").tag(""); ForEach(options, id: \.0) { Text($0.1).tag($0.0) } }.disabled(readonly)
            if let optionError { Text(optionError).font(.caption).foregroundStyle(.red) }
        }.task(id: field["optionsJson"]?.stringValue) { await loadConfiguredOptions() }
        case "CHECKBOX": VStack(alignment: .leading) {
            Text(label).font(.caption).foregroundStyle(.secondary)
            ForEach(options, id: \.0) { item in Toggle(item.1, isOn: checkBinding(item.0)).disabled(readonly) }
            if let optionError { Text(optionError).font(.caption).foregroundStyle(.red) }
        }.task(id: field["optionsJson"]?.stringValue) { await loadConfiguredOptions() }
        case "TEXTAREA": VStack(alignment: .leading) { Text(label).font(.caption).foregroundStyle(.secondary); TextEditor(text: textBinding).frame(minHeight: 90).disabled(readonly) }
        case "DATE": DatePicker(label, selection: dateBinding(includeTime: false), displayedComponents: .date).disabled(readonly)
        case "DATETIME": DatePicker(label, selection: dateBinding(includeTime: true), displayedComponents: [.date, .hourAndMinute]).disabled(readonly)
        case "FILE": WorkflowFileFieldView(
            label: label,
            businessID: businessID,
            readonly: readonly,
            onUploadingChange: onUploadingChange,
            value: $value
        )
        default: TextField(label + (field["required"] == .bool(true) ? " *" : ""), text: textBinding).keyboardType(type == "NUMBER" ? .decimalPad : .default).disabled(readonly)
        }
    }
    private var textBinding: Binding<String> { Binding(get: { value.stringValue ?? "" }, set: { value = .string($0) }) }
    private func dateBinding(includeTime: Bool) -> Binding<Date> {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "zh_CN")
        formatter.dateFormat = includeTime ? "yyyy-MM-dd HH:mm:ss" : "yyyy-MM-dd"
        return Binding(
            get: { formatter.date(from: value.stringValue ?? "") ?? Date() },
            set: { value = .string(formatter.string(from: $0)) }
        )
    }
    private func checkBinding(_ option: String) -> Binding<Bool> { Binding(get: { value.arrayValue?.contains(.string(option)) == true }, set: { selected in
        var list = value.arrayValue ?? []; if selected && !list.contains(.string(option)) { list.append(.string(option)) }; if !selected { list.removeAll { $0 == .string(option) } }; value = .array(list)
    }) }
    private func loadConfiguredOptions() async {
        guard let raw = field["optionsJson"]?.stringValue,
              let data = raw.data(using: .utf8),
              let config = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { return }
        let path: String
        let dictionary = config["dictCode"] as? String
        if let url = config["url"] as? String, config["source"] as? String == "REMOTE" {
            guard url.hasPrefix("/") else { optionError = "远程选项地址必须使用站内相对路径"; return }
            path = url
        } else if let dictionary, !dictionary.isEmpty {
            path = "/dicts/list?dictCode=\(dictionary.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? dictionary)"
        } else { return }
        do {
            let rows = records(from: try await model.session.getJSON(path))
            loadedOptions = rows.compactMap { row in
                guard let item = row.objectValue else { return nil }
                let optionValue = item["value"]?.stringValue ?? item["dictValue"]?.stringValue
                let optionLabel = item["label"]?.stringValue ?? item["dictName"]?.stringValue
                guard let optionValue else { return nil }
                return (optionValue, optionLabel ?? optionValue)
            }
            optionError = nil
        } catch {
            optionError = dictionary == nil ? "远程选项加载失败" : "字典选项加载失败"
        }
    }
}

private struct WorkflowFileFieldView: View {
    let label: String
    let businessID: String?
    let readonly: Bool
    let onUploadingChange: (Bool) -> Void
    @Binding var value: JSONValue
    @EnvironmentObject private var model: AppModel
    @State private var selecting = false
    @State private var uploading = false
    @State private var error: String?
    @State private var sharedFile: SharedWorkflowFile?

    private var attachments: [[String: JSONValue]] {
        (value.arrayValue ?? []).compactMap(\.objectValue)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(label).font(.caption).foregroundStyle(.secondary)
            ForEach(Array(attachments.enumerated()), id: \.offset) { _, attachment in
                HStack {
                    Button(attachmentName(attachment)) {
                        Task { await download(attachment) }
                    }
                    .buttonStyle(.borderless)
                    Spacer()
                    if !readonly {
                        Button(role: .destructive) {
                            Task { await remove(attachment) }
                        } label: {
                            Image(systemName: "trash")
                        }
                        .buttonStyle(.borderless)
                    }
                }
            }
            if attachments.isEmpty {
                Text("暂无附件").foregroundStyle(.secondary)
            }
            if !readonly {
                Button(uploading ? "正在上传…" : "选择附件") { selecting = true }
                    .disabled(uploading || attachments.count >= 5)
            }
            if let error {
                Text(error).font(.caption).foregroundStyle(.red)
            }
        }
        .fileImporter(
            isPresented: $selecting,
            allowedContentTypes: [.pdf, .image, .plainText, .data],
            allowsMultipleSelection: false
        ) { result in
            guard case .success(let urls) = result, let url = urls.first else {
                if case .failure(let failure) = result { error = failure.localizedDescription }
                return
            }
            Task { await upload(url) }
        }
        .sheet(item: $sharedFile) { file in
            WorkflowShareSheet(items: [file.url])
        }
    }

    private func upload(_ url: URL) async {
        let scoped = url.startAccessingSecurityScopedResource()
        defer { if scoped { url.stopAccessingSecurityScopedResource() } }
        do {
            let data = try Data(contentsOf: url)
            guard data.count <= 20 * 1024 * 1024 else {
                throw ChronosAPIError.server("单个附件不能超过 20MB")
            }
            uploading = true
            onUploadingChange(true)
            error = nil
            let uploaded = try await model.session.uploadFile(
                data: data,
                name: url.lastPathComponent,
                mimeType: UTType(filenameExtension: url.pathExtension)?.preferredMIMEType ?? "application/octet-stream",
                businessType: businessID == nil ? "WORKFLOW_FORM_DRAFT" : "WORKFLOW_FORM",
                businessId: businessID
            )
            value = .array((value.arrayValue ?? []) + [uploaded])
        } catch {
            self.error = error.localizedDescription
        }
        uploading = false
        onUploadingChange(false)
    }

    private func remove(_ attachment: [String: JSONValue]) async {
        guard let id = attachment["id"]?.stringValue else { return }
        do {
            try await model.session.deleteFile(id: id)
            value = .array((value.arrayValue ?? []).filter {
                $0.objectValue?["id"]?.stringValue != id
            })
            error = nil
        } catch {
            self.error = error.localizedDescription
        }
    }

    private func download(_ attachment: [String: JSONValue]) async {
        guard let id = attachment["id"]?.stringValue else { return }
        do {
            let data = try await model.session.downloadFile(id: id)
            let url = FileManager.default.temporaryDirectory
                .appendingPathComponent(attachmentName(attachment))
            try data.write(to: url, options: .atomic)
            sharedFile = SharedWorkflowFile(url: url)
            error = nil
        } catch {
            self.error = error.localizedDescription
        }
    }

    private func attachmentName(_ attachment: [String: JSONValue]) -> String {
        attachment["name"]?.stringValue
            ?? attachment["originalName"]?.stringValue
            ?? "附件"
    }
}

private struct SharedWorkflowFile: Identifiable {
    let id = UUID()
    let url: URL
}

private struct WorkflowShareSheet: UIViewControllerRepresentable {
    let items: [Any]
    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: items, applicationActivities: nil)
    }
    func updateUIViewController(_ controller: UIActivityViewController, context: Context) {}
}
