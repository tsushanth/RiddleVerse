import SwiftUI
import FirebaseAuth

struct GameTweakView: View {
    let game: BrowseGame
    @Environment(\.dismiss) private var dismiss
    @StateObject private var coinManager = CoinManager.shared
    @State private var versions: [GameVersion] = []
    @State private var isLoadingVersions = true
    @State private var tweakText = ""
    @State private var isTweaking = false
    @State private var tweakPhase = ""
    @State private var tweakProgress: Double = 0
    @State private var freeTweaksRemaining = 5
    @State private var tweakCost = 10
    @State private var errorMessage: String?
    @State private var successMessage: String?
    @State private var showCoinStore = false

    struct GameVersion: Identifiable {
        let id = UUID()
        let sha: String
        let shortSha: String
        let message: String
        let date: String
        let author: String
        var isInitial: Bool { message == "Initial game creation" }
    }

    var body: some View {
        ZStack {
            Color(red: 0.1, green: 0.1, blue: 0.18)
                .ignoresSafeArea()

            VStack(spacing: 0) {
                // Top bar
                HStack {
                    Button(action: { dismiss() }) {
                        Image(systemName: "xmark")
                            .font(.title3)
                            .foregroundColor(.white)
                            .frame(width: 40, height: 40)
                    }

                    Spacer()

                    VStack(spacing: 2) {
                        Text("Edit: \(game.title)")
                            .font(.subheadline.weight(.semibold))
                            .foregroundColor(.white)
                            .lineLimit(1)
                        tweakInfoLabel
                    }

                    Spacer()

                    CoinBalancePill(balance: coinManager.balance)
                }
                .padding(.horizontal)
                .padding(.top, 8)

                Divider()
                    .background(Color.white.opacity(0.1))

                // Version history
                if isLoadingVersions {
                    Spacer()
                    ProgressView()
                        .tint(.white)
                    Text("Loading version history...")
                        .font(.caption)
                        .foregroundColor(.white.opacity(0.4))
                        .padding(.top, 8)
                    Spacer()
                } else if versions.isEmpty {
                    Spacer()
                    VStack(spacing: 12) {
                        Image(systemName: "doc.text.magnifyingglass")
                            .font(.system(size: 40))
                            .foregroundColor(.white.opacity(0.3))
                        Text("No version history yet")
                            .font(.headline)
                            .foregroundColor(.white.opacity(0.5))
                        Text("Describe your change below to start editing your game")
                            .font(.caption)
                            .foregroundColor(.white.opacity(0.3))
                            .multilineTextAlignment(.center)
                    }
                    .padding(.horizontal, 40)
                    Spacer()
                } else {
                    ScrollView {
                        LazyVStack(spacing: 12) {
                            ForEach(versions) { version in
                                VersionRow(version: version)
                            }
                        }
                        .padding()
                    }
                }

                // Error/success messages
                if let error = errorMessage {
                    HStack(spacing: 6) {
                        Image(systemName: "exclamationmark.triangle.fill")
                            .foregroundColor(.red)
                        Text(error)
                            .font(.caption)
                            .foregroundColor(.white.opacity(0.8))
                    }
                    .padding(10)
                    .frame(maxWidth: .infinity)
                    .background(Color.red.opacity(0.15))
                    .cornerRadius(8)
                    .padding(.horizontal)
                    .onTapGesture { errorMessage = nil }
                }

                if let success = successMessage {
                    HStack(spacing: 6) {
                        Image(systemName: "checkmark.circle.fill")
                            .foregroundColor(.green)
                        Text(success)
                            .font(.caption)
                            .foregroundColor(.white.opacity(0.8))
                    }
                    .padding(10)
                    .frame(maxWidth: .infinity)
                    .background(Color.green.opacity(0.15))
                    .cornerRadius(8)
                    .padding(.horizontal)
                    .onTapGesture { successMessage = nil }
                }

                // Tweak input bar
                tweakInputBar
            }

            // Generation overlay
            if isTweaking {
                Color.black.opacity(0.85)
                    .ignoresSafeArea()
                    .onTapGesture {}

                VStack(spacing: 24) {
                    Spacer()

                    Image(systemName: "wand.and.stars")
                        .font(.system(size: 48))
                        .foregroundColor(.orange)

                    Text("Applying Edit")
                        .font(.title2.weight(.bold))
                        .foregroundColor(.white)

                    ProgressView(value: tweakProgress, total: 100)
                        .tint(.orange)
                        .frame(maxWidth: 250)

                    Text(tweakPhase)
                        .font(.subheadline)
                        .foregroundColor(.white.opacity(0.7))

                    Text("This may take a minute...")
                        .font(.caption)
                        .foregroundColor(.white.opacity(0.4))

                    Spacer()
                }
                .padding(40)
            }
        }
        .sheet(isPresented: $showCoinStore) {
            CoinStorePaywallView()
        }
        .onAppear {
            coinManager.fetchBalance()
            fetchVersions()
        }
    }

    // MARK: - Subviews

    private var tweakInfoLabel: some View {
        Group {
            if freeTweaksRemaining > 0 {
                Text("\(freeTweaksRemaining) free edit\(freeTweaksRemaining == 1 ? "" : "s") left")
                    .font(.caption2)
                    .foregroundColor(.green)
            } else {
                HStack(spacing: 3) {
                    Image(systemName: "circle.fill")
                        .font(.system(size: 6))
                        .foregroundColor(.yellow)
                    Text("\(tweakCost) coins per edit")
                        .font(.caption2)
                        .foregroundColor(.yellow)
                }
            }
        }
    }

    private var tweakInputBar: some View {
        VStack(spacing: 0) {
            Divider()
                .background(Color.white.opacity(0.1))

            HStack(spacing: 12) {
                TextField("Describe your change...", text: $tweakText)
                    .textFieldStyle(.plain)
                    .foregroundColor(.white)
                    .font(.subheadline)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 10)
                    .background(
                        RoundedRectangle(cornerRadius: 20)
                            .fill(Color.white.opacity(0.08))
                    )
                    .disabled(isTweaking)

                Button(action: { submitTweak() }) {
                    Image(systemName: "paperplane.fill")
                        .font(.title3)
                        .foregroundColor(tweakText.trimmingCharacters(in: .whitespaces).isEmpty || isTweaking ? .white.opacity(0.2) : .orange)
                        .frame(width: 44, height: 44)
                        .background(
                            Circle()
                                .fill(tweakText.trimmingCharacters(in: .whitespaces).isEmpty || isTweaking ? Color.white.opacity(0.05) : Color.orange.opacity(0.2))
                        )
                }
                .disabled(tweakText.trimmingCharacters(in: .whitespaces).isEmpty || isTweaking)
            }
            .padding(.horizontal)
            .padding(.vertical, 10)
            .background(Color(red: 0.08, green: 0.08, blue: 0.14))
        }
    }

    // MARK: - Version Row

    struct VersionRow: View {
        let version: GameVersion

        private var timeAgo: String {
            guard !version.date.isEmpty else { return "" }
            let formatter = ISO8601DateFormatter()
            formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
            if let date = formatter.date(from: version.date) {
                return relativeTime(from: date)
            }
            formatter.formatOptions = [.withInternetDateTime]
            if let date = formatter.date(from: version.date) {
                return relativeTime(from: date)
            }
            return ""
        }

        private func relativeTime(from date: Date) -> String {
            let seconds = Int(-date.timeIntervalSinceNow)
            if seconds < 60 { return "just now" }
            if seconds < 3600 { return "\(seconds / 60)m ago" }
            if seconds < 86400 { return "\(seconds / 3600)h ago" }
            return "\(seconds / 86400)d ago"
        }

        var body: some View {
            HStack(alignment: .top, spacing: 12) {
                // Version indicator
                VStack(spacing: 0) {
                    Circle()
                        .fill(version.isInitial ? Color.green : Color.orange)
                        .frame(width: 10, height: 10)
                    if !version.isInitial {
                        Rectangle()
                            .fill(Color.white.opacity(0.1))
                            .frame(width: 2)
                            .frame(maxHeight: .infinity)
                    }
                }
                .frame(width: 10)

                VStack(alignment: .leading, spacing: 4) {
                    // Commit message (strip "Tweak: " prefix for display)
                    let displayMsg = version.message.hasPrefix("Tweak: ")
                        ? String(version.message.dropFirst(7))
                        : version.message

                    Text(displayMsg)
                        .font(.subheadline)
                        .foregroundColor(.white)
                        .lineLimit(3)

                    HStack(spacing: 8) {
                        Text(version.shortSha)
                            .font(.caption2.monospaced())
                            .foregroundColor(.white.opacity(0.3))

                        if !timeAgo.isEmpty {
                            Text(timeAgo)
                                .font(.caption2)
                                .foregroundColor(.white.opacity(0.3))
                        }
                    }
                }

                Spacer()

                if version.isInitial {
                    Text("Original")
                        .font(.caption2.weight(.medium))
                        .foregroundColor(.green)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 3)
                        .background(Color.green.opacity(0.15))
                        .cornerRadius(6)
                }
            }
            .padding(12)
            .background(
                RoundedRectangle(cornerRadius: 12)
                    .fill(Color.white.opacity(0.04))
            )
        }
    }

    // MARK: - Networking

    private func fetchVersions() {
        isLoadingVersions = true
        errorMessage = nil

        guard let url = URL(string: "https://puzzleverseai.com/api/game-creation/\(game.id)/versions") else { return }

        NSLog("[GameTweak] Fetching versions for game %@", game.id)

        URLSession.shared.dataTask(with: url) { data, _, error in
            DispatchQueue.main.async {
                isLoadingVersions = false

                guard let data = data,
                      let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
                    NSLog("[GameTweak] Failed to fetch versions: %@", error?.localizedDescription ?? "unknown")
                    errorMessage = "Failed to load version history"
                    return
                }

                freeTweaksRemaining = json["freeTweaksRemaining"] as? Int ?? 5
                tweakCost = json["tweakCost"] as? Int ?? 10

                if let versionsArray = json["versions"] as? [[String: Any]] {
                    versions = versionsArray.compactMap { v in
                        guard let sha = v["sha"] as? String,
                              let message = v["message"] as? String else { return nil }
                        return GameVersion(
                            sha: sha,
                            shortSha: v["shortSha"] as? String ?? String(sha.prefix(7)),
                            message: message,
                            date: v["date"] as? String ?? "",
                            author: v["author"] as? String ?? ""
                        )
                    }
                    NSLog("[GameTweak] Loaded %d versions", versions.count)
                }
            }
        }.resume()
    }

    private func submitTweak() {
        let description = tweakText.trimmingCharacters(in: .whitespaces)
        guard !description.isEmpty else { return }
        guard let userId = Auth.auth().currentUser?.uid else { return }

        // Check if coins are needed and available
        if freeTweaksRemaining <= 0 && coinManager.balance < tweakCost {
            showCoinStore = true
            return
        }

        NSLog("[GameTweak] Submitting tweak: \"%@\"", description)

        isTweaking = true
        tweakPhase = "Preparing..."
        tweakProgress = 0
        errorMessage = nil
        successMessage = nil

        guard let url = URL(string: "https://puzzleverseai.com/api/game-creation/\(game.id)/tweak") else {
            isTweaking = false
            return
        }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.timeoutInterval = 600

        let body: [String: Any] = [
            "userId": userId,
            "tweakDescription": description
        ]
        request.httpBody = try? JSONSerialization.data(withJSONObject: body)

        let session = URLSession(configuration: .default)
        let task = session.dataTask(with: request) { data, _, error in
            guard let data = data else {
                DispatchQueue.main.async {
                    isTweaking = false
                    errorMessage = error?.localizedDescription ?? "Connection failed"
                }
                return
            }

            // Parse SSE events
            let responseStr = String(data: data, encoding: .utf8) ?? ""
            let lines = responseStr.components(separatedBy: "\n")

            NSLog("[GameTweak] Response received, parsing %d lines", lines.count)

            var gotResult = false

            for line in lines {
                guard line.hasPrefix("data: ") else { continue }
                let eventData = String(line.dropFirst(6))
                guard let eventJson = try? JSONSerialization.jsonObject(with: Data(eventData.utf8)) as? [String: Any],
                      let type = eventJson["type"] as? String else { continue }

                if type == "status" {
                    let message = eventJson["message"] as? String ?? ""
                    let pct = eventJson["progressPercent"] as? Double ?? 0
                    DispatchQueue.main.async {
                        tweakPhase = message
                        tweakProgress = pct
                    }
                } else if type == "result" {
                    gotResult = true
                    let remaining = eventJson["freeTweaksRemaining"] as? Int
                    let coinsSpent = eventJson["coinsSpent"] as? Int ?? 0

                    NSLog("[GameTweak] Tweak applied successfully (coins=%d)", coinsSpent)

                    DispatchQueue.main.async {
                        isTweaking = false
                        tweakText = ""
                        if let remaining = remaining {
                            freeTweaksRemaining = remaining
                        }
                        if coinsSpent > 0 {
                            coinManager.fetchBalance()
                        }
                        successMessage = "Edit applied! Your game has been updated."
                        // Refresh version history
                        fetchVersions()
                    }
                    return
                } else if type == "error" {
                    let errorMsg = eventJson["error"] as? String ?? "Unknown error"
                    NSLog("[GameTweak] Tweak error: %@", errorMsg)
                    DispatchQueue.main.async {
                        isTweaking = false
                        errorMessage = errorMsg
                    }
                    return
                }
            }

            if !gotResult {
                DispatchQueue.main.async {
                    isTweaking = false
                    errorMessage = "Edit failed — no result received"
                }
            }
        }
        task.resume()
    }
}
