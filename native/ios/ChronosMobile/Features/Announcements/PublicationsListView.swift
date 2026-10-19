import SwiftUI

struct PublicationsListView: View {
    let title: String
    let type: String?
    @EnvironmentObject private var model: AppModel
    @State private var publications: [PublicationView] = []
    @State private var keyword = ""
    @State private var unreadOnly = false
    @State private var page = 0
    @State private var last = true
    @State private var loading = false
    @State private var error: String?

    var body: some View {
        NavigationStack {
            List {
                Toggle("只看未读", isOn: $unreadOnly)
                    .onChange(of: unreadOnly) { _, _ in Task { await reload() } }
                if let error {
                    Button("\(error)\n点击重试") { Task { await reload() } }.foregroundStyle(.red)
                } else if publications.isEmpty && !loading {
                    EmptyStateView(icon: "megaphone", text: title == "通知" ? "当前没有可见通知" : "当前没有可见公告")
                }
                ForEach(publications) { publication in
                    NavigationLink { PublicationDetailView(publicationID: publication.id) } label: {
                        PublicationRow(publication: publication)
                    }
                }
                if loading { ProgressView().frame(maxWidth: .infinity) }
                if !last && !loading {
                    Button("加载更多") { Task { await load(reset: false) } }.frame(maxWidth: .infinity)
                }
            }
            .navigationTitle(title)
            .searchable(text: $keyword, prompt: "搜索标题或内容")
            .onSubmit(of: .search) { Task { await reload() } }
            .task { if publications.isEmpty { await reload() } }
            .refreshable { await reload() }
        }
    }

    private func reload() async { await load(reset: true) }

    private func load(reset: Bool) async {
        guard !loading else { return }
        loading = true
        error = nil
        if reset { page = 0 }
        do {
            let result = try await model.session.get(
                AnnouncementsAPI.list(type: type, keyword: keyword, unreadOnly: unreadOnly, page: page),
                as: PublicationPage.self
            )
            publications = reset ? result.content : publications + result.content
            last = result.last
            if !result.last { page += 1 }
        } catch {
            self.error = error.localizedDescription
        }
        loading = false
    }
}

private struct PublicationRow: View {
    let publication: PublicationView
    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(alignment: .top) {
                if publication.pinned == true {
                    Text("置顶").font(.caption2.bold()).foregroundStyle(.white)
                        .padding(.horizontal, 6).padding(.vertical, 3)
                        .background(ChronosPalette.orange, in: Capsule())
                }
                Text(publication.title).font(.headline)
                Spacer()
                if !publication.read { Circle().fill(ChronosPalette.green).frame(width: 8, height: 8) }
            }
            Text(publication.summary ?? "暂无摘要").font(.subheadline).foregroundStyle(.secondary).lineLimit(2)
            Text(displayDateTime(publication.publishedAt)).font(.caption).foregroundStyle(.secondary)
        }.padding(.vertical, 4)
    }
}
