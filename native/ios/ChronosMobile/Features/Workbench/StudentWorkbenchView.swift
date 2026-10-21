import SwiftUI

struct StudentWorkbenchView: View {
    let bootstrap: PortalBootstrap

    var body: some View {
        NavigationStack {
            ScrollView {
                // Keep the timetable alive for the full lifetime of the page so
                // reaching the bottom cannot restart its asynchronous load.
                VStack(spacing: 16) {
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
        }
    }
}

