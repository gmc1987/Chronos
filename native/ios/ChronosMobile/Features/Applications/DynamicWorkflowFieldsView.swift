import SwiftUI

struct DynamicWorkflowFields: View {
    let fields: [JSONValue]
    var businessID: String?
    var onUploadingChange: (Bool) -> Void = { _ in }
    @Binding var values: [String: JSONValue]
    var body: some View { ForEach(Array(fields.enumerated()), id: \.offset) { _, field in
        if let f = field.objectValue, let key = f["fieldKey"]?.stringValue {
            DynamicWorkflowField(
                field: f,
                businessID: businessID,
                onUploadingChange: onUploadingChange,
                value: binding(key)
            )
        }
    }}
    private func binding(_ key: String) -> Binding<JSONValue> { Binding(get: { values[key] ?? .string("") }, set: { values[key] = $0 }) }
}
