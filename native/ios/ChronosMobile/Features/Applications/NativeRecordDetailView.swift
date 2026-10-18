import SwiftUI

struct NativeRecordDetailView: View {
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
