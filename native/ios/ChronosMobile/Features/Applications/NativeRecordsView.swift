import SwiftUI

struct NativeRecordsView: View {
    let title: String, path: String, empty: String
    @EnvironmentObject private var model: AppModel
    @State private var rows: [JSONValue] = []
    @State private var loading = true
    @State private var error: String?
    @State private var query = ""
    private var visibleRows: [JSONValue] {
        let keyword = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !keyword.isEmpty else { return rows }
        return rows.filter { String(describing: $0).localizedCaseInsensitiveContains(keyword) }
    }
    var body: some View {
        Group {
            if loading { ProgressView("正在加载…") }
            else if let error { InlineErrorView(message: error, retry: { Task { await load() } }) }
            else if rows.isEmpty { EmptyStateView(icon: "tray", text: empty) }
            else if visibleRows.isEmpty { EmptyStateView(icon: "magnifyingglass", text: "没有匹配记录") }
            else { List(Array(visibleRows.enumerated()), id: \.offset) { _, row in
                NavigationLink { NativeRecordDetailView(title: title, record: row) } label: { NativeRecordCard(record: row) }
            }.listStyle(.insetGrouped) }
        }.navigationTitle(title).searchable(text: $query, prompt: "搜索当前列表")
            .task { await load() }.refreshable { await load() }
    }
    private func load() async {
        loading = true; error = nil
        do { rows = records(from: try await model.session.getJSON(path)) }
        catch { self.error = error.localizedDescription }
        loading = false
    }
}
