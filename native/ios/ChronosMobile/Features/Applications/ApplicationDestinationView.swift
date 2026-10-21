import SwiftUI

enum NativeFeature: String {
    case workflow = "WORKFLOW_TASKS", collaboration = "COLLABORATION", documents = "DOCUMENTS"
    case files = "FILES", meetings = "MEETINGS", trips = "BUSINESS_TRIPS"
    case knowledge = "KNOWLEDGE", ai = "AI_CENTER", teaching = "TEACHING_CENTER"
    case repair = "FACILITY_REPAIR"

    init?(_ app: PortalApplication) {
        let fallback: [String: Self] = ["workflow": .workflow, "oa": .collaboration,
            "document": .documents, "file": .files, "EDUCATION_MEETINGS": .meetings,
            "STAFF_BUSINESS_TRIP": .trips, "knowledge": .knowledge, "ai": .ai,
            "teaching-center": .teaching]
        if let value = Self(rawValue: app.nativeFeatureCode ?? "") { self = value; return }
        if let value = fallback[app.code ?? ""] { self = value; return }
        return nil
    }
}

@ViewBuilder func ApplicationDestination(application: PortalApplication) -> some View {
    if application.presentationMode == "EXTERNAL" || application.openMode == "EXTERNAL" {
        ExternalApplicationView(application: application)
    } else {
        switch NativeFeature(application) {
        case .workflow: WorkflowCenterView()
        case .collaboration: NativeModuleList(title: "协同办公", modules: collaborationModules)
        case .documents: NativeRecordsView(title: "公文中心", path: "/portal/collaboration/documents/my", empty: "暂无公文")
        case .files: NativeRecordsView(title: "文件中心", path: "/portal/collaboration/files/shares", empty: "暂无共享文件")
        case .meetings: MeetingsListView()
        case .trips: NativeRecordsView(title: "我的出差", path: "/portal/collaboration/business-trips", empty: "暂无出差申请")
        case .knowledge: NativeRecordsView(title: "知识中心", path: "/admin/knowledge-bases?page=0&size=50", empty: "暂无知识库")
        case .ai: NativeRecordsView(title: "AI 智能中心", path: "/agent/specs/list?page=0&size=50", empty: "暂无智能体")
        case .teaching: NativeModuleList(title: "教学中心", modules: teachingModules)
        case .repair: RepairListView()
        case nil: UnsupportedApplicationView(application: application)
        }
    }
}
