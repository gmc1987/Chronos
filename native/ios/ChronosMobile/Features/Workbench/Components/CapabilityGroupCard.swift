import SwiftUI

struct CapabilityGroupCard: View {
    let title: String
    let icon: String
    let applications: [PortalApplication]
    let emptyMessage: String

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            CardTitle(title: title, icon: icon)
            if applications.isEmpty {
                ConstructionInlineView(message: emptyMessage)
            } else {
                ForEach(applications.prefix(5)) { application in
                    NavigationLink {
                        ApplicationDestination(application: application)
                    } label: {
                        HStack {
                            Text(application.name).foregroundStyle(ChronosPalette.text)
                            Spacer()
                            Image(systemName: "chevron.right")
                                .font(.caption)
                                .foregroundStyle(ChronosPalette.secondary)
                        }
                        .padding(.vertical, 5)
                    }
                }
            }
        }
        .padding(16)
        .cardStyle()
    }
}
