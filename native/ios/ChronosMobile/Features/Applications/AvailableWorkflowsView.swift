import SwiftUI

struct AvailableWorkflowsView: View {
    /// Domain-managed workflows must be started from their dedicated native business screen.
    private let domainManagedFlows: Set<String> = [
        "EDU_GRADEBOOK_REVIEW", "EDU_GRADE_CHANGE_REVIEW", "EDU_TEACHING_CONTENT_REVIEW",
        "EDU_CLASSROOM_RESERVATION_APPROVAL", "STAFF_BUSINESS_TRIP_APPROVAL",
        "STAFF_SEAL_USE_APPROVAL", "STAFF_VEHICLE_USE_APPROVAL",
        "EDU_TEACHER_LEAVE_APPROVAL", "EDU_COURSE_ADJUSTMENT_APPROVAL"
    ]
    @EnvironmentObject private var model: AppModel
    @State private var rows: [JSONValue] = []
    @State private var error: String?
    @State private var loading = true
    var body: some View {
        Group {
            if let error { InlineErrorView(message: error, retry: { Task { await load() } }) }
            else if loading { ProgressView("正在加载可发起流程…") }
            else if rows.isEmpty { EmptyStateView(icon: "tray", text: "暂无可直接发起的通用流程") }
            else { List(Array(rows.enumerated()), id: \.offset) { _, row in
                if let id = row.objectValue?["id"]?.stringValue { NavigationLink { WorkflowStartView(flowID: id) } label: { NativeRecordCard(record: row) } }
            }.listStyle(.insetGrouped) }
        }.task { await load() }
    }
    private func load() async {
        do {
            rows = records(from: try await model.session.getJSON("/workflows/available")).filter {
                guard let code = $0.objectValue?["flowCode"]?.stringValue else { return true }
                return !domainManagedFlows.contains(code)
            }
            error = nil
        } catch { self.error = error.localizedDescription }
        loading = false
    }
}
