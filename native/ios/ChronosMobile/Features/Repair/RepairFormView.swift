import PhotosUI
import SwiftUI

struct RepairFormView: View {
    @EnvironmentObject private var model: AppModel
    @Environment(\.dismiss) private var dismiss
    let completed: () -> Void
    @State private var title = ""
    @State private var location = ""
    @State private var description = ""
    @State private var contactName = ""
    @State private var contactPhone = ""
    @State private var category = "ELECTRICAL"
    @State private var urgency = "NORMAL"
    @State private var photos: [PhotosPickerItem] = []
    @State private var submitting = false
    @State private var error: String?

    var body: some View {
        Form {
            Section("故障信息") {
                TextField("故障标题", text: $title)
                Picker("故障分类", selection: $category) { ForEach(RepairLabels.category.keys.sorted(), id: \.self) { Text(RepairLabels.category[$0]!).tag($0) } }
                Picker("紧急程度", selection: $urgency) { Text("普通").tag("NORMAL"); Text("紧急").tag("URGENT"); Text("重大").tag("CRITICAL") }
                TextField("故障位置", text: $location)
                TextField("详细描述", text: $description, axis: .vertical).lineLimit(4...8)
            }
            Section("联系信息") { TextField("联系人", text: $contactName); TextField("联系电话", text: $contactPhone).keyboardType(.phonePad) }
            Section("故障照片") { PhotosPicker(selection: $photos, maxSelectionCount: 6, matching: .images) { Label("选择照片（至少一张）", systemImage: "camera") }; Text("已选择 \(photos.count) 张").foregroundStyle(.secondary) }
            if let error { Text(error).foregroundStyle(.red) }
        }
        .navigationTitle("提交报修").navigationBarTitleDisplayMode(.inline)
        .toolbar { ToolbarItem(placement: .cancellationAction) { Button("取消") { dismiss() } }; ToolbarItem(placement: .confirmationAction) { Button("提交") { Task { await submit() } }.disabled(submitting) } }
    }

    @MainActor private func submit() async {
        guard !title.trimmingCharacters(in: .whitespaces).isEmpty, !location.isEmpty, !description.isEmpty else { error = "请完整填写故障信息"; return }
        guard !photos.isEmpty else { error = "请至少选择一张故障照片"; return }
        submitting = true; defer { submitting = false }
        do {
            var fileIDs: [JSONValue] = []
            for (index, photo) in photos.enumerated() {
                guard let data = try await photo.loadTransferable(type: Data.self) else { continue }
                let uploaded = try await model.session.uploadFile(data: data, name: "repair-\(index + 1).jpg", mimeType: "image/jpeg", businessType: "WORKFLOW_FORM_DRAFT")
                if let id = uploaded.objectValue?["id"]?.stringValue { fileIDs.append(.string(id)) }
            }
            guard !fileIDs.isEmpty else { throw ChronosAPIError.server("照片上传失败") }
            _ = try await model.session.sendJSON("/portal/facility/repair-orders", body: .object([
                "title": .string(title), "category": .string(category), "description": .string(description),
                "location": .string(location), "urgency": .string(urgency), "contactName": .string(contactName),
                "contactPhone": .string(contactPhone), "dispatchMode": .string("AUTO"), "reportFileIds": .array(fileIDs)
            ]))
            completed()
        } catch { self.error = error.localizedDescription }
    }
}
