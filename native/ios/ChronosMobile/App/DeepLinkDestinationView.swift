import SwiftUI

struct DeepLinkDestinationView: View {
    let target: MobileDeepLink
    @EnvironmentObject private var model: AppModel

    var body: some View {
        NavigationStack {
            switch target {
            case .publication(let id): PublicationDetailView(publicationID: id)
            case .workflow(let id): WorkflowInstanceView(instanceID: id)
            case .application(let id):
                if let application = model.bootstrap?.applications.first(where: { $0.id == id }) {
                    ApplicationDestination(application: application)
                } else {
                    ContentUnavailableView("无法打开应用", systemImage: "lock",
                                           description: Text("当前账号未获授权或应用已停用。"))
                }
            }
        }
    }
}
