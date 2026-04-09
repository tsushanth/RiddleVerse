import SwiftUI
import FirebaseAuth

struct GamePlayItem: Identifiable {
    let id: String
    let game: BrowseGame
    let bundleDir: URL
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
}

struct GameBrowseView: View {
    private let pageSize = 20
    @State private var games: [BrowseGame] = []
    @State private var isLoading = true
    @State private var isLoadingMore = false
    @State private var sortBy = "newest"
    @State private var showMyGames = false
    @State private var hasMore = false
    @State private var currentOffset = 0
    @State private var isDownloading = false
    @State private var downloadingId: String?
    @State private var activeGamePlay: GamePlayItem?
    @State private var tweakGame: BrowseGame?
    @State private var errorMessage: String?
    @State private var deletingIds: Set<String> = []
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
                        Button(action: { sortBy = "newest" }) {
                            Label("Newest", systemImage: sortBy == "newest" ? "checkmark" : "")
                        }
                        Button(action: { sortBy = "popular" }) {
                            Label("Most Played", systemImage: sortBy == "popular" ? "checkmark" : "")
                        }
                    } label: {
                        HStack(spacing: 4) {
                            Image(systemName: "arrow.up.arrow.down")
                            Text(sortBy == "newest" ? "Newest" : "Popular")
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
                        ForEach(games) { game in
                            GameBrowseCard(
                                game: game,
                                isDownloading: downloadingId == game.id,
                                isDeleting: deletingIds.contains(game.id),
                                isOwner: game.creatorId == currentUserId,
                                onPlay: { downloadAndPlay(game) },
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
        .onAppear { fetchGames() }
        .onChange(of: sortBy) { _ in fetchGames() }
        .onChange(of: showMyGames) { _ in fetchGames() }
        .fullScreenCover(item: $activeGamePlay) { item in
            GamePlayView(game: item.game, bundleDirectory: item.bundleDir)
        }
        .fullScreenCover(item: $tweakGame) { game in
            GameTweakView(game: game)
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
        guard let url = URL(string: urlString) else { return }

        URLSession.shared.dataTask(with: url) { data, _, error in
            DispatchQueue.main.async {
                isLoading = false
                isLoadingMore = false

                guard let data = data,
                      let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                      let gamesArray = json["games"] as? [[String: Any]] else {
                    errorMessage = error?.localizedDescription ?? "Failed to load games"
                    return
                }

                let serverHasMore = json["hasMore"] as? Bool ?? false

                let parsed: [BrowseGame] = gamesArray.compactMap { g in
                    guard let id = g["id"] as? String,
                          let title = g["title"] as? String else { return nil }
                    return BrowseGame(
                        id: id,
                        title: title,
                        creatorId: g["creator_id"] as? String ?? "",
                        creatorName: g["creator_name"] as? String ?? "Anonymous",
                        playCount: g["play_count"] as? Int ?? 0,
                        initialPrompt: g["description"] as? String ?? "",
                        createdAt: g["created_at"] as? String ?? "",
                        status: g["status"] as? String ?? "published",
                        thumbnailUrl: g["initial_screenshot_url"] as? String
                    )
                }

                if loadMore {
                    let existingIds = Set(games.map(\.id))
                    games += parsed.filter { !existingIds.contains($0.id) }
                } else {
                    var seenIds = Set<String>()
                    var seenTitles = Set<String>()
                    games = parsed.filter { game in
                        let titleKey = "\(game.creatorId)::\(game.title)"
                        guard seenIds.insert(game.id).inserted else { return false }
                        return seenTitles.insert(titleKey).inserted
                    }
                }
                hasMore = serverHasMore
                currentOffset = offset + parsed.count
            }
        }.resume()
    }

    private func downloadAndPlay(_ game: BrowseGame) {
        guard downloadingId == nil else { return }

        isDownloading = true
        downloadingId = game.id
        errorMessage = nil

        Task {
            do {
                // Fetch game details + bundle from API
                guard let url = URL(string: "https://puzzleverseai.com/api/game-creation/\(game.id)") else {
                    throw URLError(.badURL)
                }

                let (data, _) = try await URLSession.shared.data(from: url)

                guard let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                      let gameData = json["game"] as? [String: Any],
                      let base64Bundle = gameData["bundle"] as? String else {
                    throw NSError(domain: "GameBrowse", code: 0,
                                  userInfo: [NSLocalizedDescriptionKey: "Game bundle not available"])
                }

                let dir = try ZipExtractor.extractBundle(base64: base64Bundle)

                await MainActor.run {
                    isDownloading = false
                    downloadingId = nil
                    activeGamePlay = GamePlayItem(id: game.id, game: game, bundleDir: dir)
                }
            } catch {
                await MainActor.run {
                    errorMessage = "Failed to load game: \(error.localizedDescription)"
                    isDownloading = false
                    downloadingId = nil
                }
            }
        }
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

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            // Thumbnail preview
            if let thumbnailUrl = game.thumbnailUrl, let url = URL(string: thumbnailUrl) {
                Button(action: onPlay) {
                    ZStack {
                        AsyncImage(url: url) { phase in
                            switch phase {
                            case .success(let image):
                                image
                                    .resizable()
                                    .aspectRatio(contentMode: .fill)
                            case .failure:
                                Color.white.opacity(0.08)
                                    .overlay(
                                        Image(systemName: "gamecontroller.fill")
                                            .font(.system(size: 32))
                                            .foregroundColor(.white.opacity(0.2))
                                    )
                            case .empty:
                                Color.white.opacity(0.08)
                                    .overlay(ProgressView().tint(.white.opacity(0.3)))
                            @unknown default:
                                Color.white.opacity(0.08)
                            }
                        }
                        .frame(maxWidth: .infinity, maxHeight: 180)
                        .clipped()

                        // Play overlay
                        Color.black.opacity(0.2)
                        Image(systemName: "play.circle.fill")
                            .font(.system(size: 44))
                            .foregroundColor(.white.opacity(0.85))
                            .shadow(radius: 4)
                    }
                    .frame(maxWidth: .infinity, height: 180)
                    .clipped()
                }
                .buttonStyle(.plain)
                .disabled(isDownloading)
            }

            VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .top) {
                // Game icon (only when no thumbnail)
                if game.thumbnailUrl == nil {
                    Image(systemName: "gamecontroller.fill")
                        .font(.title2)
                        .foregroundColor(.orange)
                        .frame(width: 44, height: 44)
                        .background(Color.orange.opacity(0.15))
                        .cornerRadius(10)
                }

                VStack(alignment: .leading, spacing: 4) {
                    HStack(spacing: 6) {
                        Text(game.title)
                            .font(.headline)
                            .foregroundColor(.white)
                            .lineLimit(1)

                        if game.status == "draft" {
                            Text("Draft")
                                .font(.system(size: 10, weight: .semibold))
                                .foregroundColor(Color(red: 1.0, green: 0.42, blue: 0.42))
                                .padding(.horizontal, 6)
                                .padding(.vertical, 2)
                                .background(
                                    RoundedRectangle(cornerRadius: 4)
                                        .fill(Color(red: 1.0, green: 0.42, blue: 0.42).opacity(0.2))
                                )
                        }
                    }

                    HStack(spacing: 8) {
                        Text("by \(game.creatorName)")
                            .font(.caption)
                            .foregroundColor(.white.opacity(0.5))

                        if !timeAgo.isEmpty {
                            Text(timeAgo)
                                .font(.caption2)
                                .foregroundColor(.white.opacity(0.3))
                        }
                    }
                }

                Spacer()

                // Play count badge
                HStack(spacing: 4) {
                    Image(systemName: "play.fill")
                        .font(.system(size: 9))
                    Text("\(game.playCount)")
                        .font(.caption2.weight(.medium))
                }
                .foregroundColor(.white.opacity(0.5))
                .padding(.horizontal, 8)
                .padding(.vertical, 4)
                .background(Capsule().fill(Color.white.opacity(0.08)))
            }

            // Prompt preview — tap to expand
            if !game.initialPrompt.isEmpty {
                Text(game.initialPrompt)
                    .font(.caption)
                    .foregroundColor(.white.opacity(0.4))
                    .lineLimit(promptExpanded ? nil : 2)
                    .onTapGesture { promptExpanded.toggle() }
            }

            // Action buttons
            HStack(spacing: 8) {
                // Play button
                Button(action: onPlay) {
                    HStack {
                        if isDownloading {
                            ProgressView()
                                .progressViewStyle(CircularProgressViewStyle(tint: .white))
                                .scaleEffect(0.7)
                            Text("Loading...")
                        } else {
                            Image(systemName: "play.circle.fill")
                            Text("Play")
                        }
                    }
                    .font(.subheadline.weight(.semibold))
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 10)
                    .background(
                        LinearGradient(
                            colors: [Color.orange, Color(red: 0.9, green: 0.4, blue: 0.1)],
                            startPoint: .leading,
                            endPoint: .trailing
                        )
                    )
                    .cornerRadius(10)
                }
                .disabled(isDownloading)

                // Edit button (only for game creator)
                if isOwner, let onEdit = onEdit {
                    Button(action: onEdit) {
                        HStack(spacing: 4) {
                            Image(systemName: "wand.and.stars")
                            Text("Edit")
                        }
                        .font(.subheadline.weight(.semibold))
                        .foregroundColor(.white)
                        .padding(.horizontal, 16)
                        .padding(.vertical, 10)
                        .background(
                            LinearGradient(
                                colors: [Color.purple, Color(red: 0.5, green: 0.2, blue: 0.8)],
                                startPoint: .leading,
                                endPoint: .trailing
                            )
                        )
                        .cornerRadius(10)
                    }
                }

                // Share button
                if let onShare = onShare {
                    Button(action: onShare) {
                        Image(systemName: "square.and.arrow.up")
                            .font(.subheadline.weight(.semibold))
                            .foregroundColor(.white)
                            .frame(width: 40, height: 40)
                            .background(Color.white.opacity(0.1))
                            .cornerRadius(10)
                    }
                }

                // Delete button (owner only)
                if isOwner, let onDelete = onDelete {
                    Button(action: onDelete) {
                        Group {
                            if isDeleting {
                                ProgressView()
                                    .progressViewStyle(CircularProgressViewStyle(tint: Color(red: 0.9, green: 0.2, blue: 0.2)))
                                    .scaleEffect(0.7)
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
            }
            }
            .padding(16)
        }
        .background(
            RoundedRectangle(cornerRadius: 16)
                .fill(Color.white.opacity(0.06))
        )
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .stroke(Color.white.opacity(0.1), lineWidth: 1)
        )
    }
}
