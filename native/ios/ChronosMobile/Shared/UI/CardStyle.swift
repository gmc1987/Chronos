import SwiftUI

extension View {
    func cardStyle() -> some View {
        background(.white, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
            .overlay {
                RoundedRectangle(cornerRadius: 18, style: .continuous)
                    .stroke(Color.black.opacity(0.045), lineWidth: 1)
            }
            .shadow(color: Color.black.opacity(0.045), radius: 12, y: 5)
    }
}

