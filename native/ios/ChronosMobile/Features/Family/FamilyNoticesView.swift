import SwiftUI

struct FamilyNoticesView: View {
    @EnvironmentObject private var model: AppModel
    @State private var notices: [FamilyNotice] = []
    @State private var children: [FamilyChild] = []
    @State private var selectedStudentID = ""
    @State private var loading = true
    @State private var error: String?

    var body: some View {
        List {
            if !children.isEmpty {
                Picker("当前子女", selection: $selectedStudentID) {
                    Text("全部").tag("")
                    ForEach(children) { child in Text(child.studentName).tag(child.id) }
                }
            }
            if loading { ProgressView().frame(maxWidth: .infinity) }
            if let error { Text(error).foregroundStyle(.red) }
            if filteredNotices.isEmpty && !loading { Text("当前没有家校通知").foregroundStyle(.secondary) }
            ForEach(filteredNotices) { notice in
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
        do {
            async let childValues = model.session.get(FamilyAPI.children, as: [FamilyChild].self)
            async let noticeValues = model.session.get(FamilyAPI.notices, as: [FamilyNotice].self)
            (children, notices) = try await (childValues, noticeValues)
            error = nil
        }
        catch { self.error = error.localizedDescription }
        loading = false
    }

    private var filteredNotices: [FamilyNotice] {
        selectedStudentID.isEmpty ? notices : notices.filter { $0.studentId == selectedStudentID }
    }

    private func receipt(_ notice: FamilyNotice) async {
        do { _ = try await model.session.sendJSON(FamilyAPI.receipt(notice.id)); await load() }
        catch { self.error = error.localizedDescription }
    }
}
