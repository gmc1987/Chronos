import SwiftUI
import UIKit

struct NotificationSettingsView: View {
    var body: some View {
        Form {
            Section("系统通知权限") {
                Text("通知声音、横幅、锁屏显示和免打扰规则由 iOS 统一管理。")
                    .foregroundStyle(.secondary)
                Button("打开系统通知设置") {
                    guard let url = URL(string: UIApplication.openNotificationSettingsURLString) else { return }
                    UIApplication.shared.open(url)
                }
            }
        }
        .navigationTitle("消息设置")
    }
}
