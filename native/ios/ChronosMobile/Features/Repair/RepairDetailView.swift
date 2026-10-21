import SwiftUI

struct RepairDetailView: View {
    @EnvironmentObject private var model: AppModel
    let orderID: String
    let assigned: Bool
    @State private var order: RepairOrder?
    @State private var error: String?
    @State private var showCompletion = false
    @State private var showMaterial = false

    var body: some View {
        ScrollView {
            if let order {
                VStack(alignment: .leading, spacing: 14) {
                    GroupBox("故障信息") { VStack(alignment: .leading, spacing: 8) { row("报修单号", order.orderNo); row("状态", RepairLabels.status[order.status] ?? order.status); row("分类", RepairLabels.category[order.category] ?? order.category); row("位置", order.location); row("故障描述", order.description); row("维修人员", order.technicianName ?? "等待派单"); row("维修结果", order.completionNote ?? "-") }.frame(maxWidth: .infinity, alignment: .leading) }
                    GroupBox("处理记录") { VStack(alignment: .leading, spacing: 12) { ForEach(order.events ?? []) { event in VStack(alignment: .leading) { Text(event.remark ?? event.eventType); Text((event.createTime ?? "").replacingOccurrences(of: "T", with: " ")).font(.caption).foregroundStyle(.secondary) } } }.frame(maxWidth: .infinity, alignment: .leading) }
                    if assigned && order.status == "DISPATCHED" { action("接单", path: "/portal/facility/assigned-repair-orders/\(orderID)/accept") }
                    if assigned && ["ACCEPTED", "WAITING_MATERIAL"].contains(order.status) { action("开始维修", path: "/portal/facility/assigned-repair-orders/\(orderID)/start") }
                    if assigned && ["ACCEPTED", "IN_PROGRESS", "WAITING_MATERIAL"].contains(order.status) { Button("申请维修物料") { showMaterial = true }.buttonStyle(.bordered).frame(maxWidth: .infinity) }
                    if assigned && ["IN_PROGRESS", "WAITING_MATERIAL"].contains(order.status) { Button("维修完成") { showCompletion = true }.buttonStyle(.borderedProminent).tint(ChronosPalette.green).frame(maxWidth: .infinity) }
                    if !assigned && order.status == "COMPLETED" { Button("确认维修并关闭") { Task { await close() } }.buttonStyle(.borderedProminent).tint(ChronosPalette.green).frame(maxWidth: .infinity) }
                }.padding()
            } else if let error { InlineErrorView(message: error) { Task { await load() } } }
            else { ProgressView().padding(40) }
        }.navigationTitle("报修详情").task { await load() }
        .sheet(isPresented: $showCompletion) { NavigationStack { RepairCompletionView(orderID: orderID) { showCompletion = false; Task { await load() } } } }
        .sheet(isPresented: $showMaterial) { NavigationStack { RepairMaterialRequestView(orderID: orderID) { showMaterial = false; Task { await load() } } } }
    }

    private func row(_ label: String, _ value: String) -> some View { HStack(alignment: .top) { Text(label).foregroundStyle(.secondary).frame(width: 82, alignment: .leading); Text(value); Spacer() } }
    @MainActor private func load() async { do { order = try await model.session.get("/portal/facility/repair-orders/\(orderID)", as: RepairOrder.self); error = nil } catch { self.error = error.localizedDescription } }
    @MainActor private func close() async { do { _ = try await model.session.sendJSON("/portal/facility/repair-orders/\(orderID)/close", body: .object(["rating": .number(5), "comment": .string("移动端确认维修完成")])); await load() } catch { self.error = error.localizedDescription } }
    private func action(_ title: String, path: String) -> some View { Button(title) { Task { do { _ = try await model.session.sendJSON(path); await load() } catch { self.error = error.localizedDescription } } }.buttonStyle(.borderedProminent).frame(maxWidth: .infinity) }
}
