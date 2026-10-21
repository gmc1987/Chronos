import SwiftUI

struct RepairListView: View {
    @EnvironmentObject private var model: AppModel
    @State private var orders: [RepairOrder] = []
    @State private var loading = false
    @State private var error: String?
    @State private var showCreate = false
    @State private var assigned = false

    var body: some View {
        List {
            Picker("工单范围", selection: $assigned) { Text("我的报修").tag(false); Text("维修工单").tag(true) }
                .pickerStyle(.segmented).onChange(of: assigned) { Task { await load() } }
            if loading { ProgressView("正在加载报修单…") }
            if let error { Text(error).foregroundStyle(.red) }
            ForEach(orders) { order in
                NavigationLink { RepairDetailView(orderID: order.id, assigned: assigned) } label: {
                    VStack(alignment: .leading, spacing: 8) {
                        HStack { Text(order.title).font(.headline); Spacer(); Text(RepairLabels.status[order.status] ?? order.status).foregroundStyle(ChronosPalette.green) }
                        Text("\(order.orderNo) · \(RepairLabels.category[order.category] ?? order.category)").font(.caption).foregroundStyle(.secondary)
                        Label(order.location, systemImage: "mappin.and.ellipse").font(.subheadline)
                    }.padding(.vertical, 6)
                }
            }
            if !loading && orders.isEmpty { EmptyStateView(icon: "wrench.and.screwdriver", text: "暂无报修记录") }
        }
        .navigationTitle("故障报修")
        .toolbar { if !assigned { Button { showCreate = true } label: { Label("报修", systemImage: "plus") } } }
        .sheet(isPresented: $showCreate) { NavigationStack { RepairFormView { showCreate = false; Task { await load() } } } }
        .task { await load() }
        .refreshable { await load() }
    }

    @MainActor private func load() async {
        loading = true; defer { loading = false }
        do { orders = try await model.session.get(assigned ? "/portal/facility/assigned-repair-orders?page=0&size=50" : "/portal/facility/repair-orders?page=0&size=50", as: RepairPage.self).content; error = nil }
        catch { self.error = error.localizedDescription }
    }
}
