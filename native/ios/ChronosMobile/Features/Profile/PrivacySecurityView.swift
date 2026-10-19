import SwiftUI

struct PrivacySecurityView: View {
    @EnvironmentObject private var model: AppModel
    @State private var cacheCleared = false

    var body: some View {
        Form {
            Section("登录凭证") {
                Text("登录令牌保存在 iOS Keychain 中，退出登录时会清除。")
            }
            Section("离线数据") {
                Text("只缓存允许离线查看的只读接口，缓存最多保留 24 小时。")
                Button("清除离线缓存") {
                    Task {
                        await model.session.clearOfflineCache()
                        cacheCleared = true
                    }
                }
                if cacheCleared {
                    Label("离线缓存已清除", systemImage: "checkmark.circle.fill")
                        .foregroundStyle(.green)
                }
            }
        }
        .navigationTitle("隐私与安全")
    }
}
