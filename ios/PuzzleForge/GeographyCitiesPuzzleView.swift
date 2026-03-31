import SwiftUI

import Foundation

// MARK: - Geography Puzzle Generator
extension LocalMemoryPuzzleGenerator {
    
    // MARK: - Cities Puzzle Generator
    static func generateCitiesPuzzle(difficulty: String) -> (String, String) {
        let cityDatabase = GeographyCityDatabase.shared
        let randomCity = cityDatabase.getRandomCity()
        
        let questionData: [String: Any] = [
            "type": "geography_cities",
            "difficulty": difficulty,
            "city": [
                "id": randomCity.id,
                "name": randomCity.name,
                "country": randomCity.country,
                "continent": randomCity.continent.rawValue,
                "latitude": randomCity.latitude,
                "longitude": randomCity.longitude,
                "isCapital": randomCity.isCapital,
                "flag": randomCity.flag
            ],
            "timeLimit": getTimeLimit(for: difficulty),
            "maxQuestions": getMaxQuestions(for: difficulty),
            "instructions": "Place \(randomCity.name) in the correct continent and grid location"
        ]
        
        let answerData: [String: Any] = [
            "continent": randomCity.continent.rawValue,
            "gridCell": getCorrectGridCell(for: randomCity),
            "cityId": randomCity.id
        ]
        
        guard let questionJson = try? JSONSerialization.data(withJSONObject: questionData),
              let answerJson = try? JSONSerialization.data(withJSONObject: answerData),
              let questionString = String(data: questionJson, encoding: .utf8),
              let answerString = String(data: answerJson, encoding: .utf8) else {
            print("❌ Failed to serialize cities puzzle data")
            return ("", "")
        }
        
        print("✅ Generated cities puzzle for: \(randomCity.name), \(randomCity.country)")
        return (questionString, answerString)
    }
    
    // MARK: - Countries Puzzle Generator
    static func generateCountriesPuzzle(difficulty: String) -> (String, String) {
        let countryDatabase = GeographyCountryDatabase.shared
        let randomCountry = countryDatabase.getRandomCountry()
        
        let questionData: [String: Any] = [
            "type": "geography_countries",
            "difficulty": difficulty,
            "country": [
                "id": randomCountry.id,
                "name": randomCountry.name,
                "continent": randomCountry.continent.rawValue,
                "latitude": randomCountry.latitude,
                "longitude": randomCountry.longitude,
                "isLandlocked": randomCountry.isLandlocked,
                "flag": randomCountry.flag,
                "capital": randomCountry.capital
            ],
            "timeLimit": getTimeLimit(for: difficulty),
            "maxQuestions": getMaxQuestions(for: difficulty),
            "instructions": "Select all grid sections where \(randomCountry.name) is located"
        ]
        
        let answerData: [String: Any] = [
            "continent": randomCountry.continent.rawValue,
            "gridCells": getCorrectGridCells(for: randomCountry),
            "countryId": randomCountry.id
        ]
        
        guard let questionJson = try? JSONSerialization.data(withJSONObject: questionData),
              let answerJson = try? JSONSerialization.data(withJSONObject: answerData),
              let questionString = String(data: questionJson, encoding: .utf8),
              let answerString = String(data: answerJson, encoding: .utf8) else {
            print("❌ Failed to serialize countries puzzle data")
            return ("", "")
        }
        
        print("✅ Generated countries puzzle for: \(randomCountry.name) (\(randomCountry.capital))")
        return (questionString, answerString)
    }
    
    // MARK: - Helper Methods
    
    private static func getTimeLimit(for difficulty: String) -> Int {
        switch difficulty.lowercased() {
        case "easy":
            return 45 // 45 seconds per question
        case "medium":
            return 35 // 35 seconds per question
        case "hard":
            return 25 // 25 seconds per question
        default:
            return 35
        }
    }
    
    private static func getMaxQuestions(for difficulty: String) -> Int {
        switch difficulty.lowercased() {
        case "easy":
            return 8 // 8 questions total
        case "medium":
            return 12 // 12 questions total
        case "hard":
            return 15 // 15 questions total
        default:
            return 10
        }
    }
    
    private static func getCorrectGridCell(for city: GeographyCity) -> [String: Int] {
        if let gridCell = GeographyGridSystem.shared.getCorrectGridCell(for: city.id) {
            return [
                "row": gridCell.row,
                "col": gridCell.col
            ]
        }
        // Fallback to center position if not found
        print("⚠️ No grid cell found for city: \(city.name), using fallback")
        return ["row": 2, "col": 2]
    }
    
    private static func getCorrectGridCells(for country: GeographyCountry) -> [[String: Int]] {
        if let gridCells = GeographyGridSystem.shared.getCorrectGridCells(for: country.id) {
            return gridCells.map { gridCell in
                [
                    "row": gridCell.row,
                    "col": gridCell.col
                ]
            }
        }
        // Fallback to single center position if not found
        print("⚠️ No grid cells found for country: \(country.name), using fallback")
        return [["row": 2, "col": 2]]
    }
}

// MARK: - Extended Generator with Validation
extension LocalMemoryPuzzleGenerator {
    
    // Generate and validate cities puzzle
    static func generateValidatedCitiesPuzzle(difficulty: String) -> (String, String) {
        let (question, answer) = generateCitiesPuzzle(difficulty: difficulty)
        
        // Validate the generated data
        if validateCitiesPuzzleData(question: question, answer: answer) {
            return (question, answer)
        } else {
            print("⚠️ Generated cities puzzle failed validation, retrying...")
            // Retry once
            return generateCitiesPuzzle(difficulty: difficulty)
        }
    }
    
    // Generate and validate countries puzzle
    static func generateValidatedCountriesPuzzle(difficulty: String) -> (String, String) {
        let (question, answer) = generateCountriesPuzzle(difficulty: difficulty)
        
        // Validate the generated data
        if validateCountriesPuzzleData(question: question, answer: answer) {
            return (question, answer)
        } else {
            print("⚠️ Generated countries puzzle failed validation, retrying...")
            // Retry once
            return generateCountriesPuzzle(difficulty: difficulty)
        }
    }
    
    // MARK: - Validation Methods
    
    private static func validateCitiesPuzzleData(question: String, answer: String) -> Bool {
        guard !question.isEmpty, !answer.isEmpty else {
            print("❌ Empty question or answer data")
            return false
        }
        
        // Try to parse and validate JSON structure
        guard let questionData = question.data(using: .utf8),
              let answerData = answer.data(using: .utf8),
              let questionJson = try? JSONSerialization.jsonObject(with: questionData) as? [String: Any],
              let answerJson = try? JSONSerialization.jsonObject(with: answerData) as? [String: Any] else {
            print("❌ Invalid JSON structure in cities puzzle")
            return false
        }
        
        // Validate required fields
        guard let cityData = questionJson["city"] as? [String: Any],
              let cityName = cityData["name"] as? String,
              let continent = answerJson["continent"] as? String,
              let gridCell = answerJson["gridCell"] as? [String: Int] else {
            print("❌ Missing required fields in cities puzzle")
            return false
        }
        
        print("✅ Cities puzzle validation passed for: \(cityName)")
        return true
    }
    
    private static func validateCountriesPuzzleData(question: String, answer: String) -> Bool {
        guard !question.isEmpty, !answer.isEmpty else {
            print("❌ Empty question or answer data")
            return false
        }
        
        // Try to parse and validate JSON structure
        guard let questionData = question.data(using: .utf8),
              let answerData = answer.data(using: .utf8),
              let questionJson = try? JSONSerialization.jsonObject(with: questionData) as? [String: Any],
              let answerJson = try? JSONSerialization.jsonObject(with: answerData) as? [String: Any] else {
            print("❌ Invalid JSON structure in countries puzzle")
            return false
        }
        
        // Validate required fields
        guard let countryData = questionJson["country"] as? [String: Any],
              let countryName = countryData["name"] as? String,
              let continent = answerJson["continent"] as? String,
              let gridCells = answerJson["gridCells"] as? [[String: Int]] else {
            print("❌ Missing required fields in countries puzzle")
            return false
        }
        
        // Validate that we have at least one grid cell
        guard !gridCells.isEmpty else {
            print("❌ No grid cells found for country")
            return false
        }
        
        print("✅ Countries puzzle validation passed for: \(countryName) with \(gridCells.count) cells")
        return true
    }
}

// MARK: - Sample Usage Examples
extension LocalMemoryPuzzleGenerator {
    
    // Example method showing how to use the generators
    static func generateSamplePuzzles() {
        print("🌍 Generating sample geography puzzles...")
        
        // Generate a cities puzzle
        let (citiesQuestion, citiesAnswer) = generateValidatedCitiesPuzzle(difficulty: "medium")
        print("📍 Cities Question: \(citiesQuestion)")
        print("📍 Cities Answer: \(citiesAnswer)")
        print("---")
        
        // Generate a countries puzzle
        let (countriesQuestion, countriesAnswer) = generateValidatedCountriesPuzzle(difficulty: "medium")
        print("🗺️ Countries Question: \(countriesQuestion)")
        print("🗺️ Countries Answer: \(countriesAnswer)")
    }
    
    // Get puzzle type from category string
    static func getPuzzleType(from category: String) -> String? {
        switch category.lowercased() {
        case "geography_cities", "geographycities":
            return "cities"
        case "geography_countries", "geographycountries":
            return "countries"
        default:
            return nil
        }
    }
    
    // Generate puzzle based on category string
    static func generatePuzzle(for category: String, difficulty: String) -> (String, String) {
        switch category.lowercased() {
        case "geography_cities", "geographycities":
            return generateValidatedCitiesPuzzle(difficulty: difficulty)
        case "geography_countries", "geographycountries":
            return generateValidatedCountriesPuzzle(difficulty: difficulty)
        default:
            print("❌ Unknown geography puzzle category: \(category)")
            return ("", "")
        }
    }
}

struct GeographyCitiesPuzzleView: View {
    let difficulty: String
    let timer: String
    let hearts: Int
    let level: String
    let puzzleData: String
    let correctAnswer: String
    let onSubmitAnswer: (Bool) -> Void
    let fetchNextPuzzle: (Int) -> Void
    let onBack: () -> Void
    
    @StateObject private var cityDatabase = GeographyCityDatabase.shared
    @State private var gameStage: GameStage = .instructions
    @State private var currentCity: GeographyCity?
    @State private var selectedContinent: Continent?
    @State private var currentScore = 0
    @State private var totalScore = 0
    @State private var questionsAnswered = 0
    @State private var showFeedback = false
    @State private var feedbackMessage = ""
    @State private var lastResult: PlacementResult?
    @State private var timeRemaining: Int
    @State private var selectedGridCell: GridCell?
    
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
                        
                        // ✅ MAIN FIX: Use Enhanced Views Instead
                        switch gameStage {
                        case .instructions:
                            InstructionsView(onStartGame: startGame)
                            
                        case .continentSelection:
                            if let city = currentCity {
                                // ✅ CHANGED: Use EnhancedContinentSelectionView
                                EnhancedContinentSelectionView(
                                    city: city,
                                    showFeedback: showFeedback,
                                    feedbackMessage: feedbackMessage,
                                    questionsAnswered: questionsAnswered,
                                    maxQuestions: maxQuestions,
                                    onContinentSelected: handleContinentSelection
                                )
                            }
                            
                        case .precisePlacement:
                            if let city = currentCity, let continent = selectedContinent {
                                // ✅ CHANGED: Use EnhancedPrecisePlacementView
                                EnhancedPrecisePlacementView(
                                    city: city,
                                    continent: continent,
                                    selectedGridCell: selectedGridCell,
                                    showFeedback: showFeedback,
                                    lastResult: lastResult,
                                    questionsAnswered: questionsAnswered,
                                    maxQuestions: maxQuestions,
                                    onGridCellSelected: handleGridSelection
                                )
                            }
                            
                        case .completed:
                            CompletionView(
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
        currentCity = cityDatabase.getRandomCity()
        selectedContinent = nil
        selectedGridCell = nil
        currentScore = 0
        showFeedback = false
        feedbackMessage = ""
        lastResult = nil
    }
    
    private func handleContinentSelection(_ continent: Continent) {
        guard let city = currentCity else { return }
        
        if continent == city.continent {
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
            feedbackMessage = "Wrong continent. \(city.name) is in \(city.continent.rawValue)."
            showFeedback = true
            
            DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) {
                showFeedback = false
                moveToNextQuestion()
            }
        }
    }
    
    private func handleGridSelection(_ gridCell: GridCell) {
        guard let city = currentCity else { return }
        
        selectedGridCell = gridCell
        let result = GeographyGridSystem.shared.calculateCityScore(cityId: city.id, selectedGrid: gridCell)
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

// MARK: - Header View
struct HeaderView: View {
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

// MARK: - Instructions View
struct InstructionsView: View {
    let onStartGame: () -> Void
    
    var body: some View {
        VStack(spacing: 20) {
            VStack(spacing: 16) {
                Text("🌍")
                    .font(.system(size: 60))
                
                Text("Geography Cities Challenge")
                    .font(.title)
                    .fontWeight(.bold)
                    .foregroundColor(.white)
                    .multilineTextAlignment(.center)
                
                Text("Place cities in their correct locations on the world map")
                    .font(.title3)
                    .foregroundColor(.white.opacity(0.9))
                    .multilineTextAlignment(.center)
            }
            
            VStack(spacing: 20) {
                InstructionCard(
                    title: "How to Play:",
                    instructions: [
                        "1. Click on the correct continent for the city",
                        "2. If correct, choose the precise grid location",
                        "3. Get points based on accuracy",
                        "4. Complete all questions before time runs out"
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

struct InstructionCard: View {
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
                    .foregroundColor(.black)
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

struct CityCardView: View {
    let city: GeographyCity
    
    var body: some View {
        VStack(spacing: 12) {
            // Flag - larger and centered
            Text(city.flag)
                .font(.system(size: 50)) // ✅ Increased from 40 to 50
            
            // City name only - no country text
            Text(city.name)
                .font(.title2)
                .fontWeight(.bold)
                .foregroundColor(.black)
                .multilineTextAlignment(.center)
                .lineLimit(2)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(.horizontal, 16) // ✅ More horizontal padding
        .padding(.vertical, 16)   // ✅ More vertical padding
        .frame(minWidth: 200)     // ✅ Increased minimum width
        .frame(height: 120)       // ✅ Increased height from 100 to 120
        .background(Color.white)
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .shadow(radius: 8)
    }
}

struct WorldMapView: View {
    let onContinentSelected: (Continent) -> Void
    
    var body: some View {
        VStack(spacing: 20) {
            // Top Row: North America, Europe
            HStack(spacing: 20) {
                ContinentButton(continent: .northAmerica, onTap: onContinentSelected)
                ContinentButton(continent: .europe, onTap: onContinentSelected)
            }
            
            // Middle Row: Asia (centered)
            ContinentButton(continent: .asia, onTap: onContinentSelected)
                .frame(maxWidth: 280)
            
            // Bottom Row: South America, Africa, Oceania
            HStack(spacing: 20) {
                ContinentButton(continent: .southAmerica, onTap: onContinentSelected)
                ContinentButton(continent: .africa, onTap: onContinentSelected)
                ContinentButton(continent: .oceania, onTap: onContinentSelected)
            }
        }
        .padding()
        .background(Color.blue.opacity(0.3))
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }
}

struct ContinentButton: View {
    let continent: Continent
    let onTap: (Continent) -> Void
    
    var body: some View {
        Button(action: { onTap(continent) }) {
            Text(continent.rawValue)
                .font(.subheadline)
                .fontWeight(.semibold)
                .foregroundColor(.white)
                .multilineTextAlignment(.center)
                .padding()
                .frame(minWidth: 80, minHeight: 60)
                .background(continent.color)
                .clipShape(RoundedRectangle(cornerRadius: 8))
        }
    }
}

// MARK: - Precise Placement View
struct PrecisePlacementView: View {
    let city: GeographyCity
    let continent: Continent
    let selectedGridCell: GridCell?
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
            
            // City Card
            CityCardView(city: city)
            
            Text(" \(city.name)")
                .font(.title3)
                .fontWeight(.medium)
                .foregroundColor(.white)
                .multilineTextAlignment(.center)
            
            // Grid Map
            GridMapView(
                continent: continent,
                selectedCell: selectedGridCell,
                correctCell: showFeedback ? gridSystem.getCorrectGridCell(for: city.id) : nil,
                onCellSelected: onGridCellSelected
            )
            
            // Feedback
            if showFeedback, let result = lastResult {
                GridFeedbackView(result: result)
                    .transition(.scale.combined(with: .opacity))
            }
            
            Spacer()
        }
        .padding()
        .animation(.easeInOut, value: showFeedback)
    }
}

struct GridMapView: View {
    let continent: Continent
    let selectedCell: GridCell?
    let correctCell: GridCell?
    let onCellSelected: (GridCell) -> Void
    
    private let gridSystem = GeographyGridSystem.shared
    
    var body: some View {
        let dimensions = gridSystem.getGridDimensions(for: continent)
        
        VStack(spacing: 2) {
            ForEach(0..<dimensions.rows, id: \.self) { row in
                HStack(spacing: 2) {
                    ForEach(0..<dimensions.cols, id: \.self) { col in
                        let cell = GridCell(row: row, col: col)
                        
                        Button(action: { onCellSelected(cell) }) {
                            Rectangle()
                                .fill(getCellColor(for: cell))
                                .frame(height: 50)
                                .overlay(
                                    Rectangle()
                                        .stroke(Color.white.opacity(0.6), lineWidth: 1)
                                )
                                .overlay(
                                    getCellContent(for: cell)
                                )
                        }
                        .disabled(showFeedback)
                    }
                }
            }
        }
        .background(continent.color.opacity(0.3))
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(continent.color, lineWidth: 3)
        )
    }
    
    private var showFeedback: Bool {
        correctCell != nil
    }
    
    private func getCellColor(for cell: GridCell) -> Color {
        if let correctCell = correctCell, cell == correctCell {
            return .green.opacity(0.7)
        } else if let selectedCell = selectedCell, cell == selectedCell {
            return .orange.opacity(0.7)
        } else {
            return .clear
        }
    }
    
    @ViewBuilder
    private func getCellContent(for cell: GridCell) -> some View {
        if let correctCell = correctCell, cell == correctCell {
            Image(systemName: "checkmark")
                .font(.title2)
                .foregroundColor(.white)
        } else if let selectedCell = selectedCell, cell == selectedCell, correctCell != nil && cell != correctCell {
            Image(systemName: "xmark")
                .font(.title2)
                .foregroundColor(.white)
        }
    }
}

struct GridFeedbackView: View {
    let result: PlacementResult
    
    var body: some View {
        VStack(spacing: 8) {
            Text(result.accuracy)
                .font(.title2)
                .fontWeight(.bold)
                .foregroundColor(.white)
            
            Text("+\(result.score) points")
                .font(.headline)
                .foregroundColor(.white)
            
            if result.isCorrect {
                Text("Exact location!")
                    .font(.subheadline)
                    .foregroundColor(.white)
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

struct GFeedbackView: View {
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

// MARK: - Completion View
struct CompletionView: View {
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
                GScoreCard(
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

struct GScoreCard: View {
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
                Text("Cities Placed: \(questionsAnswered)")
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
