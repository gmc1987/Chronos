import SwiftUI

@main
struct ChronosMobileApp: App {
    @UIApplicationDelegateAdaptor(ChronosNotificationDelegate.self) private var notificationDelegate
    @StateObject var model = AppModel()
    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(model)
                .onOpenURL { model.open(url: $0) }
                .onReceive(NotificationCenter.default.publisher(for: .chronosDeepLink)) { event in
                    if let url = event.object as? URL { model.open(url: url) }
                }
        }
    }
}
