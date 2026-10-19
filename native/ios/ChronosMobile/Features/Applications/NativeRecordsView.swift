import SwiftUI

struct NativeRecordsView: View {
    let title: String, path: String, empty: String
    @EnvironmentObject private var model: AppModel
    @State private var rows: [JSONValue] = []
    @State private var loading = true
    @State private var loadingMore = false
    @State private var page = 0
    @State private var last = true
    @State private var error: String?
    @State private var query = ""
    private var isContactDirectory: Bool { path.contains("/portal/collaboration/contacts") }
    private var visibleRows: [JSONValue] {
        if isContactDirectory { return rows }
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
                ForEach(Array(visibleRows.enumerated()), id: \.offset) { index, row in
                    NavigationLink { NativeRecordDetailView(title: title, record: row) } label: { NativeRecordCard(record: row) }
                        .onAppear {
                            guard isContactDirectory, index == visibleRows.count - 1, !last else { return }
                            Task { await load(reset: false) }
                        }
                }
                if loadingMore {
                    HStack { Spacer(); ProgressView("正在加载更多…"); Spacer() }
                } else if !last && query.isEmpty && !isContactDirectory {
                    Button("加载更多") { Task { await load(reset: false) } }.frame(maxWidth: .infinity)
                }
            }.listStyle(.insetGrouped) }
        }.navigationTitle(title).searchable(text: $query, prompt: "搜索当前列表")
            .onSubmit(of: .search) { if isContactDirectory { Task { await load(reset: true) } } }
            .onChange(of: query) { _, value in
                if isContactDirectory && value.isEmpty { Task { await load(reset: true) } }
            }
            .task { await load(reset: true) }.refreshable { await load(reset: true) }
    }
    private func load(reset: Bool) async {
        if reset {
            guard !loadingMore else { return }
            loading = true
        } else {
            guard !loading, !loadingMore, !last else { return }
            loadingMore = true
        }
        error = nil
        if reset { page = 0 }
        defer {
            loading = false
            loadingMore = false
        }
        do {
            let value = try await model.session.getJSON(pagedPath(page))
            let loaded = records(from: value)
            rows = reset ? loaded : rows + loaded
            let pagination = value.objectValue
            last = !supportsPaging || pagination == nil
                || pagination?["last"]?.stringValue == "是"
                || pagination?["last"]?.stringValue == "true"
            if !last { page += 1 }
        }
        catch { self.error = error.localizedDescription }
    }

    private var supportsPaging: Bool { path.contains("page=") }
    private func pagedPath(_ page: Int) -> String {
        guard supportsPaging, var components = URLComponents(string: path) else { return path }
        components.queryItems = (components.queryItems ?? []).map {
            if $0.name == "page" { return URLQueryItem(name: "page", value: String(page)) }
            if isContactDirectory && $0.name == "keyword" {
                return URLQueryItem(name: "keyword", value: query.trimmingCharacters(in: .whitespacesAndNewlines))
            }
            return $0
        }
        return components.string ?? path
    }
}
