import SwiftUI

struct RootView: View {
    @EnvironmentObject var model: AppModel
    var body: some View {
        if model.loggedIn { PortalView() } else { LoginView() }
    }
}

