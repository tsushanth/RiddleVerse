import SwiftUI
import FirebaseAuth

struct GamePreviewView: View {
    let bundleDirectory: URL
    let bundleBase64: String
    let prompt: String
    let gameId: String?
    @Environment(\.dismiss) private var dismiss
    @State private var currentScore: Int = 0
    @State private var gameOver: Bool = false
    @State private var gameErrors: [String] = []
    @State private var showErrorBanner: Bool = false
    @State private var showModifySheet: Bool = false
    @State private var modifyDescription: String = ""
    @State private var isModifying: Bool = false
    @State private var modifyPhase: String = ""
    @State private var modifyProgress: Double = 0

    // Active bundle state (can change after modify)
    @State private var activeBundleDir: URL? = nil
    @State private var activeBundleBase64: String = ""
    @State private var activeGameId: String? = nil
    @State private var reloadToken: UUID = UUID()

    // Publish state
    @State private var isPublishing: Bool = false
    @State private var isPublished: Bool = false
    @State private var publishError: String? = nil
    @State private var showCloseDialog: Bool = false

    var body: some View {
        ZStack {
            Color(red: 0.1, green: 0.1, blue: 0.18)
                .ignoresSafeArea()

            VStack(spacing: 0) {
                // Top bar
                HStack {
                    Button(action: { if isPublished { dismiss() } else { showCloseDialog = true } }) {
                        Image(systemName: "xmark")
                            .font(.title3)
                            .foregroundColor(.white)
                            .frame(width: 40, height: 40)
                    }

                    Spacer()

                    if currentScore > 0 {
                        Text("Score: \(currentScore)")
                            .font(.headline)
                            .foregroundColor(Color(red: 0.42, green: 0.36, blue: 0.90))
                    }

                    Spacer()

                    // Modify button
                    Button(action: {
                        if !gameErrors.isEmpty && modifyDescription.isEmpty {
                            modifyDescription = "The game has these issues:\n" + gameErrors.joined(separator: "\n")
                        }
                        showModifySheet = true
                    }) {
                        Image(systemName: "wand.and.stars")
                            .font(.title3)
                            .foregroundColor(.white)
                            .frame(width: 40, height: 40)
                    }

                    // Publish button
                    Button(action: {
                        Task { await publishAndShare() }
                    }) {
                        Group {
                            if isPublishing {
                                ProgressView()
                                    .tint(Color(red: 0.13, green: 0.75, blue: 0.39))
                                    .scaleEffect(0.8)
                            } else if isPublished {
                                Image(systemName: "checkmark.circle.fill")
                                    .font(.title3)
                                    .foregroundColor(Color(red: 0.13, green: 0.75, blue: 0.39))
                            } else {
                                Image(systemName: "arrow.up.circle.fill")
                                    .font(.title3)
                                    .foregroundColor(Color(red: 0.13, green: 0.75, blue: 0.39))
                            }
                        }
                        .frame(width: 40, height: 40)
                    }
                    .disabled(isPublishing || isPublished)
                }
                .padding(.horizontal)
                .padding(.top, 8)

                // Error banner
                if showErrorBanner && !gameErrors.isEmpty {
                    HStack(spacing: 8) {
                        Image(systemName: "exclamationmark.triangle.fill")
                            .foregroundColor(.orange)
                        Text("This game may have issues")
                            .font(.caption)
                            .foregroundColor(.white)
                        Spacer()
                        Button("Modify") {
                            if modifyDescription.isEmpty {
                                modifyDescription = "The game has these issues:\n" + gameErrors.joined(separator: "\n")
                            }
                            showModifySheet = true
                        }
                        .font(.caption.bold())
                        .foregroundColor(.orange)
                        Button(action: { withAnimation { showErrorBanner = false } }) {
                            Image(systemName: "xmark")
                                .font(.caption)
                                .foregroundColor(.white.opacity(0.6))
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 8)
                    .background(Color(red: 0.2, green: 0.15, blue: 0.1).opacity(0.9))
                    .transition(.move(edge: .top))
                }

                // WebView game
                GameWebView(
                    bundleDirectory: activeBundleDir ?? bundleDirectory,
                    onScoreReceived: { score in
                        currentScore = score
                    },
                    onGameOver: { finalScore in
                        currentScore = finalScore
                        gameOver = true
                    },
                    onGameError: { error in
                        if !gameErrors.contains(error) {
                            gameErrors.append(error)
                            withAnimation { showErrorBanner = true }
                        }
                    }
                )
                .id(reloadToken)

            }

            // Modify progress overlay
            if isModifying {
                Color.black.opacity(0.7)
                    .ignoresSafeArea()
                VStack(spacing: 16) {
                    ProgressView()
                        .scaleEffect(1.5)
                        .tint(.white)
                    Text("Modifying game...")
                        .font(.headline)
                        .foregroundColor(.white)
                    if !modifyPhase.isEmpty {
                        Text(modifyPhase)
                            .font(.caption)
                            .foregroundColor(.white.opacity(0.7))
                    }
                    if modifyProgress > 0 {
                        ProgressView(value: modifyProgress, total: 100)
                            .tint(Color(red: 0.42, green: 0.36, blue: 0.90))
                            .frame(width: 200)
                    }
                }
            }

            // Publish error toast
            if let err = publishError {
                VStack {
                    Text(err)
                        .font(.caption)
                        .foregroundColor(.white)
                        .padding(.horizontal, 16)
                        .padding(.vertical, 8)
                        .background(Color.red.opacity(0.85))
                        .cornerRadius(10)
                        .padding(.top, 8)
                    Spacer()
                }
            }

            // Auto-saved confirmation toast (shows on initial load)
            VStack {
                Spacer()
                VStack(spacing: 4) {
                    Text("Game ready! 🎮")
                        .font(.headline)
                        .foregroundColor(.white)
                    Text("Tap 💰 in the top bar to publish and earn real cash!")
                        .font(.caption)
                        .foregroundColor(Color(red: 1.0, green: 0.84, blue: 0.0))
                }
                .padding()
                .background(Color(red: 0.15, green: 0.68, blue: 0.38))
                .cornerRadius(12)
                .padding(.bottom, 16)
                .opacity(showSavedToast ? 1 : 0)
            }
            .transition(.move(edge: .bottom))
        }
        .onAppear {
            activeBundleBase64 = bundleBase64
            activeGameId = gameId
            // Show auto-saved toast briefly
            withAnimation { showSavedToast = true }
            DispatchQueue.main.asyncAfter(deadline: .now() + 3) {
                withAnimation { showSavedToast = false }
            }
        }
        .sheet(isPresented: $showModifySheet) {
            modifySheet
        }
        .confirmationDialog("Publish before you go! 💰", isPresented: $showCloseDialog, titleVisibility: .visible) {
            Button("Publish & Earn 💰") {
                Task { await publishAndShare() }
            }
            Button("Leave without saving", role: .destructive) {
                dismiss()
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("Players can find your game and you earn real cash — 55% of every coin spent on it.")
        }
    }

    @State private var showSavedToast: Bool = false

    // MARK: - Modify Sheet

    private var modifySheet: some View {
        NavigationView {
            VStack(spacing: 16) {
                Text("Describe what's wrong or what to change:")
                    .font(.subheadline)
                    .foregroundColor(.secondary)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal)

                TextEditor(text: $modifyDescription)
                    .frame(minHeight: 120)
                    .padding(8)
                    .background(Color(.systemGray6))
                    .cornerRadius(8)
                    .padding(.horizontal)

                Text("\(modifyDescription.count)/500")
                    .font(.caption)
                    .foregroundColor(modifyDescription.count > 500 ? .red : .secondary)
                    .frame(maxWidth: .infinity, alignment: .trailing)
                    .padding(.horizontal)

                Button(action: {
                    showModifySheet = false
                    modifyGame()
                }) {
                    HStack {
                        Image(systemName: "wand.and.stars")
                        Text("Fix It")
                    }
                    .font(.headline)
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .padding()
                    .background(
                        modifyDescription.trimmingCharacters(in: .whitespaces).isEmpty || modifyDescription.count > 500
                        ? Color.gray : Color(red: 0.42, green: 0.36, blue: 0.90)
                    )
                    .cornerRadius(12)
                }
                .disabled(modifyDescription.trimmingCharacters(in: .whitespaces).isEmpty || modifyDescription.count > 500)
                .padding(.horizontal)

                Spacer()
            }
            .padding(.top)
            .navigationTitle("Modify Game")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { showModifySheet = false }
                }
            }
        }
    }

    // MARK: - Modify Flow (uses /:gameId/customize)

    private func modifyGame() {
        guard let gid = activeGameId else {
            gameErrors.append("Cannot modify: game has no ID")
            showErrorBanner = true
            return
        }

        isModifying = true
        modifyPhase = "Connecting..."
        modifyProgress = 0

        let userId = Auth.auth().currentUser?.uid ?? "anonymous"

        Task {
            do {
                guard let url = URL(string: "https://puzzleverseai.com/api/game-creation/\(gid)/customize") else {
                    throw URLError(.badURL)
                }

                var request = URLRequest(url: url)
                request.httpMethod = "POST"
                request.setValue("application/json", forHTTPHeaderField: "Content-Type")
                request.setValue("text/event-stream", forHTTPHeaderField: "Accept")
                request.timeoutInterval = 600

                let body: [String: Any] = [
                    "userId": userId,
                    "customizeDescription": modifyDescription.trimmingCharacters(in: .whitespaces)
                ]
                request.httpBody = try JSONSerialization.data(withJSONObject: body)

                let (bytes, response) = try await URLSession.shared.bytes(for: request)

                guard let httpResponse = response as? HTTPURLResponse, httpResponse.statusCode < 400 else {
                    throw NSError(domain: "Modify", code: 0,
                                  userInfo: [NSLocalizedDescriptionKey: "Server error during modification"])
                }

                var newBundle: String?
                var newGameId: String?
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
                                    modifyPhase = message
                                    modifyProgress = pct
                                }
                            case "result":
                                newBundle = json["bundle"] as? String
                                newGameId = json["gameId"] as? String
                            case "error":
                                let errMsg = json["error"] as? String ?? "Unknown error"
                                throw NSError(domain: "Modify", code: 0,
                                              userInfo: [NSLocalizedDescriptionKey: errMsg])
                            default:
                                break
                            }
                        }
                    }
                }

                guard let bundle = newBundle else {
                    throw NSError(domain: "Modify", code: 0,
                                  userInfo: [NSLocalizedDescriptionKey: "No bundle received from modification"])
                }

                // Extract new bundle and reload
                let dir = try ZipExtractor.extractBundle(base64: bundle)

                await MainActor.run {
                    activeBundleDir = dir
                    activeBundleBase64 = bundle
                    if let gid = newGameId { activeGameId = gid }
                    gameErrors.removeAll()
                    showErrorBanner = false
                    modifyDescription = ""
                    currentScore = 0
                    gameOver = false
                    reloadToken = UUID() // Force WebView reload
                    isModifying = false
                }

            } catch {
                NSLog("[Modify] Error: %@", error.localizedDescription)
                await MainActor.run {
                    isModifying = false
                    gameErrors.append("Modify failed: \(error.localizedDescription)")
                    showErrorBanner = true
                }
            }
        }
    }

    private func publishAndShare() async {
        isPublishing = true
        publishError = nil

        let bundle = activeBundleBase64.isEmpty ? bundleBase64 : activeBundleBase64
        let userId = Auth.auth().currentUser?.uid ?? "anonymous"
        let creatorName = Auth.auth().currentUser?.displayName
            ?? Auth.auth().currentUser?.email?.components(separatedBy: "@").first
            ?? "Anonymous"
        let title = String(prompt.prefix(60))

        do {
            guard let url = URL(string: "https://puzzleverseai.com/api/game-creation/save") else {
                throw URLError(.badURL)
            }
            var request = URLRequest(url: url)
            request.httpMethod = "POST"
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            request.timeoutInterval = 30

            let body: [String: Any] = [
                "title": title,
                "bundle": bundle,
                "creatorId": userId,
                "creatorName": creatorName,
                "initialPrompt": prompt
            ]
            request.httpBody = try JSONSerialization.data(withJSONObject: body)

            let (_, response) = try await URLSession.shared.data(for: request)
            if let http = response as? HTTPURLResponse, http.statusCode >= 400 {
                throw NSError(domain: "Publish", code: http.statusCode,
                              userInfo: [NSLocalizedDescriptionKey: "Server error \(http.statusCode)"])
            }

            await MainActor.run {
                isPublishing = false
                isPublished = true
                shareGame()
                dismiss()
            }
        } catch {
            await MainActor.run {
                isPublishing = false
                publishError = "Couldn't publish: \(error.localizedDescription)"
            }
        }
    }

    private func shareGame() {
        let text = "I just built this game on RiddleVerse — play it and I earn real cash! 🎮💰"
        let activityVC = UIActivityViewController(activityItems: [text], applicationActivities: nil)
        if let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
           let rootVC = windowScene.windows.first?.rootViewController {
            rootVC.present(activityVC, animated: true)
        }
    }
}
