import SwiftUI
import SafariServices
import FirebaseAuth

struct GameScoreEntry: Identifiable {
    let id = UUID()
    let username: String
    let score: Int
    let userId: String
}

struct BrowseGame: Identifiable {
    let id: String
    let title: String
    let creatorId: String
    let creatorName: String
    let playCount: Int
    let initialPrompt: String
    let createdAt: String
    let status: String
    let thumbnailUrl: String?
    // Feature A / C additions. Defaulted so this still works against pre-migration servers.
    let seriesId: String?
    let levelIndex: Int
    let levelCount: Int
    let trendingScore: Double

    init(
        id: String,
        title: String,
        creatorId: String,
        creatorName: String,
        playCount: Int,
        initialPrompt: String,
        createdAt: String,
        status: String,
        thumbnailUrl: String?,
        seriesId: String? = nil,
        levelIndex: Int = 1,
        levelCount: Int = 1,
        trendingScore: Double = 0
    ) {
        self.id = id
        self.title = title
        self.creatorId = creatorId
        self.creatorName = creatorName
        self.playCount = playCount
        self.initialPrompt = initialPrompt
        self.createdAt = createdAt
        self.status = status
        self.thumbnailUrl = thumbnailUrl
        self.seriesId = seriesId
        self.levelIndex = levelIndex
        self.levelCount = levelCount
        self.trendingScore = trendingScore
    }
}

struct GameBrowseView: View {
    private let pageSize = 20
    private let freePlaysPerGame = 3
    @State private var games: [BrowseGame] = []
    @State private var isLoading = true
    @State private var isLoadingMore = false
    @State private var sortBy = "trending"   // Feature C: default flipped from "newest"
    @State private var newReleases: [BrowseGame] = []   // Feature C: carousel above main grid
    @State private var showMyGames = false
    @State private var hasMore = false
    @State private var currentOffset = 0
    @State private var webViewURL: URL?
    @State private var currentSessionId: String? = nil
    @State private var tweakGame: BrowseGame?
    @State private var errorMessage: String?
    @State private var deletingIds: Set<String> = []
    @State private var coinGateGame: BrowseGame?
    @State private var isCoinGateSpending = false
    @ObservedObject private var coinManager = CoinManager.shared
    private var currentUserId: String? { Auth.auth().currentUser?.uid }

    var body: some View {
        VStack(spacing: 0) {
            // Sort controls
            HStack(spacing: 0) {
                Text("Community Games")
                    .font(.headline)
                    .foregroundColor(.white)
                    .layoutPriority(1)

                Spacer(minLength: 8)

                HStack(spacing: 8) {
                    if currentUserId != nil {
                        Button(action: { showMyGames.toggle() }) {
                            Text("My Games")
                                .font(.caption)
                                .lineLimit(1)
                                .foregroundColor(showMyGames ? .white : .orange)
                                .padding(.horizontal, 10)
                                .padding(.vertical, 6)
                                .background(Capsule().fill(showMyGames ? Color.orange : Color.orange.opacity(0.15)))
                        }
                    }

                    Menu {
                        Button(action: { sortBy = "trending" }) {
                            Label("Trending", systemImage: sortBy == "trending" ? "checkmark" : "")
                        }
                        Button(action: { sortBy = "popular" }) {
                            Label("Most Played", systemImage: sortBy == "popular" ? "checkmark" : "")
                        }
                        Button(action: { sortBy = "newest" }) {
                            Label("Newest", systemImage: sortBy == "newest" ? "checkmark" : "")
                        }
                    } label: {
                        HStack(spacing: 4) {
                            Image(systemName: "arrow.up.arrow.down")
                            Text(sortBy == "trending" ? "Trending" : sortBy == "popular" ? "Popular" : "Newest")
                                .font(.caption)
                                .lineLimit(1)
                                .fixedSize(horizontal: true, vertical: false)
                        }
                        .foregroundColor(.orange)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 6)
                        .background(Capsule().fill(Color.orange.opacity(0.15)))
                    }
                }
            }
            .padding(.horizontal)
            .padding(.vertical, 12)

            if isLoading {
                Spacer()
                ProgressView()
                    .tint(.white)
                Spacer()
            } else if games.isEmpty {
                Spacer()
                VStack(spacing: 12) {
                    Image(systemName: "gamecontroller")
                        .font(.system(size: 48))
                        .foregroundColor(.white.opacity(0.3))
                    Text("No games yet")
                        .font(.headline)
                        .foregroundColor(.white.opacity(0.5))
                    Text("Be the first to create and share a game!")
                        .font(.caption)
                        .foregroundColor(.white.opacity(0.3))
                }
                Spacer()
            } else {
                ScrollView {
                    LazyVStack(spacing: 12) {
                        // New Releases carousel — only on the default Trending view of the community feed
                        if sortBy == "trending" && !showMyGames && !newReleases.isEmpty {
                            VStack(alignment: .leading, spacing: 8) {
                                HStack {
                                    Text("New Releases")
                                        .font(.headline)
                                        .foregroundColor(.white)
                                    Spacer()
                                }
                                .padding(.horizontal, 4)

                                ScrollView(.horizontal, showsIndicators: false) {
                                    HStack(spacing: 10) {
                                        ForEach(newReleases) { rel in
                                            Button(action: { openGame(rel) }) {
                                                VStack(alignment: .leading, spacing: 4) {
                                                    if let thumb = rel.thumbnailUrl, let url = URL(string: thumb) {
                                                        AsyncImage(url: url) { phase in
                                                            switch phase {
                                                            case .success(let img):
                                                                img.resizable().scaledToFill()
                                                            default:
                                                                Color.white.opacity(0.08)
                                                            }
                                                        }
                                                        .frame(width: 140, height: 100)
                                                        .clipped()
                                                        .cornerRadius(8)
                                                    } else {
                                                        ZStack {
                                                            Color.white.opacity(0.08)
                                                            Image(systemName: "gamecontroller")
                                                                .foregroundColor(.white.opacity(0.4))
                                                        }
                                                        .frame(width: 140, height: 100)
                                                        .cornerRadius(8)
                                                    }
                                                    Text(rel.title)
                                                        .font(.caption.weight(.semibold))
                                                        .foregroundColor(.white)
                                                        .lineLimit(2)
                                                        .multilineTextAlignment(.leading)
                                                        .frame(width: 140, alignment: .leading)
                                                }
                                            }
                                        }
                                    }
                                    .padding(.horizontal, 4)
                                }
                            }
                            .padding(.bottom, 8)
                        }

                        ForEach(games) { game in
                            GameBrowseCard(
                                game: game,
                                isDownloading: false,
                                isDeleting: deletingIds.contains(game.id),
                                isOwner: game.creatorId == currentUserId,
                                onPlay: { openGame(game) },
                                onEdit: { tweakGame = game },
                                onDelete: game.creatorId == currentUserId ? { deleteGame(game) } : nil,
                                onShare: {
                                    let gameUrl = "https://puzzleverseai.com/api/game-creation/\(game.id)"
                                    let shareText = "Let's play \(game.title) on RiddleVerse! \(gameUrl)"

                                    // Try Telegram deep link first
                                    if let encoded = shareText.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed),
                                       let tgUrl = URL(string: "tg://msg?text=\(encoded)"),
                                       UIApplication.shared.canOpenURL(tgUrl) {
                                        UIApplication.shared.open(tgUrl)
                                    } else {
                                        // Fallback: iOS share sheet
                                        let activityVC = UIActivityViewController(activityItems: [shareText], applicationActivities: nil)
                                        if let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
                                           let rootVC = windowScene.windows.first?.rootViewController {
                                            rootVC.present(activityVC, animated: true)
                                        }
                                    }
                                }
                            )
                        }

                        // Load more button
                        if hasMore {
                            if isLoadingMore {
                                ProgressView()
                                    .tint(.white)
                                    .padding(.vertical, 12)
                            } else {
                                Button(action: { fetchGames(loadMore: true) }) {
                                    HStack(spacing: 6) {
                                        Image(systemName: "chevron.down")
                                            .font(.caption)
                                        Text("Load More")
                                            .font(.subheadline.weight(.medium))
                                    }
                                    .foregroundColor(.orange)
                                    .padding(.horizontal, 20)
                                    .padding(.vertical, 10)
                                    .overlay(
                                        Capsule()
                                            .stroke(Color.orange.opacity(0.3), lineWidth: 1)
                                    )
                                }
                                .padding(.vertical, 8)
                            }
                        }
                    }
                    .padding(.horizontal)
                    .padding(.bottom, 40)
                }
            }

            if let error = errorMessage {
                HStack {
                    Image(systemName: "exclamationmark.triangle")
                        .foregroundColor(.red)
                    Text(error)
                        .font(.caption)
                        .foregroundColor(.white.opacity(0.7))
                }
                .padding(12)
                .background(RoundedRectangle(cornerRadius: 8).fill(Color.red.opacity(0.15)))
                .padding(.horizontal)
                .padding(.bottom, 8)
            }
        }
        .onAppear {
            fetchGames()
            fetchNewReleases()
            coinManager.fetchBalance()
        }
        .onChange(of: sortBy) { _ in fetchGames() }
        .onChange(of: showMyGames) { _ in fetchGames() }
        .fullScreenCover(item: $webViewURL) { url in
            GameURLWebView(url: url, sessionId: currentSessionId)
        }
        .fullScreenCover(item: $tweakGame) { game in
            GameTweakView(game: game)
        }
        .sheet(item: $coinGateGame) { game in
            CoinGateSheet(
                game: game,
                coinManager: coinManager,
                freePlaysUsed: freePlaysPerGame,
                isSpending: $isCoinGateSpending,
                onPlay: { g in
                    let userId = Auth.auth().currentUser?.uid ?? ""
                    let urlString = "https://puzzleverseai.com/api/game-creation/\(g.id)?platform=app&userId=\(userId)"
                    if let url = URL(string: urlString) {
                        coinGateGame = nil
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
                            webViewURL = url
                        }
                    }
                },
                onDismiss: { coinGateGame = nil }
            )
        }
    }

    private func fetchGames(loadMore: Bool = false) {
        if loadMore {
            isLoadingMore = true
        } else {
            isLoading = true
            currentOffset = 0
        }
        errorMessage = nil
        let offset = loadMore ? currentOffset : 0

        var urlString = "https://puzzleverseai.com/api/game-creation/browse?sort=\(sortBy)&limit=\(pageSize)&offset=\(offset)"
        if showMyGames, let uid = currentUserId {
            urlString += "&creatorId=\(uid)"
        }
        print("🎮 [GameBrowse] Fetching: \(urlString)")
        guard let url = URL(string: urlString) else { return }

        URLSession.shared.dataTask(with: url) { data, response, error in
            DispatchQueue.main.async {
                isLoading = false
                isLoadingMore = false

                if let error = error {
                    print("❌ [GameBrowse] Network error: \(error.localizedDescription)")
                    errorMessage = error.localizedDescription
                    return
                }

                if let http = response as? HTTPURLResponse {
                    print("📡 [GameBrowse] HTTP \(http.statusCode)")
                }

                if let raw = data, let rawStr = String(data: raw, encoding: .utf8) {
                    print("📦 [GameBrowse] Raw response (\(raw.count) bytes): \(rawStr.prefix(500))")
                }

                guard let data = data,
                      let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
                    print("❌ [GameBrowse] Failed to parse JSON")
                    errorMessage = error?.localizedDescription ?? "Failed to load games"
                    return
                }

                print("📋 [GameBrowse] JSON keys: \(json.keys.sorted())")

                guard let gamesArray = json["games"] as? [[String: Any]] else {
                    print("❌ [GameBrowse] No 'games' key — got: \(json)")
                    errorMessage = "Failed to load games"
                    return
                }

                print("✅ [GameBrowse] Received \(gamesArray.count) games")

                let serverHasMore = json["hasMore"] as? Bool ?? false

                let parsed: [BrowseGame] = gamesArray.compactMap { g in
                    guard let id = g["id"] as? String,
                          let title = g["title"] as? String else {
                        print("⚠️ [GameBrowse] Skipping game missing id/title: \(g)")
                        return nil
                    }
                    return BrowseGame(
                        id: id,
                        title: title,
                        creatorId: g["creator_id"] as? String ?? "",
                        creatorName: g["creator_name"] as? String ?? "Anonymous",
                        playCount: g["play_count"] as? Int ?? 0,
                        initialPrompt: g["description"] as? String ?? "",
                        createdAt: g["created_at"] as? String ?? "",
                        status: g["status"] as? String ?? "published",
                        thumbnailUrl: g["initial_screenshot_url"] as? String,
                        seriesId: g["series_id"] as? String,
                        levelIndex: g["level_index"] as? Int ?? 1,
                        levelCount: g["level_count"] as? Int ?? 1,
                        trendingScore: g["trending_score"] as? Double ?? 0
                    )
                }

                if loadMore {
                    let existingIds = Set(games.map(\.id))
                    let newGames = parsed.filter { !existingIds.contains($0.id) }
                    print("➕ [GameBrowse] Appending \(newGames.count) new games (filtered \(parsed.count - newGames.count) dups)")
                    games += newGames
                } else {
                    var seenIds = Set<String>()
                    var seenTitles = Set<String>()
                    let deduped = parsed.filter { game in
                        let titleKey = "\(game.creatorId)::\(game.title.prefix(50))"
                        guard seenIds.insert(game.id).inserted else { return false }
                        return seenTitles.insert(titleKey).inserted
                    }
                    print("🔍 [GameBrowse] After dedup: \(deduped.count) / \(parsed.count) games shown")
                    games = deduped
                }
                hasMore = serverHasMore
                currentOffset = offset + parsed.count
                print("📊 [GameBrowse] Total displayed: \(games.count), hasMore: \(serverHasMore)")
            }
        }.resume()
    }

    // Feature C: pull a short list of the most recent releases for the carousel
    // above the main grid. Independent of the user's current sort selection.
    private func fetchNewReleases() {
        guard let url = URL(string: "https://puzzleverseai.com/api/game-creation/browse?sort=newest&limit=10&offset=0") else { return }
        URLSession.shared.dataTask(with: url) { data, _, _ in
            DispatchQueue.main.async {
                guard let data = data,
                      let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                      let gamesArray = json["games"] as? [[String: Any]] else { return }
                newReleases = gamesArray.compactMap { g in
                    guard let id = g["id"] as? String, let title = g["title"] as? String else { return nil }
                    return BrowseGame(
                        id: id,
                        title: title,
                        creatorId: g["creator_id"] as? String ?? "",
                        creatorName: g["creator_name"] as? String ?? "Anonymous",
                        playCount: g["play_count"] as? Int ?? 0,
                        initialPrompt: g["description"] as? String ?? "",
                        createdAt: g["created_at"] as? String ?? "",
                        status: g["status"] as? String ?? "published",
                        thumbnailUrl: g["initial_screenshot_url"] as? String,
                        seriesId: g["series_id"] as? String,
                        levelIndex: g["level_index"] as? Int ?? 1,
                        levelCount: g["level_count"] as? Int ?? 1,
                        trendingScore: g["trending_score"] as? Double ?? 0
                    )
                }
            }
        }.resume()
    }

    private func openGame(_ game: BrowseGame) {
        let isOwner = game.creatorId == currentUserId
        let userId = Auth.auth().currentUser?.uid ?? ""

        if !isOwner {
            let key = "plays_\(game.id)"
            let playCount = UserDefaults.standard.integer(forKey: key)
            if playCount >= freePlaysPerGame {
                // Free plays exhausted
                if !coinManager.canContinue {
                    coinGateGame = game
                    return
                }
                let creatorId = game.creatorId.isEmpty ? nil : game.creatorId
                isCoinGateSpending = true
                coinManager.spendForContinue(gameId: game.id, creatorId: creatorId) { success in
                    DispatchQueue.main.async {
                        isCoinGateSpending = false
                        if success {
                            Task { await self.launchWithSession(gameId: game.id, userId: userId) }
                        } else {
                            coinGateGame = game
                        }
                    }
                }
                return
            }
            UserDefaults.standard.set(playCount + 1, forKey: key)
        }

        Task { await launchWithSession(gameId: game.id, userId: userId) }
    }

    @MainActor
    private func launchWithSession(gameId: String, userId: String) async {
        // Start session natively — game opens directly in WKWebView, no web wrapper needed
        let sessionId = await GameSessionManager.shared.startSession(
            gameId: gameId,
            userId: userId,
            onTimeout: { /* session already ended by manager; WKWebView shows result */ }
        )
        // platform=app — backend injects the score/completion script the WKWebView
        // coordinator's native bridge depends on. platform=ios was intended for a
        // future fully-native bridge but that work is incomplete, so games loaded
        // with platform=ios render but never report scores or game-over events.
        let gameUrlString = "https://puzzleverseai.com/api/game-creation/\(gameId)?platform=app&userId=\(userId)"
        guard let url = URL(string: gameUrlString) else { return }
        // Store sessionId alongside URL so GameURLWebView can pass it to coordinator
        currentSessionId = sessionId
        webViewURL = url
    }

    private func deleteGame(_ game: BrowseGame) {
        // Optimistic removal
        games.removeAll { $0.id == game.id }
        deletingIds.insert(game.id)
        Task {
            defer { Task { @MainActor in deletingIds.remove(game.id) } }
            do {
                guard let userId = Auth.auth().currentUser?.uid else { return }
                guard let url = URL(string: "https://puzzleverseai.com/api/game-creation/\(game.id)?userId=\(userId)") else { return }
                var request = URLRequest(url: url)
                request.httpMethod = "DELETE"
                request.timeoutInterval = 15
                let (_, response) = try await URLSession.shared.data(for: request)
                if let http = response as? HTTPURLResponse, http.statusCode >= 400 {
                    await MainActor.run {
                        games.append(game)
                        games.sort { $0.createdAt > $1.createdAt }
                        errorMessage = "Failed to delete game"
                    }
                }
            } catch {
                await MainActor.run {
                    games.append(game)
                    games.sort { $0.createdAt > $1.createdAt }
                    errorMessage = "Failed to delete game"
                }
            }
        }
    }
}

// MARK: - Game Card

struct GameBrowseCard: View {
    let game: BrowseGame
    let isDownloading: Bool
    var isDeleting: Bool = false
    var isOwner: Bool = false
    let onPlay: () -> Void
    var onEdit: (() -> Void)?
    var onDelete: (() -> Void)?
    var onShare: (() -> Void)?

    @State private var promptExpanded = false
    @State private var leaderboardExpanded = false
    @State private var leaderboardEntries: [GameScoreEntry] = []
    @State private var leaderboardLoading = false
    private var currentUserId: String? { Auth.auth().currentUser?.uid }

    private var shouldShowDescription: Bool {
        let d = game.initialPrompt.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !d.isEmpty else { return false }
        let t = game.title
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .trimmingCharacters(in: CharacterSet(charactersIn: "…."))
            .trimmingCharacters(in: .whitespaces)
        if t.isEmpty { return true }
        let dl = d.lowercased(); let tl = t.lowercased()
        return !(dl == tl || dl.hasPrefix(tl))
    }

    private var timeAgo: String {
        guard !game.createdAt.isEmpty else { return "" }
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        guard let date = formatter.date(from: game.createdAt) else {
            formatter.formatOptions = [.withInternetDateTime]
            guard let date2 = formatter.date(from: game.createdAt) else { return "" }
            return relativeTime(from: date2)
        }
        return relativeTime(from: date)
    }

    private func relativeTime(from date: Date) -> String {
        let seconds = Int(-date.timeIntervalSinceNow)
        if seconds < 60 { return "just now" }
        if seconds < 3600 { return "\(seconds / 60)m ago" }
        if seconds < 86400 { return "\(seconds / 3600)h ago" }
        return "\(seconds / 86400)d ago"
    }

    @ViewBuilder
    private var seriesBadge: some View {
        if game.levelCount > 1 {
            HStack(spacing: 4) {
                Image(systemName: "square.stack.3d.up.fill")
                    .font(.system(size: 10, weight: .bold))
                Text("\(game.levelCount) levels")
                    .font(.caption2.weight(.bold))
            }
            .foregroundColor(.white)
            .padding(.horizontal, 8)
            .padding(.vertical, 4)
            .background(Capsule().fill(Color.black.opacity(0.65)))
            .padding(8)
        }
    }

    @ViewBuilder
    private var thumbnailSection: some View {
        if let thumbUrl = game.thumbnailUrl, let url = URL(string: thumbUrl) {
            AsyncImage(url: url) { phase in
                switch phase {
                case .success(let image):
                    image
                        .resizable()
                        .scaledToFill()
                        .frame(maxWidth: .infinity)
                        .frame(height: 180)
                        .clipped()
                        .overlay(
                            ZStack {
                                Color.black.opacity(0.2)
                                Image(systemName: "play.circle.fill")
                                    .font(.system(size: 48))
                                    .foregroundColor(.white.opacity(0.8))
                                VStack {
                                    HStack {
                                        Spacer()
                                        seriesBadge
                                    }
                                    Spacer()
                                }
                            }
                        )
                        .onTapGesture { onPlay() }
                default:
                    EmptyView()
                }
            }
            .clipShape(UnevenRoundedRectangle(topLeadingRadius: 16, bottomLeadingRadius: 0, bottomTrailingRadius: 0, topTrailingRadius: 16))
        }
    }

    private func loadLeaderboard() {
        guard !leaderboardLoading else { return }
        leaderboardLoading = true
        let urlString = "https://puzzleverseai.com/api/chat-scores/\(game.id)/_app_?platform=app"
        guard let url = URL(string: urlString) else { leaderboardLoading = false; return }
        URLSession.shared.dataTask(with: url) { data, _, _ in
            DispatchQueue.main.async {
                leaderboardLoading = false
                guard let data = data,
                      let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                      let scores = json["scores"] as? [[String: Any]] else { return }
                leaderboardEntries = scores.compactMap { s in
                    guard let username = s["username"] as? String,
                          let score = s["score"] as? Int else { return nil }
                    return GameScoreEntry(username: username, score: score, userId: s["userId"] as? String ?? "")
                }
            }
        }.resume()
    }

    @ViewBuilder
    private var leaderboardSection: some View {
        VStack(spacing: 0) {
            Divider().background(Color.white.opacity(0.08))
            Button(action: {
                leaderboardExpanded.toggle()
                if leaderboardExpanded && leaderboardEntries.isEmpty { loadLeaderboard() }
            }) {
                HStack {
                    Text("🏆 Leaderboard")
                        .font(.caption.weight(.semibold))
                        .foregroundColor(Color(red: 1.0, green: 0.84, blue: 0.0))
                    Spacer()
                    Image(systemName: leaderboardExpanded ? "chevron.up" : "chevron.down")
                        .font(.caption2)
                        .foregroundColor(.white.opacity(0.4))
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 10)
            }
            if leaderboardExpanded {
                VStack(spacing: 4) {
                    if leaderboardLoading {
                        ProgressView().tint(.orange).scaleEffect(0.7).padding(.vertical, 4)
                    } else if leaderboardEntries.isEmpty {
                        Text("No scores yet — be the first!")
                            .font(.caption)
                            .foregroundColor(.white.opacity(0.4))
                            .padding(.bottom, 8)
                    } else {
                        let medals = ["🥇", "🥈", "🥉"]
                        ForEach(Array(leaderboardEntries.prefix(5).enumerated()), id: \.offset) { i, entry in
                            let isMe = entry.userId == currentUserId
                            HStack(spacing: 6) {
                                Text(i < medals.count ? medals[i] : "\(i + 1).")
                                    .font(.system(size: 13))
                                    .frame(width: 22, alignment: .leading)
                                Text(entry.username)
                                    .font(.caption)
                                    .foregroundColor(isMe ? .orange : .white.opacity(0.85))
                                    .fontWeight(isMe ? .bold : .regular)
                                    .lineLimit(1)
                                Spacer()
                                Text("\(entry.score)")
                                    .font(.caption.weight(.bold))
                                    .foregroundColor(Color(red: 1.0, green: 0.84, blue: 0.0))
                            }
                            .padding(.horizontal, 16)
                        }
                        .padding(.bottom, 10)
                    }
                }
            }
        }
    }

    @ViewBuilder
    private var actionButtons: some View {
        HStack(spacing: 8) {
            // Play button only shown when no thumbnail (thumbnail itself is tappable)
            if game.thumbnailUrl == nil {
                Button(action: onPlay) {
                    HStack(spacing: 6) {
                        if isDownloading {
                            ProgressView().progressViewStyle(CircularProgressViewStyle(tint: .white)).scaleEffect(0.7)
                            Text("Loading...")
                        } else {
                            Image(systemName: "play.circle.fill")
                            Text("Play")
                        }
                    }
                    .font(.subheadline.weight(.semibold))
                    .foregroundColor(.white)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 10)
                    .background(LinearGradient(colors: [Color.orange, Color(red: 0.9, green: 0.4, blue: 0.1)], startPoint: .leading, endPoint: .trailing))
                    .cornerRadius(10)
                }
                .disabled(isDownloading)
            }

            Spacer()

            // Right-side icon-only controls
            if isOwner, let onEdit = onEdit {
                Button(action: onEdit) {
                    Image(systemName: "wand.and.stars")
                        .font(.subheadline.weight(.semibold))
                        .foregroundColor(Color.purple.opacity(0.9))
                        .frame(width: 40, height: 40)
                        .background(Color.purple.opacity(0.15))
                        .cornerRadius(10)
                }
            }
            if isOwner, let onDelete = onDelete {
                Button(action: onDelete) {
                    Group {
                        if isDeleting {
                            ProgressView().progressViewStyle(CircularProgressViewStyle(tint: Color(red: 0.9, green: 0.2, blue: 0.2))).scaleEffect(0.7)
                        } else {
                            Image(systemName: "trash")
                                .font(.subheadline.weight(.semibold))
                                .foregroundColor(Color(red: 0.9, green: 0.2, blue: 0.2))
                        }
                    }
                    .frame(width: 40, height: 40)
                    .background(Color(red: 0.9, green: 0.2, blue: 0.2).opacity(0.12))
                    .cornerRadius(10)
                }
                .disabled(isDeleting)
            }
            if let onShare = onShare {
                Button(action: onShare) {
                    Image(systemName: "square.and.arrow.up")
                        .font(.subheadline.weight(.semibold))
                        .foregroundColor(Color(red: 0.0, green: 0.53, blue: 0.8))
                        .frame(width: 40, height: 40)
                        .background(Color(red: 0.0, green: 0.53, blue: 0.8).opacity(0.12))
                        .cornerRadius(10)
                }
            }
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            thumbnailSection
            VStack(alignment: .leading, spacing: 12) {
                HStack(alignment: .top) {
                    Image(systemName: "gamecontroller.fill")
                        .font(.title2)
                        .foregroundColor(.orange)
                        .frame(width: 44, height: 44)
                        .background(Color.orange.opacity(0.15))
                        .cornerRadius(10)
                    VStack(alignment: .leading, spacing: 4) {
                        HStack(spacing: 6) {
                            Text(game.title).font(.headline).foregroundColor(.white).lineLimit(1)
                            if game.status == "draft" {
                                Text("Draft")
                                    .font(.system(size: 10, weight: .semibold))
                                    .foregroundColor(Color(red: 1.0, green: 0.42, blue: 0.42))
                                    .padding(.horizontal, 6).padding(.vertical, 2)
                                    .background(RoundedRectangle(cornerRadius: 4).fill(Color(red: 1.0, green: 0.42, blue: 0.42).opacity(0.2)))
                            }
                        }
                        HStack(spacing: 8) {
                            Text("by \(game.creatorName)").font(.caption).foregroundColor(.white.opacity(0.5))
                            if !timeAgo.isEmpty {
                                Text(timeAgo).font(.caption2).foregroundColor(.white.opacity(0.3))
                            }
                        }
                    }
                    Spacer()
                    HStack(spacing: 4) {
                        Image(systemName: "play.fill").font(.system(size: 9))
                        Text("\(game.playCount)").font(.caption2.weight(.medium))
                    }
                    .foregroundColor(.white.opacity(0.5))
                    .padding(.horizontal, 8).padding(.vertical, 4)
                    .background(Capsule().fill(Color.white.opacity(0.08)))
                }
                // Hide description if it duplicates the title (server falls back to
                // title=prompt.substring(0,100), description=prompt.substring(0,500), so
                // pre-backfill rows have title as a prefix of description).
                if shouldShowDescription {
                    Text(game.initialPrompt)
                        .font(.caption)
                        .foregroundColor(.white.opacity(0.4))
                        .lineLimit(promptExpanded ? nil : 2)
                        .onTapGesture { promptExpanded.toggle() }
                }
                actionButtons
            }
            .padding(16)
            leaderboardSection
            EditsSection(gameId: game.id)
        }
        .background(RoundedRectangle(cornerRadius: 16).fill(Color.white.opacity(0.06)))
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .overlay(RoundedRectangle(cornerRadius: 16).stroke(Color.white.opacity(0.1), lineWidth: 1))
    }
}

// MARK: - Edits Section (expandable tweak history)

struct GameEditVersion: Identifiable {
    let id = UUID()
    let sha: String
    let shortSha: String
    let message: String
    let date: String
    let author: String
    var isInitial: Bool { message == "Initial game creation" }
    var displayMessage: String {
        message.hasPrefix("Tweak: ") ? String(message.dropFirst(7)) : message
    }
}

struct EditsSection: View {
    let gameId: String
    @State private var expanded = false
    @State private var versions: [GameEditVersion] = []
    @State private var loading = false
    @State private var fetched = false

    var body: some View {
        VStack(spacing: 0) {
            Divider().background(Color.white.opacity(0.08))
            Button(action: {
                expanded.toggle()
                if expanded && !fetched { load() }
            }) {
                HStack {
                    HStack(spacing: 6) {
                        Text("✨ Edits")
                            .font(.caption.weight(.semibold))
                            .foregroundColor(Color(red: 0.81, green: 0.58, blue: 0.85))
                        if fetched && !versions.isEmpty {
                            let count = versions.filter { !$0.isInitial }.count
                            if count > 0 {
                                Text("(\(count))")
                                    .font(.caption2)
                                    .foregroundColor(.white.opacity(0.4))
                            }
                        }
                    }
                    Spacer()
                    Image(systemName: expanded ? "chevron.up" : "chevron.down")
                        .font(.caption2)
                        .foregroundColor(.white.opacity(0.4))
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 10)
            }
            if expanded {
                VStack(alignment: .leading, spacing: 0) {
                    if loading {
                        ProgressView()
                            .tint(Color(red: 0.81, green: 0.58, blue: 0.85))
                            .scaleEffect(0.7)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 6)
                    } else if versions.isEmpty {
                        Text("No edits yet")
                            .font(.caption)
                            .foregroundColor(.white.opacity(0.4))
                            .padding(.horizontal, 16)
                            .padding(.bottom, 10)
                    } else {
                        ForEach(versions) { v in
                            HStack(alignment: .top, spacing: 10) {
                                Circle()
                                    .fill(v.isInitial ? Color(red: 0.4, green: 0.74, blue: 0.41) : Color(red: 0.81, green: 0.58, blue: 0.85))
                                    .frame(width: 8, height: 8)
                                    .padding(.top, 5)
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(v.displayMessage)
                                        .font(.caption)
                                        .foregroundColor(.white.opacity(0.85))
                                        .lineLimit(2)
                                    HStack(spacing: 8) {
                                        if !v.shortSha.isEmpty {
                                            Text(v.shortSha)
                                                .font(.caption2.monospaced())
                                                .foregroundColor(.white.opacity(0.3))
                                        }
                                        let ago = relativeTime(from: v.date)
                                        if !ago.isEmpty {
                                            Text(ago)
                                                .font(.caption2)
                                                .foregroundColor(.white.opacity(0.3))
                                        }
                                        if v.isInitial {
                                            Text("Original")
                                                .font(.caption2.weight(.medium))
                                                .foregroundColor(Color(red: 0.4, green: 0.74, blue: 0.41))
                                                .padding(.horizontal, 6)
                                                .padding(.vertical, 2)
                                                .background(Color(red: 0.4, green: 0.74, blue: 0.41).opacity(0.15))
                                                .cornerRadius(4)
                                        }
                                    }
                                }
                                Spacer()
                            }
                            .padding(.horizontal, 16)
                            .padding(.vertical, 4)
                        }
                        Spacer().frame(height: 6)
                    }
                }
            }
        }
    }

    private func load() {
        guard !loading else { return }
        loading = true
        guard let url = URL(string: "https://puzzleverseai.com/api/game-creation/\(gameId)/versions") else {
            loading = false; fetched = true; return
        }
        URLSession.shared.dataTask(with: url) { data, _, _ in
            DispatchQueue.main.async {
                loading = false
                fetched = true
                guard let data = data,
                      let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                      let arr = json["versions"] as? [[String: Any]] else { return }
                versions = arr.compactMap { v in
                    guard let sha = v["sha"] as? String,
                          let message = v["message"] as? String else { return nil }
                    return GameEditVersion(
                        sha: sha,
                        shortSha: v["shortSha"] as? String ?? String(sha.prefix(7)),
                        message: message,
                        date: v["date"] as? String ?? "",
                        author: v["author"] as? String ?? ""
                    )
                }
            }
        }.resume()
    }

    private func relativeTime(from iso: String) -> String {
        guard !iso.isEmpty else { return "" }
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        var date = formatter.date(from: iso)
        if date == nil {
            formatter.formatOptions = [.withInternetDateTime]
            date = formatter.date(from: iso)
        }
        guard let date else { return "" }
        let seconds = Int(-date.timeIntervalSinceNow)
        if seconds < 60 { return "just now" }
        if seconds < 3600 { return "\(seconds / 60)m ago" }
        if seconds < 86400 { return "\(seconds / 3600)h ago" }
        return "\(seconds / 86400)d ago"
    }
}

// MARK: - Coin Gate Sheet

struct CoinGateSheet: View {
    let game: BrowseGame
    @ObservedObject var coinManager: CoinManager
    let freePlaysUsed: Int
    @Binding var isSpending: Bool
    let onPlay: (BrowseGame) -> Void
    let onDismiss: () -> Void

    var body: some View {
        ZStack {
            Color(red: 0.1, green: 0.1, blue: 0.18).ignoresSafeArea()
            VStack(spacing: 20) {
                Text("🎮")
                    .font(.system(size: 48))
                    .padding(.top, 32)
                Text("Free Plays Used Up")
                    .font(.title2.weight(.bold))
                    .foregroundColor(.white)
                Text("You've used your \(freePlaysUsed) free plays for \"\(game.title)\".\nSpend coins to keep playing!")
                    .font(.subheadline)
                    .foregroundColor(.white.opacity(0.7))
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 24)

                // Coin balance
                HStack(spacing: 6) {
                    Image(systemName: "star.fill")
                        .foregroundColor(.yellow)
                        .font(.subheadline)
                    Text("\(coinManager.balance) coins")
                        .font(.subheadline.weight(.semibold))
                        .foregroundColor(.yellow)
                }
                .padding(.horizontal, 20)
                .padding(.vertical, 8)
                .background(Capsule().fill(Color.yellow.opacity(0.12)))

                VStack(spacing: 12) {
                    Button(action: {
                        guard coinManager.canContinue, !isSpending else { return }
                        isSpending = true
                        let creatorId = game.creatorId.isEmpty ? nil : game.creatorId
                        coinManager.spendForContinue(gameId: game.id, creatorId: creatorId) { success in
                            DispatchQueue.main.async {
                                isSpending = false
                                if success { onPlay(game) }
                            }
                        }
                    }) {
                        HStack {
                            if isSpending {
                                ProgressView().progressViewStyle(CircularProgressViewStyle(tint: .white)).scaleEffect(0.8)
                            } else {
                                Text("🔄  Play — 🪙 \(CoinManager.continueCost) coins")
                                    .font(.subheadline.weight(.bold))
                                    .foregroundColor(.white)
                            }
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                        .background(RoundedRectangle(cornerRadius: 14)
                            .fill(coinManager.canContinue ? Color(red: 0.42, green: 0.39, blue: 1.0) : Color.gray.opacity(0.4)))
                    }
                    .disabled(!coinManager.canContinue || isSpending)

                    if !coinManager.canContinue {
                        Text("Not enough coins — need \(CoinManager.continueCost)")
                            .font(.caption)
                            .foregroundColor(Color(red: 1.0, green: 0.42, blue: 0.42))
                    }

                    Button(action: onDismiss) {
                        Text("Maybe Later")
                            .font(.subheadline)
                            .foregroundColor(.white.opacity(0.5))
                    }
                    .padding(.bottom, 32)
                }
                .padding(.horizontal, 24)
            }
        }
    }
}

// MARK: - WebView for in-app game play

import WebKit

extension URL: @retroactive Identifiable {
    public var id: String { absoluteString }
}

struct GameURLWebView: View {
    let url: URL
    let sessionId: String?
    @Environment(\.dismiss) private var dismiss
    @State private var showResult = false
    @State private var resultScore: Int? = nil
    @State private var resultDuration: Int = 0
    @State private var resultBillable: Bool = false

    init(url: URL, sessionId: String? = nil) {
        self.url = url
        self.sessionId = sessionId
    }

    var body: some View {
        ZStack(alignment: .topLeading) {
            GameWKWebView(
                url: url,
                sessionId: sessionId,
                onGameOver: { score, duration, billable in
                    resultScore = score
                    resultDuration = duration
                    resultBillable = billable
                    showResult = true
                },
                onClose: { dismiss() }
            )
            .ignoresSafeArea()

            // Floating exit button — required because the game HTML
            // is served clean (no leaderboard widget with close), and
            // .fullScreenCover has no swipe-to-dismiss gesture.
            if !showResult {
                Button(action: {
                    Task {
                        if let sid = sessionId {
                            await GameSessionManager.shared.endSession(
                                sessionId: sid, score: nil, forcedBy: "user_exit"
                            )
                        }
                        dismiss()
                    }
                }) {
                    Image(systemName: "xmark")
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundColor(.white)
                        .frame(width: 36, height: 36)
                        .background(Color.black.opacity(0.55))
                        .clipShape(Circle())
                        .overlay(Circle().stroke(Color.white.opacity(0.25), lineWidth: 1))
                }
                .padding(.leading, 16)
                .padding(.top, 12)
            }

            // Native result overlay — appears when session ends
            if showResult {
                GameResultOverlay(
                    score: resultScore,
                    duration: resultDuration,
                    billable: resultBillable,
                    onDismiss: { dismiss() }
                )
            }
        }
    }
}

struct GameResultOverlay: View {
    let score: Int?
    let duration: Int
    let billable: Bool
    let onDismiss: () -> Void

    var body: some View {
        ZStack {
            Color.black.opacity(0.85).ignoresSafeArea()
            VStack(spacing: 16) {
                Text(billable ? "🏆" : "👋").font(.system(size: 56))
                Text(billable ? "Game Complete!" : "Too Short")
                    .font(.title2).bold().foregroundColor(.white)
                if let s = score, s > 0 {
                    Text("Score: \(s)")
                        .font(.title3).foregroundColor(.white)
                }
                Text("Time: \(duration / 60):\(String(format: "%02d", duration % 60))")
                    .font(.subheadline).foregroundColor(.gray)
                Button(action: onDismiss) {
                    Text("Done")
                        .font(.headline).foregroundColor(.white)
                        .frame(maxWidth: .infinity).padding()
                        .background(Color(red: 0.13, green: 0.75, blue: 0.39))
                        .cornerRadius(14)
                }
                .padding(.horizontal, 40).padding(.top, 8)
            }
            .padding(32)
            .background(Color(white: 0.1)).cornerRadius(20)
            .padding(40)
        }
    }
}

struct GameWKWebView: UIViewRepresentable {
    let url: URL
    let sessionId: String?
    var onGameOver: ((Int?, Int, Bool) -> Void)? = nil  // score, duration, billable
    var onClose: (() -> Void)? = nil

    class Coordinator: NSObject, WKUIDelegate, WKNavigationDelegate, WKScriptMessageHandler {
        var sessionId: String?
        var onGameOver: ((Int?, Int, Bool) -> Void)?
        var onClose: (() -> Void)?
        private var ended = false

        func finish(score: Int?, forcedBy: String) {
            guard !ended else { return }
            ended = true
            guard let sid = sessionId else {
                DispatchQueue.main.async { self.onClose?() }
                return
            }
            Task {
                let result = await GameSessionManager.shared.endSession(
                    sessionId: sid, score: score, forcedBy: forcedBy
                )
                let finalScore = result?["score"] as? Int
                let duration = result?["duration_seconds"] as? Int ?? 0
                let billable = result?["is_billable"] as? Bool ?? false
                DispatchQueue.main.async {
                    self.onGameOver?(finalScore, duration, billable)
                }
            }
        }

        // Native WKScriptMessageHandler — receives gameHandler, gameScore, and gameLog messages
        func userContentController(_ userContentController: WKUserContentController,
                                   didReceive message: WKScriptMessage) {
            // gameLog: forward JS console / errors to Xcode console
            if message.name == "gameLog" {
                if let body = message.body as? String,
                   let data = body.data(using: .utf8),
                   let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
                    let level = json["l"] as? String ?? "log"
                    let msg = json["m"] as? String ?? ""
                    print("[GameJS \(level)] \(msg)")
                }
                return
            }

            guard let body = message.body as? String,
                  let data = body.data(using: .utf8),
                  let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any]
            else { return }

            let type = json["type"] as? String ?? ""
            let score = json["score"] as? Int

            if type == "gameOver" || type == "game_over" {
                finish(score: score, forcedBy: "user_button")
            } else if type == "score", let s = score {
                GameSessionManager.shared.reportScore(s)
            }
        }

        // gameScore handler (some games use this instead of gameHandler)
        // Both are registered, this unified handler covers both
        func webViewDidClose(_ webView: WKWebView) {
            DispatchQueue.main.async { self.onClose?() }
        }

        func webView(_ webView: WKWebView,
                     decidePolicyFor navigationAction: WKNavigationAction,
                     decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
            if navigationAction.request.url?.scheme == "riddleverse" {
                DispatchQueue.main.async { self.onClose?() }
                decisionHandler(.cancel)
                return
            }
            decisionHandler(.allow)
        }
    }

    func makeCoordinator() -> Coordinator {
        let c = Coordinator()
        c.sessionId = sessionId
        c.onGameOver = onGameOver
        c.onClose = onClose
        return c
    }

    func makeUIView(context: Context) -> WKWebView {
        let config = WKWebViewConfiguration()
        config.allowsInlineMediaPlayback = true
        config.mediaTypesRequiringUserActionForPlayback = []

        // Register native message handlers — these intercept window.webkit.messageHandlers.*
        config.userContentController.add(context.coordinator, name: "gameHandler")
        config.userContentController.add(context.coordinator, name: "gameScore")
        config.userContentController.add(context.coordinator, name: "gameLog")

        // 1) CSS shim: AI-generated games frequently set `touch-action: none` on
        // body, which on iOS can suppress click event synthesis for in-game
        // buttons (Play / Play Again / Next Level). Force buttons to use
        // `manipulation` so taps reliably fire clicks regardless.
        let buttonShimCSS = """
        var s=document.createElement('style');
        s.textContent='button,[role=button]{touch-action:manipulation !important;cursor:pointer}';
        (document.head||document.documentElement).appendChild(s);
        """
        config.userContentController.addUserScript(WKUserScript(
            source: buttonShimCSS,
            injectionTime: .atDocumentEnd,
            forMainFrameOnly: true
        ))

        // 2) JS console / error bridge so we can see what's happening in the
        // game JS from the Xcode console. Forwards window.onerror,
        // unhandledrejection, and console.{log,warn,error} via gameLog.
        let logBridge = """
        (function(){
            function post(level, args){
                try {
                    window.webkit.messageHandlers.gameLog.postMessage(
                        JSON.stringify({l: level, m: Array.from(args).map(a => {
                            try { return typeof a === 'object' ? JSON.stringify(a) : String(a); }
                            catch(e) { return String(a); }
                        }).join(' ')})
                    );
                } catch(e) {}
            }
            ['log','warn','error','info'].forEach(function(k){
                var orig = console[k];
                console[k] = function(){ post(k, arguments); if (orig) orig.apply(console, arguments); };
            });
            window.addEventListener('error', function(e){
                post('jserror', [e.message + ' @ ' + (e.filename||'?') + ':' + (e.lineno||0)]);
            });
            window.addEventListener('unhandledrejection', function(e){
                post('jserror', ['unhandled rejection: ' + (e.reason && e.reason.message ? e.reason.message : String(e.reason))]);
            });
        })();
        """
        config.userContentController.addUserScript(WKUserScript(
            source: logBridge,
            injectionTime: .atDocumentStart,
            forMainFrameOnly: true
        ))

        let webView = WKWebView(frame: .zero, configuration: config)
        webView.backgroundColor = UIColor(red: 0.1, green: 0.1, blue: 0.18, alpha: 1)
        webView.isOpaque = false
        webView.uiDelegate = context.coordinator
        webView.navigationDelegate = context.coordinator
        webView.load(URLRequest(url: url))
        return webView
    }

    func updateUIView(_ uiView: WKWebView, context: Context) {}
}
