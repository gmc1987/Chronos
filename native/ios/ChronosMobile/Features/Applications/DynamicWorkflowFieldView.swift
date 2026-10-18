import SwiftUI

struct DynamicWorkflowField: View {
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
