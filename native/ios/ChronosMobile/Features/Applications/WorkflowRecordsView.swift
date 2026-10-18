import SwiftUI

struct WorkflowRecordsView: View {
    let path: String, empty: String
    @EnvironmentObject private var model: AppModel
    @State private var rows: [JSONValue] = []
    @State private var error: String?
    var body: some View {
        Group {
            if let error { InlineErrorView(message: error, retry: { Task { await load() } }) }
            else if rows.isEmpty { EmptyStateView(icon: "checkmark.circle", text: empty) }
            else { List(Array(rows.enumerated()), id: \.offset) { _, row in
                if let id = row.objectValue?["instanceId"]?.stringValue ?? row.objectValue?["id"]?.stringValue {
                    NavigationLink { WorkflowInstanceView(instanceID: id) } label: { NativeRecordCard(record: row) }
                }
            }.listStyle(.insetGrouped) }
        }.task(id: path) { await load() }.refreshable { await load() }
    }
    private func load() async { do { rows = records(from: try await model.session.getJSON(path)); error = nil } catch { self.error = error.localizedDescription } }
}
