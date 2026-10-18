import SwiftUI

struct NativeRecordCard: View {
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
