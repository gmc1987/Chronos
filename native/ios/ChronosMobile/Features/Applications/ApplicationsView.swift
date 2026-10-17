import SwiftUI

struct ApplicationsView: View {
    let applications: [PortalApplication]

    var body: some View {
        NavigationStack {
            Group {
                if applications.isEmpty {
                    EmptyStateView(icon: "square.grid.2x2", text: "当前账号暂无可用应用")
                } else {
                    List(applications) { application in
                        NavigationLink {
                            ApplicationDestination(application: application)
                        } label: {
                            HStack(spacing: 12) {
                                Text(String(application.name.prefix(1)))
                                    .font(.headline)
                                    .foregroundStyle(.white)
                                    .frame(width: 40, height: 40)
                                    .background(ChronosPalette.green, in: RoundedRectangle(cornerRadius: 11))
                                VStack(alignment: .leading, spacing: 3) {
                                    Text(application.name)
                                    Text(application.description ?? "服务端授权应用")
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                        .lineLimit(2)
                                }
                            }
                        }
                    }
                    .listStyle(.insetGrouped)
                }
            }
            .navigationTitle("应用")
        }
    }
}

@ViewBuilder
func ApplicationDestination(application: PortalApplication) -> some View {
    let route = application.routePath.lowercased()
    if route.contains("/grades") {
        GradesListView()
    } else if route.contains("/my-exams") {
        ExamsListView()
    } else if route.contains("/schedule") {
        NavigationStack {
            ScrollView { TimetableCard().padding() }
                .background(ChronosPalette.background)
                .navigationTitle(application.name)
        }
    } else if route.contains("/publications") {
        PublicationsListView(title: application.name, type: nil)
    } else {
        ConstructionView(
            title: application.name,
            message: application.openMode == "EXTERNAL"
                ? "该应用为外部系统，请通过受控 Web 门户访问。"
                : "该应用已获授权，原生页面尚在建设中，请前往 Web 门户处理。"
        )
    }
}

