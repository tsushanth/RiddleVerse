import SwiftUI
import PhotosUI
import UserNotifications
import FirebaseAuth

struct GameCreationView: View {
    @EnvironmentObject var generationManager: GameGenerationManager
    var selectedSection: Binding<Int>? = nil
    private var selectedSectionValue: Binding<Int> { selectedSection ?? $_localSection }
    @State private var _localSection = 1 // 0 = Create, 1 = Explore (default to Explore)
    @State private var promptText: String = ""
    @StateObject private var speechRecognizer = SpeechRecognizer()
    @State private var selectedItem: PhotosPickerItem?
    @State private var selectedImage: UIImage?
    @State private var selectedImageBase64: String?
    @StateObject private var coinManager = CoinManager.shared
    @State private var showCoinStore = false
    @State private var showSubscriptionUpgrade = false
    @State private var showCreationPaywall = false
    @StateObject private var subscriptionManagerRef = SubscriptionManager.shared

    // Fallback suggestions in case API is unreachable
    private static let fallbackSuggestions: [(label: String, prompt: String)] = [
        ("Snake game", "A classic snake game with neon glow effects. Swipe to change direction. Speed increases every 5 points. Walls and self-bite end the game."),
        ("Brick breaker", "A brick breaker game with colorful bricks. Drag paddle to bounce ball. Power-ups: wider paddle, multi-ball. 3 lives."),
        ("Memory cards", "A memory card matching game with 6 pairs in a 3x4 grid. Tap to flip and find matches. Track moves and time."),
        ("Whack-a-mole", "Whack-a-mole with emoji characters on a 3x3 grid. Tap moles to score. Golden moles = bonus. 30 second timer."),
        ("Space invaders", "Space invaders — drag spaceship, tap to shoot. Aliens descend and shoot back. Wave system gets harder."),
        ("Trivia quiz", "Science trivia with 10 questions, 4 options each. 15-second timer. Faster = more points.")
    ]

    @State private var suggestions: [(label: String, prompt: String)] = GameCreationView.fallbackSuggestions
    @State private var notifyConfirmed = false

    var body: some View {
        ZStack {
            Color(red: 0.1, green: 0.1, blue: 0.18)
                .ignoresSafeArea()

            VStack(spacing: 0) {
                // Section picker
                HStack(spacing: 0) {
                    Button(action: { selectedSectionValue.wrappedValue = 0 }) {
                        HStack(spacing: 4) {
                            Text("Create")
                                .fontWeight(selectedSectionValue.wrappedValue == 0 ? .bold : .regular)
                            Text("EARN 💰")
                                .font(.system(size: 9, weight: .bold))
                                .foregroundColor(.white)
                                .padding(.horizontal, 4)
                                .padding(.vertical, 1)
                                .background(RoundedRectangle(cornerRadius: 4).fill(Color(red: 0.13, green: 0.75, blue: 0.39)))
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 8)
                        .background(selectedSectionValue.wrappedValue == 0 ? Color.white.opacity(0.15) : Color.clear)
                        .cornerRadius(8)
                    }
                    .foregroundColor(selectedSectionValue.wrappedValue == 0 ? .orange : .white.opacity(0.5))

                    Button(action: { selectedSectionValue.wrappedValue = 1 }) {
                        Text("Explore")
                            .fontWeight(selectedSectionValue.wrappedValue == 1 ? .bold : .regular)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 8)
                            .background(selectedSectionValue.wrappedValue == 1 ? Color.white.opacity(0.15) : Color.clear)
                            .cornerRadius(8)
                    }
                    .foregroundColor(selectedSectionValue.wrappedValue == 1 ? .orange : .white.opacity(0.5))
                }
                .background(Color.white.opacity(0.08))
                .cornerRadius(8)
                .padding(.horizontal)
                .padding(.top, 12)
                .padding(.bottom, 4)

                if selectedSectionValue.wrappedValue == 0 {
                    createContent
                } else {
                    GameBrowseView()
                }
            }
        }
        .task {
            // Fetch dynamic suggestions from server
            guard let url = URL(string: "https://puzzleverseai.com/api/game-creation/suggestions?count=6") else { return }
            do {
                let (data, _) = try await URLSession.shared.data(from: url)
                if let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                   let arr = json["suggestions"] as? [[String: Any]] {
                    let fetched: [(label: String, prompt: String)] = arr.compactMap { s in
                        guard let label = s["label"] as? String, !label.isEmpty,
                              let prompt = s["prompt"] as? String, !prompt.isEmpty else { return nil }
                        return (label: label, prompt: prompt)
                    }
                    if !fetched.isEmpty {
                        suggestions = fetched
                    }
                }
            } catch {
                // Keep fallback suggestions
            }
        }
        .onChange(of: speechRecognizer.transcript) { newValue in
            if speechRecognizer.isRecording {
                promptText = newValue
            }
        }
        .fullScreenCover(isPresented: $generationManager.showPreview) {
            if let dir = generationManager.bundleDir, let base64 = generationManager.bundleBase64 {
                GamePreviewView(
                    bundleDirectory: dir,
                    bundleBase64: base64,
                    prompt: generationManager.currentPrompt,
                    gameId: generationManager.resultGameId
                )
            }
        }
        .sheet(isPresented: $generationManager.showRateLimitUpsell) {
            RateLimitCoinSheet(
                coinCost: generationManager.rateLimitCoinCost,
                currentBalance: coinManager.balance,
                onUseCoins: {
                    generationManager.showRateLimitUpsell = false
                    generationManager.startGeneration(
                        prompt: promptText,
                        imageBase64: selectedImageBase64,
                        useCoins: true
                    )
                },
                onBuyCoins: {
                    generationManager.showRateLimitUpsell = false
                    showCoinStore = true
                },
                onSubscribe: {
                    generationManager.showRateLimitUpsell = false
                    showSubscriptionUpgrade = true
                },
                onDismiss: {
                    generationManager.showRateLimitUpsell = false
                }
            )
            .presentationDetents([.medium])
            .presentationDragIndicator(.visible)
        }
        .sheet(isPresented: $showCoinStore) {
            CoinStorePaywallView()
        }
        .fullScreenCover(isPresented: $showSubscriptionUpgrade) {
            RemotePaywallView(
                context: .gameCreation,
                targetTier: .premium,
                onSuccess: { showSubscriptionUpgrade = false },
                onCancel: { showSubscriptionUpgrade = false }
            )
        }
        .fullScreenCover(isPresented: $showCreationPaywall) {
            RemotePaywallView(
                context: .gameCreation,
                targetTier: .premium,
                onSuccess: {
                    showCreationPaywall = false
                    // After subscribing, proceed with generation
                    notifyConfirmed = false
                    generationManager.startGeneration(prompt: promptText, imageBase64: selectedImageBase64)
                },
                onCancel: {
                    showCreationPaywall = false
                }
            )
        }
    }

    private var createContent: some View {
        ScrollView {
            VStack(spacing: 24) {
                // Earn banner
                VStack(spacing: 8) {
                    HStack(spacing: 10) {
                        Text("💰")
                            .font(.title)
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Build Games, Earn Real Cash!")
                                .font(.subheadline.weight(.bold))
                                .foregroundColor(.white)
                            Text("Create a game → Share with friends → Earn coins when they play → Cash out real money via Stripe!")
                                .font(.caption)
                                .foregroundColor(.white.opacity(0.85))
                        }
                        Spacer()
                    }
                    HStack(spacing: 16) {
                        Label("55% Revenue Share", systemImage: "percent")
                        Spacer()
                        Label("Cash Out Anytime", systemImage: "banknote")
                    }
                    .font(.system(size: 10, weight: .semibold))
                    .foregroundColor(.white.opacity(0.9))
                }
                .padding(12)
                .background(
                    LinearGradient(colors: [Color(red: 0.13, green: 0.75, blue: 0.39), Color(red: 0.08, green: 0.55, blue: 0.3)], startPoint: .topLeading, endPoint: .bottomTrailing)
                )
                .cornerRadius(12)
                .padding(.top, 12)

                // Header
                VStack(spacing: 8) {
                    Text("Create a Game")
                        .font(.system(size: 28, weight: .bold))
                        .foregroundColor(.white)

                    Text("Describe any game and AI will build it for you")
                        .font(.subheadline)
                        .foregroundColor(.white.opacity(0.6))
                }

                    // Input area
                    VStack(spacing: 12) {
                        ZStack(alignment: .topLeading) {
                            if promptText.isEmpty {
                                Text("Describe your game...")
                                    .foregroundColor(.white.opacity(0.3))
                                    .padding(.horizontal, 16)
                                    .padding(.vertical, 14)
                            }

                            TextEditor(text: $promptText)
                                .frame(minHeight: 100, maxHeight: 150)
                                .padding(.horizontal, 12)
                                .padding(.vertical, 8)
                                .foregroundColor(.white)
                                .scrollContentBackground(.hidden)
                                .tint(.orange)
                        }
                        .background(
                            RoundedRectangle(cornerRadius: 16)
                                .fill(Color.white.opacity(0.08))
                        )
                        .overlay(
                            RoundedRectangle(cornerRadius: 16)
                                .stroke(Color.white.opacity(0.15), lineWidth: 1)
                        )

                        // Prompt quality indicator + Voice + Image
                        HStack(spacing: 10) {
                            Button(action: {
                                speechRecognizer.toggleRecording()
                                if !speechRecognizer.isRecording {
                                    promptText = speechRecognizer.transcript
                                }
                            }) {
                                HStack(spacing: 6) {
                                    Image(systemName: speechRecognizer.isRecording ? "mic.fill" : "mic")
                                        .foregroundColor(speechRecognizer.isRecording ? .red : .orange)
                                    Text(speechRecognizer.isRecording ? "Listening..." : "Voice")
                                        .font(.caption)
                                        .foregroundColor(.white.opacity(0.7))
                                }
                                .padding(.horizontal, 12)
                                .padding(.vertical, 8)
                                .background(
                                    Capsule()
                                        .fill(speechRecognizer.isRecording ? Color.red.opacity(0.2) : Color.white.opacity(0.08))
                                )
                            }

                            PhotosPicker(selection: $selectedItem, matching: .images) {
                                HStack(spacing: 6) {
                                    Image(systemName: "photo")
                                        .foregroundColor(.orange)
                                    Text(selectedImageBase64 != nil ? "Image added" : "Reference")
                                        .font(.caption)
                                        .foregroundColor(.white.opacity(0.7))
                                }
                                .padding(.horizontal, 12)
                                .padding(.vertical, 8)
                                .background(
                                    Capsule()
                                        .fill(selectedImageBase64 != nil ? Color.orange.opacity(0.2) : Color.white.opacity(0.08))
                                )
                            }
                            .onChange(of: selectedItem) { newItem in
                                guard let newItem else { return }
                                Task {
                                    if let data = try? await newItem.loadTransferable(type: Data.self),
                                       let uiImage = UIImage(data: data) {
                                        let resized = resizeImage(uiImage, maxDimension: 1024)
                                        if let jpegData = resized.jpegData(compressionQuality: 0.7) {
                                            selectedImage = resized
                                            selectedImageBase64 = jpegData.base64EncodedString()
                                        }
                                    }
                                }
                            }

                            Spacer()

                            // Quality indicator
                            HStack(spacing: 6) {
                                Circle()
                                    .fill(promptQualityColor)
                                    .frame(width: 8, height: 8)
                                Text(promptQualityLabel)
                                    .font(.caption2)
                                    .foregroundColor(promptQualityColor)
                            }
                        }

                        // Quality hint bar
                        if !promptText.isEmpty {
                            GeometryReader { geo in
                                ZStack(alignment: .leading) {
                                    RoundedRectangle(cornerRadius: 2)
                                        .fill(Color.white.opacity(0.1))
                                        .frame(height: 3)
                                    RoundedRectangle(cornerRadius: 2)
                                        .fill(promptQualityColor)
                                        .frame(width: geo.size.width * promptQualityProgress, height: 3)
                                        .animation(.easeInOut(duration: 0.3), value: promptQualityProgress)
                                }
                            }
                            .frame(height: 3)

                            if promptQualityLevel < 2 {
                                Text(promptQualityHint)
                                    .font(.caption2)
                                    .foregroundColor(.white.opacity(0.4))
                                    .transition(.opacity)
                            }
                        }
                    }
                    .padding(.horizontal)

                    // Image preview thumbnail
                    if let img = selectedImage {
                        HStack(spacing: 10) {
                            SwiftUI.Image(uiImage: img)
                                .resizable()
                                .aspectRatio(contentMode: .fill)
                                .frame(width: 56, height: 56)
                                .clipShape(RoundedRectangle(cornerRadius: 8))

                            VStack(alignment: .leading, spacing: 2) {
                                Text("Reference image")
                                    .font(.caption)
                                    .fontWeight(.medium)
                                    .foregroundColor(.white.opacity(0.8))
                                Text("AI will use this as visual context")
                                    .font(.caption2)
                                    .foregroundColor(.white.opacity(0.4))
                            }

                            Spacer()

                            Button(action: {
                                selectedImage = nil
                                selectedImageBase64 = nil
                                selectedItem = nil
                            }) {
                                Image(systemName: "xmark")
                                    .font(.caption)
                                    .foregroundColor(.white.opacity(0.5))
                                    .frame(width: 28, height: 28)
                            }
                        }
                        .padding(8)
                        .background(
                            RoundedRectangle(cornerRadius: 12)
                                .fill(Color.white.opacity(0.06))
                        )
                        .padding(.horizontal)
                    }

                    // Suggestion chips
                    VStack(alignment: .leading, spacing: 10) {
                        Text("Try these ideas")
                            .font(.caption)
                            .foregroundColor(.white.opacity(0.5))
                            .padding(.horizontal)

                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 8) {
                                ForEach(suggestions, id: \.label) { suggestion in
                                    Button(action: { promptText = suggestion.prompt }) {
                                        Text(suggestion.label)
                                            .font(.caption)
                                            .foregroundColor(.white.opacity(0.8))
                                            .padding(.horizontal, 14)
                                            .padding(.vertical, 8)
                                            .background(
                                                Capsule()
                                                    .fill(Color.white.opacity(0.08))
                                            )
                                            .overlay(
                                                Capsule()
                                                    .stroke(Color.white.opacity(0.1), lineWidth: 1)
                                            )
                                    }
                                }
                            }
                            .padding(.horizontal)
                        }
                    }

                    // Progress card (shown during generation)
                    if generationManager.isGenerating {
                        VStack(alignment: .leading, spacing: 10) {
                            HStack(spacing: 10) {
                                ProgressView()
                                    .progressViewStyle(CircularProgressViewStyle(tint: .white))
                                    .scaleEffect(0.8)
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(generationManager.buildPhase.isEmpty ? "Connecting..." : generationManager.buildPhase)
                                        .font(.subheadline.bold())
                                        .foregroundColor(.white)
                                    if !generationManager.buildDetail.isEmpty {
                                        Text(generationManager.buildDetail)
                                            .font(.caption)
                                            .foregroundColor(.white.opacity(0.7))
                                    }
                                }
                            }

                            // Progress bar
                            GeometryReader { geo in
                                ZStack(alignment: .leading) {
                                    RoundedRectangle(cornerRadius: 4)
                                        .fill(Color.white.opacity(0.15))
                                        .frame(height: 6)
                                    RoundedRectangle(cornerRadius: 4)
                                        .fill(
                                            LinearGradient(
                                                colors: [.orange, Color(red: 0.9, green: 0.4, blue: 0.1)],
                                                startPoint: .leading,
                                                endPoint: .trailing
                                            )
                                        )
                                        .frame(width: max(0, geo.size.width * generationManager.progressPercent / 100.0), height: 6)
                                        .animation(.easeInOut(duration: 0.5), value: generationManager.progressPercent)
                                }
                            }
                            .frame(height: 6)

                            // Percentage + ETA
                            HStack {
                                Text("\(Int(generationManager.progressPercent))%")
                                    .font(.caption.bold())
                                    .foregroundColor(.white)
                                Spacer()
                                if generationManager.estimatedSecondsRemaining > 0 {
                                    Text(formatETA(generationManager.estimatedSecondsRemaining))
                                        .font(.caption)
                                        .foregroundColor(.white.opacity(0.6))
                                }
                            }

                            // Notify me button
                            if notifyConfirmed {
                                HStack {
                                    Text("\u{1F514}")
                                        .font(.caption)
                                    Text("We'll notify you when it's ready!")
                                        .font(.caption)
                                        .foregroundColor(Color(red: 0.15, green: 0.68, blue: 0.38))
                                }
                                .frame(maxWidth: .infinity)
                                .padding(.top, 4)
                            } else {
                                Button(action: {
                                    UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound]) { _, _ in }
                                    notifyConfirmed = true
                                }) {
                                    Text("\u{1F514}  Notify me when done")
                                        .font(.subheadline)
                                        .foregroundColor(.orange)
                                        .frame(maxWidth: .infinity)
                                        .padding(.vertical, 8)
                                        .overlay(
                                            RoundedRectangle(cornerRadius: 10)
                                                .stroke(Color.orange.opacity(0.5), lineWidth: 1)
                                        )
                                }
                                .padding(.top, 4)
                            }
                        }
                        .padding(16)
                        .background(
                            RoundedRectangle(cornerRadius: 14)
                                .fill(Color.white.opacity(0.06))
                        )
                        .overlay(
                            RoundedRectangle(cornerRadius: 14)
                                .stroke(Color.orange.opacity(0.2), lineWidth: 1)
                        )
                        .padding(.horizontal)
                    }

                    // Generate button
                    Button(action: generateGame) {
                        HStack {
                            if generationManager.isGenerating {
                                ProgressView()
                                    .progressViewStyle(CircularProgressViewStyle(tint: .white))
                                    .scaleEffect(0.8)
                                Text("Generating...")
                            } else {
                                Image(systemName: "wand.and.stars")
                                Text("Generate Game")
                            }
                        }
                        .font(.headline)
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .padding()
                        .background(
                            LinearGradient(
                                colors: [Color.orange, Color(red: 0.9, green: 0.4, blue: 0.1)],
                                startPoint: .leading,
                                endPoint: .trailing
                            )
                        )
                        .cornerRadius(14)
                        .opacity(canGenerate ? 1 : 0.5)
                    }
                    .disabled(!canGenerate)
                    .padding(.horizontal)

                    // Error message
                    if let error = generationManager.errorMessage {
                        let isQuotaError = error.localizedCaseInsensitiveContains("usage limit") ||
                            error.localizedCaseInsensitiveContains("resets")

                        HStack(alignment: .top, spacing: 12) {
                            Text(isQuotaError ? "\u{23F0}" : "\u{26A0}\u{FE0F}")
                                .font(.title2)

                            VStack(alignment: .leading, spacing: 4) {
                                Text(isQuotaError ? "Usage Limit Reached" : "Error")
                                    .font(.subheadline)
                                    .fontWeight(.bold)
                                    .foregroundColor(.white)

                                Text(error)
                                    .font(.caption)
                                    .foregroundColor(.white.opacity(0.8))
                            }
                        }
                        .padding(16)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(
                            RoundedRectangle(cornerRadius: 12)
                                .fill(isQuotaError ? Color.orange.opacity(0.15) : Color.red.opacity(0.15))
                        )
                        .padding(.horizontal)
                    }

                    Spacer(minLength: 40)
                }
            }
        }

    // MARK: - Prompt Quality

    /// 0 = too short, 1 = basic, 2 = good, 3 = great
    private var promptQualityLevel: Int {
        let trimmed = promptText.trimmingCharacters(in: .whitespacesAndNewlines)
        let len = trimmed.count
        if len == 0 { return 0 }
        if len < 30 { return 0 }
        if len < 80 { return 1 }
        if len < 150 { return 2 }
        return 3
    }

    private var promptQualityColor: Color {
        switch promptQualityLevel {
        case 0: return promptText.isEmpty ? .white.opacity(0.3) : .red
        case 1: return .orange
        case 2: return .yellow
        default: return .green
        }
    }

    private var promptQualityLabel: String {
        switch promptQualityLevel {
        case 0: return promptText.isEmpty ? "0/500" : "Too short"
        case 1: return "Basic"
        case 2: return "Good"
        default: return "Great detail!"
        }
    }

    private var promptQualityProgress: CGFloat {
        let len = CGFloat(promptText.trimmingCharacters(in: .whitespacesAndNewlines).count)
        return min(len / 150.0, 1.0)
    }

    private var promptQualityHint: String {
        switch promptQualityLevel {
        case 0: return "Tip: Describe gameplay mechanics, visual style, and rules for best results"
        case 1: return "Add more detail — describe controls, scoring, difficulty, and visual style"
        default: return ""
        }
    }

    private var canGenerate: Bool {
        !promptText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty &&
        promptText.count <= 500 &&
        !generationManager.isGenerating
    }

    // MARK: - Actions

    private func generateGame() {
        guard canGenerate else { return }

        // Free users: show paywall on first game creation attempt (limit = 1)
        if !subscriptionManagerRef.hasActiveSubscription() {
            let creationsUsed = UserDefaults.standard.integer(forKey: "com.riddleverse.freeGameCreations")
            if creationsUsed >= 1 {
                showCreationPaywall = true
                return
            }
            // Record this free creation
            UserDefaults.standard.set(creationsUsed + 1, forKey: "com.riddleverse.freeGameCreations")
        }

        notifyConfirmed = false
        generationManager.startGeneration(prompt: promptText, imageBase64: selectedImageBase64)
    }

    private func resizeImage(_ image: UIImage, maxDimension: CGFloat) -> UIImage {
        let size = image.size
        let scale = min(maxDimension / max(size.width, size.height), 1.0)
        if scale >= 1.0 { return image }
        let newSize = CGSize(width: size.width * scale, height: size.height * scale)
        let renderer = UIGraphicsImageRenderer(size: newSize)
        return renderer.image { _ in
            image.draw(in: CGRect(origin: .zero, size: newSize))
        }
    }

    private func formatETA(_ seconds: Int) -> String {
        if seconds < 60 {
            return "~\(seconds)s remaining"
        } else {
            let min = seconds / 60
            let sec = seconds % 60
            return "~\(min)m \(sec)s remaining"
        }
    }

    // MARK: - Save

    private func saveGame(title: String, bundle: String) {
        guard let url = URL(string: "https://puzzleverseai.com/api/game-creation/save") else { return }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")

        let body: [String: Any] = [
            "title": title,
            "bundle": bundle,
            "creatorId": Auth.auth().currentUser?.uid ?? "anonymous",
            "creatorName": Auth.auth().currentUser?.displayName ?? Auth.auth().currentUser?.email?.components(separatedBy: "@").first ?? "Anonymous",
            "initialPrompt": promptText
        ]

        request.httpBody = try? JSONSerialization.data(withJSONObject: body)

        URLSession.shared.dataTask(with: request) { _, _, _ in
            // Fire and forget for MVP
        }.resume()
    }
}
