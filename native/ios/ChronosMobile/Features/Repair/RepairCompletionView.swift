import PhotosUI
import SwiftUI

struct RepairCompletionView: View {
    @EnvironmentObject private var model: AppModel
    @Environment(\.dismiss) private var dismiss
    let orderID: String
    let completed: () -> Void
    @State private var note = ""
    @State private var photo: PhotosPickerItem?
    @State private var error: String?
    @State private var submitting = false

    var body: some View {
        Form {
            TextField("填写维修结果", text: $note, axis: .vertical).lineLimit(4...8)
            PhotosPicker(selection: $photo, matching: .images) { Label(photo == nil ? "选择完工照片" : "已选择完工照片", systemImage: "camera") }
            if let error { Text(error).foregroundStyle(.red) }
        }.navigationTitle("维修完成").navigationBarTitleDisplayMode(.inline)
        .toolbar { ToolbarItem(placement: .cancellationAction) { Button("取消") { dismiss() } }; ToolbarItem(placement: .confirmationAction) { Button("提交") { Task { await submit() } }.disabled(submitting) } }
    }

    @MainActor private func submit() async {
        guard !note.trimmingCharacters(in: .whitespaces).isEmpty, let photo else { error = "请填写维修结果并选择照片"; return }
        submitting = true; defer { submitting = false }
        do {
            guard let data = try await photo.loadTransferable(type: Data.self) else { throw ChronosAPIError.server("照片读取失败") }
            _ = try await model.session.uploadFile(data: data, name: "completion.jpg", mimeType: "image/jpeg", businessType: "FACILITY_REPAIR_COMPLETION", businessId: orderID)
            _ = try await model.session.sendJSON("/portal/facility/assigned-repair-orders/\(orderID)/complete", body: .object(["note": .string(note)]))
            completed()
        } catch { self.error = error.localizedDescription }
    }
}
