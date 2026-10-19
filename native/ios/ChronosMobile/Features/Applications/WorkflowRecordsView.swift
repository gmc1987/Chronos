import SwiftUI

struct WorkflowRecordsView: View {
    let path: String, empty: String
    @EnvironmentObject private var model: AppModel
    @State private var rows: [JSONValue] = []
    @State private var error: String?
    @State private var page = 0
    @State private var last = true
    @State private var loading = false
    var body: some View {
        Group {
            if let error { InlineErrorView(message: error, retry: { Task { await load(reset: true) } }) }
            else if rows.isEmpty { EmptyStateView(icon: "checkmark.circle", text: empty) }
            else { List {
                ForEach(Array(rows.enumerated()), id: \.offset) { _, row in
                    if let id = row.objectValue?["instanceId"]?.stringValue ?? row.objectValue?["id"]?.stringValue {
                        NavigationLink { WorkflowInstanceView(instanceID: id) } label: { NativeRecordCard(record: row) }
                    }
                }
                if !last { Button("加载更多") { Task { await load(reset: false) } }.frame(maxWidth: .infinity) }
            }.listStyle(.insetGrouped) }
        }.task(id: path) { await load(reset: true) }.refreshable { await load(reset: true) }
    }
    private func load(reset: Bool) async {
        guard !loading else { return }
        loading = true
        if reset { page = 0 }
        do {
            let value = try await model.session.getJSON(pagedPath(page))
            let loaded = records(from: value)
            rows = reset ? loaded : rows + loaded
            last = value.objectValue?["last"]?.stringValue == "是"
                || value.objectValue?["last"]?.stringValue == "true"
            if !last { page += 1 }
            error = nil
        } catch { self.error = error.localizedDescription }
        loading = false
    }
    private func pagedPath(_ page: Int) -> String {
        guard var components = URLComponents(string: path) else { return path }
        components.queryItems = (components.queryItems ?? []).map {
            $0.name == "page" ? URLQueryItem(name: "page", value: String(page)) : $0
        }
        return components.string ?? path
    }
}
