import SwiftUI

struct NativeRecordsView: View {
    let title: String, path: String, empty: String
    @EnvironmentObject private var model: AppModel
    @State private var rows: [JSONValue] = []
    @State private var loading = true
    @State private var error: String?
    var body: some View {
        Group {
            if loading { ProgressView("正在加载…") }
            else if let error { InlineErrorView(message: error, retry: { Task { await load() } }) }
            else if rows.isEmpty { EmptyStateView(icon: "tray", text: empty) }
            else { List(Array(rows.enumerated()), id: \.offset) { _, row in
                NavigationLink { NativeRecordDetailView(title: title, record: row) } label: { NativeRecordCard(record: row) }
            }.listStyle(.insetGrouped) }
        }.navigationTitle(title).task { await load() }.refreshable { await load() }
    }
    private func load() async {
        loading = true; error = nil
        do { rows = records(from: try await model.session.getJSON(path)) }
        catch { self.error = error.localizedDescription }
        loading = false
    }
}
