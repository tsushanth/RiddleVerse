import SwiftUI
import PhotosUI
import FirebaseAuth

struct GameRemixSheet: View {
    let puzzleType: String
    let difficulty: String
    let onDismiss: () -> Void

    @State private var remixDescription = ""
    @State private var gameTitle: String
    @State private var showGamePlay = false
    @StateObject private var speechRecognizer = SpeechRecognizer()
    @StateObject private var coinManager = CoinManager.shared
    @StateObject private var genManager = GameGenerationManager.shared
    @State private var selectedItem: PhotosPickerItem?
    @State private var selectedImage: UIImage?
    @State private var selectedImageBase64: String?
    @State private var showRateLimitSheet = false
    @State private var showSubscriptionUpgrade = false
    @State private var showCoinStore = false

    private var displayName: String {
        puzzleType
            .replacingOccurrences(of: "_", with: " ")
            .split(separator: " ")
            .map { $0.prefix(1).uppercased() + $0.dropFirst() }
            .joined(separator: " ")
    }

    init(puzzleType: String, difficulty: String, onDismiss: @escaping () -> Void) {
        self.puzzleType = puzzleType
        self.difficulty = difficulty
        self.onDismiss = onDismiss
        let name = puzzleType
            .replacingOccurrences(of: "_", with: " ")
            .split(separator: " ")
            .map { $0.prefix(1).uppercased() + $0.dropFirst() }
            .joined(separator: " ")
        let userName = Auth.auth().currentUser?.displayName ?? Auth.auth().currentUser?.email?.components(separatedBy: "@").first ?? "Player"
        _gameTitle = State(initialValue: "\(name) Custom \(userName)")
    }

    var body: some View {
        ZStack {
            Color(red: 0.1, green: 0.1, blue: 0.18)
                .ignoresSafeArea()

            VStack(spacing: 0) {
                // Top bar
                HStack {
                    Button(action: { onDismiss() }) {
                        Image(systemName: "xmark")
                            .font(.title3)
                            .foregroundColor(.white)
                            .frame(width: 40, height: 40)
                    }

                    Spacer()

                    VStack(spacing: 2) {
                        Text("Remix: \(displayName)")
                            .font(.subheadline.weight(.semibold))
                            .foregroundColor(.white)
                        Text("Create your version")
                            .font(.caption2)
                            .foregroundColor(.white.opacity(0.5))
                    }

                    Spacer()

                    Color.clear.frame(width: 40, height: 40)
                }
                .padding(.horizontal)
                .padding(.top, 12)

                Divider()
                    .background(Color.white.opacity(0.1))
                    .padding(.top, 8)

                ScrollView {
                    VStack(spacing: 20) {
                        // Marketing card
                        HStack(spacing: 14) {
                            Image(systemName: "dollarsign.circle.fill")
                                .font(.system(size: 36))
                                .foregroundColor(.green)

                            VStack(alignment: .leading, spacing: 4) {
                                Text("Create & Earn Real Money")
                                    .font(.headline)
                                    .foregroundColor(.white)
                                Text("Earn coins when others play — cash out via Stripe!")
                                    .font(.caption)
                                    .foregroundColor(.white.opacity(0.6))
                                Text("You earn 55% of every coin spent on your game")
                                    .font(.caption2)
                                    .foregroundColor(.green.opacity(0.8))
                            }
                        }
                        .padding(16)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(
                            RoundedRectangle(cornerRadius: 14)
                                .fill(Color.green.opacity(0.1))
                                .overlay(
                                    RoundedRectangle(cornerRadius: 14)
                                        .stroke(Color.green.opacity(0.2), lineWidth: 1)
                                )
                        )

                        // Title field
                        VStack(alignment: .leading, spacing: 6) {
                            Text("Game Title")
                                .font(.caption.weight(.medium))
                                .foregroundColor(.white.opacity(0.5))

                            TextField("My \(displayName) Game", text: $gameTitle)
                                .textFieldStyle(.plain)
                                .foregroundColor(.white)
                                .padding(14)
                                .background(
                                    RoundedRectangle(cornerRadius: 12)
                                        .fill(Color.white.opacity(0.08))
                                )
                        }

                        // Description field
                        VStack(alignment: .leading, spacing: 6) {
                            Text("Describe Your Version")
                                .font(.caption.weight(.medium))
                                .foregroundColor(.white.opacity(0.5))

                            ZStack(alignment: .topLeading) {
                                if remixDescription.isEmpty {
                                    Text("Describe gameplay changes, visual style, theme, difficulty tweaks, scoring rules...")
                                        .foregroundColor(.white.opacity(0.25))
                                        .padding(.horizontal, 14)
                                        .padding(.vertical, 14)
                                }
                                TextEditor(text: $remixDescription)
                                    .foregroundColor(.white)
                                    .scrollContentBackground(.hidden)
                                    .padding(.horizontal, 10)
                                    .padding(.vertical, 8)
                            }
                            .frame(height: 120)
                            .background(
                                RoundedRectangle(cornerRadius: 12)
                                    .fill(Color.white.opacity(0.08))
                            )

                            // Voice + Photo + Quality row
                            HStack(spacing: 10) {
                                Button(action: {
                                    speechRecognizer.toggleRecording()
                                    if !speechRecognizer.isRecording {
                                        remixDescription = speechRecognizer.transcript
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
                                        .fill(descriptionQualityColor)
                                        .frame(width: 8, height: 8)
                                    Text(descriptionQualityLabel)
                                        .font(.caption2)
                                        .foregroundColor(descriptionQualityColor)
                                }
                            }

                            // Quality progress bar
                            if !remixDescription.isEmpty {
                                GeometryReader { geo in
                                    ZStack(alignment: .leading) {
                                        RoundedRectangle(cornerRadius: 2)
                                            .fill(Color.white.opacity(0.1))
                                            .frame(height: 3)
                                        RoundedRectangle(cornerRadius: 2)
                                            .fill(descriptionQualityColor)
                                            .frame(width: geo.size.width * descriptionQualityProgress, height: 3)
                                            .animation(.easeInOut(duration: 0.3), value: descriptionQualityProgress)
                                    }
                                }
                                .frame(height: 3)

                                if descriptionQualityLevel < 2 {
                                    Text(descriptionQualityHint)
                                        .font(.caption2)
                                        .foregroundColor(.white.opacity(0.4))
                                }
                            }
                        }

                        // Image preview
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
                        }

                        // Suggestion chips
                        VStack(alignment: .leading, spacing: 8) {
                            Text("Quick Ideas")
                                .font(.caption.weight(.medium))
                                .foregroundColor(.white.opacity(0.5))

                            FlowLayout(spacing: 8) {
                                remixSuggestionChip("Make it harder")
                                remixSuggestionChip("Space theme")
                                remixSuggestionChip("Add a timer")
                                remixSuggestionChip("More questions")
                                remixSuggestionChip("Neon colors")
                                remixSuggestionChip("Kids friendly")
                            }
                        }

                        // Error message
                        if let error = genManager.errorMessage {
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
                            .onTapGesture { genManager.errorMessage = nil }
                        }
                    }
                    .padding(20)
                }

                // Create button
                VStack(spacing: 0) {
                    Divider()
                        .background(Color.white.opacity(0.1))

                    Button(action: {
                        if !coinManager.canAffordRemix {
                            showCoinStore = true
                        } else {
                            submitRemix()
                        }
                    }) {
                        HStack {
                            Image(systemName: "wand.and.stars")
                            Text("Create Game")
                                .fontWeight(.bold)
                            Spacer()
                            CoinCostBadge(cost: CoinManager.remixCost, balance: coinManager.balance)
                        }
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 16)
                        .background(
                            LinearGradient(
                                colors: canSubmit ? [.purple, .blue] : [.gray],
                                startPoint: .leading,
                                endPoint: .trailing
                            )
                        )
                        .cornerRadius(14)
                    }
                    .disabled(!canSubmit)
                    .padding(16)
                }
            }

            // Generation overlay
            if genManager.isGenerating && genManager.isRemixGeneration {
                Color.black.opacity(0.85)
                    .ignoresSafeArea()
                    .onTapGesture {}

                VStack(spacing: 24) {
                    Spacer()

                    Image(systemName: "wand.and.stars")
                        .font(.system(size: 48))
                        .foregroundColor(.orange)

                    Text("Creating Your Game")
                        .font(.title2.weight(.bold))
                        .foregroundColor(.white)

                    ProgressView(value: genManager.progressPercent, total: 100)
                        .tint(.orange)
                        .frame(maxWidth: 250)

                    Text(genManager.buildPhase)
                        .font(.subheadline)
                        .foregroundColor(.white.opacity(0.7))

                    Text("This may take 2-5 minutes...")
                        .font(.caption)
                        .foregroundColor(.white.opacity(0.4))

                    // Notify me & dismiss button
                    Button(action: {
                        genManager.isOnRemixSheet = false
                        onDismiss()
                    }) {
                        HStack(spacing: 8) {
                            Image(systemName: "bell.fill")
                                .font(.subheadline)
                            Text("Notify Me When Done")
                                .font(.subheadline.weight(.semibold))
                        }
                        .foregroundColor(.white)
                        .padding(.horizontal, 24)
                        .padding(.vertical, 12)
                        .background(
                            Capsule()
                                .fill(Color.white.opacity(0.15))
                                .overlay(Capsule().stroke(Color.white.opacity(0.3), lineWidth: 1))
                        )
                    }
                    .padding(.top, 8)

                    Text("You can leave this screen — we'll notify you!")
                        .font(.caption2)
                        .foregroundColor(.white.opacity(0.35))

                    Text("Your game will appear in Explore > Custom Games")
                        .font(.caption2)
                        .foregroundColor(.white.opacity(0.35))

                    Spacer()
                }
                .padding(40)
            }
        }
        .onAppear {
            genManager.isOnRemixSheet = true
        }
        .onDisappear {
            genManager.isOnRemixSheet = false
        }
        .onChange(of: speechRecognizer.transcript) { newValue in
            if speechRecognizer.isRecording {
                remixDescription = newValue
            }
        }
        .fullScreenCover(isPresented: $showGamePlay) {
            if let dir = genManager.bundleDir {
                let game = BrowseGame(
                    id: genManager.remixResultGameId ?? UUID().uuidString,
                    title: gameTitle.isEmpty ? "My \(displayName) Game" : gameTitle,
                    creatorId: Auth.auth().currentUser?.uid ?? "",
                    creatorName: "You",
                    playCount: 0,
                    initialPrompt: remixDescription,
                    createdAt: "",
                    status: "published"
                )
                GamePlayView(game: game, bundleDirectory: dir)
            }
        }
        .onChange(of: genManager.showPreview) { newValue in
            if newValue && genManager.isRemixGeneration {
                genManager.showPreview = false
                let gameId = genManager.remixResultGameId ?? ""
                coinManager.spendForRemix(gameId: gameId, creatorId: nil) { _ in }
                showGamePlay = true
            }
        }
        .sheet(isPresented: $showRateLimitSheet) {
            RateLimitCoinSheet(
                coinCost: genManager.rateLimitCoinCost,
                currentBalance: coinManager.balance,
                onUseCoins: {
                    showRateLimitSheet = false
                    submitRemix(useCoins: true)
                },
                onBuyCoins: {
                    showRateLimitSheet = false
                    showCoinStore = true
                },
                onSubscribe: {
                    showRateLimitSheet = false
                    showSubscriptionUpgrade = true
                },
                onDismiss: { showRateLimitSheet = false }
            )
            .presentationDetents([.medium])
        }
        .onChange(of: genManager.showRateLimitUpsell) { newValue in
            if newValue && genManager.isRemixGeneration {
                genManager.showRateLimitUpsell = false
                showRateLimitSheet = true
            }
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
    }

    // MARK: - Quality Indicators

    private var descriptionQualityLevel: Int {
        let len = remixDescription.trimmingCharacters(in: .whitespacesAndNewlines).count
        if len == 0 { return 0 }
        if len < 30 { return 0 }
        if len < 80 { return 1 }
        if len < 150 { return 2 }
        return 3
    }

    private var descriptionQualityColor: Color {
        switch descriptionQualityLevel {
        case 0: return remixDescription.isEmpty ? .white.opacity(0.3) : .red
        case 1: return .orange
        case 2: return .yellow
        default: return .green
        }
    }

    private var descriptionQualityLabel: String {
        switch descriptionQualityLevel {
        case 0: return remixDescription.isEmpty ? "0/500" : "Too short"
        case 1: return "Basic"
        case 2: return "Good"
        default: return "Great detail!"
        }
    }

    private var descriptionQualityProgress: CGFloat {
        let len = CGFloat(remixDescription.trimmingCharacters(in: .whitespacesAndNewlines).count)
        return min(len / 150.0, 1.0)
    }

    private var descriptionQualityHint: String {
        switch descriptionQualityLevel {
        case 0: return "Tip: Describe gameplay mechanics, visual style, and rules for best results"
        case 1: return "Add more detail — describe controls, scoring, difficulty, and visual style"
        default: return ""
        }
    }

    private var canSubmit: Bool {
        !remixDescription.trimmingCharacters(in: .whitespaces).isEmpty
        && remixDescription.count <= 500
        && !genManager.isGenerating
    }

    @ViewBuilder
    private func remixSuggestionChip(_ text: String) -> some View {
        Button(action: {
            if remixDescription.isEmpty {
                remixDescription = text
            } else {
                remixDescription += ", \(text.lowercased())"
            }
        }) {
            Text(text)
                .font(.caption)
                .foregroundColor(.white.opacity(0.8))
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
                .background(Color.white.opacity(0.1))
                .cornerRadius(16)
        }
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

    // MARK: - Networking

    private func submitRemix(useCoins: Bool = false) {
        let desc = remixDescription.trimmingCharacters(in: .whitespaces)
        guard !desc.isEmpty else { return }

        let effectiveTitle = gameTitle.trimmingCharacters(in: .whitespaces).isEmpty
            ? "My \(displayName) Game" : gameTitle.trimmingCharacters(in: .whitespaces)

        genManager.isOnRemixSheet = true
        genManager.startRemixGeneration(
            puzzleType: puzzleType,
            difficulty: difficulty,
            remixDescription: desc,
            title: effectiveTitle,
            imageBase64: selectedImageBase64,
            useCoins: useCoins
        )
    }
}

// MARK: - Rate Limit Coin Upsell Sheet
struct RateLimitCoinSheet: View {
    let coinCost: Int
    let currentBalance: Int
    let onUseCoins: () -> Void
    let onBuyCoins: () -> Void
    let onSubscribe: () -> Void
    let onDismiss: () -> Void

    var hasEnoughCoins: Bool { currentBalance >= coinCost }

    var body: some View {
        VStack(spacing: 20) {
            // Handle bar
            RoundedRectangle(cornerRadius: 3)
                .fill(Color.white.opacity(0.3))
                .frame(width: 40, height: 5)
                .padding(.top, 12)

            Image(systemName: "clock.badge.exclamationmark")
                .font(.system(size: 44))
                .foregroundColor(.orange)

            Text("Free Generations Used Up")
                .font(.title3.weight(.bold))
                .foregroundColor(.white)

            Text("You've used all 5 free generations this hour.\nSpend \(coinCost) coins to create this game now.")
                .font(.subheadline)
                .foregroundColor(.white.opacity(0.7))
                .multilineTextAlignment(.center)

            if hasEnoughCoins {
                Button(action: onUseCoins) {
                    HStack {
                        Image(systemName: "star.circle.fill")
                            .foregroundColor(.yellow)
                        Text("Use \(coinCost) Coins")
                            .fontWeight(.bold)
                        Text("(\(currentBalance) available)")
                            .font(.caption)
                            .foregroundColor(.white.opacity(0.6))
                    }
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 14)
                    .background(
                        LinearGradient(colors: [.orange, .red], startPoint: .leading, endPoint: .trailing)
                    )
                    .cornerRadius(14)
                }
            }

            Button(action: onBuyCoins) {
                HStack {
                    Image(systemName: "dollarsign.circle.fill")
                    Text("Buy Coins")
                        .fontWeight(.semibold)
                }
                .foregroundColor(.white)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 14)
                .background(Color.blue)
                .cornerRadius(14)
            }

            Button(action: onSubscribe) {
                HStack {
                    Image(systemName: "crown.fill")
                        .foregroundColor(.yellow)
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Subscribe for Unlimited")
                            .fontWeight(.semibold)
                        Text("Get unlimited generations + monthly coins")
                            .font(.caption2)
                            .foregroundColor(.white.opacity(0.6))
                    }
                }
                .foregroundColor(.white)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 14)
                .background(
                    LinearGradient(colors: [.purple, .blue], startPoint: .leading, endPoint: .trailing)
                )
                .cornerRadius(14)
            }

            Button("Maybe Later", action: onDismiss)
                .font(.subheadline)
                .foregroundColor(.white.opacity(0.5))
        }
        .padding(.horizontal, 24)
        .padding(.bottom, 30)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color(red: 0.1, green: 0.1, blue: 0.18))
        .onAppear {
            CoinManager.shared.fetchBalance()
        }
    }
}

// Simple flow layout for suggestion chips
struct FlowLayout: Layout {
    var spacing: CGFloat = 8

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let result = layout(proposal: proposal, subviews: subviews)
        return result.size
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        let result = layout(proposal: proposal, subviews: subviews)
        for (index, position) in result.positions.enumerated() {
            subviews[index].place(at: CGPoint(x: bounds.minX + position.x, y: bounds.minY + position.y), proposal: .unspecified)
        }
    }

    private func layout(proposal: ProposedViewSize, subviews: Subviews) -> (size: CGSize, positions: [CGPoint]) {
        let maxWidth = proposal.width ?? .infinity
        var positions: [CGPoint] = []
        var x: CGFloat = 0
        var y: CGFloat = 0
        var rowHeight: CGFloat = 0

        for subview in subviews {
            let size = subview.sizeThatFits(.unspecified)
            if x + size.width > maxWidth && x > 0 {
                x = 0
                y += rowHeight + spacing
                rowHeight = 0
            }
            positions.append(CGPoint(x: x, y: y))
            rowHeight = max(rowHeight, size.height)
            x += size.width + spacing
        }

        return (CGSize(width: maxWidth, height: y + rowHeight), positions)
    }
}
