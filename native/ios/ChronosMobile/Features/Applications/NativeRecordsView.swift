import SwiftUI

struct NativeRecordsView: View {
    let title: String, path: String, empty: String
    @EnvironmentObject private var model: AppModel
    @State private var rows: [JSONValue] = []
    @State private var loading = true
    @State private var page = 0
    @State private var last = true
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
            else if let error { InlineErrorView(message: error, retry: { Task { await load(reset: true) } }) }
            else if rows.isEmpty { EmptyStateView(icon: "tray", text: empty) }
            else if visibleRows.isEmpty { EmptyStateView(icon: "magnifyingglass", text: "没有匹配记录") }
            else { List {
                ForEach(Array(visibleRows.enumerated()), id: \.offset) { _, row in
                    NavigationLink { NativeRecordDetailView(title: title, record: row) } label: { NativeRecordCard(record: row) }
                }
                if !last && query.isEmpty {
                    Button("加载更多") { Task { await load(reset: false) } }.frame(maxWidth: .infinity)
                }
            }.listStyle(.insetGrouped) }
        }.navigationTitle(title).searchable(text: $query, prompt: "搜索当前列表")
            .task { await load(reset: true) }.refreshable { await load(reset: true) }
    }
    private func load(reset: Bool) async {
        loading = true; error = nil
        if reset { page = 0 }
        do {
            let value = try await model.session.getJSON(pagedPath(page))
            let loaded = records(from: value)
            rows = reset ? loaded : rows + loaded
            last = !supportsPaging || value.objectValue?["last"]?.stringValue == "是"
                || value.objectValue?["last"]?.stringValue == "true"
            if !last { page += 1 }
        }
        catch { self.error = error.localizedDescription }
        loading = false
    }

    private var supportsPaging: Bool { path.contains("page=") }
    private func pagedPath(_ page: Int) -> String {
        guard supportsPaging, var components = URLComponents(string: path) else { return path }
        components.queryItems = (components.queryItems ?? []).map {
            $0.name == "page" ? URLQueryItem(name: "page", value: String(page)) : $0
        }
        return components.string ?? path
    }
}
