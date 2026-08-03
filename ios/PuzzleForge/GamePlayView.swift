import SwiftUI
import RevenueCat
import FirebaseAuth

struct GamePlayView: View {
    @State private var game: BrowseGame   // @State so we can swap to the next level in-place
    let bundleDirectory: URL
    @Environment(\.dismiss) private var dismiss
    @StateObject private var coinManager = CoinManager.shared
    @State private var currentScore = 0
    @State private var gameOver = false
    @State private var showLeaderboard = false
    @State private var showCoinStore = false
    @State private var showReplayGate = false  // shown when user opens a game they've already played
    @State private var showHowToPlay = false
    @State private var leaderboard: [LeaderboardPlayer] = []
    @State private var userRank: Int?
    @State private var isLoadingLeaderboard = false
    @State private var scoreSubmitted = false
    @State private var playStartTime = Date()
    @State private var webViewKey = UUID() // Used to reload WebView on continue
    // Feedback on close
    @State private var showFeedbackDialog = false
    @State private var showFeedbackPrompt = false
    @State private var feedbackText = ""
    @State private var gameErrors: [String] = []
    @State private var isFixing = false
    @State private var fixPhase = ""
    @State private var fixProgress: Double = 0
    // Difficulty escalation
    @State private var currentDifficultyLevel = 1
    @State private var currentBundleDirectory: URL?
    @State private var isGeneratingHarder = false
    @State private var generationPhase = ""
    @State private var generationProgress: Double = 0
    // Next-level (sequential series progression)
    @State private var nextLevelCheckDone = false       // false until /next-level lookup returns
    @State private var nextLevelExistingGameId: String? = nil
    @State private var nextLevelExistingTitle: String? = nil
    @State private var nextLevelIndex: Int = 2          // updated from lookup response
    @State private var isGeneratingNextLevel = false
    @State private var nextLevelPhase = ""
    @State private var nextLevelProgress: Double = 0
    @State private var nextLevelSuggestedTitle: String? = nil
    // Feature C: play_event tracking. Each load gets a fresh event_id which we
    // forward to the leaderboard endpoint on game over so the same row flips
    // from completed=false to completed=true.
    @State private var currentPlayEventId: String? = nil

    init(game: BrowseGame, bundleDirectory: URL) {
        self._game = State(initialValue: game)
        self.bundleDirectory = bundleDirectory
    }

    struct LeaderboardPlayer: Identifiable {
        let id = UUID()
        let rank: Int
        let playerName: String
        let score: Int
        let isCurrentUser: Bool
    }

    var body: some View {
        ZStack {
            Color(red: 0.1, green: 0.1, blue: 0.18)
                .ignoresSafeArea()

            VStack(spacing: 0) {
                // Top bar
                HStack {
                    Button(action: {
                        if !gameErrors.isEmpty || game.playCount < 3 {
                            showFeedbackDialog = true
                        } else {
                            dismiss()
                        }
                    }) {
                        Image(systemName: "xmark")
                            .font(.title3)
                            .foregroundColor(.white)
                            .frame(width: 40, height: 40)
                    }

                    Spacer()

                    VStack(spacing: 2) {
                        HStack(spacing: 6) {
                            Text(game.title)
                                .font(.subheadline.weight(.semibold))
                                .foregroundColor(.white)
                                .lineLimit(1)
                            if currentDifficultyLevel > 1 {
                                Text("Lv.\(currentDifficultyLevel)")
                                    .font(.caption2.weight(.bold))
                                    .foregroundColor(.white)
                                    .padding(.horizontal, 6)
                                    .padding(.vertical, 2)
                                    .background(Color.red.opacity(0.8))
                                    .cornerRadius(6)
                            }
                        }
                        if currentScore > 0 {
                            Text("Score: \(currentScore)")
                                .font(.caption)
                                .foregroundColor(.orange)
                        }
                    }

                    Spacer()

                    // Coin balance + trophy + info
                    HStack(spacing: 4) {
                        CoinBalancePill(balance: coinManager.balance)

                        Button(action: {
                            showLeaderboard = true
                            fetchLeaderboard()
                        }) {
                            Image(systemName: "trophy")
                                .font(.title3)
                                .foregroundColor(.orange)
                                .frame(width: 36, height: 36)
                        }

                        if !game.initialPrompt.isEmpty {
                            Button(action: { showHowToPlay = true }) {
                                Image(systemName: "info.circle")
                                    .font(.title3)
                                    .foregroundColor(.white.opacity(0.6))
                                    .frame(width: 36, height: 36)
                            }
                        }
                    }
                }
                .padding(.horizontal)
                .padding(.top, 8)

                // WebView
                GameWebView(
                    bundleDirectory: currentBundleDirectory ?? bundleDirectory,
                    onScoreReceived: { score in
                        currentScore = score
                    },
                    onGameOver: { finalScore in
                        currentScore = finalScore
                        gameOver = true
                        submitScore(finalScore)
                        UserDefaults.standard.set(true, forKey: "played_\(game.id)")
                        checkNextLevel()
                    },
                    onGameError: { error in
                        if !gameErrors.contains(error) {
                            gameErrors.append(error)
                        }
                    }
                )
                .id(webViewKey)
                .onAppear { startPlayEvent() }
                .onChange(of: webViewKey) { _ in startPlayEvent() }
            }

            // Replay gate — shown when user reopens a game they've already played
            if showReplayGate {
                Color.black.opacity(0.85)
                    .ignoresSafeArea()
                    .onTapGesture {}
                VStack(spacing: 20) {
                    Image(systemName: "gamecontroller.fill")
                        .font(.system(size: 48))
                        .foregroundColor(.yellow)
                    Text("Play Again?")
                        .font(.title.bold())
                        .foregroundColor(.white)
                    Text("You've already played this game.\nSpend \(CoinManager.continueCost) coins to play again.")
                        .font(.subheadline)
                        .foregroundColor(.white.opacity(0.7))
                        .multilineTextAlignment(.center)
                    Button(action: { handlePlayAgain() }) {
                        HStack {
                            Image(systemName: "play.circle.fill")
                            Text("Play Again")
                            Spacer()
                            CoinCostBadge(cost: CoinManager.continueCost, balance: coinManager.balance)
                        }
                        .font(.headline)
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .padding()
                        .background(LinearGradient(colors: [Color(red: 0.2, green: 0.6, blue: 1.0), Color(red: 0.4, green: 0.3, blue: 0.9)], startPoint: .leading, endPoint: .trailing))
                        .cornerRadius(12)
                    }
                    Button(action: { dismiss() }) {
                        Text("Back")
                            .font(.subheadline)
                            .foregroundColor(.white.opacity(0.6))
                    }
                }
                .padding(32)
            }

            // Game over overlay (hidden when generating harder variant or next level)
            if gameOver && !isGeneratingHarder && !isGeneratingNextLevel {
                Color.black.opacity(0.7)
                    .ignoresSafeArea()
                    .onTapGesture {} // Prevent taps from reaching WebView

                VStack(spacing: 20) {
                    Spacer()

                    Text("Game Over!")
                        .font(.largeTitle.weight(.bold))
                        .foregroundColor(.white)

                    Text("\(currentScore)")
                        .font(.system(size: 64, weight: .bold))
                        .foregroundColor(.orange)

                    Text("points")
                        .font(.title3)
                        .foregroundColor(.white.opacity(0.6))

                    if scoreSubmitted {
                        HStack(spacing: 6) {
                            Image(systemName: "checkmark.circle.fill")
                                .foregroundColor(.green)
                            Text("Score submitted!")
                                .foregroundColor(.green)
                        }
                        .font(.caption)
                    }

                    Spacer()

                    VStack(spacing: 12) {
                        // Play Again button (costs coins on replay)
                        Button(action: { handlePlayAgain() }) {
                            HStack {
                                Image(systemName: "play.circle.fill")
                                Text("Play Again")
                                Spacer()
                                CoinCostBadge(cost: CoinManager.continueCost, balance: coinManager.balance)
                            }
                            .font(.headline)
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity)
                            .padding()
                            .background(
                                LinearGradient(
                                    colors: [Color(red: 0.2, green: 0.6, blue: 1.0), Color(red: 0.4, green: 0.3, blue: 0.9)],
                                    startPoint: .leading,
                                    endPoint: .trailing
                                )
                            )
                            .cornerRadius(12)
                        }

                        // Harder Challenge button
                        if currentDifficultyLevel < CoinManager.maxDifficultyLevel {
                            let nextLevel = currentDifficultyLevel + 1
                            let cost = CoinManager.difficultyCost(nextLevel)
                            let label = nextLevel == 2 ? "Try Harder Version" :
                                        nextLevel == 3 ? "Try Hard Version" :
                                        nextLevel == 4 ? "Try Very Hard Version" :
                                        "Try Expert Version"
                            Button(action: { handleHarderChallenge() }) {
                                HStack {
                                    Image(systemName: "flame.fill")
                                    Text(label)
                                    Spacer()
                                    CoinCostBadge(cost: cost, balance: coinManager.balance)
                                }
                                .font(.headline)
                                .foregroundColor(.white)
                                .frame(maxWidth: .infinity)
                                .padding()
                                .background(
                                    LinearGradient(
                                        colors: [Color.red, Color.orange],
                                        startPoint: .leading,
                                        endPoint: .trailing
                                    )
                                )
                                .cornerRadius(12)
                            }
                        }

                        // Next-level: Play if it exists, Generate if not. Hidden until lookup completes.
                        if nextLevelCheckDone {
                            if let _ = nextLevelExistingGameId {
                                // Level N+1 already exists — primary CTA to play it
                                Button(action: { handlePlayNextLevel() }) {
                                    HStack {
                                        Image(systemName: "arrow.right.circle.fill")
                                        Text(nextLevelExistingTitle.map { "Play \($0)" } ?? "Play Level \(nextLevelIndex)")
                                            .lineLimit(1)
                                        Spacer()
                                    }
                                    .font(.headline)
                                    .foregroundColor(.white)
                                    .frame(maxWidth: .infinity)
                                    .padding()
                                    .background(
                                        LinearGradient(
                                            colors: [Color(red: 0.2, green: 0.7, blue: 0.4), Color(red: 0.1, green: 0.55, blue: 0.3)],
                                            startPoint: .leading,
                                            endPoint: .trailing
                                        )
                                    )
                                    .cornerRadius(12)
                                }
                            } else {
                                // No next level yet — primary CTA to generate one (same coin cost as harder variant)
                                let nextLvlCost = CoinManager.difficultyCost(2)
                                Button(action: { handleGenerateNextLevel() }) {
                                    HStack {
                                        Image(systemName: "sparkles")
                                        Text("Generate Level \(nextLevelIndex)")
                                        Spacer()
                                        CoinCostBadge(cost: nextLvlCost, balance: coinManager.balance)
                                    }
                                    .font(.headline)
                                    .foregroundColor(.white)
                                    .frame(maxWidth: .infinity)
                                    .padding()
                                    .background(
                                        LinearGradient(
                                            colors: [Color(red: 0.61, green: 0.15, blue: 0.69), Color(red: 0.42, green: 0.11, blue: 0.5)],
                                            startPoint: .leading,
                                            endPoint: .trailing
                                        )
                                    )
                                    .cornerRadius(12)
                                }
                            }
                        }

                        // Get coins button (if can't afford any action)
                        if !coinManager.canContinue || (currentDifficultyLevel < CoinManager.maxDifficultyLevel && !coinManager.canAffordDifficulty(currentDifficultyLevel + 1)) || !coinManager.canAffordCustomize {
                            Button(action: { showCoinStore = true }) {
                                HStack {
                                    Image(systemName: "plus.circle.fill")
                                    Text("Get Coins")
                                }
                                .font(.headline)
                                .foregroundColor(.yellow)
                                .frame(maxWidth: .infinity)
                                .padding()
                                .background(Color.yellow.opacity(0.15))
                                .cornerRadius(12)
                            }
                        }

                        Button(action: {
                            showLeaderboard = true
                            fetchLeaderboard()
                        }) {
                            HStack {
                                Image(systemName: "trophy.fill")
                                Text("View Leaderboard")
                            }
                            .font(.headline)
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity)
                            .padding()
                            .background(Color.orange)
                            .cornerRadius(12)
                        }

                        Button(action: { dismiss() }) {
                            Text("Done")
                                .font(.headline)
                                .foregroundColor(.white.opacity(0.7))
                                .frame(maxWidth: .infinity)
                                .padding()
                                .background(Color.white.opacity(0.1))
                                .cornerRadius(12)
                        }
                    }
                    .padding(.horizontal, 40)
                    .padding(.bottom, 60)
                }
            }

            // Next-level generation overlay
            if isGeneratingNextLevel {
                Color.black.opacity(0.85)
                    .ignoresSafeArea()
                    .onTapGesture {}

                VStack(spacing: 24) {
                    Spacer()

                    Image(systemName: "sparkles")
                        .font(.system(size: 48))
                        .foregroundColor(Color(red: 0.85, green: 0.5, blue: 1.0))

                    Text(nextLevelSuggestedTitle ?? "Generating Level \(nextLevelIndex)")
                        .font(.title2.weight(.bold))
                        .foregroundColor(.white)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 24)

                    ProgressView(value: nextLevelProgress, total: 100)
                        .tint(Color(red: 0.85, green: 0.5, blue: 1.0))
                        .frame(maxWidth: 250)

                    Text(nextLevelPhase)
                        .font(.subheadline)
                        .foregroundColor(.white.opacity(0.7))

                    Text("This may take a couple minutes...")
                        .font(.caption)
                        .foregroundColor(.white.opacity(0.4))

                    Spacer()
                }
                .padding(40)
            }

            // Harder challenge generation overlay
            if isGeneratingHarder {
                Color.black.opacity(0.85)
                    .ignoresSafeArea()
                    .onTapGesture {}

                VStack(spacing: 24) {
                    Spacer()

                    Image(systemName: "flame.fill")
                        .font(.system(size: 48))
                        .foregroundColor(.orange)

                    Text("Generating Level \(currentDifficultyLevel + 1)")
                        .font(.title2.weight(.bold))
                        .foregroundColor(.white)

                    ProgressView(value: generationProgress, total: 100)
                        .tint(.orange)
                        .frame(maxWidth: 250)

                    Text(generationPhase)
                        .font(.subheadline)
                        .foregroundColor(.white.opacity(0.7))

                    Text("This may take a couple minutes...")
                        .font(.caption)
                        .foregroundColor(.white.opacity(0.4))

                    Spacer()
                }
                .padding(40)
            }

            // Fix progress overlay
            if isFixing {
                Color.black.opacity(0.85)
                    .ignoresSafeArea()
                    .onTapGesture {}

                VStack(spacing: 24) {
                    Spacer()
                    ProgressView()
                        .scaleEffect(1.5)
                        .tint(.white)
                    Text("Fixing game...")
                        .font(.title2.weight(.bold))
                        .foregroundColor(.white)
                    if !fixPhase.isEmpty {
                        Text(fixPhase)
                            .font(.subheadline)
                            .foregroundColor(.white.opacity(0.7))
                    }
                    if fixProgress > 0 {
                        ProgressView(value: fixProgress, total: 100)
                            .tint(Color(red: 0.42, green: 0.36, blue: 0.90))
                            .frame(maxWidth: 250)
                    }
                    Spacer()
                }
                .padding(40)
            }

        }
        .sheet(isPresented: $showLeaderboard) {
            GameLeaderboardSheet(
                gameTitle: game.title,
                players: leaderboard,
                userRank: userRank,
                isLoading: isLoadingLeaderboard
            )
        }
        .sheet(isPresented: $showCoinStore) {
            CoinStorePaywallView()
        }
        .sheet(isPresented: $showHowToPlay) {
            HowToPlaySheet(gameTitle: game.title, prompt: game.initialPrompt)
        }
        .onAppear {
            coinManager.fetchBalance()
            if UserDefaults.standard.bool(forKey: "played_\(game.id)") {
                showReplayGate = true
            }
        }
        .confirmationDialog("Did this game work?", isPresented: $showFeedbackDialog, titleVisibility: .visible) {
            Button("Yes, it works!") {
                dismiss()
            }
            Button("No, it has issues") {
                showFeedbackPrompt = true
            }
            Button("Cancel", role: .cancel) {}
        }
        .alert("What's wrong?", isPresented: $showFeedbackPrompt) {
            TextField("e.g. buttons don't work, blank screen", text: $feedbackText)
            Button("Fix It") {
                var description = feedbackText.trimmingCharacters(in: .whitespaces)
                if !gameErrors.isEmpty {
                    description += "\nDetected errors: " + gameErrors.joined(separator: "; ")
                }
                if description.isEmpty {
                    description = "The game doesn't work properly. Please fix all issues."
                }
                fixGame(description: description)
            }
            Button("Just Close", role: .destructive) {
                dismiss()
            }
        } message: {
            Text("Brief description — even a couple words helps")
        }
    }

    private func fixGame(description: String) {
        isFixing = true
        fixPhase = "Connecting..."
        fixProgress = 0

        guard let url = URL(string: "https://puzzleverseai.com/api/game-creation/\(game.id)/customize") else {
            isFixing = false
            return
        }

        let userId = Auth.auth().currentUser?.uid ?? "anonymous"

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("text/event-stream", forHTTPHeaderField: "Accept")
        request.timeoutInterval = 600

        let body: [String: Any] = [
            "userId": userId,
            "customizeDescription": description
        ]
        request.httpBody = try? JSONSerialization.data(withJSONObject: body)

        Task {
            do {
                let (bytes, response) = try await URLSession.shared.bytes(for: request)
                guard let httpResponse = response as? HTTPURLResponse, httpResponse.statusCode < 400 else {
                    await MainActor.run { isFixing = false }
                    return
                }

                var newBundle: String?
                var lineBuffer = ""

                for try await byte in bytes {
                    lineBuffer.append(Character(UnicodeScalar(byte)))

                    while let range = lineBuffer.range(of: "\n\n") {
                        let eventText = String(lineBuffer[lineBuffer.startIndex..<range.lowerBound])
                        lineBuffer = String(lineBuffer[range.upperBound...])

                        for line in eventText.components(separatedBy: "\n") {
                            guard line.hasPrefix("data: ") else { continue }
                            let jsonStr = String(line.dropFirst(6))
                            guard let data = jsonStr.data(using: .utf8),
                                  let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                                  let type = json["type"] as? String else { continue }

                            switch type {
                            case "status":
                                let message = json["message"] as? String ?? ""
                                let pct = json["progressPercent"] as? Double ?? 0
                                await MainActor.run {
                                    fixPhase = message
                                    fixProgress = pct
                                }
                            case "result":
                                newBundle = json["bundle"] as? String
                            case "error":
                                await MainActor.run { isFixing = false }
                                return
                            default:
                                break
                            }
                        }
                    }
                }

                guard let bundle = newBundle else {
                    await MainActor.run { isFixing = false }
                    return
                }

                let dir = try ZipExtractor.extractBundle(base64: bundle)
                await MainActor.run {
                    currentBundleDirectory = dir
                    gameErrors.removeAll()
                    feedbackText = ""
                    gameOver = false
                    scoreSubmitted = false
                    currentScore = 0
                    isFixing = false
                    webViewKey = UUID()
                }
            } catch {
                NSLog("[GamePlay] Fix error: %@", error.localizedDescription)
                await MainActor.run { isFixing = false }
            }
        }
    }

    private func handlePlayAgain() {
        let currentUserId = Auth.auth().currentUser?.uid
        let creatorId = (game.creatorId != currentUserId) ? game.creatorId : nil

        if !coinManager.canContinue {
            showCoinStore = true
            return
        }

        coinManager.spendForContinue(gameId: game.id, creatorId: creatorId) { success in
            if success {
                gameOver = false
                showReplayGate = false
                scoreSubmitted = false
                webViewKey = UUID()
            } else {
                showCoinStore = true
            }
        }
    }

    private func spendAndPlay(base64: String, level: Int) {
        let currentUserId = Auth.auth().currentUser?.uid
        let creatorId = (game.creatorId != currentUserId) ? game.creatorId : nil

        generationPhase = "Spending \(CoinManager.difficultyCost(level)) coins..."
        coinManager.spendForHarderChallenge(gameId: game.id, difficultyLevel: level, creatorId: creatorId) { success in
            if success {
                extractAndPlayBundle(base64: base64, level: level)
            } else {
                isGeneratingHarder = false
                gameOver = true
            }
        }
    }

    private func handleHarderChallenge() {
        let nextLevel = currentDifficultyLevel + 1
        guard nextLevel <= CoinManager.maxDifficultyLevel else { return }

        if !coinManager.canAffordDifficulty(nextLevel) {
            showCoinStore = true
            return
        }

        isGeneratingHarder = true
        generationPhase = "Checking availability..."
        generationProgress = 0

        // Check variant first — coins are spent only after variant is ready
        checkAndPlayDifficulty(level: nextLevel)
    }

    private func checkAndPlayDifficulty(level: Int) {
        guard let url = URL(string: "https://puzzleverseai.com/api/game-creation/\(game.id)/difficulty/\(level)") else { return }

        URLSession.shared.dataTask(with: url) { data, _, error in
            guard let data = data,
                  let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
                DispatchQueue.main.async {
                    isGeneratingHarder = false
                    gameOver = true
                }
                return
            }

            if let exists = json["exists"] as? Bool, exists,
               let variant = json["variant"] as? [String: Any],
               let bundle = variant["bundle"] as? String {
                // Variant exists — spend coins then play
                DispatchQueue.main.async {
                    spendAndPlay(base64: bundle, level: level)
                }
            } else if let generating = json["generating"] as? Bool, generating {
                // Someone else is generating it — poll
                DispatchQueue.main.async {
                    generationPhase = "Another player is generating this level..."
                    generationProgress = 50
                }
                pollForVariant(level: level)
            } else {
                // Not generated — trigger generation, coins spent after success
                DispatchQueue.main.async {
                    generateHarderVariant(level: level)
                }
            }
        }.resume()
    }

    private func pollForVariant(level: Int) {
        DispatchQueue.global().asyncAfter(deadline: .now() + 10) {
            guard let url = URL(string: "https://puzzleverseai.com/api/game-creation/\(game.id)/difficulty/\(level)") else { return }

            URLSession.shared.dataTask(with: url) { data, _, _ in
                guard let data = data,
                      let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { return }

                if let exists = json["exists"] as? Bool, exists,
                   let variant = json["variant"] as? [String: Any],
                   let bundle = variant["bundle"] as? String {
                    DispatchQueue.main.async {
                        spendAndPlay(base64: bundle, level: level)
                    }
                } else {
                    // Still generating — keep polling
                    pollForVariant(level: level)
                }
            }.resume()
        }
    }

    private func generateHarderVariant(level: Int) {
        isGeneratingHarder = true
        generationPhase = "Preparing harder challenge..."
        generationProgress = 0

        guard let userId = Auth.auth().currentUser?.uid else { return }
        guard let url = URL(string: "https://puzzleverseai.com/api/game-creation/\(game.id)/difficulty/\(level)/generate") else { return }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.timeoutInterval = 600

        let body: [String: Any] = ["userId": userId]
        request.httpBody = try? JSONSerialization.data(withJSONObject: body)

        let session = URLSession(configuration: .default)
        let task = session.dataTask(with: request) { data, _, error in
            guard let data = data else {
                DispatchQueue.main.async {
                    isGeneratingHarder = false
                    gameOver = true
                }
                return
            }

            // Parse SSE events from response
            let responseStr = String(data: data, encoding: .utf8) ?? ""
            let lines = responseStr.components(separatedBy: "\n")
            var gotResult = false

            for line in lines {
                guard line.hasPrefix("data: ") else { continue }
                let eventData = String(line.dropFirst(6))
                guard let eventJson = try? JSONSerialization.jsonObject(with: Data(eventData.utf8)) as? [String: Any] else { continue }

                if let type = eventJson["type"] as? String {
                    if type == "status" {
                        let message = eventJson["message"] as? String ?? ""
                        let pct = eventJson["progressPercent"] as? Double ?? 0
                        DispatchQueue.main.async {
                            generationPhase = message
                            generationProgress = pct
                        }
                    } else if type == "result", let bundle = eventJson["bundle"] as? String {
                        gotResult = true
                        // Generation succeeded — now spend coins then play
                        DispatchQueue.main.async {
                            spendAndPlay(base64: bundle, level: level)
                        }
                        return
                    } else if type == "error" {
                        let errorMsg = eventJson["error"] as? String ?? "Generation failed"
                        NSLog("[GamePlay] Harder generation error: %@", errorMsg)
                        DispatchQueue.main.async {
                            isGeneratingHarder = false
                            gameOver = true
                        }
                        return
                    }
                }
            }

            if !gotResult {
                DispatchQueue.main.async {
                    isGeneratingHarder = false
                    gameOver = true
                }
            }
        }
        task.resume()
    }

    private func extractAndPlayBundle(base64: String, level: Int) {
        do {
            let dir = try ZipExtractor.extractBundle(base64: base64)
            currentBundleDirectory = dir
            currentDifficultyLevel = level
            isGeneratingHarder = false
            gameOver = false
            scoreSubmitted = false
            currentScore = 0
            webViewKey = UUID()
        } catch {
            NSLog("[GamePlay] Failed to extract difficulty bundle: %@", error.localizedDescription)
            isGeneratingHarder = false
        }
    }

    // MARK: - Next Level (sequential series progression)

    private func checkNextLevel() {
        nextLevelCheckDone = false
        nextLevelExistingGameId = nil
        nextLevelExistingTitle = nil

        guard let url = URL(string: "https://puzzleverseai.com/api/game-creation/\(game.id)/next-level") else {
            nextLevelCheckDone = true
            return
        }

        URLSession.shared.dataTask(with: url) { data, _, _ in
            guard let data = data,
                  let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
                DispatchQueue.main.async {
                    nextLevelIndex = 2
                    nextLevelCheckDone = true
                }
                return
            }

            let exists = (json["exists"] as? Bool) ?? false
            DispatchQueue.main.async {
                if exists, let g = json["game"] as? [String: Any] {
                    nextLevelExistingGameId = g["id"] as? String
                    nextLevelExistingTitle = g["title"] as? String
                    nextLevelIndex = (g["levelIndex"] as? Int) ?? 2
                } else {
                    nextLevelExistingGameId = nil
                    nextLevelExistingTitle = nil
                    nextLevelIndex = (json["nextLevelIndex"] as? Int) ?? 2
                }
                nextLevelCheckDone = true
            }
        }.resume()
    }

    private func handlePlayNextLevel() {
        guard let nextGameId = nextLevelExistingGameId else { return }
        isGeneratingNextLevel = true
        nextLevelPhase = "Loading level..."
        nextLevelProgress = 50
        nextLevelSuggestedTitle = nextLevelExistingTitle

        guard let url = URL(string: "https://puzzleverseai.com/api/game-creation/\(nextGameId)") else {
            isGeneratingNextLevel = false
            return
        }

        URLSession.shared.dataTask(with: url) { data, _, _ in
            guard let data = data,
                  let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                  let gameJson = json["game"] as? [String: Any],
                  let bundle = gameJson["bundle"] as? String else {
                DispatchQueue.main.async {
                    isGeneratingNextLevel = false
                    gameOver = true
                }
                return
            }

            DispatchQueue.main.async {
                applyNextLevel(
                    newGameId: nextGameId,
                    newTitle: (gameJson["title"] as? String) ?? nextLevelExistingTitle ?? "Level \(nextLevelIndex)",
                    newDescription: (gameJson["description"] as? String) ?? "",
                    newCreatorName: (gameJson["creatorName"] as? String) ?? game.creatorName,
                    bundleBase64: bundle
                )
            }
        }.resume()
    }

    private func handleGenerateNextLevel() {
        let cost = CoinManager.difficultyCost(2)
        if !coinManager.canAffordDifficulty(2) {
            showCoinStore = true
            return
        }

        guard let userId = Auth.auth().currentUser?.uid else { return }
        guard let url = URL(string: "https://puzzleverseai.com/api/game-creation/\(game.id)/next-level/generate") else { return }

        isGeneratingNextLevel = true
        nextLevelPhase = "Preparing next level..."
        nextLevelProgress = 0
        nextLevelSuggestedTitle = nil

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("text/event-stream", forHTTPHeaderField: "Accept")
        request.timeoutInterval = 600
        request.httpBody = try? JSONSerialization.data(withJSONObject: ["userId": userId])

        let task = URLSession(configuration: .default).dataTask(with: request) { data, _, error in
            guard let data = data else {
                DispatchQueue.main.async {
                    isGeneratingNextLevel = false
                    gameOver = true
                }
                return
            }

            let responseStr = String(data: data, encoding: .utf8) ?? ""
            var gotResult = false

            for line in responseStr.components(separatedBy: "\n") {
                if line.hasPrefix(":") { continue } // heartbeat
                guard line.hasPrefix("data: ") else { continue }
                let payload = String(line.dropFirst(6))
                guard let json = try? JSONSerialization.jsonObject(with: Data(payload.utf8)) as? [String: Any],
                      let type = json["type"] as? String else { continue }

                if type == "status" {
                    let message = json["message"] as? String ?? ""
                    let pct = json["progressPercent"] as? Double ?? 0
                    let suggested = json["suggestedTitle"] as? String
                    DispatchQueue.main.async {
                        nextLevelPhase = message
                        nextLevelProgress = pct
                        if let s = suggested { nextLevelSuggestedTitle = s }
                    }
                } else if type == "result" {
                    gotResult = true
                    let success = (json["success"] as? Bool) ?? false
                    let alreadyExists = (json["alreadyExists"] as? Bool) ?? false

                    if alreadyExists, let winnerId = json["gameId"] as? String {
                        // Race lost — fetch & play the winning level instead, no coin spend
                        DispatchQueue.main.async {
                            nextLevelExistingGameId = winnerId
                            handlePlayNextLevel()
                        }
                        return
                    }

                    guard success, let bundle = json["bundle"] as? String, let newGameId = json["gameId"] as? String else {
                        DispatchQueue.main.async {
                            isGeneratingNextLevel = false
                            gameOver = true
                            NSLog("[NextLevel] Generation failed")
                        }
                        return
                    }

                    let newTitle = (json["title"] as? String) ?? "Level \(nextLevelIndex)"

                    DispatchQueue.main.async {
                        nextLevelPhase = "Spending \(cost) coins..."
                    }

                    let currentUserId = Auth.auth().currentUser?.uid
                    let creatorIdForRev = (game.creatorId != currentUserId) ? game.creatorId : nil
                    coinManager.spendForHarderChallenge(gameId: game.id, difficultyLevel: 2, creatorId: creatorIdForRev) { paid in
                        DispatchQueue.main.async {
                            if paid {
                                applyNextLevel(
                                    newGameId: newGameId,
                                    newTitle: newTitle,
                                    newDescription: "",
                                    newCreatorName: game.creatorName,
                                    bundleBase64: bundle
                                )
                            } else {
                                isGeneratingNextLevel = false
                                gameOver = true
                                NSLog("[NextLevel] Payment failed after successful generation")
                            }
                        }
                    }
                    return
                } else if type == "error" {
                    let msg = json["error"] as? String ?? "Generation failed"
                    NSLog("[NextLevel] Worker error: %@", msg)
                    DispatchQueue.main.async {
                        isGeneratingNextLevel = false
                        gameOver = true
                    }
                    return
                }
            }

            if !gotResult {
                DispatchQueue.main.async {
                    isGeneratingNextLevel = false
                    gameOver = true
                }
            }
        }
        task.resume()
    }

    /// Swap the view's `game` to the next level and play. Called after either
    /// fetching an existing next level or freshly generating one.
    private func applyNextLevel(newGameId: String, newTitle: String, newDescription: String, newCreatorName: String, bundleBase64: String) {
        do {
            let dir = try ZipExtractor.extractBundle(base64: bundleBase64)
            // Rebuild the BrowseGame so top-bar title etc. update
            game = BrowseGame(
                id: newGameId,
                title: newTitle,
                creatorId: game.creatorId,
                creatorName: newCreatorName,
                playCount: 0,
                initialPrompt: game.initialPrompt,
                createdAt: ISO8601DateFormatter().string(from: Date()),
                status: game.status,
                thumbnailUrl: nil
            )
            currentBundleDirectory = dir
            currentDifficultyLevel = 1   // Each level is its own game; difficulty variant resets
            currentScore = 0
            gameOver = false
            scoreSubmitted = false
            isGeneratingNextLevel = false
            playStartTime = Date()
            webViewKey = UUID()
            // Reset next-level lookup state — will re-check when this new level ends
            nextLevelCheckDone = false
            nextLevelExistingGameId = nil
            nextLevelExistingTitle = nil
            nextLevelSuggestedTitle = nil
        } catch {
            NSLog("[NextLevel] Failed to extract bundle: %@", error.localizedDescription)
            isGeneratingNextLevel = false
            gameOver = true
        }
    }

    private func submitScore(_ score: Int) {
        guard let userId = Auth.auth().currentUser?.uid else { return }
        guard score > 0 else { return }

        let timeTaken = Int(Date().timeIntervalSince(playStartTime) * 1000) // ms

        guard let url = URL(string: "https://puzzleverseai.com/api/leaderboard/puzzle/\(game.id)") else { return }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")

        var body: [String: Any] = [
            "userId": userId,
            "timeTaken": timeTaken,
            "score": score
        ]
        // Feature C: forward the play_event id from startPlayEvent so the
        // backend updates the existing row (completed=true) instead of inserting
        // a duplicate. Falls back gracefully if the id wasn't captured.
        if let eid = currentPlayEventId { body["eventId"] = eid }

        request.httpBody = try? JSONSerialization.data(withJSONObject: body)

        URLSession.shared.dataTask(with: request) { _, response, _ in
            if let httpResp = response as? HTTPURLResponse, httpResp.statusCode == 200 {
                DispatchQueue.main.async {
                    scoreSubmitted = true
                }
            }
        }.resume()
    }

    // Feature C: emit a play-event start row when the WebView loads or reloads.
    // Fires for: initial open, Play Again, harder-variant swap, next-level swap —
    // anywhere the webViewKey changes. Fire-and-forget: server failures must not
    // affect gameplay.
    private func startPlayEvent() {
        guard let userId = Auth.auth().currentUser?.uid else { return }
        guard let url = URL(string: "https://puzzleverseai.com/api/game-creation/\(game.id)/play-event/start") else { return }

        currentPlayEventId = nil
        playStartTime = Date()

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try? JSONSerialization.data(withJSONObject: ["userId": userId])

        URLSession.shared.dataTask(with: request) { data, _, _ in
            guard let data = data,
                  let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                  let eventId = json["eventId"] as? String else { return }
            DispatchQueue.main.async { currentPlayEventId = eventId }
        }.resume()
    }

    private func fetchLeaderboard() {
        isLoadingLeaderboard = true
        let userId = Auth.auth().currentUser?.uid ?? ""

        guard let url = URL(string: "https://puzzleverseai.com/api/leaderboard/puzzle/\(game.id)?userId=\(userId)") else { return }

        URLSession.shared.dataTask(with: url) { data, _, _ in
            DispatchQueue.main.async {
                isLoadingLeaderboard = false

                guard let data = data,
                      let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                      let players = json["topPlayers"] as? [[String: Any]] else {
                    return
                }

                userRank = json["userRank"] as? Int

                leaderboard = players.compactMap { p in
                    guard let rank = p["rank"] as? Int,
                          let name = p["playerName"] as? String,
                          let score = p["score"] as? Int else { return nil }
                    let pUserId = p["userId"] as? String ?? ""
                    return LeaderboardPlayer(
                        rank: rank,
                        playerName: name,
                        score: score,
                        isCurrentUser: pUserId == userId
                    )
                }
            }
        }.resume()
    }
}

// MARK: - Coin Balance Pill

struct CoinBalancePill: View {
    let balance: Int

    var body: some View {
        HStack(spacing: 4) {
            Image(systemName: "circle.fill")
                .font(.system(size: 10))
                .foregroundColor(.yellow)
            Text("\(balance)")
                .font(.caption.weight(.bold))
                .foregroundColor(.yellow)
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 4)
        .background(Color.yellow.opacity(0.15))
        .cornerRadius(12)
    }
}

// MARK: - Coin Cost Badge

struct CoinCostBadge: View {
    let cost: Int
    let balance: Int

    var body: some View {
        HStack(spacing: 3) {
            Image(systemName: "circle.fill")
                .font(.system(size: 8))
                .foregroundColor(.yellow)
            Text("\(cost)")
                .font(.subheadline.weight(.bold))
                .foregroundColor(balance >= cost ? .yellow : .red)
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 4)
        .background(Color.black.opacity(0.3))
        .cornerRadius(8)
    }
}

// MARK: - Coin Store Sheet

struct CoinStoreSheet: View {
    @ObservedObject var coinManager: CoinManager
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationView {
            ZStack {
                Color(red: 0.1, green: 0.1, blue: 0.18)
                    .ignoresSafeArea()

                VStack(spacing: 20) {
                    // Current balance
                    VStack(spacing: 8) {
                        HStack(spacing: 6) {
                            Image(systemName: "circle.fill")
                                .font(.title2)
                                .foregroundColor(.yellow)
                            Text("\(coinManager.balance)")
                                .font(.system(size: 36, weight: .bold))
                                .foregroundColor(.white)
                        }
                        Text("Your Coins")
                            .font(.subheadline)
                            .foregroundColor(.white.opacity(0.5))
                    }
                    .padding(.top, 20)

                    // Coin packs
                    VStack(spacing: 12) {
                        ForEach(CoinManager.CoinPack.allCases, id: \.rawValue) { pack in
                            CoinPackRow(
                                pack: pack,
                                product: coinManager.product(for: pack),
                                isPurchasing: {
                                    if case .purchasing = coinManager.purchaseState { return true }
                                    return false
                                }()
                            ) {
                                Task { await coinManager.purchase(pack) }
                            }
                        }
                    }
                    .padding(.horizontal)

                    // Purchase state feedback
                    if case .success = coinManager.purchaseState {
                        HStack(spacing: 6) {
                            Image(systemName: "checkmark.circle.fill")
                                .foregroundColor(.green)
                            Text("Coins added!")
                                .foregroundColor(.green)
                        }
                        .font(.subheadline)
                    } else if case .failed(let msg) = coinManager.purchaseState {
                        Text(msg)
                            .font(.caption)
                            .foregroundColor(.red)
                    }

                    Spacer()

                    Text("Coins are used to continue games and support creators.")
                        .font(.caption)
                        .foregroundColor(.white.opacity(0.3))
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 40)
                        .padding(.bottom, 20)
                }
            }
            .navigationTitle("Coin Store")
            .navigationBarTitleDisplayMode(.inline)
            .toolbarColorScheme(.dark, for: .navigationBar)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Done") { dismiss() }
                        .foregroundColor(.orange)
                }
            }
        }
        .task {
            await coinManager.loadProducts()
        }
    }
}

// MARK: - Coin Pack Row

struct CoinPackRow: View {
    let pack: CoinManager.CoinPack
    let product: StoreProduct?
    let isPurchasing: Bool
    let onBuy: () -> Void

    var body: some View {
        Button(action: onBuy) {
            HStack {
                Image(systemName: "circle.fill")
                    .font(.title2)
                    .foregroundColor(.yellow)

                VStack(alignment: .leading, spacing: 2) {
                    Text(pack.label)
                        .font(.headline)
                        .foregroundColor(.white)
                    if pack == .large {
                        Text("Best Value")
                            .font(.caption2.weight(.bold))
                            .foregroundColor(.green)
                    }
                }

                Spacer()

                Text(product?.localizedPriceString ?? "...")
                    .font(.subheadline.weight(.bold))
                    .foregroundColor(.white)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 8)
                    .background(Color.orange)
                    .cornerRadius(8)
            }
            .padding(16)
            .background(
                RoundedRectangle(cornerRadius: 14)
                    .fill(Color.white.opacity(0.06))
            )
        }
        .disabled(isPurchasing || product == nil)
    }
}

// MARK: - Leaderboard Sheet

struct GameLeaderboardSheet: View {
    let gameTitle: String
    let players: [GamePlayView.LeaderboardPlayer]
    let userRank: Int?
    let isLoading: Bool

    var body: some View {
        NavigationView {
            ZStack {
                Color(red: 0.1, green: 0.1, blue: 0.18)
                    .ignoresSafeArea()

                if isLoading {
                    ProgressView()
                        .tint(.white)
                } else if players.isEmpty {
                    VStack(spacing: 12) {
                        Image(systemName: "trophy")
                            .font(.system(size: 48))
                            .foregroundColor(.white.opacity(0.3))
                        Text("No scores yet")
                            .font(.headline)
                            .foregroundColor(.white.opacity(0.5))
                        Text("Play the game to set the first high score!")
                            .font(.caption)
                            .foregroundColor(.white.opacity(0.3))
                    }
                } else {
                    ScrollView {
                        VStack(spacing: 8) {
                            if let rank = userRank {
                                HStack {
                                    Image(systemName: "person.fill")
                                        .foregroundColor(.orange)
                                    Text("Your rank: #\(rank)")
                                        .font(.subheadline.weight(.medium))
                                        .foregroundColor(.orange)
                                }
                                .padding(12)
                                .frame(maxWidth: .infinity)
                                .background(
                                    RoundedRectangle(cornerRadius: 10)
                                        .fill(Color.orange.opacity(0.12))
                                )
                                .padding(.bottom, 8)
                            }

                            ForEach(players) { player in
                                HStack(spacing: 12) {
                                    Text(rankDisplay(player.rank))
                                        .font(player.rank <= 3 ? .title2 : .subheadline.weight(.bold))
                                        .frame(width: 40)
                                        .foregroundColor(player.rank <= 3 ? .white : .white.opacity(0.6))

                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(player.playerName)
                                            .font(.subheadline.weight(.medium))
                                            .foregroundColor(player.isCurrentUser ? .orange : .white)
                                            .lineLimit(1)
                                    }

                                    Spacer()

                                    Text("\(player.score)")
                                        .font(.headline)
                                        .foregroundColor(.orange)
                                }
                                .padding(12)
                                .background(
                                    RoundedRectangle(cornerRadius: 12)
                                        .fill(player.isCurrentUser ?
                                              Color.orange.opacity(0.15) :
                                              Color.white.opacity(0.06))
                                )
                            }
                        }
                        .padding()
                    }
                }
            }
            .navigationTitle(gameTitle)
            .navigationBarTitleDisplayMode(.inline)
            .toolbarColorScheme(.dark, for: .navigationBar)
        }
    }

    private func rankDisplay(_ rank: Int) -> String {
        switch rank {
        case 1: return "\u{1F947}"
        case 2: return "\u{1F948}"
        case 3: return "\u{1F949}"
        default: return "#\(rank)"
        }
    }
}

// MARK: - How to Play Sheet

struct HowToPlaySheet: View {
    let gameTitle: String
    let prompt: String
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationView {
            ZStack {
                Color(red: 0.1, green: 0.1, blue: 0.18).ignoresSafeArea()

                ScrollView {
                    VStack(alignment: .leading, spacing: 20) {
                        HStack {
                            Image(systemName: "gamecontroller.fill")
                                .font(.title2)
                                .foregroundColor(.orange)
                            Text("About This Game")
                                .font(.headline)
                                .foregroundColor(.white)
                        }

                        Text(prompt)
                            .font(.body)
                            .foregroundColor(.white.opacity(0.85))
                            .fixedSize(horizontal: false, vertical: true)

                        Divider().background(Color.white.opacity(0.1))

                        VStack(alignment: .leading, spacing: 8) {
                            Label("Play and solve the puzzles at your own pace", systemImage: "brain.head.profile")
                            Label("Earn points — compete on the leaderboard", systemImage: "trophy")
                            Label("Challenge yourself with a harder version", systemImage: "flame")
                        }
                        .font(.subheadline)
                        .foregroundColor(.white.opacity(0.65))
                    }
                    .padding(24)
                }
            }
            .navigationTitle(gameTitle)
            .navigationBarTitleDisplayMode(.inline)
            .toolbarColorScheme(.dark, for: .navigationBar)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Done") { dismiss() }
                        .foregroundColor(.orange)
                }
            }
        }
    }
}
