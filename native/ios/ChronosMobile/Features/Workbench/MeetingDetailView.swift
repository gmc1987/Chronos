import SwiftUI

struct MeetingDetailView: View {
    @EnvironmentObject private var model: AppModel
    @Environment(\.openURL) private var openURL
    @State private var value: MeetingView
    @State private var comment = ""
    @State private var error: String?
    @State private var processing = false

    init(meeting: MeetingView) {
        _value = State(initialValue: meeting)
    }

    var body: some View {
        Form {
            Section("会议信息") {
                LabeledContent("主题", value: value.meeting.title)
                LabeledContent("开始", value: displayDateTime(value.meeting.startTime))
                LabeledContent("结束", value: displayDateTime(value.meeting.endTime))
                LabeledContent("组织者", value: value.meeting.organizerUsername ?? "—")
                LabeledContent("形式", value: value.meeting.meetingType ?? "—")
                LabeledContent("地点", value: roomText)
                LabeledContent("参会状态", value: responseLabel)
                if let agenda = value.meeting.agenda, !agenda.isEmpty {
                    LabeledContent("议程", value: agenda)
                }
            }

            if value.meeting.status == "PUBLISHED" {
                Section("参会确认") {
                    TextField("谢绝或请假原因", text: $comment, axis: .vertical)
                    Button("确认参加") { Task { await respond("ACCEPTED") } }
                    Button("谢绝参加") { Task { await respond("DECLINED") } }
                    Button("会议请假") { Task { await respond("LEAVE") } }
                    Button(value.currentParticipant?.checkedInAt == nil ? "会议签到" : "已签到") {
                        Task { await checkIn() }
                    }.disabled(value.currentParticipant?.checkedInAt != nil)
                    if let raw = value.meeting.joinUrl, let url = URL(string: raw) {
                        Button("进入线上会议") { openURL(url) }
                    }
                }.disabled(processing)
            }

            if let error { Text(error).foregroundStyle(.red) }
        }
        .navigationTitle("会议详情")
    }

    private var roomText: String {
        let text = [value.room?.roomName, value.room?.location]
            .compactMap { $0 }.filter { !$0.isEmpty }.joined(separator: " · ")
        return text.isEmpty ? "—" : text
    }

    private var responseLabel: String {
        let labels = ["INVITED": "待确认", "ACCEPTED": "已接受", "DECLINED": "已谢绝", "LEAVE": "已请假"]
        return labels[value.currentParticipant?.responseStatus ?? ""] ?? "组织者"
    }

    private func respond(_ status: String) async {
        if status == "LEAVE" && comment.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            error = "会议请假必须填写原因"
            return
        }
        processing = true
        do {
            _ = try await model.session.sendJSON(MeetingsAPI.response(value.meeting.id), body: .object([
                "status": .string(status), "comment": .string(comment)]))
            await reload()
        } catch { self.error = error.localizedDescription }
        processing = false
    }

    private func checkIn() async {
        processing = true
        do {
            _ = try await model.session.sendJSON(MeetingsAPI.checkIn(value.meeting.id))
            await reload()
        } catch { self.error = error.localizedDescription }
        processing = false
    }

    private func reload() async {
        do {
            let meetings = try await model.session.get(MeetingsAPI.list, as: [MeetingView].self)
            if let updated = meetings.first(where: { $0.id == value.id }) { value = updated }
            error = nil
        } catch { self.error = error.localizedDescription }
    }
}
