import SwiftUI

struct ApplicationsView: View {
    let applications: [PortalApplication]
    @EnvironmentObject private var model: AppModel
    @State private var query = ""
    @State private var favoriteIDs: Set<String> = []
    @State private var recent: [PortalApplication] = []
    @State private var error: String?

    private var filtered: [PortalApplication] {
        guard !query.isEmpty else { return applications }
        return applications.filter {
            $0.name.localizedCaseInsensitiveContains(query)
                || ($0.description?.localizedCaseInsensitiveContains(query) ?? false)
        }
    }

    private var grouped: [(String, [PortalApplication])] {
        let order = ["协同办公", "教学管理", "会议与日程", "智能服务", "其他应用"]
        let available = filtered.filter { !favoriteIDs.contains($0.id) }
        let groups = Dictionary(grouping: available, by: \.category)
        return order.compactMap { name in
            guard let values = groups[name], !values.isEmpty else { return nil }
            return (name, values)
        } + groups.keys.filter { !order.contains($0) }.sorted().compactMap { name in
            groups[name].map { (name, $0) }
        }
    }

    var body: some View {
        NavigationStack {
            List {
                if let error { Text(error).foregroundStyle(.red).font(.caption) }
                if query.isEmpty && !recent.isEmpty {
                    Section("最近使用") { ForEach(recent) { applicationRow($0) } }
                }
                let favorites = filtered.filter { favoriteIDs.contains($0.id) }
                if !favorites.isEmpty {
                    Section("我的收藏") { ForEach(favorites) { applicationRow($0) } }
                }
                ForEach(grouped, id: \.0) { group in
                    Section(query.isEmpty ? group.0 : "\(group.0) · 搜索结果") {
                        ForEach(group.1) { applicationRow($0) }
                    }
                }
            }
            .overlay { if applications.isEmpty { EmptyStateView(icon: "square.grid.2x2", text: "当前账号暂无可用应用") } }
            .navigationTitle("应用")
            .searchable(text: $query, prompt: "搜索应用")
            .task { await loadPersonalization() }
            .refreshable { await loadPersonalization() }
        }
    }

    @ViewBuilder private func applicationRow(_ app: PortalApplication) -> some View {
        HStack(spacing: 12) {
            NavigationLink {
                ApplicationDestination(application: app)
                    .task { await recordVisit(app) }
            } label: {
                HStack(spacing: 12) {
                    Text(String(app.name.prefix(1))).font(.headline).foregroundStyle(.white)
                        .frame(width: 42, height: 42)
                        .background(ChronosPalette.green, in: RoundedRectangle(cornerRadius: 12))
                    VStack(alignment: .leading, spacing: 3) {
                        HStack {
                            Text(app.name).font(.headline)
                            if app.badgeCount > 0 {
                                Text(app.badgeCount > 99 ? "99+" : String(app.badgeCount))
                                    .font(.caption2.bold()).foregroundStyle(.white)
                                    .padding(.horizontal, 7).padding(.vertical, 2)
                                    .background(.red, in: Capsule())
                                    .accessibilityLabel("\(app.badgeCount) 项待处理")
                            }
                        }
                        Text(app.description ?? "服务端授权应用").font(.caption)
                            .foregroundStyle(.secondary).lineLimit(2)
                    }
                }
            }
            Button { Task { await toggleFavorite(app) } } label: {
                Image(systemName: favoriteIDs.contains(app.id) ? "star.fill" : "star")
                    .foregroundStyle(favoriteIDs.contains(app.id) ? .orange : .secondary)
            }.buttonStyle(.borderless).accessibilityLabel(favoriteIDs.contains(app.id) ? "取消收藏" : "收藏")
        }
        .accessibilityElement(children: .contain)
        .accessibilityLabel("\(app.name)，\(app.category)\(app.badgeCount > 0 ? "，\(app.badgeCount) 项待处理" : "")")
    }

    private func loadPersonalization() async {
        do {
            let favorites = try await model.session.get("/portal/favorites", as: [PortalApplication].self)
            recent = try await model.session.get("/portal/recent-visits", as: [PortalApplication].self)
            favoriteIDs = Set(favorites.map(\.id))
            error = nil
        } catch { self.error = error.localizedDescription }
    }

    private func toggleFavorite(_ app: PortalApplication) async {
        let wasFavorite = favoriteIDs.contains(app.id)
        do {
            _ = try await model.session.sendJSON(
                "/portal/favorites/\(app.id)", method: wasFavorite ? "DELETE" : "PUT"
            )
            if wasFavorite { favoriteIDs.remove(app.id) } else { favoriteIDs.insert(app.id) }
            error = nil
        } catch { self.error = error.localizedDescription }
    }

    private func recordVisit(_ app: PortalApplication) async {
        do {
            _ = try await model.session.sendJSON("/portal/applications/\(app.id)/visit")
            recent.removeAll { $0.id == app.id }
            recent.insert(app, at: 0)
            if recent.count > 8 { recent.removeLast(recent.count - 8) }
        } catch { self.error = error.localizedDescription }
    }
}
