import SwiftUI

struct TelegramBotBanner: View {
    let onDismiss: () -> Void
    private let botURL = URL(string: "https://t.me/Riddleverse_bot")!

    var body: some View {
        HStack(spacing: 12) {
            Text("✈️")
                .font(.system(size: 28))

            VStack(alignment: .leading, spacing: 2) {
                Text("Play RiddleVerse on Telegram!")
                    .font(.subheadline.bold())
                    .foregroundStyle(.white)
                Text("Quiz your group chat • @Riddleverse_bot")
                    .font(.caption)
                    .foregroundStyle(.white.opacity(0.85))
                    .lineLimit(1)
            }

            Spacer()

            Button(action: onDismiss) {
                Image(systemName: "xmark")
                    .font(.caption.bold())
                    .foregroundStyle(.white.opacity(0.8))
                    .padding(6)
                    .background(.white.opacity(0.15), in: Circle())
            }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 12)
        .background(
            LinearGradient(
                colors: [Color(hex: "0088CC") ?? Color.blue, Color(hex: "00AAEE") ?? Color.cyan],
                startPoint: .leading,
                endPoint: .trailing
            ),
            in: RoundedRectangle(cornerRadius: 14)
        )
        .contentShape(RoundedRectangle(cornerRadius: 14))
        .onTapGesture {
            UIApplication.shared.open(botURL)
        }
    }
}


#Preview {
    TelegramBotBanner(onDismiss: {})
        .padding()
        .background(Color(.systemGroupedBackground))
}
