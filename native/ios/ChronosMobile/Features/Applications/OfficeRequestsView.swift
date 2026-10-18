import SwiftUI

struct OfficeRequestsView: View {
    @EnvironmentObject private var model: AppModel
    @State private var rows: [JSONValue] = []
    @State private var loading = true
    @State private var error: String?

    var body: some View {
        Group {
            if loading { ProgressView("正在加载…") }
            else if let error { InlineErrorView(message: error, retry: { Task { await load() } }) }
            else if rows.isEmpty { EmptyStateView(icon: "car", text: "暂无用车或用印申请") }
            else { List(Array(rows.enumerated()), id: \.offset) { _, row in
                NavigationLink { NativeRecordDetailView(title: "申请详情", record: row) } label: { NativeRecordCard(record: row) }
            }.listStyle(.insetGrouped) }
        }
        .navigationTitle("用车与用印")
        .toolbar { NavigationLink { OfficeRequestForm { Task { await load() } } } label: { Label("发起申请", systemImage: "plus") } }
        .task { await load() }
        .refreshable { await load() }
    }

    private func load() async {
        loading = true; error = nil
        do { rows = records(from: try await model.session.getJSON("/portal/collaboration/requests/my")) }
        catch { self.error = error.localizedDescription }
        loading = false
    }
}
