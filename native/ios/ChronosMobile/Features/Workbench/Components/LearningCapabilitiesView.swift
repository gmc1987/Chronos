import SwiftUI

struct LearningCapabilitiesView: View {
    let applications: [PortalApplication]

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("学习中心").font(.headline)
            ForEach(capabilities, id: \.title) { capability in
                NavigationLink {
                    if let application = capability.application {
                        ApplicationDestination(application: application)
                    } else {
                        ConstructionView(title: capability.title)
                    }
                } label: {
                    HStack(spacing: 13) {
                        Image(systemName: capability.icon)
                            .foregroundStyle(capability.application == nil ? .gray : ChronosPalette.green)
                            .frame(width: 34, height: 34)
                            .background(
                                (capability.application == nil ? Color.gray : ChronosPalette.green).opacity(0.1),
                                in: RoundedRectangle(cornerRadius: 10)
                            )
                        VStack(alignment: .leading, spacing: 3) {
                            Text(capability.title).foregroundStyle(ChronosPalette.text)
                            Text(capability.application?.description ?? "建设中，暂未获得服务端授权应用")
                                .font(.caption)
                                .foregroundStyle(ChronosPalette.secondary)
                                .lineLimit(2)
                        }
                        Spacer()
                        Text(capability.application == nil ? "建设中" : "进入")
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(capability.application == nil ? .gray : ChronosPalette.green)
                    }
                    .padding(.vertical, 4)
                }
            }
        }
        .padding(16)
        .cardStyle()
    }

    var capabilities: [(title: String, icon: String, application: PortalApplication?)] {
        [
            ("课件", "doc.richtext", findApplication(["课件", "courseware", "material"])),
            ("错题沉淀", "xmark.circle", findApplication(["错题", "wrong"])),
            ("题库", "questionmark.square", findApplication(["题库", "question", "bank"]))
        ]
    }

    func findApplication(_ markers: [String]) -> PortalApplication? {
        applications.first { appMatches($0, markers) }
    }
}

