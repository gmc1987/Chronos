import SwiftUI
import UIKit

@MainActor final class AppModel: ObservableObject {
    let session = ChronosSession()
    @Published var loggedIn = false
    @Published var bootstrap: PortalBootstrap?
    @Published var workbenchKind: WorkbenchKind = .staff
    @Published var error: String?
    @Published var isLoggingIn = false
    @Published var isBootstrapping = false
    @Published var deepLink: MobileDeepLink?
    @Published var usingOfflineData = false
    @Published var offlineCachedAt: Date?

    init() {
        Task {
            if await session.restorePersisted() != nil {
                loggedIn = true
                await reloadBootstrap()
            }
        }
    }

    func login(username: String, password: String) async {
        guard !isLoggingIn else { return }
        isLoggingIn = true
        error = nil
        defer { isLoggingIn = false }
        do {
            _ = try await session.login(username: username, password: password)
            loggedIn = true
            await reloadBootstrap()
        } catch let loginError { error = loginError.localizedDescription }
    }

    func reloadBootstrap() async {
        guard !isBootstrapping else { return }
        isBootstrapping = true
        error = nil
        defer { isBootstrapping = false }
        do {
            let value = try await session.get(WorkbenchAPI.bootstrap, as: PortalBootstrap.self)
            bootstrap = value
            workbenchKind = WorkbenchResolver.resolve(bootstrap: value)
            usingOfflineData = await session.lastResponseWasCached()
            offlineCachedAt = await session.lastCachedAt()
            if let token = UserDefaults.standard.string(forKey: "chronos.mobile.apns-token") {
                await registerPushToken(token)
            }
        } catch {
            self.error = error.localizedDescription
        }
    }

    func logout() {
        Task {
            await unregisterPushToken()
            await session.revoke()
        }
        loggedIn = false
        bootstrap = nil
        workbenchKind = .staff
        error = nil
        usingOfflineData = false
        offlineCachedAt = nil
        UserDefaults.standard.removeObject(forKey: "chronos.mobile.timetable.expanded")
    }

    func open(url: URL) { deepLink = MobileDeepLink(url: url) }

    func registerPushToken(_ token: String) async {
        guard loggedIn, !token.isEmpty else { return }
        _ = try? await session.sendJSON(
            "/message/mobile-devices/\(installationID)",
            method: "PUT",
            body: .object([
                "platform": .string("IOS"),
                "providerToken": .string(token),
                "deviceModel": .string(UIDevice.current.model),
                "appVersion": .string(Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "")
            ])
        )
    }

    private func unregisterPushToken() async {
        guard UserDefaults.standard.string(forKey: "chronos.mobile.apns-token") != nil else { return }
        _ = try? await session.sendJSON(
            "/message/mobile-devices/\(installationID)?platform=IOS",
            method: "DELETE"
        )
    }

    private var installationID: String {
        let key = "chronos.mobile.installation-id"
        if let existing = UserDefaults.standard.string(forKey: key) { return existing }
        let created = UUID().uuidString
        UserDefaults.standard.set(created, forKey: key)
        return created
    }
}
