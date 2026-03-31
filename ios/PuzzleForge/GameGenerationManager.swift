import SwiftUI
import UIKit
import UserNotifications
import FirebaseAuth

// MARK: - Generation Mode & Result

enum GenerationMode {
    case custom(prompt: String, imageBase64: String?, useCoins: Bool)
    case remix(puzzleType: String, difficulty: String, remixDescription: String,
               title: String, imageBase64: String?, useCoins: Bool)
}

struct GenerationResult {
    let bundleDir: URL
    let bundleBase64: String
    let gameId: String?
}

class GameGenerationManager: ObservableObject {
    static let shared = GameGenerationManager()

    // Generation state
    @Published var isGenerating: Bool = false
    @Published var buildPhase: String = ""
    @Published var buildDetail: String = ""
    @Published var progressPercent: Double = 0
    @Published var estimatedSecondsRemaining: Int = 0
    @Published var errorMessage: String? = nil

    // Rate limit upsell state
    @Published var showRateLimitUpsell: Bool = false
    @Published var rateLimitCoinCost: Int = 10

    // Result state
    @Published var bundleDir: URL? = nil
    @Published var bundleBase64: String? = nil
    @Published var showPreview: Bool = false
    @Published var resultGameId: String? = nil

    // Remix-specific state
    @Published var remixResultTitle: String = ""
    @Published var isRemixGeneration: Bool = false

    // Notification state
    @Published var showCompletionBanner: Bool = false
    @Published var completionPrompt: String = ""

    // Track whether user is currently on the Create tab
    var isOnCreateTab: Bool = false
    // Track whether user is currently on the remix sheet
    var isOnRemixSheet: Bool = false

    // The prompt used for the current/last generation
    var currentPrompt: String = ""

    // Kept for backwards compatibility with callers that read remixResultGameId
    var remixResultGameId: String? {
        get { resultGameId }
        set { resultGameId = newValue }
    }

    private var generationTask: Task<Void, Never>?
    private var progressTimer: Timer?
    private var phaseStartPct: Double = 0
    private var phaseEndPct: Double = 0
    private var phaseDuration: Double = 0
    private var phaseStartTime: Date?
    private var backgroundTaskID: UIBackgroundTaskIdentifier = .invalid

    // MARK: - Background Task Protection

    private func beginBackgroundProtection() {
        backgroundTaskID = UIApplication.shared.beginBackgroundTask(withName: "GameGeneration") { [weak self] in
            // System is about to kill our background time — clean up
            NSLog("[GameGen] Background time expiring")
            self?.endBackgroundProtection()
        }
    }

    private func endBackgroundProtection() {
        if backgroundTaskID != .invalid {
            UIApplication.shared.endBackgroundTask(backgroundTaskID)
            backgroundTaskID = .invalid
        }
    }

    // MARK: - Unified Generation Entry Point

    func startGeneration(mode: GenerationMode) {
        guard !isGenerating else { return }

        isGenerating = true
        errorMessage = nil
        showRateLimitUpsell = false
        buildDetail = ""
        progressPercent = 0
        estimatedSecondsRemaining = 0
        resultGameId = nil

        switch mode {
        case .custom(let prompt, _, _):
            isRemixGeneration = false
            buildPhase = "Connecting..."
            completionPrompt = prompt
            currentPrompt = prompt

        case .remix(_, _, let remixDescription, let title, _, _):
            isRemixGeneration = true
            buildPhase = "Preparing remix..."
            completionPrompt = title
            currentPrompt = remixDescription
            remixResultTitle = title
        }

        // Keep screen on during generation
        UIApplication.shared.isIdleTimerDisabled = true
        beginBackgroundProtection()

        generationTask = Task {
            do {
                let result = try await streamGeneration(mode: mode)
                await MainActor.run {
                    self.stopProgressInterpolation()
                    self.endBackgroundProtection()
                    bundleDir = result.bundleDir
                    bundleBase64 = result.bundleBase64
                    resultGameId = result.gameId
                    isGenerating = false
                    progressPercent = 100
                    UIApplication.shared.isIdleTimerDisabled = false
                    self.sendCompletionNotification(prompt: self.completionPrompt)

                    let isOnScreen = self.isRemixGeneration ? self.isOnRemixSheet : self.isOnCreateTab
                    if isOnScreen {
                        showPreview = true
                    } else {
                        showCompletionBanner = true
                    }
                }
            } catch {
                if Task.isCancelled { return }
                NSLog("[GameGen] %@ ERROR: %@", self.isRemixGeneration ? "Remix" : "Custom", "\(error)")
                await MainActor.run {
                    self.stopProgressInterpolation()
                    self.endBackgroundProtection()
                    isGenerating = false
                    UIApplication.shared.isIdleTimerDisabled = false
                    if !self.showRateLimitUpsell {
                        errorMessage = error.localizedDescription
                    }
                }
            }
        }
    }

    // Convenience wrappers for backwards compatibility
    func startGeneration(prompt: String, imageBase64: String? = nil, useCoins: Bool = false) {
        startGeneration(mode: .custom(prompt: prompt, imageBase64: imageBase64, useCoins: useCoins))
    }

    func startRemixGeneration(
        puzzleType: String,
        difficulty: String,
        remixDescription: String,
        title: String,
        imageBase64: String? = nil,
        useCoins: Bool = false
    ) {
        startGeneration(mode: .remix(
            puzzleType: puzzleType, difficulty: difficulty,
            remixDescription: remixDescription, title: title,
            imageBase64: imageBase64, useCoins: useCoins
        ))
    }

    func cancelGeneration() {
        generationTask?.cancel()
        stopProgressInterpolation()
        endBackgroundProtection()
        isGenerating = false
        buildPhase = ""
        buildDetail = ""
        progressPercent = 0
        UIApplication.shared.isIdleTimerDisabled = false
    }

    private func sendCompletionNotification(prompt: String) {
        UNUserNotificationCenter.current().getNotificationSettings { settings in
            guard settings.authorizationStatus == .authorized else { return }

            let content = UNMutableNotificationContent()
            content.title = "Game Ready!"
            if self.isRemixGeneration {
                content.body = "Your game \"\(String(prompt.prefix(40)))\" is ready! Find it in Explore > Custom Games."
            } else {
                content.body = "Your game \"\(String(prompt.prefix(40)))\" has been created. Tap to play!"
            }
            content.sound = .default
            let request = UNNotificationRequest(identifier: "game-gen-\(UUID().uuidString)", content: content, trigger: nil)
            UNUserNotificationCenter.current().add(request)
        }
    }

    func dismissBanner() {
        showCompletionBanner = false
    }

    func openCompletedGame() {
        showCompletionBanner = false
        showPreview = true
    }

    // MARK: - Progress Interpolation

    private func startProgressInterpolation() {
        progressTimer?.invalidate()
        guard phaseDuration > 0 else { return }
        phaseStartTime = Date()
        progressTimer = Timer.scheduledTimer(withTimeInterval: 0.5, repeats: true) { [weak self] _ in
            guard let self = self, let startTime = self.phaseStartTime else { return }
            let elapsed = Date().timeIntervalSince(startTime)
            let fraction = min(elapsed / self.phaseDuration, 0.95)
            let interpolated = self.phaseStartPct + (self.phaseEndPct - self.phaseStartPct) * fraction
            DispatchQueue.main.async {
                self.progressPercent = interpolated
            }
        }
    }

    private func stopProgressInterpolation() {
        progressTimer?.invalidate()
        progressTimer = nil
    }

    // MARK: - Unified SSE Streaming

    private func streamGeneration(mode: GenerationMode) async throws -> GenerationResult {
        let (url, body, logPrefix) = try buildRequest(for: mode)

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("text/event-stream", forHTTPHeaderField: "Accept")
        request.setValue("ios", forHTTPHeaderField: "x-platform")
        request.timeoutInterval = 600
        request.httpBody = try JSONSerialization.data(withJSONObject: body)

        NSLog("[GameGen] %@ request to: %@", logPrefix, url.absoluteString)
        let (bytes, response) = try await URLSession.shared.bytes(for: request)

        guard let httpResponse = response as? HTTPURLResponse else {
            throw URLError(.badServerResponse)
        }

        // Handle error status codes
        if httpResponse.statusCode >= 400 {
            var errorBody = ""
            for try await byte in bytes {
                errorBody.append(Character(UnicodeScalar(byte)))
                if errorBody.count > 2000 { break }
            }

            var serverError = "Server error (\(httpResponse.statusCode))"
            var parsedJson: [String: Any]? = nil
            if let data = errorBody.data(using: .utf8),
               let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
                parsedJson = json
                if let errMsg = json["error"] as? String {
                    serverError = errMsg
                }
            }

            // Check for rate limit with coin upsell option
            if (httpResponse.statusCode == 429 || httpResponse.statusCode == 402),
               let canUse = parsedJson?["canUseCoins"] as? Bool, canUse {
                let cost = (parsedJson?["coinCost"] as? Int) ?? 10
                await MainActor.run {
                    self.rateLimitCoinCost = cost
                    self.showRateLimitUpsell = true
                }
                throw NSError(domain: "GameGen", code: httpResponse.statusCode,
                              userInfo: [NSLocalizedDescriptionKey: "RATE_LIMIT_UPSELL"])
            }

            if httpResponse.statusCode == 429 {
                serverError = "Rate limit exceeded. Try again in an hour."
            }
            throw NSError(domain: "GameGen", code: httpResponse.statusCode,
                          userInfo: [NSLocalizedDescriptionKey: serverError])
        }

        // Parse SSE stream
        var bundleBase64Result: String?
        var gameId: String?
        var lineBuffer = ""

        for try await byte in bytes {
            try Task.checkCancellation()
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
                        let detail = json["detail"] as? String ?? ""
                        let pct = json["progressPercent"] as? Double ?? self.progressPercent
                        let endPct = json["progressEndPct"] as? Double ?? pct
                        let phaseSecs = json["phaseDurationSeconds"] as? Double ?? 0
                        let eta = json["estimatedSecondsRemaining"] as? Int ?? 0
                        await MainActor.run {
                            self.buildPhase = message
                            self.buildDetail = detail
                            self.progressPercent = pct
                            self.estimatedSecondsRemaining = eta
                            self.phaseStartPct = pct
                            self.phaseEndPct = endPct
                            self.phaseDuration = phaseSecs
                            self.startProgressInterpolation()
                        }

                    case "result":
                        bundleBase64Result = json["bundle"] as? String
                        gameId = json["gameId"] as? String

                    case "error":
                        let errorMsg = json["error"] as? String ?? "Unknown error"
                        throw NSError(domain: "GameGen", code: 0,
                                      userInfo: [NSLocalizedDescriptionKey: errorMsg])
                    default:
                        break
                    }
                }
            }
        }

        guard let base64 = bundleBase64Result else {
            throw NSError(domain: "GameGen", code: 0,
                          userInfo: [NSLocalizedDescriptionKey: "Build completed but no game bundle received. Please try again."])
        }

        await MainActor.run {
            self.buildPhase = "Extracting game..."
            self.buildDetail = ""
            self.progressPercent = 95
        }

        let dir = try ZipExtractor.extractBundle(base64: base64)
        return GenerationResult(bundleDir: dir, bundleBase64: base64, gameId: gameId)
    }

    /// Build the URL and JSON body for a generation request based on mode
    private func buildRequest(for mode: GenerationMode) throws -> (URL, [String: Any], String) {
        let userId = Auth.auth().currentUser?.uid ?? "anonymous"
        let userName = Auth.auth().currentUser?.displayName
            ?? Auth.auth().currentUser?.email?.components(separatedBy: "@").first
            ?? "Anonymous"

        switch mode {
        case .custom(let prompt, let imageBase64, let useCoins):
            guard let url = URL(string: "https://puzzleverseai.com/api/game-creation/generate") else {
                throw URLError(.badURL)
            }
            let basePrompt = prompt.trimmingCharacters(in: .whitespacesAndNewlines)
            let fullPrompt = basePrompt + "\n\nInclude a brief 'How to Play' note in the game UI."
            var body: [String: Any] = [
                "prompt": fullPrompt,
                "userId": userId,
                "userName": userName,
                "title": String(basePrompt.prefix(100))
            ]
            if let imageBase64 = imageBase64 { body["referenceImage"] = imageBase64 }
            if useCoins { body["useCoins"] = true }
            return (url, body, "Custom")

        case .remix(let puzzleType, let difficulty, let remixDescription, let title, let imageBase64, let useCoins):
            guard let url = URL(string: "https://puzzleverseai.com/api/game-creation/remix") else {
                throw URLError(.badURL)
            }
            let fullRemixDesc = remixDescription + "\n\nInclude a brief 'How to Play' note in the game UI."
            var body: [String: Any] = [
                "userId": userId,
                "userName": userName,
                "puzzleType": puzzleType,
                "difficulty": difficulty,
                "remixDescription": fullRemixDesc,
                "title": title
            ]
            if let imageBase64 = imageBase64 { body["referenceImage"] = imageBase64 }
            if useCoins { body["useCoins"] = true }
            return (url, body, "Remix")
        }
    }
}

// MARK: - Completion Banner

struct GameCompletionBanner: View {
    let onTap: () -> Void
    let onDismiss: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: "checkmark.circle.fill")
                .foregroundColor(.green)
                .font(.title2)

            VStack(alignment: .leading, spacing: 2) {
                Text("Game Ready!")
                    .font(.subheadline.bold())
                    .foregroundColor(.white)
                Text("Tap to preview — also in Explore > Custom Games")
                    .font(.caption)
                    .foregroundColor(.white.opacity(0.7))
            }

            Spacer()

            Button(action: onDismiss) {
                Image(systemName: "xmark")
                    .foregroundColor(.white.opacity(0.6))
                    .font(.caption)
            }
        }
        .padding(16)
        .background(
            RoundedRectangle(cornerRadius: 14)
                .fill(Color(red: 0.15, green: 0.15, blue: 0.25))
                .shadow(color: .black.opacity(0.3), radius: 8)
        )
        .padding(.horizontal, 16)
        .padding(.top, 8)
        .onTapGesture(perform: onTap)
    }
}
