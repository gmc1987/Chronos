import UIKit
import UserNotifications

extension Notification.Name {
    static let chronosDeepLink = Notification.Name("chronos.mobile.deep-link")
}

final class ChronosNotificationDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        let center = UNUserNotificationCenter.current()
        center.delegate = self
        center.requestAuthorization(options: [.alert, .badge, .sound]) { allowed, _ in
            if allowed { DispatchQueue.main.async { application.registerForRemoteNotifications() } }
        }
        return true
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification
    ) async -> UNNotificationPresentationOptions { [.banner, .sound, .badge] }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse
    ) async {
        let info = response.notification.request.content.userInfo
        let value = info["deepLink"] as? String ?? info["url"] as? String
        if let value, let url = URL(string: value) {
            await MainActor.run { NotificationCenter.default.post(name: .chronosDeepLink, object: url) }
        }
    }
}
