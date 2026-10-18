import SwiftUI
import WebKit

struct ApplicationsView: View {
    let applications: [PortalApplication]

    var body: some View {
        NavigationStack {
            Group {
                if applications.isEmpty {
                    EmptyStateView(icon: "square.grid.2x2", text: "当前账号暂无可用应用")
                } else {
                    List(applications) { application in
                        NavigationLink {
                            ApplicationDestination(application: application)
                        } label: {
                            HStack(spacing: 12) {
                                Text(String(application.name.prefix(1)))
                                    .font(.headline)
                                    .foregroundStyle(.white)
                                    .frame(width: 40, height: 40)
                                    .background(ChronosPalette.green, in: RoundedRectangle(cornerRadius: 11))
                                VStack(alignment: .leading, spacing: 3) {
                                    Text(application.name)
                                    Text(application.description ?? "服务端授权应用")
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                        .lineLimit(2)
                                }
                            }
                        }
                    }
                    .listStyle(.insetGrouped)
                }
            }
            .navigationTitle("应用")
        }
    }
}

@ViewBuilder
func ApplicationDestination(application: PortalApplication) -> some View {
    if application.openMode == "EXTERNAL" || URL(string: application.routePath)?.scheme != nil {
        ExternalApplicationView(application: application)
    } else {
        AuthenticatedPortalView(application: application)
    }
}

struct ExternalApplicationView: View {
    let application: PortalApplication
    @Environment(\.openURL) private var openURL

    var body: some View {
        ContentUnavailableView {
            Label(application.name, systemImage: "arrow.up.right.square")
        } description: {
            Text("该应用由外部系统提供。")
        } actions: {
            Button("打开应用") {
                if let url = URL(string: application.routePath) { openURL(url) }
            }
            .buttonStyle(.borderedProminent)
        }
        .navigationTitle(application.name)
    }
}

/// Hosts the complete operational portal page and reuses the current mobile login.
struct AuthenticatedPortalView: View {
    let application: PortalApplication
    @EnvironmentObject var model: AppModel
    @State private var accessToken: String?
    @State private var loadedToken = false
    @State private var loadError: String?
    @State private var reloadID = UUID()

    private var routePath: String {
        application.routePath.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    var body: some View {
        Group {
            if routePath.isEmpty {
                ContentUnavailableView(
                    "应用地址未配置",
                    systemImage: "link.badge.plus",
                    description: Text("请在门户配置中为“\(application.name)”设置访问地址。")
                )
            } else if let accessToken {
                ZStack {
                    PortalWebView(
                        routePath: routePath,
                        accessToken: accessToken,
                        loadError: $loadError
                    )
                    .id(reloadID)

                    if let loadError {
                        ContentUnavailableView {
                            Label("页面加载失败", systemImage: "wifi.exclamationmark")
                        } description: {
                            Text(loadError)
                        } actions: {
                            Button("重新加载") {
                                self.loadError = nil
                                reloadID = UUID()
                            }
                            .buttonStyle(.borderedProminent)
                        }
                        .background(ChronosPalette.background)
                    }
                }
            } else if loadedToken {
                ContentUnavailableView(
                    "登录状态已失效",
                    systemImage: "person.crop.circle.badge.exclamationmark",
                    description: Text("请退出后重新登录，再打开应用。")
                )
            } else {
                ProgressView("正在打开应用…")
            }
        }
        .navigationTitle(application.name)
        .navigationBarTitleDisplayMode(.inline)
        .task {
            accessToken = await model.session.currentAccessToken()
            loadedToken = true
        }
    }
}

struct PortalWebView: UIViewRepresentable {
    let routePath: String
    let accessToken: String
    @Binding var loadError: String?

    func makeUIView(context: Context) -> WKWebView {
        let configuration = WKWebViewConfiguration()
        let source = "localStorage.setItem('admin_access_token', \(json(accessToken)));"
        configuration.userContentController.addUserScript(WKUserScript(
            source: source,
            injectionTime: .atDocumentStart,
            forMainFrameOnly: true
        ))
        let webView = WKWebView(frame: .zero, configuration: configuration)
        webView.allowsBackForwardNavigationGestures = true
        webView.navigationDelegate = context.coordinator
        return webView
    }

    func updateUIView(_ webView: WKWebView, context: Context) {
        let base = ProcessInfo.processInfo.environment["CHRONOS_PORTAL_BASE_URL"]
            ?? "http://localhost:5173"
        let normalizedRoute = routePath.hasPrefix("/") ? routePath : "/\(routePath)"
        guard let url = URL(string: base + normalizedRoute),
              webView.url != url else { return }
        webView.load(URLRequest(url: url))
    }

    func makeCoordinator() -> Coordinator { Coordinator(loadError: $loadError) }

    final class Coordinator: NSObject, WKNavigationDelegate {
        private var loadError: Binding<String?>

        init(loadError: Binding<String?>) {
            self.loadError = loadError
        }

        func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
            loadError.wrappedValue = nil
        }

        func webView(
            _ webView: WKWebView,
            didFailProvisionalNavigation navigation: WKNavigation!,
            withError error: Error
        ) {
            loadError.wrappedValue = error.localizedDescription
        }

        func webView(
            _ webView: WKWebView,
            didFail navigation: WKNavigation!,
            withError error: Error
        ) {
            loadError.wrappedValue = error.localizedDescription
        }

        func webView(
            _ webView: WKWebView,
            decidePolicyFor navigationResponse: WKNavigationResponse,
            decisionHandler: @escaping (WKNavigationResponsePolicy) -> Void
        ) {
            guard let response = navigationResponse.response as? HTTPURLResponse,
                  response.statusCode >= 400 else {
                decisionHandler(.allow)
                return
            }
            loadError.wrappedValue = "门户服务返回 HTTP \(response.statusCode)"
            decisionHandler(.cancel)
        }
    }

    private func json(_ value: String) -> String {
        guard let data = try? JSONSerialization.data(withJSONObject: [value]),
              let array = String(data: data, encoding: .utf8) else { return "\"\"" }
        return String(array.dropFirst().dropLast())
    }
}

