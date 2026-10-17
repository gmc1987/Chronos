import SwiftUI

enum ListLoadState<Value> {
    case loading
    case loaded([Value])
    case error(String)
}

struct LoadableList<Value, Row: View>: View {
    let state: ListLoadState<Value>
    let emptyIcon: String
    let emptyText: String
    let retry: () -> Void
    @ViewBuilder let row: (Value) -> Row

    var body: some View {
        switch state {
        case .loading:
            ProgressView("加载中…")
        case .error(let message):
            InlineErrorView(message: message, retry: retry)
                .padding()
        case .loaded(let values):
            if values.isEmpty {
                EmptyStateView(icon: emptyIcon, text: emptyText)
            } else {
                List {
                    ForEach(Array(values.enumerated()), id: \.offset) { _, value in
                        row(value)
                    }
                }
                .listStyle(.insetGrouped)
            }
        }
    }
}

struct CardTitle: View {
    let title: String
    let icon: String

    var body: some View {
        Label(title, systemImage: icon)
            .font(.headline)
            .foregroundStyle(ChronosPalette.text)
    }
}

struct InlineErrorView: View {
    let message: String
    let retry: () -> Void

    var body: some View {
        VStack(spacing: 9) {
            Label(message, systemImage: "exclamationmark.triangle")
                .font(.subheadline)
                .foregroundStyle(.red)
                .multilineTextAlignment(.center)
            Button("重试", action: retry)
                .font(.subheadline.weight(.semibold))
        }
        .frame(maxWidth: .infinity, minHeight: 90)
    }
}

struct EmptyStateView: View {
    let icon: String
    let text: String

    var body: some View {
        ContentUnavailableView(text, systemImage: icon)
    }
}

struct ConstructionInlineView: View {
    let message: String

    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: "hammer")
            Text(message)
        }
        .font(.subheadline)
        .foregroundStyle(ChronosPalette.secondary)
        .frame(maxWidth: .infinity, minHeight: 64, alignment: .center)
    }
}

struct ConstructionView: View {
    let title: String
    var message = "功能建设中，暂未接入可用的移动端接口。"

    var body: some View {
        EmptyStateView(icon: "hammer", text: message)
            .navigationTitle(title)
    }
}

