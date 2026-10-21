import SwiftUI

struct RepairMaterialRequestView: View {
    @EnvironmentObject private var model: AppModel
    @Environment(\.dismiss) private var dismiss
    let orderID: String
    let completed: () -> Void
    @State private var materials: [RepairMaterial] = []
    @State private var materialID = ""
    @State private var quantity = 1.0
    @State private var remark = ""
    @State private var error: String?

    var body: some View {
        Form {
            Picker("维修物料", selection: $materialID) { Text("请选择").tag(""); ForEach(materials.filter { $0.status == "ACTIVE" && $0.availableQuantity > 0 }) { Text("\($0.materialName)（可用 \($0.availableQuantity, specifier: "%.3g")\($0.unit)）").tag($0.id) } }
            TextField("申请数量", value: $quantity, format: .number).keyboardType(.decimalPad)
            TextField("申请说明", text: $remark, axis: .vertical)
            if let error { Text(error).foregroundStyle(.red) }
        }.navigationTitle("申请维修物料").navigationBarTitleDisplayMode(.inline)
        .toolbar { ToolbarItem(placement: .cancellationAction) { Button("取消") { dismiss() } }; ToolbarItem(placement: .confirmationAction) { Button("提交") { Task { await submit() } } } }
        .task { await load() }
    }

    @MainActor private func load() async { do { materials = try await model.session.get("/portal/facility/materials?page=0&size=100", as: RepairMaterialPage.self).content } catch { self.error = error.localizedDescription } }
    @MainActor private func submit() async {
        guard !materialID.isEmpty, quantity > 0 else { error = "请选择物料并填写有效数量"; return }
        do {
            _ = try await model.session.sendJSON("/portal/facility/material-requests", body: .object([
                "repairOrderId": .string(orderID), "remark": .string(remark),
                "items": .array([.object(["materialId": .string(materialID), "quantity": .number(quantity)])])
            ])); completed()
        } catch { self.error = error.localizedDescription }
    }
}
