import SwiftUI

struct ExternalApplicationView: View {
    let application: PortalApplication
    @Environment(\.openURL) private var openURL
    var body: some View {
        ContentUnavailableView { Label(application.name, systemImage: "arrow.up.right.square") }
        description: { Text("该功能由外部系统提供，将使用系统浏览器打开。") }
        actions: {
            Button("打开外部应用") { if let url = URL(string: application.routePath) { openURL(url) } }
                .buttonStyle(.borderedProminent)
        }.navigationTitle(application.name)
    }
}

struct NativeModule: Identifiable {
    enum Destination {
        case records(path: String, empty: String)
        case leaves
        case businessTrips
        case officeRequests
    }

    let id: String, title: String, detail: String, icon: String, destination: Destination

    init(id: String, title: String, detail: String, icon: String, path: String, empty: String? = nil) {
        self.id = id
        self.title = title
        self.detail = detail
        self.icon = icon
        self.destination = .records(path: path, empty: empty ?? "暂无\(title)数据")
    }

    init(id: String, title: String, detail: String, icon: String, destination: Destination) {
        self.id = id
        self.title = title
        self.detail = detail
        self.icon = icon
        self.destination = destination
    }
}

let collaborationModules = [
    NativeModule(id: "leaves", title: "请假", detail: "发起请假并查看审批状态", icon: "calendar.badge.clock", destination: .leaves),
    NativeModule(id: "requests", title: "用车与用印", detail: "发起申请并查看执行状态", icon: "car", destination: .officeRequests),
    NativeModule(id: "trips", title: "出差", detail: "发起出差并查看行程、预算和审批", icon: "suitcase", destination: .businessTrips),
    NativeModule(id: "documents", title: "公文", detail: "收发、签发和归档", icon: "doc.text", path: "/portal/collaboration/documents/my"),
    NativeModule(id: "files", title: "共享文件", detail: "统一文件共享", icon: "folder", path: "/portal/collaboration/files/shares"),
    NativeModule(id: "contacts", title: "通讯录", detail: "教职工联系方式", icon: "person.2", path: "/portal/collaboration/contacts?keyword=")]

let teachingModules = [
    ("plans", "教学计划", "list.bullet.clipboard"), ("lesson-plans", "教案", "doc.richtext"),
    ("preparations", "集体备课", "person.3"), ("coursewares", "课件", "rectangle.on.rectangle"),
    ("materials", "教学材料", "books.vertical"), ("questions", "题库", "questionmark.square"),
    ("knowledge-points", "知识点", "point.3.connected.trianglepath.dotted"),
    ("research", "教研", "person.2.wave.2"), ("mistakes", "错题沉淀", "exclamationmark.bubble")]
    .map { NativeModule(id: $0.0, title: $0.1, detail: "查看和管理\($0.1)", icon: $0.2,
        path: "/education/teaching-center/api/\($0.0)?page=0&size=50") }
