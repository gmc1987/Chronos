import SwiftUI

struct BootstrapStateView: View {
    @EnvironmentObject var model: AppModel

    var body: some View {
        ZStack {
            ChronosPalette.background.ignoresSafeArea()
            VStack(spacing: 16) {
                if model.isBootstrapping {
                    ProgressView()
                    Text("正在初始化工作台…")
                        .foregroundStyle(ChronosPalette.secondary)
                } else {
                    Image(systemName: "exclamationmark.arrow.triangle.2.circlepath")
                        .font(.system(size: 34))
                        .foregroundStyle(ChronosPalette.orange)
                    Text(model.error ?? "工作台初始化失败")
                        .multilineTextAlignment(.center)
                    Button("重新加载") {
                        Task { await model.reloadBootstrap() }
                    }
                    .buttonStyle(.borderedProminent)
                    Button("退出登录", role: .destructive, action: model.logout)
                }
            }
            .padding(28)
        }
    }
}

