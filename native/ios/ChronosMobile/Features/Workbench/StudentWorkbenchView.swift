import SwiftUI

struct StudentWorkbenchView: View {
    let bootstrap: PortalBootstrap
    @EnvironmentObject var model: AppModel

    var body: some View {
        NavigationStack {
            ScrollView {
                LazyVStack(spacing: 16) {
                    IdentityHeader(
                        eyebrow: "学生工作台",
                        name: bootstrap.user.displayName ?? bootstrap.user.username,
                        detail: bootstrap.contributions["DATA"]?.data?.termName ?? "当前学期",
                        icon: "graduationcap.fill"
                    )
                    TimetableCard()
                    LearningCapabilitiesView(applications: bootstrap.applications)
                }
                .padding()
            }
            .background(ChronosPalette.background)
            .navigationTitle("工作台")
            .refreshable { await model.reloadBootstrap() }
        }
    }
}

