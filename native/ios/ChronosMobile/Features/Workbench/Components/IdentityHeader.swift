import SwiftUI

struct IdentityHeader: View {
    let eyebrow: String
    let name: String
    let detail: String
    let icon: String

    var body: some View {
        HStack(spacing: 15) {
            Image(systemName: icon)
                .font(.system(size: 25, weight: .semibold))
                .foregroundStyle(.white)
                .frame(width: 54, height: 54)
                .background(ChronosPalette.green, in: RoundedRectangle(cornerRadius: 16))
            VStack(alignment: .leading, spacing: 5) {
                Text(eyebrow.uppercased())
                    .font(.caption.weight(.bold))
                    .tracking(1.4)
                    .foregroundStyle(ChronosPalette.green)
                Text(name)
                    .font(.title2.bold())
                    .foregroundStyle(ChronosPalette.text)
                Text("\(detail) · \(todayText)")
                    .font(.subheadline)
                    .foregroundStyle(ChronosPalette.secondary)
            }
            Spacer()
        }
        .padding(18)
        .cardStyle()
    }
}

