import SwiftUI

struct PublicationsListView: View {
    let title: String
    let type: String?
    @EnvironmentObject var model: AppModel
    @State var state: ListLoadState<PublicationView> = .loading

    var body: some View {
        NavigationStack {
            LoadableList(
                state: state,
                emptyIcon: "megaphone",
                emptyText: title == "通知" ? "当前没有可见通知" : "当前没有可见公告",
                retry: { Task { await load() } }
            ) { publication in
                VStack(alignment: .leading, spacing: 6) {
                    HStack(alignment: .top) {
                        if publication.pinned == true {
                            Text("置顶")
                                .font(.system(size: 10, weight: .bold))
                                .foregroundStyle(.white)
                                .padding(.horizontal, 6)
                                .padding(.vertical, 3)
                                .background(ChronosPalette.orange, in: Capsule())
                        }
                        Text(publication.title)
                            .font(.headline)
                        Spacer()
                        if !publication.read {
                            Circle().fill(ChronosPalette.green).frame(width: 8, height: 8)
                        }
                    }
                    Text(publication.summary ?? "暂无摘要")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .lineLimit(2)
                    Text(displayDateTime(publication.publishedAt))
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                .padding(.vertical, 4)
            }
            .navigationTitle(title)
            .task { await load() }
            .refreshable { await load() }
        }
    }

    func load() async {
        state = .loading
        do {
            let page = try await model.session.get(
                AnnouncementsAPI.list(type: type),
                as: PublicationPage.self
            )
            state = .loaded(page.content)
        } catch {
            state = .error(error.localizedDescription)
        }
    }
}
