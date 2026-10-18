import SwiftUI

struct OfficeRequestForm: View {
    let onSubmitted: () -> Void
    @EnvironmentObject private var model: AppModel
    @Environment(\.dismiss) private var dismiss
    @State private var requestType = "VEHICLE"
    @State private var resources: [JSONValue] = []
    @State private var resourceID = ""
    @State private var startAt = Date().addingTimeInterval(3600)
    @State private var endAt = Date().addingTimeInterval(7200)
    @State private var purpose = ""
    @State private var destination = ""
    @State private var documentTitle = ""
    @State private var passengerCount = 1
    @State private var copyCount = 1
    @State private var submitting = false
    @State private var error: String?

    var body: some View {
        Form {
            Section("申请类型") {
                Picker("类型", selection: $requestType) { Text("用车").tag("VEHICLE"); Text("用印").tag("SEAL") }
                Picker("资源", selection: $resourceID) {
                    Text("请选择").tag("")
                    ForEach(Array(resources.enumerated()), id: \.offset) { _, row in
                        let object = row.objectValue ?? [:]
                        Text(object["resourceName"]?.stringValue ?? "未命名资源").tag(object["id"]?.stringValue ?? "")
                    }
                }
                DatePicker("开始时间", selection: $startAt)
                DatePicker("结束时间", selection: $endAt, in: startAt...)
                TextField("申请事由", text: $purpose, axis: .vertical)
            }
            Section(requestType == "VEHICLE" ? "用车信息" : "用印信息") {
                if requestType == "VEHICLE" {
                    TextField("目的地", text: $destination)
                    Stepper("乘车人数：\(passengerCount)", value: $passengerCount, in: 1...99)
                } else {
                    TextField("文件名称", text: $documentTitle)
                    Stepper("文件份数：\(copyCount)", value: $copyCount, in: 1...999)
                }
            }
            if let error { Text(error).foregroundStyle(.red) }
            Button(submitting ? "正在提交…" : "提交申请") { Task { await submit() } }.disabled(submitting)
        }
        .navigationTitle("发起用车/用印")
        .task(id: requestType) { await loadResources() }
    }

    private func loadResources() async {
        resourceID = ""; error = nil
        do { resources = records(from: try await model.session.getJSON("/portal/collaboration/resources?type=\(requestType)")) }
        catch { self.error = error.localizedDescription }
    }

    private func submit() async {
        let note = purpose.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !resourceID.isEmpty, !note.isEmpty else { error = "请选择资源并填写申请事由"; return }
        if requestType == "VEHICLE" && destination.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            error = "请填写用车目的地"; return
        }
        if requestType == "SEAL" && documentTitle.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            error = "请填写用印文件名称"; return
        }
        submitting = true
        do {
            _ = try await model.session.sendJSON("/portal/collaboration/requests", body: .object([
                "requestType": .string(requestType), "resourceId": .string(resourceID),
                "startAt": .string(localDateTime(startAt)), "endAt": .string(localDateTime(endAt)), "purpose": .string(note),
                "destination": requestType == "VEHICLE" ? .string(destination) : .null,
                "passengerCount": requestType == "VEHICLE" ? .number(Double(passengerCount)) : .null,
                "documentTitle": requestType == "SEAL" ? .string(documentTitle) : .null,
                "copyCount": requestType == "SEAL" ? .number(Double(copyCount)) : .null]))
            onSubmitted(); dismiss()
        } catch { self.error = error.localizedDescription }
        submitting = false
    }

    private func localDateTime(_ value: Date) -> String {
        let formatter = DateFormatter(); formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "yyyy-MM-dd'T'HH:mm:ss"; return formatter.string(from: value)
    }
}
