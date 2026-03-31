//
//  GeographyCitiesPuzzleTutorialManager.swift
//  PuzzleForge
//
//  Created by Sushanth Tiruvaipati on 8/7/25.
//


//
//  GeographyCitiesPuzzleTutorialManager.swift
//  PuzzleForge
//
//  Created by Assistant on [Current Date]
//

import SwiftUI

// MARK: - Tutorial Manager
class GeographyCitiesPuzzleTutorialManager: ObservableObject {
    func getTutorialSteps() -> [TutorialStep] {
        return [
            TutorialStep(
                title: "Welcome to Geography Cities! 🌍",
                description: "Learn how to place cities in their correct locations on the world map. Test your knowledge of global geography!",
                targetComponent: "map",
                id: "" // Informational
            ),
            TutorialStep(
                title: "The City Challenge 📍",
                description: "You'll see a city card with its flag. Your job is to first identify the correct continent, then place it precisely on the grid.",
                targetComponent: "city_card",
                id: "" // Informational
            ),
            TutorialStep(
                title: "Select the Continent 🗺️",
                description: "First, tap on the continent where you think this city is located. Each continent has its own color and shape.",
                targetComponent: "continent_map",
                id: "select_continent" // Interactive
            ),
            TutorialStep(
                title: "Great Choice! ✅",
                description: "Excellent! When you select the correct continent, you earn 25 points and move to the precise placement stage.",
                targetComponent: "continent_map",
                id: "" // Informational
            ),
            TutorialStep(
                title: "Precise Placement Grid 🎯",
                description: "Now you'll see a detailed grid of the continent. Tap on the grid cell where you think the city is most likely located.",
                targetComponent: "grid_map",
                id: "select_grid_cell" // Interactive
            ),
            TutorialStep(
                title: "Scoring System 💎",
                description: "You get up to 50 additional points based on accuracy. The closer you are to the actual location, the higher your score!",
                targetComponent: "feedback",
                id: "" // Informational
            ),
            TutorialStep(
                title: "Watch Your Time ⏰",
                description: "Keep an eye on the timer at the top. You need to complete all questions before time runs out to maximize your score.",
                targetComponent: "header",
                id: "" // Informational
            ),
            TutorialStep(
                title: "Track Your Progress 📊",
                description: "The header shows your current score, remaining time, and question progress. Try to build up streaks for bonus points!",
                targetComponent: "header",
                id: "" // Informational
            ),
            TutorialStep(
                title: "Strategy Tips 🧠",
                description: "Look for clues like the flag design, city name origins, and architectural styles. Capital cities are often centrally located!",
                targetComponent: "city_card",
                id: "" // Informational
            ),
            TutorialStep(
                title: "Ready to Explore! 🚀",
                description: "Perfect! You now know how to play Geography Cities. Use your knowledge and intuition to place cities accurately around the world!",
                targetComponent: "map",
                id: "" // Informational
            )
        ]
    }
}

// MARK: - Main Tutorial View
struct GeographyCitiesPuzzleTutorialView: View {
    let onTutorialComplete: () -> Void
    let onTutorialSkipped: () -> Void
    let onBack: () -> Void
    
    @StateObject private var tutorialManager = GeographyCitiesPuzzleTutorialManager()
    @State private var currentStepIndex = 0
    @State private var tutorialState = TutorialState.active
    @State private var sampleCitiesData = createSampleCitiesData()
    
    private var steps: [TutorialStep] {
        tutorialManager.getTutorialSteps()
    }
    
    private var currentStep: TutorialStep? {
        guard currentStepIndex < steps.count else { return nil }
        return steps[currentStepIndex]
    }
    
    var body: some View {
        ZStack {
            // Tutorial version of the cities screen
            TutorialCitiesContent(
                currentStep: currentStep,
                sampleCitiesData: sampleCitiesData,
                onTutorialAction: handleTutorialAction,
                onBack: onBack
            )
            
            // Tutorial overlay
            if tutorialState == .active && currentStepIndex < steps.count {
                let step = steps[currentStepIndex]
                
                if step.id.isEmpty {
                    // Informational step
                    GeographyCitiesSmartTutorialOverlay(
                        currentStep: step,
                        totalSteps: steps.count,
                        currentStepNumber: currentStepIndex + 1,
                        onNext: advanceStep,
                        onSkip: skipTutorial
                    )
                } else {
                    // Interactive step
                    GeographyCitiesMinimalInteractiveOverlay(
                        currentStep: step,
                        totalSteps: steps.count,
                        currentStepNumber: currentStepIndex + 1,
                        onNext: advanceStep,
                        onSkip: skipTutorial
                    )
                }
            }
        }
        .onChange(of: tutorialState) { state in
            switch state {
            case .completed:
                onTutorialComplete()
            case .skipped:
                onTutorialSkipped()
            case .active:
                break
            }
        }
    }
    
    private func advanceStep() {
        if currentStepIndex < steps.count - 1 {
            currentStepIndex += 1
        } else {
            tutorialState = .completed
        }
    }
    
    private func skipTutorial() {
        tutorialState = .skipped
    }
    
    private func handleTutorialAction(_ action: String) {
        let step = steps[safe: currentStepIndex]
        if let step = step, !step.id.isEmpty && step.id == action {
            advanceStep()
        }
    }
}

// MARK: - Tutorial Cities Content
struct TutorialCitiesContent: View {
    let currentStep: TutorialStep?
    let sampleCitiesData: String
    let onTutorialAction: (String) -> Void
    let onBack: () -> Void
    
    @State private var gameStage: TutorialCitiesGameStage = .continentSelection
    @State private var currentCity: TutorialGeographyCity?
    @State private var selectedContinent: TutorialContinent?
    @State private var selectedGridCell: TutorialGridCell?
    @State private var showFeedback = false
    @State private var feedbackMessage = ""
    @State private var totalScore = 245
    @State private var questionsAnswered = 3
    @State private var timeRemaining = 420 // 7 minutes remaining
    
    private let maxQuestions = 10
    
    var body: some View {
        GeometryReader { geometry in
            ZStack {
                // Background matching the actual cities view
                LinearGradient(
                    colors: [Color(red: 0.12, green: 0.23, blue: 0.54), Color(red: 0.18, green: 0.35, blue: 0.75)],
                    startPoint: .top,
                    endPoint: .bottom
                )
                .ignoresSafeArea()
                
                VStack(spacing: 0) {
                    // Header
                    tutorialHeaderView
                        .padding(.horizontal)
                        .padding(.top, 10)
                    
                    // Main content based on game stage
                    switch gameStage {
                    case .continentSelection:
                        if let city = currentCity {
                            tutorialContinentSelectionView(city: city)
                        }
                    case .precisePlacement:
                        if let city = currentCity, let continent = selectedContinent {
                            tutorialPrecisePlacementView(city: city, continent: continent)
                        }
                    }
                }
            }
        }
        .navigationBarHidden(true)
        .onAppear {
            setupTutorialCities()
        }
    }
    
    // MARK: - UI Components
    private var tutorialHeaderView: some View {
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
        .modifier(TutorialHighlight(isHighlighted: currentStep?.targetComponent == "header"))
    }
    
    private func tutorialContinentSelectionView(city: TutorialGeographyCity) -> some View {
        VStack(spacing: 20) {
            // Progress
            Text("Question \(questionsAnswered + 1) of \(maxQuestions)")
                .font(.headline)
                .foregroundColor(.white)
            
            // City Card
            tutorialCityCardView(city: city)
                .modifier(TutorialHighlight(isHighlighted: currentStep?.targetComponent == "city_card"))
            
            // World Map
            tutorialWorldMapView()
                .modifier(TutorialHighlight(isHighlighted: currentStep?.targetComponent == "continent_map"))
            
            // Feedback
            if showFeedback {
                tutorialFeedbackView(message: feedbackMessage, isCorrect: feedbackMessage.contains("Correct"))
                    .transition(.scale.combined(with: .opacity))
                    .modifier(TutorialHighlight(isHighlighted: currentStep?.targetComponent == "feedback"))
            }
            
            Spacer()
        }
        .padding()
        .animation(.easeInOut, value: showFeedback)
    }
    
    private func tutorialPrecisePlacementView(city: TutorialGeographyCity, continent: TutorialContinent) -> some View {
        VStack(spacing: 20) {
            // Progress
            Text("Question \(questionsAnswered + 1) of \(maxQuestions) - Precise Placement")
                .font(.headline)
                .foregroundColor(.white)
                .multilineTextAlignment(.center)
            
            // City Card
            tutorialCityCardView(city: city)
                .modifier(TutorialHighlight(isHighlighted: currentStep?.targetComponent == "city_card"))
            
            Text("Place \(city.name) precisely")
                .font(.title3)
                .fontWeight(.medium)
                .foregroundColor(.white)
                .multilineTextAlignment(.center)
            
            // Grid Map
            tutorialGridMapView(continent: continent)
                .modifier(TutorialHighlight(isHighlighted: currentStep?.targetComponent == "grid_map"))
            
            // Feedback for grid placement
            if showFeedback {
                tutorialGridFeedbackView()
                    .transition(.scale.combined(with: .opacity))
                    .modifier(TutorialHighlight(isHighlighted: currentStep?.targetComponent == "feedback"))
            }
            
            Spacer()
        }
        .padding()
        .animation(.easeInOut, value: showFeedback)
    }
    
    private func tutorialCityCardView(city: TutorialGeographyCity) -> some View {
        VStack(spacing: 12) {
            // Flag
            Text(city.flag)
                .font(.system(size: 50))
            
            // City name
            Text(city.name)
                .font(.title2)
                .fontWeight(.bold)
                .foregroundColor(.black)
                .multilineTextAlignment(.center)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 16)
        .frame(minWidth: 200)
        .frame(height: 120)
        .background(Color.white)
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .shadow(radius: 8)
    }
    
    private func tutorialWorldMapView() -> some View {
        VStack(spacing: 20) {
            // Top Row: North America, Europe
            HStack(spacing: 20) {
                tutorialContinentButton(continent: .northAmerica)
                tutorialContinentButton(continent: .europe)
            }
            
            // Middle Row: Asia (centered)
            tutorialContinentButton(continent: .asia)
                .frame(maxWidth: 280)
            
            // Bottom Row: South America, Africa, Oceania
            HStack(spacing: 20) {
                tutorialContinentButton(continent: .southAmerica)
                tutorialContinentButton(continent: .africa)
                tutorialContinentButton(continent: .oceania)
            }
        }
        .padding()
        .background(Color.blue.opacity(0.3))
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }
    
    private func tutorialContinentButton(continent: TutorialContinent) -> some View {
        Button(action: {
            handleContinentSelection(continent)
        }) {
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
    
    private func tutorialGridMapView(continent: TutorialContinent) -> some View {
        VStack(spacing: 2) {
            ForEach(0..<4, id: \.self) { row in
                HStack(spacing: 2) {
                    ForEach(0..<5, id: \.self) { col in
                        let cell = TutorialGridCell(row: row, col: col)
                        
                        Button(action: {
                            handleGridSelection(cell)
                        }) {
                            Rectangle()
                                .fill(getTutorialCellColor(for: cell))
                                .frame(height: 50)
                                .overlay(
                                    Rectangle()
                                        .stroke(Color.white.opacity(0.6), lineWidth: 1)
                                )
                                .overlay(getTutorialCellContent(for: cell))
                        }
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
    
    private func tutorialFeedbackView(message: String, isCorrect: Bool) -> some View {
        Text(message)
            .font(.headline)
            .fontWeight(.medium)
            .foregroundColor(.white)
            .padding()
            .background(isCorrect ? Color.green : Color.red)
            .clipShape(RoundedRectangle(cornerRadius: 12))
    }
    
    private func tutorialGridFeedbackView() -> some View {
        VStack(spacing: 8) {
            Text("Very Close!")
                .font(.title2)
                .fontWeight(.bold)
                .foregroundColor(.white)
            
            Text("+45 points")
                .font(.headline)
                .foregroundColor(.white)
            
            Text("Great geographical knowledge!")
                .font(.subheadline)
                .foregroundColor(.white)
        }
        .padding()
        .background(Color.green)
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }
    
    // MARK: - Tutorial Logic
    private func setupTutorialCities() {
        // Set up sample city data
        currentCity = TutorialGeographyCity(
            name: "Tokyo",
            flag: "🇯🇵",
            continent: .asia
        )
    }
    
    private func handleContinentSelection(_ continent: TutorialContinent) {
        guard let city = currentCity else { return }
        
        if continent == city.continent {
            selectedContinent = continent
            feedbackMessage = "Correct continent! Now place it precisely."
            showFeedback = true
            onTutorialAction("select_continent")
            
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
                showFeedback = false
                gameStage = .precisePlacement
            }
        } else {
            feedbackMessage = "Wrong continent. \(city.name) is in \(city.continent.rawValue)."
            showFeedback = true
            onTutorialAction("select_continent")
        }
    }
    
    private func handleGridSelection(_ gridCell: TutorialGridCell) {
        selectedGridCell = gridCell
        showFeedback = true
        onTutorialAction("select_grid_cell")
        
        // Add to score for tutorial effect
        totalScore += 45
    }
    
    private func getTutorialCellColor(for cell: TutorialGridCell) -> Color {
        if let selected = selectedGridCell, selected == cell {
            return .orange.opacity(0.7)
        } else if showFeedback && cell.row == 1 && cell.col == 2 {
            return .green.opacity(0.7) // Correct cell
        }
        return .clear
    }
    
    @ViewBuilder
    private func getTutorialCellContent(for cell: TutorialGridCell) -> some View {
        if showFeedback && cell.row == 1 && cell.col == 2 {
            Image(systemName: "checkmark")
                .font(.title2)
                .foregroundColor(.white)
        } else if let selected = selectedGridCell, selected == cell && showFeedback {
            Image(systemName: "location.fill")
                .font(.title2)
                .foregroundColor(.white)
        }
    }
}

// MARK: - Geography Cities Specific Tutorial Overlay Views
struct GeographyCitiesSmartTutorialOverlay: View {
    let currentStep: TutorialStep
    let totalSteps: Int
    let currentStepNumber: Int
    let onNext: () -> Void
    let onSkip: () -> Void
    
    var body: some View {
        GeometryReader { geometry in
            VStack {
                // Smart positioning based on target component
                if currentStep.targetComponent == "header" {
                    tutorialCard
                        .padding(.top, 100)
                    Spacer()
                } else if currentStep.targetComponent == "grid_map" {
                    Spacer()
                    tutorialCard
                        .padding(.bottom, 20)
                } else if currentStep.targetComponent == "continent_map" {
                    Spacer()
                    tutorialCard
                        .padding(.bottom, 20)
                } else {
                    Spacer()
                    tutorialCard
                        .padding(.bottom, 100)
                }
            }
        }
        .background(Color.clear)
    }
    
    private var tutorialCard: some View {
        VStack(spacing: 16) {
            // Progress indicator
            HStack {
                ForEach(1...totalSteps, id: \.self) { step in
                    Circle()
                        .fill(step <= currentStepNumber ? Color.white : Color.white.opacity(0.3))
                        .frame(width: 8, height: 8)
                }
            }

            VStack(spacing: 12) {
                Text(currentStep.title)
                    .font(.headline)
                    .fontWeight(.bold)
                    .foregroundColor(.white)
                    .multilineTextAlignment(.center)

                Text(currentStep.description)
                    .font(.subheadline)
                    .foregroundColor(.white.opacity(0.9))
                    .multilineTextAlignment(.center)
            }

            HStack(spacing: 16) {
                Button(action: onSkip) {
                    Text("Skip Tutorial")
                        .foregroundColor(.white.opacity(0.7))
                        .padding(.horizontal, 20)
                        .padding(.vertical, 8)
                        .overlay(
                            RoundedRectangle(cornerRadius: 20)
                                .stroke(Color.white.opacity(0.3), lineWidth: 1)
                        )
                }

                Button(action: onNext) {
                    Text("Next")
                        .fontWeight(.semibold)
                        .foregroundColor(Color(red: 0.12, green: 0.23, blue: 0.54))
                        .padding(.horizontal, 24)
                        .padding(.vertical, 12)
                        .background(Color.white)
                        .cornerRadius(25)
                }
            }
        }
        .padding(24)
        .background(
            RoundedRectangle(cornerRadius: 16)
                .fill(Color.black.opacity(0.85))
        )
        .padding(.horizontal, 20)
    }
}

struct GeographyCitiesMinimalInteractiveOverlay: View {
    let currentStep: TutorialStep
    let totalSteps: Int
    let currentStepNumber: Int
    let onNext: () -> Void
    let onSkip: () -> Void
    
    var body: some View {
        GeometryReader { geometry in
            VStack {
                // Smart positioning to avoid blocking interactive elements
                if currentStep.targetComponent == "continent_map" || currentStep.targetComponent == "grid_map" {
                    tutorialCard
                        .padding(.top, 80)
                    Spacer()
                } else {
                    Spacer()
                    tutorialCard
                        .padding(.bottom, 80)
                }
            }
        }
        .background(Color.clear)
    }
    
    private var tutorialCard: some View {
        VStack(spacing: 16) {
            // Progress indicator
            HStack {
                ForEach(1...totalSteps, id: \.self) { step in
                    Circle()
                        .fill(step <= currentStepNumber ? Color.white : Color.white.opacity(0.3))
                        .frame(width: 8, height: 8)
                }
            }
            
            VStack(spacing: 12) {
                Text(currentStep.title)
                    .font(.headline)
                    .fontWeight(.bold)
                    .foregroundColor(.white)
                    .multilineTextAlignment(.center)
                
                Text(currentStep.description)
                    .font(.subheadline)
                    .foregroundColor(.white.opacity(0.9))
                    .multilineTextAlignment(.center)
                    .lineLimit(nil)
                
                Text("👆 Try it now!")
                    .font(.caption)
                    .fontWeight(.medium)
                    .foregroundColor(.yellow)
                    .padding(.top, 4)
            }
            
            Button(action: onSkip) {
                Text("Skip Tutorial")
                    .foregroundColor(.white.opacity(0.7))
                    .padding(.horizontal, 20)
                    .padding(.vertical, 8)
                    .overlay(
                        RoundedRectangle(cornerRadius: 20)
                            .stroke(Color.white.opacity(0.3), lineWidth: 1)
                    )
            }
        }
        .padding(20)
        .background(
            RoundedRectangle(cornerRadius: 16)
                .fill(Color.black.opacity(0.85))
        )
        .padding(.horizontal, 20)
    }
}

// MARK: - Tutorial Data Models
enum TutorialCitiesGameStage {
    case continentSelection, precisePlacement
}

struct TutorialGeographyCity {
    let name: String
    let flag: String
    let continent: TutorialContinent
}

enum TutorialContinent: String, CaseIterable {
    case northAmerica = "North America"
    case southAmerica = "South America"
    case europe = "Europe"
    case africa = "Africa"
    case asia = "Asia"
    case oceania = "Oceania"
    
    var color: Color {
        switch self {
        case .northAmerica: return .green
        case .southAmerica: return .orange
        case .europe: return .blue
        case .africa: return .red
        case .asia: return .purple
        case .oceania: return .cyan
        }
    }
}

struct TutorialGridCell: Equatable {
    let row: Int
    let col: Int
}

// MARK: - Sample Data
func createSampleCitiesData() -> String {
    return """
    {
        "type": "geography_cities",
        "difficulty": "medium",
        "city": {
            "name": "Tokyo",
            "flag": "🇯🇵",
            "continent": "Asia"
        },
        "instructions": "Place Tokyo in the correct continent and grid location"
    }
    """
}