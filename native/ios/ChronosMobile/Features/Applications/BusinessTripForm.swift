import SwiftUI

struct BusinessTripForm: View {
    let onSubmitted: () -> Void
    @EnvironmentObject private var model: AppModel
    @Environment(\.dismiss) private var dismiss
    @State private var destination = ""
    @State private var purpose = ""
    @State private var startDate = Date()
    @State private var endDate = Date()
    @State private var transport = "0"
    @State private var accommodation = "0"
    @State private var meal = "0"
    @State private var other = "0"
    @State private var submitting = false
    @State private var error: String?

    var body: some View {
        Form {
            Section("行程") {
                TextField("出差地点", text: $destination)
                DatePicker("开始日期", selection: $startDate, displayedComponents: .date)
                DatePicker("结束日期", selection: $endDate, in: startDate..., displayedComponents: .date)
                TextField("出差事由", text: $purpose, axis: .vertical)
            }
            Section("预算（人民币）") {
                amountField("交通费", value: $transport)
                amountField("住宿费", value: $accommodation)
                amountField("餐费", value: $meal)
                amountField("其他费用", value: $other)
            }
            if let error { Text(error).foregroundStyle(.red) }
            Button(submitting ? "正在提交…" : "提交出差申请") { Task { await submit() } }.disabled(submitting)
        }.navigationTitle("发起出差")
    }

    private func amountField(_ title: String, value: Binding<String>) -> some View {
        TextField(title, text: value).keyboardType(.decimalPad)
    }

    private func submit() async {
        let place = destination.trimmingCharacters(in: .whitespacesAndNewlines)
        let note = purpose.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !place.isEmpty, !note.isEmpty else { error = "请填写出差地点和事由"; return }
        submitting = true
        do {
            _ = try await model.session.sendJSON("/portal/collaboration/business-trips", body: .object([
                "destination": .string(place), "startDate": .string(isoDate(startDate)), "endDate": .string(isoDate(endDate)),
                "purpose": .string(note), "currency": .string("CNY"),
                "transportAmount": .number(Double(transport) ?? 0), "accommodationAmount": .number(Double(accommodation) ?? 0),
                "mealAmount": .number(Double(meal) ?? 0), "otherAmount": .number(Double(other) ?? 0)]))
            onSubmitted(); dismiss()
        } catch { self.error = error.localizedDescription }
        submitting = false
    }
}
