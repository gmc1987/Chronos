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
        .preferredColorScheme(.light)
    }

    @ViewBuilder
    func tabs(_ bootstrap: PortalBootstrap) -> some View {
        switch model.workbenchKind {
        case .student:
            TabView {
                StudentWorkbenchView(bootstrap: bootstrap)
                    .tabItem { Label("工作台", systemImage: "rectangle.grid.2x2") }
                GradesListView()
                    .tabItem { Label("成绩", systemImage: "graduationcap") }
                ExamsListView()
                    .tabItem { Label("考试", systemImage: "doc.text") }
                PublicationsListView(title: "通知", type: "NOTICE")
                    .tabItem { Label("通知", systemImage: "bell") }
                ProfileView(bootstrap: bootstrap, kind: .student)
                    .tabItem { Label("我的", systemImage: "person") }
            }
        case .teacher, .staff, .management:
            TabView {
                RoleWorkbenchView(bootstrap: bootstrap, kind: model.workbenchKind)
                    .tabItem { Label("工作台", systemImage: "rectangle.grid.2x2") }
                ApplicationsView(applications: bootstrap.applications)
                    .tabItem { Label("应用", systemImage: "square.grid.2x2") }
                PublicationsListView(title: "公告", type: "ANNOUNCEMENT")
                    .tabItem { Label("公告", systemImage: "megaphone") }
                ProfileView(bootstrap: bootstrap, kind: model.workbenchKind)
                    .tabItem { Label("我的", systemImage: "person") }
            }
        }
    }
}

