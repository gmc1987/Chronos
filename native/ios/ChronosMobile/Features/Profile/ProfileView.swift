import SwiftUI

struct ProfileView: View {
    let bootstrap: PortalBootstrap
    let kind: WorkbenchKind
    @EnvironmentObject var model: AppModel

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    LabeledContent(
                        "姓名",
                        value: bootstrap.user.displayName ?? bootstrap.user.username
                    )
                    LabeledContent("账号", value: bootstrap.user.username)
                    LabeledContent("当前工作台", value: kind.displayName)
                    if let employeeId = bootstrap.user.employeeId, !employeeId.isEmpty {
                        LabeledContent("工号", value: employeeId)
                    }
                } header: {
                    Text("个人资料")
                } footer: {
                    Text("工作台由服务端身份、角色、权限和授权应用共同决定，不提供身份切换。")
                }
                Section("设置") {
                    Label("消息设置", systemImage: "bell.badge")
                    Label("隐私与安全", systemImage: "lock.shield")
                    LabeledContent("App 版本", value: "0.1.0")
                }
                Section {
                    Button("退出登录", role: .destructive, action: model.logout)
                }
            }
            .navigationTitle("我的")
        }
    }
}

