import SwiftUI

struct AvailableWorkflowsView: View {
    @EnvironmentObject private var model: AppModel
    @State private var rows: [JSONValue] = []
    @State private var error: String?
    var body: some View {
        Group {
            if let error { InlineErrorView(message: error, retry: { Task { await load() } }) }
            else if rows.isEmpty { ProgressView("正在加载可发起流程…") }
            else { List(Array(rows.enumerated()), id: \.offset) { _, row in
                if let id = row.objectValue?["id"]?.stringValue { NavigationLink { WorkflowStartView(flowID: id) } label: { NativeRecordCard(record: row) } }
            }.listStyle(.insetGrouped) }
        }.task { await load() }
    }
    private func load() async { do { rows = records(from: try await model.session.getJSON("/workflows/available")); error = nil } catch { self.error = error.localizedDescription } }
}
