import SwiftUI
import UIKit

struct PublicationDetailView: View {
    let publicationID: String
    @EnvironmentObject private var model: AppModel
    @State private var publication: PublicationView?
    @State private var loading = true
    @State private var error: String?
    @State private var sharedFile: SharedPublicationFile?

    var body: some View {
        Group {
            if loading { ProgressView("正在加载…") }
            else if let error { ContentUnavailableView("加载失败", systemImage: "exclamationmark.triangle", description: Text(error)) }
            else if let publication {
                ScrollView {
                    VStack(alignment: .leading, spacing: 18) {
                        Text(publication.title).font(.title2.bold())
                        Label(displayDateTime(publication.publishedAt), systemImage: "calendar")
                            .font(.subheadline).foregroundStyle(.secondary)
                        if let summary = publication.summary, !summary.isEmpty {
                            Text(summary).font(.headline).foregroundStyle(.secondary)
                        }
                        Text(publication.content ?? "暂无正文")
                            .textSelection(.enabled).frame(maxWidth: .infinity, alignment: .leading)
                        if !publication.attachments.isEmpty {
                            Divider()
                            Text("附件").font(.headline)
                            ForEach(publication.attachments) { attachment in
                                Button { Task { await download(attachment) } } label: {
                                    Label(attachment.originalName, systemImage: "paperclip")
                                        .frame(maxWidth: .infinity, alignment: .leading)
                                }
                            }
                        }
                    }.padding(20)
                }
            }
        }
        .navigationTitle("通知详情").navigationBarTitleDisplayMode(.inline)
        .task { await load() }
        .sheet(item: $sharedFile) { file in PublicationShareSheet(items: [file.url]) }
    }

    private func load() async {
        loading = true
        error = nil
        do {
            publication = try await model.session.get(AnnouncementsAPI.detail(publicationID), as: PublicationView.self)
            _ = try await model.session.sendJSON(AnnouncementsAPI.markRead(publicationID))
        } catch { self.error = error.localizedDescription }
        loading = false
    }

    private func download(_ attachment: PublicationAttachment) async {
        do {
            let data = try await model.session.download(AnnouncementsAPI.attachment(attachment.id))
            let url = FileManager.default.temporaryDirectory.appendingPathComponent(attachment.originalName)
            try data.write(to: url, options: .atomic)
            sharedFile = SharedPublicationFile(url: url)
        } catch { self.error = error.localizedDescription }
    }
}

private struct SharedPublicationFile: Identifiable {
    let url: URL
    var id: URL { url }
}

private struct PublicationShareSheet: UIViewControllerRepresentable {
    let items: [Any]
    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: items, applicationActivities: nil)
    }
    func updateUIViewController(_ controller: UIActivityViewController, context: Context) {}
}
