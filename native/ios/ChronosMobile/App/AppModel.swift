import SwiftUI

@MainActor final class AppModel: ObservableObject {
    let session = ChronosSession()
    @Published var loggedIn = false
    @Published var bootstrap: PortalBootstrap?
    @Published var workbenchKind: WorkbenchKind = .staff
    @Published var error: String?
    @Published var isLoggingIn = false
    @Published var isBootstrapping = false

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
        } catch {
            self.error = error.localizedDescription
        }
    }

    func logout() {
        Task { await session.revoke() }
        loggedIn = false
        bootstrap = nil
        workbenchKind = .staff
        error = nil
        UserDefaults.standard.removeObject(forKey: "chronos.mobile.timetable.expanded")
    }
}
