import SwiftUI

struct LeaveRequestsView: View {
    @EnvironmentObject private var model: AppModel
    @State private var rows: [JSONValue] = []
    @State private var loading = true
    @State private var error: String?

    var body: some View {
        Group {
            if loading { ProgressView("正在加载…") }
            else if let error { InlineErrorView(message: error, retry: { Task { await load() } }) }
            else if rows.isEmpty { EmptyStateView(icon: "calendar.badge.clock", text: "暂无请假记录") }
            else { List(Array(rows.enumerated()), id: \.offset) { _, row in
                NavigationLink {
                    RequestActionDetailView(title: "请假详情", record: row, kind: .leave) {
                        Task { await load() }
                    }
                } label: { NativeRecordCard(record: row) }
            }.listStyle(.insetGrouped) }
        }
        .navigationTitle("请假")
        .toolbar { NavigationLink { LeaveRequestForm { Task { await load() } } } label: { Label("发起请假", systemImage: "plus") } }
        .task { await load() }
        .refreshable { await load() }
    }

    private func load() async {
        loading = true; error = nil
        do { rows = records(from: try await model.session.getJSON("/portal/education/leaves")) }
        catch { self.error = error.localizedDescription }
        loading = false
    }
}
