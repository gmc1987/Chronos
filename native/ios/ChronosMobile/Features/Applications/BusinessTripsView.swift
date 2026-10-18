import SwiftUI

struct BusinessTripsView: View {
    @EnvironmentObject private var model: AppModel
    @State private var rows: [JSONValue] = []
    @State private var loading = true
    @State private var error: String?

    var body: some View {
        Group {
            if loading { ProgressView("正在加载…") }
            else if let error { InlineErrorView(message: error, retry: { Task { await load() } }) }
            else if rows.isEmpty { EmptyStateView(icon: "suitcase", text: "暂无出差申请") }
            else { List(Array(rows.enumerated()), id: \.offset) { _, row in
                NavigationLink {
                    RequestActionDetailView(title: "出差详情", record: row, kind: .businessTrip) {
                        Task { await load() }
                    }
                } label: { NativeRecordCard(record: row) }
            }.listStyle(.insetGrouped) }
        }
        .navigationTitle("出差")
        .toolbar { NavigationLink { BusinessTripForm { Task { await load() } } } label: { Label("发起出差", systemImage: "plus") } }
        .task { await load() }
        .refreshable { await load() }
    }

    private func load() async {
        loading = true; error = nil
        do { rows = records(from: try await model.session.getJSON("/portal/collaboration/business-trips")) }
        catch { self.error = error.localizedDescription }
        loading = false
    }
}
