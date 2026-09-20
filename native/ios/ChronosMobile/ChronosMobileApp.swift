import SwiftUI

@main
struct ChronosMobileApp: App {
    @StateObject private var model = AppModel()
    var body: some Scene {
        WindowGroup { RootView().environmentObject(model) }
    }
}

@MainActor final class AppModel: ObservableObject {
    let session = ChronosSession()
    @Published var loggedIn = false
    @Published var roles: [Role] = []
    @Published var error: String?

    func login(username: String, password: String) async {
        do {
            let payload = try await session.login(username: username, password: password)
            roles = payload.roles ?? []
            loggedIn = true
        } catch let loginError { error = loginError.localizedDescription }
    }
    func logout() { Task { await session.clear() }; loggedIn = false; roles = [] }
}

struct RootView: View {
    @EnvironmentObject var model: AppModel
    var body: some View {
        if model.loggedIn { PortalView() } else { LoginView() }
    }
}

struct LoginView: View {
    @EnvironmentObject var model: AppModel
    @State private var username = ""
    @State private var password = ""
    var body: some View {
        NavigationStack {
            Form {
                Section("Chronos 统一门户") {
                    TextField("账号", text: $username).textInputAutocapitalization(.never)
                    SecureField("密码", text: $password)
                    Button("登录") { Task { await model.login(username: username, password: password) } }
                        .disabled(username.isEmpty || password.isEmpty)
                }
                if let error = model.error { Text(error).foregroundStyle(.red) }
            }
            .navigationTitle("欢迎回来")
        }
    }
}

struct PortalView: View {
    @EnvironmentObject var model: AppModel
    var body: some View {
        TabView {
            FeatureView(title: "成绩", path: "/portal/education/grades").tabItem { Label("成绩", systemImage: "graduationcap") }
            FeatureView(title: "课表", path: "/portal/education/schedule").tabItem { Label("课表", systemImage: "calendar") }
            FeatureView(title: "通知", path: "/portal/education/class-notices").tabItem { Label("通知", systemImage: "bell") }
            FeatureView(title: "家校", path: "/portal/education/family/notices").tabItem { Label("家校", systemImage: "person.2") }
            Button("退出登录", action: model.logout).tabItem { Label("我的", systemImage: "person") }
        }
    }
}

struct FeatureView: View {
    @EnvironmentObject var model: AppModel
    let title: String
    let path: String
    @State private var content = "加载中…"
    var body: some View {
        NavigationStack { ScrollView { Text(content).frame(maxWidth: .infinity, alignment: .leading).padding() }.navigationTitle(title) }
            .task {
                do {
                    let data = try await model.session.get(path, as: AnyCodable.self)
                    content = data.description
                } catch { content = error.localizedDescription }
            }
    }
}

struct AnyCodable: Decodable, CustomStringConvertible {
    let value: Any
    init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()
        if let value = try? container.decode([String: AnyCodable].self) { self.value = value }
        else if let value = try? container.decode([AnyCodable].self) { self.value = value }
        else if let value = try? container.decode(String.self) { self.value = value }
        else if let value = try? container.decode(Double.self) { self.value = value }
        else { self.value = NSNull() }
    }
    var description: String { String(describing: value) }
}
