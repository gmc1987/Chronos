import SwiftUI

struct FamilyNoticesView: View {
    @EnvironmentObject private var model: AppModel
    @State private var notices: [FamilyNotice] = []
    @State private var loading = true
    @State private var error: String?

    var body: some View {
        List {
            if loading { ProgressView().frame(maxWidth: .infinity) }
            if let error { Text(error).foregroundStyle(.red) }
            if notices.isEmpty && !loading { Text("当前没有家校通知").foregroundStyle(.secondary) }
            ForEach(notices) { notice in
                VStack(alignment: .leading, spacing: 8) {
                    Text(notice.title).font(.headline)
                    Text(notice.content ?? "暂无内容").font(.subheadline)
                    Text(displayDateTime(notice.publishAt)).font(.caption).foregroundStyle(.secondary)
                    if notice.receiptRequired == true {
                        if notice.receiptStatus == "RECEIVED" {
                            Label("已回执", systemImage: "checkmark.circle.fill").foregroundStyle(.green)
                        } else if notice.expired == true {
                            Text("已过期，无法回执").foregroundStyle(.secondary)
                        } else {
                            Button("确认收到") { Task { await receipt(notice) } }.buttonStyle(.borderedProminent)
                        }
                    }
                }.padding(.vertical, 5)
            }
        }.navigationTitle("家校通知").task { await load() }.refreshable { await load() }
    }

    private func load() async {
        loading = true
        do { notices = try await model.session.get(FamilyAPI.notices, as: [FamilyNotice].self); error = nil }
        catch { self.error = error.localizedDescription }
        loading = false
    }

    private func receipt(_ notice: FamilyNotice) async {
        do { _ = try await model.session.sendJSON(FamilyAPI.receipt(notice.id)); await load() }
        catch { self.error = error.localizedDescription }
    }
}
