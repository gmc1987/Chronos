import UIKit
import UserNotifications

extension Notification.Name {
    static let chronosDeepLink = Notification.Name("chronos.mobile.deep-link")
    static let chronosPushToken = Notification.Name("chronos.mobile.push-token")
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

    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        let token = deviceToken.map { String(format: "%02x", $0) }.joined()
        UserDefaults.standard.set(token, forKey: "chronos.mobile.apns-token")
        NotificationCenter.default.post(name: .chronosPushToken, object: token)
    }

    func application(
        _ application: UIApplication,
        didFailToRegisterForRemoteNotificationsWithError error: Error
    ) {
        // Simulator and unsigned builds may not receive an APNs environment.
        // Local notifications and deep links remain available in that case.
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
