import SwiftUI
import SVGView


struct GeographyCountriesPuzzleView: View {
    let difficulty: String
    let timer: String
    let hearts: Int
    let level: String
    let puzzleData: String
    let correctAnswer: String
    let onSubmitAnswer: (Bool) -> Void
    let fetchNextPuzzle: (Int) -> Void
    let onBack: () -> Void
    
    @StateObject private var countryDatabase = GeographyCountryDatabase.shared
    @State private var gameStage: GameStage = .instructions
    @State private var currentCountry: GeographyCountry?
    @State private var selectedContinent: Continent?
    @State private var currentScore = 0
    @State private var totalScore = 0
    @State private var questionsAnswered = 0
    @State private var showFeedback = false
    @State private var feedbackMessage = ""
    @State private var lastResult: PlacementResult?
    @State private var timeRemaining: Int
    @State private var selectedGridCells: [GridCell] = []
    @State private var isSelectionMode = true
    
    
    private let maxQuestions: Int
    
    init(difficulty: String, timer: String, hearts: Int, level: String, puzzleData: String, correctAnswer: String, onSubmitAnswer: @escaping (Bool) -> Void, fetchNextPuzzle: @escaping (Int) -> Void, onBack: @escaping () -> Void) {
        self.difficulty = difficulty
        self.timer = timer
        self.hearts = hearts
        self.level = level
        self.puzzleData = puzzleData
        self.correctAnswer = correctAnswer
        self.onSubmitAnswer = onSubmitAnswer
        self.fetchNextPuzzle = fetchNextPuzzle
        self.onBack = onBack
        
        switch difficulty.lowercased() {
        case "easy":
            self.maxQuestions = 8
        case "medium":
            self.maxQuestions = 12
        case "hard":
            self.maxQuestions = 15
        default:
            self.maxQuestions = 10
        }
        
        self._timeRemaining = State(initialValue: maxQuestions * 30)
    }
    
    var body: some View {
        GeometryReader { geometry in
            ZStack {
                // Background
                LinearGradient(
                    colors: [Color(red: 0.12, green: 0.23, blue: 0.54), Color(red: 0.18, green: 0.35, blue: 0.75)],
                    startPoint: .top,
                    endPoint: .bottom
                )
                .ignoresSafeArea()
                
                VStack(spacing: 0) {
                    // Header
                    if gameStage != .instructions {
                        HeaderView(
                            timeRemaining: timeRemaining,
                            totalScore: totalScore,
                            questionsAnswered: questionsAnswered,
                            maxQuestions: maxQuestions,
                            onBack: onBack
                        )
                        .padding(.horizontal)
                        .padding(.top, 10)
                    }
                    
                    // ✅ UPDATED: Use Enhanced Views from Cities (same as cities view)
                    switch gameStage {
                    case .instructions:
                        CountryInstructionsView(onStartGame: startGame)
                        
                    case .continentSelection:
                        if let country = currentCountry {
                            // ✅ CHANGED: Use EnhancedContinentSelectionView (same as cities)
                            EnhancedCountryContinentSelectionView(
                                country: country,
                                showFeedback: showFeedback,
                                feedbackMessage: feedbackMessage,
                                questionsAnswered: questionsAnswered,
                                maxQuestions: maxQuestions,
                                onContinentSelected: handleContinentSelection
                            )
                        }
                        
                    case .precisePlacement:
                        if let country = currentCountry, let continent = selectedContinent {
                            // ✅ CHANGED: Use EnhancedPrecisePlacementView for countries
                            EnhancedCountryPrecisePlacementView(
                                country: country,
                                continent: continent,
                                selectedGridCells: selectedGridCells,
                                showFeedback: showFeedback,
                                lastResult: lastResult,
                                questionsAnswered: questionsAnswered,
                                maxQuestions: maxQuestions,
                                onGridCellSelected: handleGridToggle
                            )
                        }
                        
                    case .completed:
                        CountryCompletionView(
                            totalScore: totalScore,
                            questionsAnswered: questionsAnswered,
                            maxQuestions: maxQuestions,
                            onContinue: handleCompletion
                        )
                    }
                }
            }
        }
        .navigationBarHidden(true)
        .onAppear {
            startNewQuestion()
        }
        .onReceive(Timer.publish(every: 1, on: .main, in: .common).autoconnect()) { _ in
            if gameStage != .instructions && gameStage != .completed && timeRemaining > 0 {
                timeRemaining -= 1
                if timeRemaining <= 0 {
                    gameStage = .completed
                }
            }
        }
    }
    
    private func startGame() {
        gameStage = .continentSelection
        startNewQuestion()
    }
    
    private func startNewQuestion() {
        currentCountry = countryDatabase.getRandomCountry()
        selectedContinent = nil
        selectedGridCells = []
        isSelectionMode = true
        currentScore = 0
        showFeedback = false
        feedbackMessage = ""
        lastResult = nil
    }
    
    private func handleContinentSelection(_ continent: Continent) {
        guard let country = currentCountry else { return }
        
        if continent == country.continent {
            currentScore += 25
            totalScore += 25
            selectedContinent = continent
            feedbackMessage = "Correct continent! Now place it precisely."
            showFeedback = true
            
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
                showFeedback = false
                gameStage = .precisePlacement
            }
        } else {
            feedbackMessage = "Wrong continent. \(country.name) is in \(country.continent.rawValue)."
            showFeedback = true
            
            DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) {
                showFeedback = false
                moveToNextQuestion()
            }
        }
    }
    
    private func handleGridToggle(_ gridCell: GridCell) {
        if selectedGridCells.contains(gridCell) {
            selectedGridCells.removeAll { $0 == gridCell }
        } else {
            selectedGridCells.append(gridCell)
        }
        
        // Auto-submit after a short delay if user seems done selecting
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) {
            if !selectedGridCells.isEmpty && !showFeedback {
                submitSelection()
            }
        }
    }
    
    private func submitSelection() {
        guard let country = currentCountry, !selectedGridCells.isEmpty else { return }
        
        isSelectionMode = false
        let result = GeographyGridSystem.shared.calculateCountryScore(countryId: country.id, selectedGrids: selectedGridCells)
        lastResult = result
        currentScore += result.score
        totalScore += result.score
        showFeedback = true
        
        DispatchQueue.main.asyncAfter(deadline: .now() + 2.5) {
            showFeedback = false
            moveToNextQuestion()
        }
    }
    
    private func moveToNextQuestion() {
        questionsAnswered += 1
        if questionsAnswered >= maxQuestions {
            gameStage = .completed
        } else {
            gameStage = .continentSelection
            startNewQuestion()
        }
    }
    
    private func handleCompletion() {
        let finalScore = Double(totalScore) / Double(maxQuestions * 75) * 100
        onSubmitAnswer(finalScore >= 60)
        fetchNextPuzzle(Int(finalScore))
    }
}

struct EnhancedCountryContinentSelectionView: View {
    let country: GeographyCountry
    let showFeedback: Bool
    let feedbackMessage: String
    let questionsAnswered: Int
    let maxQuestions: Int
    let onContinentSelected: (Continent) -> Void
    
    var body: some View {
        VStack(spacing: 20) {
            // Progress
            Text("Question \(questionsAnswered + 1) of \(maxQuestions)")
                .font(.headline)
                .foregroundColor(.white)
            
            // ✅ UPDATED: Fixed Country Card
            CountryCardView(country: country)
            
            // ✅ REMOVED: Redundant text (just like in cities view)
            // Text("Which continent is \(country.name) located in?")
            
            // ✅ UPDATED: Use same Enhanced World Map as cities
            EnhancedWorldMapView(onContinentSelected: onContinentSelected)
            
            // Feedback
            if showFeedback {
                GFeedbackView(message: feedbackMessage, isCorrect: feedbackMessage.contains("Correct"))
                    .transition(.scale.combined(with: .opacity))
            }
            
            Spacer()
        }
        .padding()
        .animation(.easeInOut, value: showFeedback)
    }
}

struct EnhancedCountryPrecisePlacementView: View {
    let country: GeographyCountry
    let continent: Continent
    let selectedGridCells: [GridCell]
    let showFeedback: Bool
    let lastResult: PlacementResult?
    let questionsAnswered: Int
    let maxQuestions: Int
    let onGridCellSelected: (GridCell) -> Void
    
    private let gridSystem = GeographyGridSystem.shared
    
    var body: some View {
        VStack(spacing: 20) {
            // Progress
            Text("Question \(questionsAnswered + 1) of \(maxQuestions) - Precise Placement")
                .font(.headline)
                .foregroundColor(.white)
                .multilineTextAlignment(.center)
            
            // ✅ UPDATED: Fixed Country Card
            CountryCardView(country: country)
            
            Text("Tap all grid sections where \(country.name) is located")
                .font(.title3)
                .fontWeight(.medium)
                .foregroundColor(.white)
                .multilineTextAlignment(.center)
            
            // Selection info
            HStack(spacing: 16) {
                Text("Selected: \(selectedGridCells.count)")
                    .font(.subheadline)
                    .foregroundColor(.white.opacity(0.8))
                
                if !selectedGridCells.isEmpty {
                    Text("• Tap again to deselect")
                        .font(.caption)
                        .foregroundColor(.white.opacity(0.6))
                }
            }
            
            // ✅ UPDATED: Use Enhanced Grid Map (similar to cities but for multiple cells)
            EnhancedCountryGridMapView(
                continent: continent,
                selectedCells: selectedGridCells,
                correctCells: showFeedback ? gridSystem.getCorrectGridCells(for: country.id) ?? [] : [],
                onCellSelected: onGridCellSelected
            )
            
            // Feedback
            if showFeedback, let result = lastResult {
                CountryGridFeedbackView(
                    result: result,
                    selectedCount: selectedGridCells.count,
                    correctCount: gridSystem.getCorrectGridCells(for: country.id)?.count ?? 0
                )
                .transition(.scale.combined(with: .opacity))
            }
            
            Spacer()
        }
        .padding()
        .animation(.easeInOut, value: showFeedback)
    }
}

struct EnhancedCountryGridMapView: View {
    let continent: Continent
    let selectedCells: [GridCell]
    let correctCells: [GridCell]
    let onCellSelected: (GridCell) -> Void
    
    private let gridSystem = GeographyGridSystem.shared
    
    var body: some View {
        let dimensions = gridSystem.getGridDimensions(for: continent)
        
        VStack(spacing: 16) {
            Text("Grid: \(dimensions.rows)×\(dimensions.cols)")
                .font(.caption)
                .foregroundColor(.white.opacity(0.8))
            
            ZStack {
                // ✅ UPDATED: Use same SVG background as cities
                ContinentBackgroundSVG(continent: continent)
                    .frame(width: 300, height: 220)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                
                // Grid overlay
                VStack(spacing: 1) {
                    ForEach(0..<dimensions.rows, id: \.self) { row in
                        HStack(spacing: 1) {
                            ForEach(0..<dimensions.cols, id: \.self) { col in
                                let cell = GridCell(row: row, col: col)
                                
                                Button(action: { onCellSelected(cell) }) {
                                    Rectangle()
                                        .fill(getCellColor(for: cell).opacity(0.7))
                                        .overlay(
                                            Rectangle()
                                                .stroke(Color.white.opacity(0.8), lineWidth: 0.5)
                                        )
                                        .overlay(getCellContent(for: cell))
                                }
                                .disabled(correctCells.count > 0)
                            }
                        }
                    }
                }
                .frame(width: 300, height: 220)
            }
            
            // Legend
            if correctCells.count > 0 {
                HStack(spacing: 20) {
                    Button(action: {}) { // No action needed, just visual
                        LegendItem(color: .green, label: "Correct", icon:"")
                    }
                    .disabled(true)
                    
                    Button(action: {}) {
                        LegendItem(color: .orange, label: "Selected", icon:"")
                    }
                    .disabled(true)
                    
                    Button(action: {}) {
                        LegendItem(color: .red, label: "Wrong", icon:"")
                    }
                    .disabled(true)
                }
            }
        }
    }
    
    private func getCellColor(for cell: GridCell) -> Color {
        if correctCells.count > 0 {
            if correctCells.contains(cell) && selectedCells.contains(cell) {
                return .green
            } else if correctCells.contains(cell) {
                return .green.opacity(0.6)
            } else if selectedCells.contains(cell) {
                return .red
            }
        } else if selectedCells.contains(cell) {
            return .orange
        }
        return .clear
    }
    
    @ViewBuilder
    private func getCellContent(for cell: GridCell) -> some View {
        if correctCells.count > 0 {
            if correctCells.contains(cell) && selectedCells.contains(cell) {
                Image(systemName: "checkmark.circle.fill")
                    .font(.caption)
                    .foregroundColor(.white)
            } else if selectedCells.contains(cell) && !correctCells.contains(cell) {
                Image(systemName: "xmark.circle.fill")
                    .font(.caption)
                    .foregroundColor(.white)
            }
        } else if selectedCells.contains(cell) {
            Circle()
                .fill(Color.white.opacity(0.9))
                .frame(width: 6, height: 6)
        }
    }
}

// MARK: - ✅ UPDATED: Fixed Country Card (same improvements as cities)

struct CountryCardView: View {
    let country: GeographyCountry
    
    var body: some View {
        VStack(spacing: 12) {
            // Flag - larger and centered
            Text(country.flag)
                .font(.system(size: 50)) // ✅ Increased from 40 to 50
            
            // Country name only - with high contrast
            Text(country.name)
                .font(.title2)
                .fontWeight(.bold)
                .foregroundColor(.black) // ✅ FIXED: Use explicit black instead of .primary
                .multilineTextAlignment(.center)
                .lineLimit(2)
                .fixedSize(horizontal: false, vertical: true)
            
            // Capital info (keep this for countries)
            Text("Capital: \(country.capital)")
                .font(.caption)
                .foregroundColor(.gray)
                .multilineTextAlignment(.center)
                .lineLimit(1)
        }
        .padding(.horizontal, 16) // ✅ More horizontal padding
        .padding(.vertical, 16)   // ✅ More vertical padding
        .frame(minWidth: 200)     // ✅ Increased minimum width
        .frame(height: 130)       // ✅ Increased height to accommodate capital
        .background(Color.white)
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .shadow(radius: 8)
    }
}

struct CountryGridFeedbackView: View {
    let result: PlacementResult
    let selectedCount: Int
    let correctCount: Int
    
    var body: some View {
        VStack(spacing: 8) {
            Text(result.accuracy)
                .font(.title2)
                .fontWeight(.bold)
                .foregroundColor(.white)
            
            Text("+\(result.score) points")
                .font(.headline)
                .foregroundColor(.white)
            
            HStack(spacing: 20) {
                VStack(spacing: 4) {
                    Text("Country spans")
                        .font(.caption)
                        .foregroundColor(.white.opacity(0.8))
                    Text("\(correctCount) cells")
                        .font(.subheadline)
                        .fontWeight(.semibold)
                        .foregroundColor(.white)
                }
                
                VStack(spacing: 4) {
                    Text("You selected")
                        .font(.caption)
                        .foregroundColor(.white.opacity(0.8))
                    Text("\(selectedCount) cells")
                        .font(.subheadline)
                        .fontWeight(.semibold)
                        .foregroundColor(.white)
                }
            }
        }
        .padding()
        .background(getBackgroundColor())
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }
    
    private func getBackgroundColor() -> Color {
        switch result.score {
        case 40...50: return .green
        case 20...39: return .orange
        default: return .red
        }
    }
}


// MARK: - Instructions View
struct CountryInstructionsView: View {
    let onStartGame: () -> Void
    
    var body: some View {
        VStack(spacing: 20) {
            VStack(spacing: 16) {
                Text("🗺️")
                    .font(.system(size: 60))
                
                Text("Geography Countries Challenge")
                    .font(.title)
                    .fontWeight(.bold)
                    .foregroundColor(.white)
                    .multilineTextAlignment(.center)
                
                Text("Place countries in their correct locations on the world map")
                    .font(.title3)
                    .foregroundColor(.white.opacity(0.9))
                    .multilineTextAlignment(.center)
            }
            
            VStack(spacing: 20) {
                InstructionCard(
                    title: "How to Play:",
                    instructions: [
                        "1. Click on the correct continent for the country",
                        "2. If correct, select all grid sections for the country",
                        "3. Countries can span multiple grid cells",
                        "4. Get points based on accuracy",
                        "5. Complete all questions before time runs out"
                    ],
                    tip: "💡 Scoring: 25 pts for correct continent + up to 50 pts for accuracy"
                )
            }
            
            Spacer()
            
            Button(action: onStartGame) {
                Text("START CHALLENGE")
                    .font(.title2)
                    .fontWeight(.bold)
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 16)
                    .background(Color.green)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
            }
            .padding(.horizontal)
        }
        .padding()
    }
}

// MARK: - Completion View
struct CountryCompletionView: View {
    let totalScore: Int
    let questionsAnswered: Int
    let maxQuestions: Int
    let onContinue: () -> Void
    
    var body: some View {
        VStack(spacing: 30) {
            VStack(spacing: 16) {
                Text("🎉")
                    .font(.system(size: 60))
                
                Text("Geography Challenge Complete!")
                    .font(.title)
                    .fontWeight(.bold)
                    .foregroundColor(.white)
                    .multilineTextAlignment(.center)
            }
            
            VStack(spacing: 20) {
                CountryScoreCard(
                    totalScore: totalScore,
                    questionsAnswered: questionsAnswered,
                    maxQuestions: maxQuestions
                )
            }
            
            Spacer()
            
            Button(action: onContinue) {
                Text("CONTINUE")
                    .font(.title2)
                    .fontWeight(.bold)
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 16)
                    .background(Color.green)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
            }
            .padding(.horizontal)
        }
        .padding()
    }
}

struct CountryScoreCard: View {
    let totalScore: Int
    let questionsAnswered: Int
    let maxQuestions: Int
    
    private var percentage: Int {
        Int(Double(totalScore) / Double(maxQuestions * 75) * 100)
    }
    
    var body: some View {
        VStack(spacing: 16) {
            Text("Final Score")
                .font(.title2)
                .fontWeight(.bold)
                .foregroundColor(.blue)
            
            Text("\(totalScore)")
                .font(.system(size: 48, weight: .bold))
                .foregroundColor(.green)
            
            VStack(spacing: 8) {
                Text("Countries Placed: \(questionsAnswered)")
                    .font(.headline)
                    .foregroundColor(.primary)
                
                Text("Accuracy: \(percentage)%")
                    .font(.headline)
                    .foregroundColor(.primary)
            }
        }
        .padding()
        .background(Color.white.opacity(0.95))
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .padding(.horizontal)
    }
}

struct EnhancedCountryCardView: View {
    let country: GeographyCountry
    
    var body: some View {
        VStack(spacing: 8) {
            Text(country.flag)
                .font(.system(size: 40))
            
            Text(country.name)
                .font(.title2)
                .fontWeight(.bold)
                .foregroundColor(.primary)
            
            Text("Capital: \(country.capital)")
                .font(.subheadline)
                .foregroundColor(.secondary)
            
            if country.isLandlocked {
                HStack(spacing: 4) {
                    Image(systemName: "mountain.2.fill")
                        .font(.caption)
                    Text("Landlocked")
                        .font(.caption)
                }
                .foregroundColor(.blue)
            }
        }
        .padding()
        .frame(width: 180, height: 120)
        .background(
            RoundedRectangle(cornerRadius: 12)
                .fill(Color.white)
                .shadow(color: .black.opacity(0.1), radius: 8, x: 0, y: 4)
        )
    }
}

struct EnhancedWorldMapWithSVG: View {
    let onContinentSelected: (Continent) -> Void
    @State private var selectedContinent: Continent?
    
    var body: some View {
        VStack(spacing: 16) {
            Text("Tap on a continent")
                .font(.caption)
                .foregroundColor(.white.opacity(0.8))
            
            // World map container with ocean background
            ZStack {
                // Ocean background with wave effect
                RoundedRectangle(cornerRadius: 16)
                    .fill(
                        LinearGradient(
                            colors: [
                                Color.blue.opacity(0.4),
                                Color.blue.opacity(0.6),
                                Color.blue.opacity(0.5)
                            ],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        )
                    )
                    .overlay(
                        // Subtle wave pattern
                        RoundedRectangle(cornerRadius: 16)
                            .stroke(Color.white.opacity(0.1), lineWidth: 1)
                    )
                
                // Continents positioned geographically
                GeometryReader { geometry in
                    let mapWidth = geometry.size.width
                    let mapHeight = geometry.size.height
                    
                    Group {
                        // North America
                        // North America
                        Button(action: { handleContinentTap(.northAmerica) }) {
                            ContinentSVGButton(continent: .northAmerica, isSelected: selectedContinent == .northAmerica)
                        }
                        .frame(width: mapWidth * 0.22, height: mapHeight * 0.35)
                        .position(x: mapWidth * 0.18, y: mapHeight * 0.25)

                        // South America
                        Button(action: { handleContinentTap(.southAmerica) }) {
                            ContinentSVGButton(continent: .southAmerica, isSelected: selectedContinent == .southAmerica)
                        }
                        .frame(width: mapWidth * 0.15, height: mapHeight * 0.4)
                        .position(x: mapWidth * 0.22, y: mapHeight * 0.65)

                        // Europe
                        Button(action: { handleContinentTap(.europe) }) {
                            ContinentSVGButton(continent: .europe, isSelected: selectedContinent == .europe)
                        }
                        .frame(width: mapWidth * 0.18, height: mapHeight * 0.25)
                        .position(x: mapWidth * 0.48, y: mapHeight * 0.28)

                        // Africa
                        Button(action: { handleContinentTap(.africa) }) {
                            ContinentSVGButton(continent: .africa, isSelected: selectedContinent == .africa)
                        }
                        .frame(width: mapWidth * 0.16, height: mapHeight * 0.35)
                        .position(x: mapWidth * 0.46, y: mapHeight * 0.6)

                        // Asia
                        Button(action: { handleContinentTap(.asia) }) {
                            ContinentSVGButton(continent: .asia, isSelected: selectedContinent == .asia)
                        }
                        .frame(width: mapWidth * 0.32, height: mapHeight * 0.3)
                        .position(x: mapWidth * 0.72, y: mapHeight * 0.35)

                        // Oceania
                        Button(action: { handleContinentTap(.oceania) }) {
                            ContinentSVGButton(continent: .oceania, isSelected: selectedContinent == .oceania)
                        }
                        .frame(width: mapWidth * 0.14, height: mapHeight * 0.15)
                        .position(x: mapWidth * 0.78, y: mapHeight * 0.75)
                    }
                }
            }
            .frame(height: 280)
            .clipped()
        }
        .padding()
    }
    
    private func handleContinentTap(_ continent: Continent) {
        withAnimation(.easeInOut(duration: 0.25)) {
            selectedContinent = continent
        }
        
        // Haptic feedback
        let impact = UIImpactFeedbackGenerator(style: .medium)
        impact.impactOccurred()
        
        // Delayed selection to show visual feedback
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) {
            onContinentSelected(continent)
            selectedContinent = nil
        }
    }
}

struct ContinentSVGButton: View {
    let continent: Continent
    let isSelected: Bool
    
    private var svgFileName: String {
        switch continent {
        case .northAmerica: return "north_america"
        case .southAmerica: return "south_america"
        case .europe: return "europe"
        case .africa: return "africa"
        case .asia: return "asia"
        case .oceania: return "oceania"
        }
    }
    
    var body: some View {
        Button(action: {}) { // Empty action - handled by parent
            ZStack {
                // SVG Content (same as before)
                if let svgPath = Bundle.main.path(forResource: svgFileName, ofType: "svg") {
                    continentPlaceholderShape
                        .foregroundColor(continent.color.opacity(isSelected ? 1.0 : 0.8))
                } else {
                    continentPlaceholderShape
                        .foregroundColor(continent.color.opacity(isSelected ? 1.0 : 0.8))
                }
                
                // Selection highlight
                if isSelected {
                    RoundedRectangle(cornerRadius: 8)
                        .stroke(Color.white, lineWidth: 3)
                        .background(Color.white.opacity(0.2))
                        .cornerRadius(8)
                }
                
                // Continent label - now more prominent
                VStack {
                    Spacer()
                    Text(continent.rawValue)
                        .font(.caption)  // Larger font
                        .fontWeight(.bold)  // Bolder
                        .foregroundColor(.white)
                        .padding(.horizontal, 8)  // More padding
                        .padding(.vertical, 4)
                        .background(Color.black.opacity(0.8))  // More opaque
                        .cornerRadius(6)
                        .shadow(color: .black.opacity(0.7), radius: 3)
                        .padding(.bottom, 4)
                }
            }
        }
        .scaleEffect(isSelected ? 1.05 : 1.0)
        .animation(.spring(response: 0.3, dampingFraction: 0.8), value: isSelected)
        .shadow(color: .black.opacity(isSelected ? 0.4 : 0.2), radius: isSelected ? 8 : 4)
    }
    
    @ViewBuilder
    private var continentPlaceholderShape: some View {
        // Placeholder shapes until you implement SVGView
        switch continent {
        case .northAmerica:
            Ellipse().frame(maxWidth: .infinity, maxHeight: .infinity)
        case .southAmerica:
            RoundedRectangle(cornerRadius: 20).frame(maxWidth: .infinity, maxHeight: .infinity)
        case .europe:
            RoundedRectangle(cornerRadius: 15).frame(maxWidth: .infinity, maxHeight: .infinity)
        case .africa:
            Ellipse().frame(maxWidth: .infinity, maxHeight: .infinity)
        case .asia:
            RoundedRectangle(cornerRadius: 25).frame(maxWidth: .infinity, maxHeight: .infinity)
        case .oceania:
            HStack(spacing: 8) {
                Circle()
                Circle().scaleEffect(0.7)
                Circle().scaleEffect(0.5)
            }
        }
    }
}

struct EnhancedCountryGridMapWithSVG: View {
    let continent: Continent
    let selectedCells: [GridCell]
    let correctCells: [GridCell]
    let onCellSelected: (GridCell) -> Void
    
    private let gridSystem = GeographyGridSystem.shared
    
    var body: some View {
        let dimensions = gridSystem.getGridDimensions(for: continent)
        
        VStack(spacing: 16) {
            Text("Grid: \(dimensions.rows)×\(dimensions.cols)")
                .font(.caption)
                .foregroundColor(.white.opacity(0.8))
            
            ZStack {
                // ✅ SVG continent background
                ContinentDetailSVGBackground(continent: continent)
                    .frame(width: 300, height: 220)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                
                // Grid overlay
                VStack(spacing: 1) {
                    ForEach(0..<dimensions.rows, id: \.self) { row in
                        HStack(spacing: 1) {
                            ForEach(0..<dimensions.cols, id: \.self) { col in
                                let cell = GridCell(row: row, col: col)
                                
                                CountryGridCellButton(
                                    cell: cell,
                                    isSelected: selectedCells.contains(cell),
                                    isCorrect: correctCells.contains(cell),
                                    showFeedback: !correctCells.isEmpty,
                                    onTap: { onCellSelected(cell) }
                                )
                            }
                        }
                    }
                }
                .frame(width: 300, height: 220)
            }
            
            // Legend
            if !correctCells.isEmpty {
                HStack(spacing: 20) {
                    LegendItem(color: .green, label: "Correct", icon:"")
                    LegendItem(color: .orange, label: "Selected", icon:"")
                    LegendItem(color: .red, label: "Wrong", icon:"")
                }
            }
        }
    }
}

struct ContinentDetailSVGBackground: View {
    let continent: Continent
    
    private var detailSvgFileName: String {
        switch continent {
        case .northAmerica: return "north_america_detailed"
        case .southAmerica: return "south_america_detailed"
        case .europe: return "europe_detailed"
        case .africa: return "africa_detailed"
        case .asia: return "asia_detailed"
        case .oceania: return "oceania_detailed"
        }
    }
    
    var body: some View {
        ZStack {
            // Base continent color
            RoundedRectangle(cornerRadius: 12)
                .fill(
                    LinearGradient(
                        colors: [continent.color.opacity(0.5), continent.color.opacity(0.7)],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                )
            
            // ✅ TODO: Replace with actual detailed SVG when package is added
            // if let svgPath = Bundle.main.path(forResource: detailSvgFileName, ofType: "svg") {
            //     SVGView(contentsOfFile: svgPath)
            //         .foregroundColor(.white.opacity(0.3))
            //         .blendMode(.overlay)
            // }
            
            // Temporary placeholder
            continent.color.opacity(0.2)
        }
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(continent.color.opacity(0.8), lineWidth: 2)
        )
    }
}

struct CountryGridCellButton: View {
    let cell: GridCell
    let isSelected: Bool
    let isCorrect: Bool
    let showFeedback: Bool
    let onTap: () -> Void
    
    var body: some View {
        Button(action: onTap) {
            Rectangle()
                .fill(getCellColor().opacity(0.7))
                .overlay(
                    Rectangle()
                        .stroke(Color.white.opacity(0.8), lineWidth: 0.5)
                )
                .overlay(getCellContent())
        }
        .disabled(showFeedback)
        .animation(.easeInOut(duration: 0.2), value: isSelected)
        .animation(.easeInOut(duration: 0.3), value: isCorrect)
    }
    
    private func getCellColor() -> Color {
        if showFeedback {
            if isCorrect && isSelected {
                return .green
            } else if isCorrect {
                return .green.opacity(0.6)
            } else if isSelected {
                return .red
            }
        } else if isSelected {
            return .orange
        }
        return .clear
    }
    
    @ViewBuilder
    private func getCellContent() -> some View {
        if showFeedback {
            if isCorrect && isSelected {
                Image(systemName: "checkmark.circle.fill")
                    .font(.caption)
                    .foregroundColor(.white)
            } else if isSelected && !isCorrect {
                Image(systemName: "xmark.circle.fill")
                    .font(.caption)
                    .foregroundColor(.white)
            }
        } else if isSelected {
            Circle()
                .fill(Color.white.opacity(0.9))
                .frame(width: 6, height: 6)
        }
    }
}



struct SVGDebugView: View {
    var body: some View {
        VStack {
            Text("SVG Files Check:")
                .font(.headline)
            
            ForEach(Continent.allCases, id: \.self) { continent in
                let fileName = continent.rawValue.lowercased().replacingOccurrences(of: " ", with: "_")
                let hasFile = Bundle.main.url(forResource: fileName, withExtension: "svg") != nil
                
                HStack {
                    Text(fileName + ".svg")
                    Spacer()
                    Text(hasFile ? "✅ Found" : "❌ Missing")
                        .foregroundColor(hasFile ? .green : .red)
                }
            }
        }
        .padding()
    }
}

struct CountryFeedbackView: View {
    let message: String
    let isCorrect: Bool
    
    var body: some View {
        Text(message)
            .font(.headline)
            .fontWeight(.medium)
            .foregroundColor(.white)
            .padding()
            .background(isCorrect ? Color.green : Color.red)
            .clipShape(RoundedRectangle(cornerRadius: 12))
    }
}

struct EnhancedCountryGridFeedbackView: View {
    let result: PlacementResult
    let selectedCount: Int
    let correctCount: Int
    
    var body: some View {
        VStack(spacing: 8) {
            Text(result.accuracy)
                .font(.title2)
                .fontWeight(.bold)
                .foregroundColor(.white)
            
            Text("+\(result.score) points")
                .font(.headline)
                .foregroundColor(.white)
            
            HStack(spacing: 20) {
                VStack(spacing: 4) {
                    Text("Country spans")
                        .font(.caption)
                        .foregroundColor(.white.opacity(0.8))
                    Text("\(correctCount) cells")
                        .font(.subheadline)
                        .fontWeight(.semibold)
                        .foregroundColor(.white)
                }
                
                VStack(spacing: 4) {
                    Text("You selected")
                        .font(.caption)
                        .foregroundColor(.white.opacity(0.8))
                    Text("\(selectedCount) cells")
                        .font(.subheadline)
                        .fontWeight(.semibold)
                        .foregroundColor(.white)
                }
            }
        }
        .padding()
        .background(getBackgroundColor())
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }
    
    private func getBackgroundColor() -> Color {
        switch result.score {
        case 40...50: return .green
        case 20...39: return .orange
        default: return .red
        }
    }
}

// MARK: - Country Header View
struct CountryHeaderView: View {
    let timeRemaining: Int
    let totalScore: Int
    let questionsAnswered: Int
    let maxQuestions: Int
    let onBack: () -> Void
    
    var body: some View {
        HStack {
            Button(action: onBack) {
                Image(systemName: "pause.fill")
                    .font(.title2)
                    .foregroundColor(.white)
                    .frame(width: 44, height: 44)
                    .background(Color.blue.opacity(0.8))
                    .clipShape(RoundedRectangle(cornerRadius: 8))
            }
            
            Spacer()
            
            HStack(spacing: 16) {
                // Timer
                HStack(spacing: 4) {
                    Image(systemName: "clock")
                        .foregroundColor(.white)
                    Text("\(timeRemaining / 60):\(String(format: "%02d", timeRemaining % 60))")
                        .font(.headline)
                        .fontWeight(.bold)
                        .foregroundColor(.white)
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
                .background(Color.white.opacity(0.2))
                .clipShape(RoundedRectangle(cornerRadius: 8))
                
                // Score
                HStack(spacing: 4) {
                    Image(systemName: "star.fill")
                        .foregroundColor(.yellow)
                    Text("\(totalScore)")
                        .font(.headline)
                        .fontWeight(.bold)
                        .foregroundColor(.white)
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
                .background(Color.white.opacity(0.2))
                .clipShape(RoundedRectangle(cornerRadius: 8))
            }
        }
    }
}

struct CountryInstructionCard: View {
    let title: String
    let instructions: [String]
    let tip: String
    
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(title)
                .font(.headline)
                .fontWeight(.bold)
                .foregroundColor(.blue)
            
            ForEach(instructions, id: \.self) { instruction in
                Text(instruction)
                    .font(.subheadline)
                    .foregroundColor(.primary)
            }
            
            Text(tip)
                .font(.caption)
                .fontWeight(.medium)
                .foregroundColor(.blue)
        }
        .padding()
        .background(Color.white.opacity(0.95))
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .padding(.horizontal)
    }
}

// MARK: - Country Continent Selection View
struct CountryContinentSelectionView: View {
    let country: GeographyCountry
    let showFeedback: Bool
    let feedbackMessage: String
    let questionsAnswered: Int
    let maxQuestions: Int
    let onContinentSelected: (Continent) -> Void
    
    var body: some View {
        VStack(spacing: 20) {
            // Progress
            Text("Question \(questionsAnswered + 1) of \(maxQuestions)")
                .font(.headline)
                .foregroundColor(.white)
            
            // Country Card
            CountryCardView(country: country)
            
            Text("Which continent is \(country.name) located in?")
                .font(.title2)
                .fontWeight(.medium)
                .foregroundColor(.white)
                .multilineTextAlignment(.center)
            
            // World Map with Continents
            WorldMapView(onContinentSelected: onContinentSelected)
            
            // Feedback
            if showFeedback {
                GFeedbackView(message: feedbackMessage, isCorrect: feedbackMessage.contains("Correct"))
                    .transition(.scale.combined(with: .opacity))
            }
            
            Spacer()
        }
        .padding()
        .animation(.easeInOut, value: showFeedback)
    }
}

