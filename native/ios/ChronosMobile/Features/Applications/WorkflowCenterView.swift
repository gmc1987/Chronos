import SwiftUI

enum WorkflowTab: String, CaseIterable, Identifiable {
    case pending = "待办", handled = "已办", initiated = "我发起的", start = "发起"
    var id: String { rawValue }
}

struct WorkflowCenterView: View {
    @State private var tab: WorkflowTab = .pending
    var body: some View {
        VStack(spacing: 0) {
            Picker("流程", selection: $tab) { ForEach(WorkflowTab.allCases) { Text($0.rawValue).tag($0) } }.pickerStyle(.segmented).padding()
            Group { switch tab {
            case .pending: WorkflowRecordsView(path: "/workflow-tasks/pending?page=0&size=50", empty: "暂无待办任务")
            case .handled: WorkflowRecordsView(path: "/workflow-tasks/handled?page=0&size=50", empty: "暂无已办任务")
            case .initiated: WorkflowRecordsView(path: "/workflow-instances/initiated?page=0&size=50", empty: "暂无发起记录")
            case .start: AvailableWorkflowsView()
            }}.id(tab)
        }.navigationTitle("流程中心")
    }
}
