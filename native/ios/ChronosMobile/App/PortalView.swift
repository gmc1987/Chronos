import SwiftUI

struct PortalView: View {
    @EnvironmentObject var model: AppModel

    var body: some View {
        Group {
            if let bootstrap = model.bootstrap {
                tabs(bootstrap)
            } else {
                BootstrapStateView()
            }
        }
        .tint(ChronosPalette.green)
        .sheet(item: $model.deepLink) { DeepLinkDestinationView(target: $0) }
        .safeAreaInset(edge: .top) {
            if model.usingOfflineData {
                Label(offlineMessage, systemImage: "wifi.slash")
                    .font(.caption).frame(maxWidth: .infinity).padding(6)
                    .background(.orange.opacity(0.9)).foregroundStyle(.white)
            }
        }
    }

    private var offlineMessage: String {
        guard let date = model.offlineCachedAt else {
            return "网络不可用，正在显示 24 小时内缓存"
        }
        return "离线缓存 · 更新于 \(date.formatted(date: .abbreviated, time: .shortened))"
    }

    @ViewBuilder
    func tabs(_ bootstrap: PortalBootstrap) -> some View {
        switch model.workbenchKind {
        case .parent:
            TabView {
                ParentWorkbenchView(bootstrap: bootstrap)
                    .tabItem { Label("工作台", systemImage: "house.fill") }
                FamilyNoticesView()
                    .tabItem { Label("家校通知", systemImage: "bell") }
                ApplicationsView(applications: bootstrap.applications)
                    .tabItem { Label("应用", systemImage: "square.grid.2x2") }
                ProfileView(bootstrap: bootstrap, kind: .parent)
                    .tabItem { Label("我的", systemImage: "person") }
            }
        case .student:
            TabView {
                StudentWorkbenchView(bootstrap: bootstrap)
                    .tabItem { Label("工作台", systemImage: "house.fill") }
                GradesListView()
                    .tabItem { Label("成绩", systemImage: "graduationcap") }
                ExamsListView()
                    .tabItem { Label("考试", systemImage: "doc.text") }
                if bootstrap.user.permissions.contains("message:publication:read") {
                    PublicationsListView(title: "通知", type: "NOTICE")
                        .tabItem { Label("通知", systemImage: "bell") }
                }
                ProfileView(bootstrap: bootstrap, kind: .student)
                    .tabItem { Label("我的", systemImage: "person") }
            }
        case .teacher, .staff, .management:
            TabView {
                RoleWorkbenchView(bootstrap: bootstrap, kind: model.workbenchKind)
                    .tabItem { Label("工作台", systemImage: "house.fill") }
                ApplicationsView(applications: bootstrap.applications)
                    .tabItem { Label("应用", systemImage: "square.grid.2x2") }
                if bootstrap.user.permissions.contains("message:publication:read") {
                    PublicationsListView(title: "公告", type: "ANNOUNCEMENT")
                        .tabItem { Label("公告", systemImage: "megaphone") }
                }
                ProfileView(bootstrap: bootstrap, kind: model.workbenchKind)
                    .tabItem { Label("我的", systemImage: "person") }
            }
        }
    }
}
