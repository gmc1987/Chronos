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
    @Published var selectedRole: Role?
    init() {
        Task {
            if await session.restorePersisted() != nil {
                roles = Self.loadRoles()
                selectedRole = roles.first
                loggedIn = !roles.isEmpty
            }
        }
    }

    func login(username: String, password: String) async {
        do {
            let payload = try await session.login(username: username, password: password)
            roles = payload.roles ?? []
            Self.saveRoles(roles)
            selectedRole = roles.first
            loggedIn = true
        } catch let loginError { error = loginError.localizedDescription }
    }
    func logout() { Task { await session.revoke() }; loggedIn = false; roles = []; selectedRole = nil; error = nil }
    private static func saveRoles(_ value: [Role]) {
        UserDefaults.standard.set(try? JSONEncoder().encode(value), forKey: "chronos.mobile.roles")
    }
    private static func loadRoles() -> [Role] {
        guard let data = UserDefaults.standard.data(forKey: "chronos.mobile.roles"),
              let value = try? JSONDecoder().decode([Role].self, from: data) else { return [] }
        return value
    }
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
            ForEach(features(for: model.selectedRole), id: \.path) { feature in
                FeatureView(title: feature.title, path: feature.path)
                    .tabItem { Label(feature.title, systemImage: feature.icon) }
            }
            NavigationStack {
                Form {
                    if model.roles.count > 1 {
                        Picker("当前身份", selection: Binding(get: { model.selectedRole ?? model.roles[0] }, set: { model.selectedRole = $0 })) {
                            ForEach(model.roles, id: \.id) { role in Text(role.displayName).tag(role) }
                        }
                    }
                    Section { Button("退出登录", role: .destructive, action: model.logout) }
                }.navigationTitle("我的")
            }.tabItem { Label("我的", systemImage: "person") }
        }
    }
    private func features(for role: Role?) -> [Feature] {
        switch role?.kind {
        case .teacher:
            return [Feature("教学中心", "/portal/education/teaching-center", "book"), Feature("课表", "/portal/education/schedule", "calendar"), Feature("通知", "/portal/education/class-notices", "bell")]
        case .parent:
            return [Feature("孩子", "/portal/education/family/children", "person.2"), Feature("家校通知", "/portal/education/family/notices", "bell"), Feature("成绩", "/portal/education/grades", "graduationcap")]
        default:
            return [Feature("成绩", "/portal/education/grades", "graduationcap"), Feature("课表", "/portal/education/schedule", "calendar"), Feature("作业", "/portal/education/homework", "checklist"), Feature("通知", "/portal/education/class-notices", "bell")]
        }
    }
}

private struct Feature {
    let title: String
    let path: String
    let icon: String
    init(_ title: String, _ path: String, _ icon: String) { self.title = title; self.path = path; self.icon = icon }
}

struct FeatureView: View {
    @EnvironmentObject var model: AppModel
    let title: String
    let path: String
    @State private var state: LoadState = .loading
    var body: some View {
        NavigationStack {
            Group {
                switch state {
                case .loading: ProgressView("加载中…")
                case .error(let message):
                    VStack(spacing: 12) { Text(message).foregroundStyle(.red); Button("重试") { Task { await load() } } }
                case .empty: VStack(spacing: 8) { Image(systemName: "tray"); Text("暂无数据"); Text("当前没有可展示的内容").foregroundStyle(.secondary) }
                case .content(let value): ScrollView { Text(value).frame(maxWidth: .infinity, alignment: .leading).padding() }
                }
            }.navigationTitle(title)
        }.task { await load() }
    }
    private func load() async {
        state = .loading
        do {
            let data = try await model.session.get(path, as: AnyCodable.self)
            state = data.description.isEmpty || data.description == "NSNull" ? .empty : .content(data.description)
        } catch { state = .error(error.localizedDescription) }
    }
}

private enum LoadState { case loading, content(String), empty, error(String) }

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

private extension Role {
    var id: String { roleCode ?? roleName ?? "role" }
    var displayName: String { roleName ?? roleCode ?? "门户用户" }
    var kind: RoleKind {
        let value = (roleCode ?? roleName ?? "").lowercased()
        if value.contains("teacher") || value.contains("教师") { return .teacher }
        if value.contains("parent") || value.contains("家长") { return .parent }
        return .student
    }
}
private enum RoleKind { case teacher, student, parent }
