import SwiftUI

struct ApplicationsView: View {
    let applications: [PortalApplication]
    var body: some View {
        NavigationStack {
            List(applications) { app in
                NavigationLink { ApplicationDestination(application: app) } label: {
                    HStack(spacing: 12) {
                        Text(String(app.name.prefix(1))).font(.headline).foregroundStyle(.white)
                            .frame(width: 42, height: 42)
                            .background(ChronosPalette.green, in: RoundedRectangle(cornerRadius: 12))
                        VStack(alignment: .leading, spacing: 3) {
                            Text(app.name).font(.headline)
                            Text(app.description ?? "服务端授权应用").font(.caption)
                                .foregroundStyle(.secondary).lineLimit(2)
                        }
                    }
                }
            }.overlay {
                if applications.isEmpty { EmptyStateView(icon: "square.grid.2x2", text: "当前账号暂无可用应用") }
            }.navigationTitle("应用")
        }
    }
}
