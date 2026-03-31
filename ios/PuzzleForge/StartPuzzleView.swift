//
//  StartPuzzleView.swift (Updated with All Tutorial Integration)
//  PuzzleForge
//
//  Created by Sushanth Tiruvaipati on 6/9/25.
//

import SwiftUI
import FirebaseAuth

// MARK: - Tutorial Preferences Manager
class TutorialPreferences: ObservableObject {
    private let userDefaults = UserDefaults.standard

    func shouldShowTutorialPrompt(for puzzleType: String) -> Bool {
        let key = "tutorial_seen_\(puzzleType.lowercased())"
        return !userDefaults.bool(forKey: key)
    }

    func markTutorialSeen(for puzzleType: String) {
        let key = "tutorial_seen_\(puzzleType.lowercased())"
        userDefaults.set(true, forKey: key)
    }

    func resetTutorialForTesting(puzzleType: String) {
        let key = "tutorial_seen_\(puzzleType.lowercased())"
        userDefaults.removeObject(forKey: key)
    }

    /// Check if auto-tutorial is enabled (default: true for first-time experience)
    func isAutoTutorialEnabled() -> Bool {
        // Default to true if not set
        if userDefaults.object(forKey: "auto_tutorial_enabled") == nil {
            return true
        }
        return userDefaults.bool(forKey: "auto_tutorial_enabled")
    }

    /// Enable or disable auto-tutorial for first-time puzzle plays
    func setAutoTutorialEnabled(_ enabled: Bool) {
        userDefaults.set(enabled, forKey: "auto_tutorial_enabled")
    }

    /// Check if this puzzle type has a tutorial available (matches Android TutorialPreferences)
    func hasInteractiveTutorial(puzzleType: String) -> Bool {
        let normalizedType = puzzleType.lowercased()
            .replacingOccurrences(of: " ", with: "")
            .replacingOccurrences(of: "_", with: "")
        return TutorialContentLibrary.supportedTypes.contains(normalizedType)
    }

    /// Check if tutorial should auto-show for first-time users
    func shouldAutoShowTutorial(puzzleType: String) -> Bool {
        return !hasSeenTutorial(puzzleType) &&
               isAutoTutorialEnabled() &&
               hasInteractiveTutorial(puzzleType: puzzleType)
    }

    private func hasSeenTutorial(_ puzzleType: String) -> Bool {
        let key = "tutorial_seen_\(puzzleType.lowercased())"
        return userDefaults.bool(forKey: key)
    }

    /// Reset all tutorial preferences
    func resetAllTutorialPreferences() {
        let keys = userDefaults.dictionaryRepresentation().keys.filter { $0.hasPrefix("tutorial_seen_") }
        keys.forEach { userDefaults.removeObject(forKey: $0) }
    }
}

// MARK: - Help Button Component
struct HelpButton: View {
    let onShowTutorial: () -> Void
    
    var body: some View {
        Button(action: onShowTutorial) {
            Image(systemName: "questionmark.circle")
                .font(.title3)
                .foregroundColor(.blue)
                .frame(width: 32, height: 32)
        }
    }
}

private func getPuzzleDisplayName(_ puzzleType: String) -> String {
    switch puzzleType.lowercased() {
    case "crossword": return "Crossword Puzzles"
    case "crypto": return "Crypto Puzzles" // NEW
    case "wordsnake": return "Word Snake Puzzles" // NEW
    case "math": return "Math Problems"
    case "trivia": return "Trivia"
    case "synonyms": return "Synonyms"
    case "antonyms": return "Antonyms"
    case "memorysquares": return "Memory Squares"
    case "mathestimation": return "Math Estimation"
    case "division": return "Division"
    case "anagram": return "Anagram"
    case "average": return "Average Calculations"
    case "percentages": return "Percentage Calculations"
    case "discounts": return "Discount Calculations"
    case "purchasing": return "Subscription Calculations"
    case "conversion": return "Unit Conversions"
    case "subtraction": return "Subtraction"
    case "mathtipping": return "Tip Calculations"
    default: return puzzleType.capitalized
    }
}

// MARK: - Tutorial Banner Component
struct TutorialBanner: View {
    let puzzleType: String
    let onStartTutorial: () -> Void
    let onDismiss: () -> Void
    
    var body: some View {
        VStack(spacing: 12) {
            HStack {
                VStack(alignment: .leading, spacing: 4) {
                    HStack {
                        Image(systemName: "lightbulb.fill")
                            .foregroundColor(.yellow)
                            .font(.caption)
                        
                        Text("New to \(getPuzzleDisplayName(puzzleType))?")
                            .font(.caption)
                            .fontWeight(.semibold)
                            .foregroundColor(.primary)
                    }
                    
                    Text("Take a quick interactive tutorial to get started!")
                        .font(.caption2)
                        .foregroundColor(.secondary)
                        .lineLimit(2)
                }
                
                Spacer()
                
                Button("Dismiss", action: onDismiss)
                    .font(.caption2)
                    .foregroundColor(.blue)
            }
            
            HStack(spacing: 12) {
                Button(action: onStartTutorial) {
                    HStack {
                        Image(systemName: "play.fill")
                            .font(.caption2)
                        Text("Start Tutorial")
                            .font(.caption)
                            .fontWeight(.medium)
                    }
                    .foregroundColor(.white)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 8)
                    .background(Color.blue)
                    .cornerRadius(16)
                }
                
                Spacer()
                
                Text("2-3 min")
                    .font(.caption2)
                    .foregroundColor(.secondary)
            }
        }
        .padding(12)
        .background(Color.blue.opacity(0.1))
        .cornerRadius(12)
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(Color.blue.opacity(0.3), lineWidth: 1)
        )
    }
}


struct StartPuzzleView: View {
    let quizTitle: String
    let puzzleType: String
    @Binding var selectedDifficulty: String
    let onPlayNow: (Int) -> Void  // Now passes puzzle count
    let onBack: () -> Void
    
    // State variables
    @StateObject private var statsManager = UserStatsManager.shared
    @StateObject private var tutorialPreferences = TutorialPreferences() // NEW
    @State private var statsData: PuzzleStatistics?
    @State private var isLoadingStats = true
    @State private var showTutorial = false
    @State private var showTutorialBanner = false
    @State private var showDifficultyHint = false
    @State private var selectedGameMode = "Default"
    @State private var selectedPuzzleCount = 5
    @State private var isExpanded = false
    
    // Tutorial states - Complex interactive tutorials
    @State private var showCrosswordTutorial = false
    @State private var showCryptoTutorial = false
    @State private var showWordSnakeTutorial = false
    @State private var showGeographyCitiesTutorial = false
    @State private var showGeographyCountriesTutorial = false
    // Simple tutorial state
    @State private var showSimpleTutorial = false
    @State private var simpleTutorialContent: SimpleTutorialContent?
    @State private var showRemixSheet = false
    @StateObject private var limitManager = RegenerationLimitManager.shared

    
    // Mock data for demonstration (replace with actual user data)
    private let userPoints = 5099
    private let userStreak = 5
    
    // Puzzle count options
    private let puzzleCountOptions = [3, 5, 10, 15, 20]
    
    // Game mode options based on puzzle type
    private var screenOptions: [String] {
        switch puzzleType.lowercased() {
        case "crossword": return ["Crossword Screen"]
        case "crypto": return ["Crypto Screen"] // NEW
        case "wordsnake": return ["Word Snake Screen"] // NEW
        case "math", "trivia": return ["Multiple Choice", "Match Screen", "Default"]
        case "anagram": return ["Jumble Input Screen", "Default"]
        case "synonyms": return ["Synonym Grouping Screen"]
        case "average": return ["Averages Screen"]
        case "division": return ["Division Screen"]
        default: return ["Default"]
        }
    }
    
#if DEBUG
private var debugSection: some View {
    VStack {
        Text("🧪 Debug Controls")
            .font(.caption)
            .foregroundColor(.red)
        
        Button("Reset Tutorial") {
            tutorialPreferences.resetTutorialForTesting(puzzleType: puzzleType)
            checkTutorialBanner()
        }
        .font(.caption)
        .foregroundColor(.red)
    }
    .padding(.top, 8)
}
#endif
    
    var body: some View {
        GeometryReader { geometry in
            ZStack {
                // Background gradient
                LinearGradient(
                    colors: [Color(red: 0.1, green: 0.1, blue: 0.1), Color(red: 0.18, green: 0.18, blue: 0.18)],
                    startPoint: .top,
                    endPoint: .bottom
                )
                .ignoresSafeArea()
                
                ScrollView {
                    VStack(spacing: 0) {
                        // Main content
                        VStack(spacing: 16) {
                            // Header
                            headerSection
                            
                            // Title section
                            titleSection
                            
                            #if DEBUG
                            debugSection
                            #endif
                            
                            // Tutorial banner
                            if showTutorialBanner {
                                tutorialBannerView
                            }
                            
                            // Statistics cards
                            if isLoadingStats {
                                loadingStatsView
                            } else if let stats = statsData {
                                statisticsSection(stats: stats)
                            }
                            
                            // Benefits section
                            benefitsSection
                            
                            // Game configuration
                            gameConfigurationSection
                        }
                        .padding(.horizontal, 20)
                        .padding(.bottom, 140) // Space for floating buttons
                    }
                }
                
                // Floating action buttons at bottom
                floatingButtonsSection
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottom)
            }
        }
        .onAppear {
            loadUserStats()
            checkTutorialBanner()
        }
        // NEW: Tutorial sheets
        .sheet(isPresented: $showCrosswordTutorial) {
            CrosswordPuzzleTutorialView(
                onTutorialComplete: {
                    showCrosswordTutorial = false
                    tutorialPreferences.markTutorialSeen(for: puzzleType)
                    withAnimation {
                        showTutorialBanner = false
                    }
                },
                onTutorialSkipped: {
                    showCrosswordTutorial = false
                    tutorialPreferences.markTutorialSeen(for: puzzleType)
                    withAnimation {
                        showTutorialBanner = false
                    }
                },
                onBack: {
                    showCrosswordTutorial = false
                }
            )
        }
        .sheet(isPresented: $showCryptoTutorial) {
            CryptoPuzzleTutorialView(
                onTutorialComplete: {
                    showCryptoTutorial = false
                    tutorialPreferences.markTutorialSeen(for: puzzleType)
                    withAnimation {
                        showTutorialBanner = false
                    }
                },
                onTutorialSkipped: {
                    showCryptoTutorial = false
                    tutorialPreferences.markTutorialSeen(for: puzzleType)
                    withAnimation {
                        showTutorialBanner = false
                    }
                },
                onBack: {
                    showCryptoTutorial = false
                }
            )
        }
        .sheet(isPresented: $showWordSnakeTutorial) {
            WordSnakePuzzleTutorialView(
                onTutorialComplete: {
                    showWordSnakeTutorial = false
                    tutorialPreferences.markTutorialSeen(for: puzzleType)
                    withAnimation {
                        showTutorialBanner = false
                    }
                },
                onTutorialSkipped: {
                    showWordSnakeTutorial = false
                    tutorialPreferences.markTutorialSeen(for: puzzleType)
                    withAnimation {
                        showTutorialBanner = false
                    }
                },
                onBack: {
                    showWordSnakeTutorial = false
                }
            )
        }
        .sheet(isPresented: $showGeographyCitiesTutorial) {
            GeographyCitiesPuzzleTutorialView(
                onTutorialComplete: {
                    showGeographyCitiesTutorial = false
                    tutorialPreferences.markTutorialSeen(for: puzzleType)
                    withAnimation {
                        showTutorialBanner = false
                    }
                },
                onTutorialSkipped: {
                    showGeographyCitiesTutorial = false
                    tutorialPreferences.markTutorialSeen(for: puzzleType)
                    withAnimation {
                        showTutorialBanner = false
                    }
                },
                onBack: {
                    showGeographyCitiesTutorial = false
                }
            )
        }
        .sheet(isPresented: $showGeographyCountriesTutorial) {
            GeographyCountriesPuzzleTutorialView(
                onTutorialComplete: {
                    showGeographyCountriesTutorial = false
                    tutorialPreferences.markTutorialSeen(for: puzzleType)
                    withAnimation {
                        showTutorialBanner = false
                    }
                },
                onTutorialSkipped: {
                    showGeographyCountriesTutorial = false
                    tutorialPreferences.markTutorialSeen(for: puzzleType)
                    withAnimation {
                        showTutorialBanner = false
                    }
                },
                onBack: {
                    showGeographyCountriesTutorial = false
                }
            )
        }
        // Simple tutorial sheet for all other puzzle types
        .sheet(isPresented: $showSimpleTutorial) {
            if let content = simpleTutorialContent {
                SimpleTutorialView(
                    content: content,
                    onComplete: {
                        showSimpleTutorial = false
                        tutorialPreferences.markTutorialSeen(for: puzzleType)
                        withAnimation {
                            showTutorialBanner = false
                        }
                    },
                    onSkip: {
                        showSimpleTutorial = false
                        tutorialPreferences.markTutorialSeen(for: puzzleType)
                        withAnimation {
                            showTutorialBanner = false
                        }
                    }
                )
            }
        }
        .fullScreenCover(isPresented: $showRemixSheet) {
            GameRemixSheet(
                puzzleType: puzzleType,
                difficulty: selectedDifficulty,
                onDismiss: { showRemixSheet = false }
            )
        }
    }

    // MARK: - Header Section
    private var headerSection: some View {
        HStack {
            Button(action: onBack) {
                Image(systemName: "xmark")
                    .font(.title3)
                    .foregroundColor(.white)
            }
            
            Spacer()
            
            HStack(spacing: 16) {
                // Help button - UPDATED to show appropriate tutorial
                if !showTutorialBanner {
                    Button(action: {
                        showAppropriateTeacher() // NEW method
                    }) {
                        Image(systemName: "questionmark.circle")
                            .font(.title3)
                            .foregroundColor(.white)
                    }
                }
                
                // Favorite button
                Button(action: { /* Toggle favorite */ }) {
                    Image(systemName: "heart")
                        .font(.title3)
                        .foregroundColor(.white)
                }
            }
        }
        .padding(.top, 8)
    }
    
    // MARK: - Title Section
    private var titleSection: some View {
        VStack(spacing: 8) {
            Text(quizTitle)
                .font(.largeTitle)
                .fontWeight(.bold)
                .foregroundColor(.white)
                .multilineTextAlignment(.center)
            
            Text(getPuzzleCategory(puzzleType))
                .font(.title3)
                .foregroundColor(.gray)
                .multilineTextAlignment(.center)
        }
        .padding(.vertical, 16)
    }
    
    // MARK: - Tutorial Banner - UPDATED
    private var tutorialBannerView: some View {
        VStack(spacing: 12) {
            HStack {
                VStack(alignment: .leading, spacing: 4) {
                    HStack {
                        Image(systemName: getTutorialIcon(for: puzzleType)) // NEW
                            .foregroundColor(.yellow)
                            .font(.caption)
                        
                        Text("New to \(getPuzzleDisplayName(puzzleType))?")
                            .font(.caption)
                            .fontWeight(.semibold)
                            .foregroundColor(.primary)
                    }
                    
                    Text(getTutorialDescription(for: puzzleType)) // NEW
                        .font(.caption2)
                        .foregroundColor(.secondary)
                }
                
                Spacer()
                
                Button("Dismiss") {
                    withAnimation {
                        showTutorialBanner = false
                        tutorialPreferences.markTutorialSeen(for: puzzleType)
                    }
                }
                .font(.caption2)
                .foregroundColor(.blue)
            }
            
            HStack(spacing: 12) {
                Button("Start Tutorial") {
                    showAppropriateTeacher() // NEW method
                }
                .font(.caption)
                .fontWeight(.medium)
                .foregroundColor(.white)
                .padding(.horizontal, 16)
                .padding(.vertical, 8)
                .background(Color.blue)
                .cornerRadius(16)
                
                Spacer()
                
                Text("2-3 min")
                    .font(.caption2)
                    .foregroundColor(.secondary)
            }
        }
        .padding(12)
        .background(Color.blue.opacity(0.1))
        .cornerRadius(12)
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(Color.blue.opacity(0.3), lineWidth: 1)
        )
        .transition(.opacity.combined(with: .move(edge: .top)))
    }
    
    // MARK: - Loading Stats View
    private var loadingStatsView: some View {
        HStack {
            ProgressView()
                .scaleEffect(0.8)
            Text("Loading your stats...")
                .foregroundColor(.gray)
                .font(.caption)
        }
        .padding(40)
    }
    
    // MARK: - Statistics Section
    @ViewBuilder
    private func statisticsSection(stats: PuzzleStatistics) -> some View {
        VStack(spacing: 16) {
            // Main stats row
            HStack(spacing: 16) {
                StatCard(
                    title: "HIGH SCORE",
                    value: stats.highScore > 0 ? "\(stats.highScore)" : "Not played"
                )
                
                StatCard(
                    title: "DIFFICULTY",
                    value: stats.difficulty
                )
            }
            
            // Secondary stats row
            HStack(spacing: 16) {
                StatCard(
                    title: "TIME TRAINED",
                    value: stats.totalTimeSpent > 0 ? String(format: "%.1f hrs", stats.totalTimeSpent) : "0 hrs"
                )
                
                StatCard(
                    title: "WINS",
                    value: "\(stats.wins)"
                )
            }
            
            // Top scores section (if user has scores)
            if !stats.topScores.isEmpty && stats.topScores[0] > 0 {
                topScoresSection(topScores: stats.topScores)
            }
        }
    }
    
    // MARK: - Stat Card Component
    private func StatCard(title: String, value: String) -> some View {
        VStack(spacing: 8) {
            Text(value)
                .font(.title2)
                .fontWeight(.bold)
                .foregroundColor(.white)
                .multilineTextAlignment(.center)
            
            Text(title)
                .font(.caption)
                .foregroundColor(.gray)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
        .frame(height: 80)
        .background(Color(red: 0.2, green: 0.2, blue: 0.2))
        .cornerRadius(12)
    }
    
    // MARK: - Top Scores Section
    private func topScoresSection(topScores: [Int]) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("TOP SCORES")
                .font(.caption)
                .fontWeight(.bold)
                .foregroundColor(.gray)
            
            LazyVGrid(columns: Array(repeating: GridItem(.flexible()), count: 2), spacing: 8) {
                ForEach(Array(topScores.prefix(6).enumerated()), id: \.offset) { index, score in
                    HStack {
                        Text("\(index + 1).")
                            .font(.caption)
                            .foregroundColor(.gray)
                            .frame(width: 20, alignment: .leading)
                        
                        Text("\(score)")
                            .font(.caption)
                            .fontWeight(.medium)
                            .foregroundColor(.white)
                        
                        Spacer()
                    }
                }
            }
            .padding()
            .background(Color(red: 0.2, green: 0.2, blue: 0.2))
            .cornerRadius(12)
        }
    }
    
    // MARK: - Benefits Section
    private var benefitsSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("BENEFITS")
                .font(.caption)
                .fontWeight(.bold)
                .foregroundColor(.gray)
            
            ForEach(getPuzzleBenefits(puzzleType), id: \.self) { benefit in
                HStack(alignment: .top, spacing: 12) {
                    Image(systemName: "checkmark.circle.fill")
                        .font(.caption)
                        .foregroundColor(.gray)
                    
                    Text(benefit)
                        .font(.caption)
                        .foregroundColor(.gray)
                        .fixedSize(horizontal: false, vertical: true)
                    
                    Spacer()
                }
            }
        }
    }
    
    // MARK: - Game Configuration Section
    private var gameConfigurationSection: some View {
        VStack(spacing: 16) {
            Button(action: {
                withAnimation {
                    isExpanded.toggle()
                }
            }) {
                HStack {
                    Text("Game Settings")
                        .font(.headline)
                        .foregroundColor(.white)
                    
                    Spacer()
                    
                    Image(systemName: isExpanded ? "chevron.up" : "chevron.down")
                        .foregroundColor(.gray)
                }
                .padding()
                .background(Color(red: 0.2, green: 0.2, blue: 0.2))
                .cornerRadius(12)
            }
            
            if isExpanded {
                VStack(spacing: 16) {
                    // Difficulty selection
                    difficultySelectionSection
                    
                    // Game mode selection (if multiple options)
                    if screenOptions.count > 1 {
                        gameModeSelectionSection
                    }
                    
                    // Puzzle count selection
                    puzzleCountSelectionSection
                }
                .transition(.opacity.combined(with: .move(edge: .top)))
            }
        }
    }
    
    // MARK: - Difficulty Selection
    private var difficultySelectionSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text("Difficulty Level")
                    .font(.subheadline)
                    .foregroundColor(.gray)
                
                Spacer()
                
                Button(action: { showDifficultyHint.toggle() }) {
                    Image(systemName: "info.circle")
                        .foregroundColor(.blue)
                }
            }
            
            if showDifficultyHint {
                Text("💡 Choose your difficulty based on your experience level")
                    .font(.caption)
                    .foregroundColor(.blue)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 8)
                    .background(Color.blue.opacity(0.1))
                    .cornerRadius(8)
            }
            
            HStack(spacing: 8) {
                ForEach(["Easy", "Medium", "Hard"], id: \.self) { difficulty in
                    Button(difficulty) {
                        selectedDifficulty = difficulty
                    }
                    .font(.caption)
                    .fontWeight(.medium)
                    .foregroundColor(selectedDifficulty == difficulty ? .white : .gray)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 8)
                    .background(
                        selectedDifficulty == difficulty ?
                        Color.blue : Color(red: 0.3, green: 0.3, blue: 0.3)
                    )
                    .cornerRadius(8)
                }
            }
        }
    }
    
    // MARK: - Game Mode Selection
    private var gameModeSelectionSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Game Mode")
                .font(.subheadline)
                .foregroundColor(.gray)
            
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(screenOptions, id: \.self) { option in
                        Button(getDisplayName(for: option)) {
                            selectedGameMode = option
                        }
                        .font(.caption)
                        .fontWeight(.medium)
                        .foregroundColor(selectedGameMode == option ? .white : .gray)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 8)
                        .background(
                            selectedGameMode == option ?
                            Color.blue : Color(red: 0.3, green: 0.3, blue: 0.3)
                        )
                        .cornerRadius(8)
                    }
                }
                .padding(.horizontal, 1)
            }
        }
    }
    
    // MARK: - Puzzle Count Selection
    private var puzzleCountSelectionSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text("Number of Puzzles")
                    .font(.subheadline)
                    .foregroundColor(.gray)
                
                Spacer()
                
                Text("Complete \(selectedPuzzleCount) puzzles to see results")
                    .font(.caption2)
                    .foregroundColor(.blue)
            }
            
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(puzzleCountOptions, id: \.self) { count in
                        Button("\(count)") {
                            selectedPuzzleCount = count
                        }
                        .font(.caption)
                        .fontWeight(.medium)
                        .foregroundColor(selectedPuzzleCount == count ? .white : .gray)
                        .frame(width: 44, height: 32)
                        .background(
                            selectedPuzzleCount == count ?
                            Color.orange : Color(red: 0.3, green: 0.3, blue: 0.3)
                        )
                        .cornerRadius(8)
                    }
                }
                .padding(.horizontal, 1)
            }
        }
    }
    
    // MARK: - Floating Buttons Section
    private var floatingButtonsSection: some View {
        VStack(spacing: 12) {
            // Main play button
            Button(action: { onPlayNow(selectedPuzzleCount) }) {
                HStack {
                    Image(systemName: "play.fill")
                    Text("Start Playing")
                        .fontWeight(.bold)
                }
                .font(.title3)
                .foregroundColor(.white)
                .frame(maxWidth: .infinity)
                .padding()
                .background(Color.blue)
                .cornerRadius(28)
                .shadow(color: .blue.opacity(0.3), radius: 8, x: 0, y: 4)
            }
            
            // Create Your Version (Remix) button
            Button(action: { showRemixSheet = true }) {
                HStack(spacing: 10) {
                    Image(systemName: "wand.and.stars")
                        .font(.subheadline)
                    Text("Create Your Version")
                        .fontWeight(.semibold)
                    Spacer()
                    Text("Earn coins")
                        .font(.caption2)
                        .foregroundColor(.white.opacity(0.6))
                    Image(systemName: "chevron.right")
                        .font(.caption2)
                        .foregroundColor(.white.opacity(0.5))
                }
                .font(.subheadline)
                .foregroundColor(.white)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 12)
                .padding(.horizontal, 16)
                .background(
                    LinearGradient(
                        colors: [.purple, .blue],
                        startPoint: .leading,
                        endPoint: .trailing
                    )
                )
                .cornerRadius(28)
            }

            // Back button
            Button(action: onBack) {
                HStack {
                    Image(systemName: "arrow.left")
                    Text("Back to Home")
                        .fontWeight(.medium)
                }
                .foregroundColor(.blue)
                .frame(maxWidth: .infinity)
                .padding()
                .background(Color.clear)
                .overlay(
                    RoundedRectangle(cornerRadius: 28)
                        .stroke(Color.blue.opacity(0.5), lineWidth: 1)
                )
                .cornerRadius(28)
            }
        }
        .padding(20)
        .background(
            LinearGradient(
                colors: [
                    Color.clear,
                    Color(red: 0.1, green: 0.1, blue: 0.1).opacity(0.8),
                    Color(red: 0.1, green: 0.1, blue: 0.1)
                ],
                startPoint: .top,
                endPoint: .bottom
            )
        )
    }
    
    // MARK: - NEW Helper Methods for Tutorial Integration
    
    private func showAppropriateTeacher() {
        let normalizedType = puzzleType.lowercased()
            .replacingOccurrences(of: " ", with: "")
            .replacingOccurrences(of: "_", with: "")

        // Check for complex interactive tutorials first
        switch normalizedType {
        case "crossword":
            showCrosswordTutorial = true
            return
        case "crypto":
            showCryptoTutorial = true
            return
        case "wordsnake":
            showWordSnakeTutorial = true
            return
        case "geographycities":
            showGeographyCitiesTutorial = true
            return
        case "geographycountries":
            showGeographyCountriesTutorial = true
            return
        default:
            break
        }

        // Try to show simple tutorial for other puzzle types
        if let tutorialContent = TutorialContentLibrary.getTutorial(for: puzzleType) {
            simpleTutorialContent = tutorialContent
            showSimpleTutorial = true
        } else {
            print("No tutorial available for puzzle type: \(puzzleType)")
        }
    }
    
    private func getTutorialIcon(for puzzleType: String) -> String {
        switch puzzleType.lowercased() {
        case "crossword": return "grid.circle"
        case "crypto": return "lock.shield"
        case "wordsnake", "word_snake": return "circle.dotted"
        case "geography_cities", "geographycities": return "location.circle"
        case "geography_countries", "geographycountries": return "globe"
        default: return "lightbulb.fill"
        }
    }
    
    private func getTutorialDescription(for puzzleType: String) -> String {
        switch puzzleType.lowercased() {
        case "crossword": return "Learn how to solve crosswords with clues and grid navigation!"
        case "crypto": return "Master the art of code-breaking and pattern recognition!"
        case "wordsnake", "word_snake": return "Discover how to find words snaking through letter grids!"
        case "geography_cities", "geographycities": return "Learn to place cities on world maps with precision!"
        case "geography_countries", "geographycountries": return "Master country placement and multi-cell selection!"
        default: return "Take a quick interactive tutorial to get started!"
        }
    }
    
    // MARK: - Existing Helper Methods
    
    private func loadUserStats() {
        isLoadingStats = true
        
        // Load real statistics
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
            let userStats = statsManager.getPuzzleStats(puzzleType: puzzleType)
            let difficultyRating = statsManager.getDifficultyRating(puzzleType, difficulty: selectedDifficulty)
            
            let statsData = (
                totalPlays: userStats.totalPlays,
                wins: userStats.wins,
                winRate: userStats.winRate,
                averageScore: userStats.averageScore,
                highScore: userStats.highScore,
                difficulty: difficultyRating
            )
            isLoadingStats = false
            
            print("📊 Loaded stats for \(puzzleType): plays=\(userStats.totalPlays), highScore=\(userStats.highScore)")
        }
    }
    
    private func checkTutorialBanner() {
        // Check if tutorial should be shown using the updated TutorialPreferences
        // This now supports all 40+ puzzle types like Android
        showTutorialBanner = tutorialPreferences.shouldAutoShowTutorial(puzzleType: puzzleType)
    }
    
    private func getPuzzleCategory(_ puzzleType: String) -> String {
        switch puzzleType.lowercased() {
        case "wordassociation", "synonyms", "anagram": return "Language & Words"
        case "crossword", "wordsearch", "wordsnake", "word_snake": return "Word Puzzles"
        case "crypto": return "Logic & Codes"
        case "geography_cities", "geographycities", "geography_countries", "geographycountries": return "Geography & Maps" // NEW
        case "math", "mathestimation", "division": return "Mathematics"
        case "memorysquares", "memorystory": return "Memory & Recall"
        default: return "Brain Training"
        }
    }
    
    private func getPuzzleBenefits(_ puzzleType: String) -> [String] {
        switch puzzleType.lowercased() {
        case "wordassociation":
            return [
                "Stop mixing up commonly confused words",
                "Eliminate distracting errors in your speaking"
            ]
        case "crossword":
            return [
                "Expand vocabulary through contextual clues",
                "Improve pattern recognition and deduction skills"
            ]
        case "crypto": // NEW
            return [
                "Enhance logical reasoning and pattern analysis",
                "Develop code-breaking and cryptographic thinking"
            ]
        case "wordsnake", "word_snake": // NEW
            return [
                "Strengthen spatial reasoning and path-finding skills",
                "Improve visual word recognition and processing speed"
            ]
        case "geography_cities", "geographycities": // NEW
            return [
                "Improve spatial reasoning and world knowledge",
                "Enhance geographical awareness and cultural literacy"
            ]
        case "geography_countries", "geographycountries": // NEW
            return [
                "Master global geography and country recognition",
                "Develop strategic thinking through multi-cell placement"
            ]
        case "math", "mathestimation":
            return [
                "Improve numerical reasoning skills",
                "Enhance problem-solving speed and accuracy"
            ]
        case "memorysquares", "memorystory":
            return [
                "Strengthen working memory capacity",
                "Improve focus and attention span"
            ]
        case "synonyms":
            return [
                "Expand your vocabulary range",
                "Improve word choice in communication"
            ]
        default:
            return [
                "Enhance cognitive flexibility",
                "Improve pattern recognition skills"
            ]
        }
    }
    
    private func getDisplayName(for option: String) -> String {
        switch option {
        case "Default": return "Q&A"
        case "multipleChoice": return "Multiple Choice"
        case "Match Screen": return "Word Match"
        case "Jumble Input Screen": return "Word Jumble"
        case "Crossword Screen": return "Crossword Grid"
        case "Crypto Screen": return "Code Breaking"
        case "Word Snake Screen": return "Snake Hunt"
        case "Geography Cities Screen": return "City Placement" // NEW
        case "Geography Countries Screen": return "Country Mapping" // NEW
        default: return option
        }
    }
}



// MARK: - Preview
struct EnhancedStartPuzzleView_Previews: PreviewProvider {
    static var previews: some View {
        Group {
            // Crossword preview
            StartPuzzleView(
                quizTitle: "Daily Crossword",
                puzzleType: "crossword",
                selectedDifficulty: .constant("Medium"),
                onPlayNow: { count in print("Starting \(count) crosswords") },
                onBack: {}
            )
            .previewDisplayName("Crossword")
            
            // Crypto preview
            StartPuzzleView(
                quizTitle: "Secret Messages",
                puzzleType: "crypto",
                selectedDifficulty: .constant("Medium"),
                onPlayNow: { count in print("Starting \(count) crypto puzzles") },
                onBack: {}
            )
            .previewDisplayName("Crypto")
            
            // Word Snake preview
            StartPuzzleView(
                quizTitle: "Word Hunt",
                puzzleType: "wordsnake",
                selectedDifficulty: .constant("Medium"),
                onPlayNow: { count in print("Starting \(count) word snakes") },
                onBack: {}
            )
            .previewDisplayName("Word Snake")
        }
        .preferredColorScheme(.dark)
    }
}
