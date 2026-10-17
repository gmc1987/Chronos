import SwiftUI

@main
struct ChronosMobileApp: App {
    @StateObject var model = AppModel()
    var body: some Scene {
        WindowGroup { RootView().environmentObject(model) }
    }
}
