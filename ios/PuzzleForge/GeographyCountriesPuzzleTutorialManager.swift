//
//  GeographyCountriesPuzzleTutorialManager.swift
//  PuzzleForge
//
//  Created by Assistant on [Current Date]
//

import SwiftUI


func createSampleCountriesData() -> String {
    return """
    {
        "type": "geography_countries",
        "difficulty": "medium",
        "country": {
            "name": "Brazil",
            "flag": "🇧🇷",
            "capital": "Brasília",
            "continent": "South America"
        },
        "instructions": "Select all grid sections where Brazil is located"
    }
    """
}

func convertTutorialToRealContinent(_ tutorialContinent: TutorialContinent) -> Continent {
   switch tutorialContinent {
   case .northAmerica:
       return .northAmerica
   case .southAmerica:
       return .southAmerica
   case .europe:
       return .europe
   case .africa:
       return .africa
   case .asia:
       return .asia
   case .oceania:
       return .oceania
   }
}

func convertFromRealContinentToTutorial(_ continent: Continent) -> TutorialContinent {
   switch continent {
   case .northAmerica:
       return .northAmerica
   case .southAmerica:
       return .southAmerica
   case .europe:
       return .europe
   case .africa:
       return .africa
   case .asia:
       return .asia
   case .oceania:
       return .oceania
   }
}

// MARK: - Tutorial Manager
class GeographyCountriesPuzzleTutorialManager: ObservableObject {
    func getTutorialSteps() -> [TutorialStep] {
        return [
            TutorialStep(
                title: "Welcome to Geography Countries! 🗺️",
                description: "Learn how to place countries in their correct locations. Countries can span multiple grid cells, so precision matters!",
                targetComponent: "map",
                id: "" // Informational
            ),
            TutorialStep(
                title: "The Country Challenge 🏛️",
                description: "You'll see a country card with its flag and capital. Countries are larger than cities and can cover multiple grid sections.",
                targetComponent: "country_card",
                id: "" // Informational
            ),
            TutorialStep(
                title: "Select the Continent 🌍",
                description: "First, tap on the continent where this country is located. This works the same as cities - one continent per country.",
                targetComponent: "continent_map",
                id: "select_continent" // Interactive
            ),
            TutorialStep(
                title: "Perfect Selection! 🎯",
                description: "Great! Correct continent selection earns you 25 points and unlocks the multi-cell placement challenge.",
                targetComponent: "continent_map",
                id: "" // Informational
            ),
            TutorialStep(
                title: "Multi-Cell Grid Placement 📍",
                description: "Now the key difference: countries can span multiple grid cells. Tap ALL cells where you think this country extends.",
                targetComponent: "grid_map",
                id: "select_multiple_cells" // Interactive
            ),
            TutorialStep(
                title: "Selection Strategy 🧭",
                description: "Think about the country's actual size and shape. Large countries like Russia or Canada span many cells, while smaller ones like Vatican City might only need one.",
                targetComponent: "grid_map",
                id: "select_more_cells" // Interactive
            ),
            TutorialStep(
                title: "Deselection Feature ↩️",
                description: "Made a mistake? Tap any selected cell again to deselect it. You can refine your selection until you're satisfied.",
                targetComponent: "grid_map",
                id: "deselect_cell" // Interactive
            ),
            TutorialStep(
                title: "Advanced Scoring 🏆",
                description: "You get points based on accuracy: correct cells earn points, missing cells lose points, and extra wrong cells also reduce your score.",
                targetComponent: "feedback",
                id: "" // Informational
            ),
            TutorialStep(
                title: "Time Management ⌛",
                description: "Countries take more time than cities due to their complexity. Plan your selections carefully but don't overthink!",
                targetComponent: "header",
                id: "" // Informational
            ),
            TutorialStep(
                title: "Master Geographer Ready! 🎓",
                description: "Excellent! You now understand country placement. Use your knowledge of country sizes, borders, and positions to excel!",
                targetComponent: "map",
                id: "" // Informational
            )
        ]
    }
}

// MARK: - Main Tutorial View
struct GeographyCountriesPuzzleTutorialView: View {
    let onTutorialComplete: () -> Void
    let onTutorialSkipped: () -> Void
    let onBack: () -> Void
    
    @StateObject private var tutorialManager = GeographyCountriesPuzzleTutorialManager()
    @State private var currentStepIndex = 0
    @State private var tutorialState = TutorialState.active
    @State private var sampleCountriesData = "sample_data"

    private var steps: [TutorialStep] {
        tutorialManager.getTutorialSteps()
    }
    
    private var currentStep: TutorialStep? {
        guard currentStepIndex < steps.count else { return nil }
        return steps[currentStepIndex]
    }
    
    var body: some View {
        ZStack {
            // Tutorial version of the countries screen
            TutorialCountriesContent(
                currentStep: currentStep,
                sampleCountriesData: sampleCountriesData,
                onTutorialAction: handleTutorialAction,
                onBack: onBack
            )
            
            // Tutorial overlay
            if tutorialState == .active && currentStepIndex < steps.count {
                let step = steps[currentStepIndex]
                
                if step.id.isEmpty {
                    // Informational step
                    GeographyCountriesSmartTutorialOverlay(
                        currentStep: step,
                        totalSteps: steps.count,
                        currentStepNumber: currentStepIndex + 1,
                        onNext: advanceStep,
                        onSkip: skipTutorial
                    )
                } else {
                    // Interactive step
                    GeographyCountriesMinimalInteractiveOverlay(
                        currentStep: step,
                        totalSteps: steps.count,
                        currentStepNumber: currentStepIndex + 1,
                        onNext: advanceStep,
                        onSkip: skipTutorial
                    )
                }
            }
        }.onAppear {
            sampleCountriesData = createSampleCountriesData()
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

// MARK: - Tutorial Countries Content
struct TutorialCountriesContent: View {
    let currentStep: TutorialStep?
    let sampleCountriesData: String
    let onTutorialAction: (String) -> Void
    let onBack: () -> Void
    
    @State private var gameStage: TutorialCountriesGameStage = .continentSelection
    @State private var currentCountry: TutorialGeographyCountry?
    @State private var selectedContinent: TutorialContinent?
    @State private var selectedGridCells: [TutorialGridCell] = []
    @State private var showFeedback = false
    @State private var feedbackMessage = ""
    @State private var totalScore = 320
    @State private var questionsAnswered = 4
    @State private var timeRemaining = 380 // 6:20 remaining
    
    private let maxQuestions = 12
    
    var body: some View {
        GeometryReader { geometry in
            ZStack {
                // Background matching the actual countries view
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
                        if let country = currentCountry {
                            tutorialContinentSelectionView(country: country)
                        }
                    case .precisePlacement:
                        if let country = currentCountry, let continent = selectedContinent {
                            tutorialPrecisePlacementView(country: country, continent: continent)
                        }
                    }
                }
            }
        }
        .navigationBarHidden(true)
        .onAppear {
            setupTutorialCountries()
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
    
    private func tutorialContinentSelectionView(country: TutorialGeographyCountry) -> some View {
        VStack(spacing: 20) {
            // Progress
            Text("Question \(questionsAnswered + 1) of \(maxQuestions)")
                .font(.headline)
                .foregroundColor(.white)
            
            // Country Card
            tutorialCountryCardView(country: country)
                .modifier(TutorialHighlight(isHighlighted: currentStep?.targetComponent == "country_card"))
            
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
    
    private func tutorialPrecisePlacementView(country: TutorialGeographyCountry, continent: TutorialContinent) -> some View {
        VStack(spacing: 20) {
            // Progress
            Text("Question \(questionsAnswered + 1) of \(maxQuestions) - Precise Placement")
                .font(.headline)
                .foregroundColor(.white)
                .multilineTextAlignment(.center)
            
            // Country Card
            tutorialCountryCardView(country: country)
                .modifier(TutorialHighlight(isHighlighted: currentStep?.targetComponent == "country_card"))
            
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
            
            // Grid Map
            tutorialCountryGridMapView(continent: continent)
                .modifier(TutorialHighlight(isHighlighted: currentStep?.targetComponent == "grid_map"))
            
            // Feedback for grid placement
            if showFeedback {
                tutorialCountryGridFeedbackView()
                    .transition(.scale.combined(with: .opacity))
                    .modifier(TutorialHighlight(isHighlighted: currentStep?.targetComponent == "feedback"))
            }
            
            Spacer()
        }
        .padding()
        .animation(.easeInOut, value: showFeedback)
    }
    
    private func tutorialCountryCardView(country: TutorialGeographyCountry) -> some View {
        VStack(spacing: 12) {
            // Flag
            Text(country.flag)
                .font(.system(size: 50))
            
            // Country name
            Text(country.name)
                .font(.title2)
                .fontWeight(.bold)
                .foregroundColor(.black)
                .multilineTextAlignment(.center)
            
            // Capital
            Text("Capital: \(country.capital)")
                .font(.caption)
                .foregroundColor(.gray)
                .multilineTextAlignment(.center)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 16)
        .frame(minWidth: 200)
        .frame(height: 130)
        .background(Color.white)
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .shadow(radius: 8)
    }
    
    private func tutorialWorldMapView() -> some View {
        // ✅ UPDATED: Use Enhanced World Map (matches actual countries view)
        EnhancedWorldMapView(onContinentSelected: { continent in
            handleContinentSelection(convertFromRealContinentToTutorial(continent))
        })
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
    
    private func tutorialCountryGridMapView(continent: TutorialContinent) -> some View {
        VStack(spacing: 16) {
            Text("Grid: 4×5")
                .font(.caption)
                .foregroundColor(.white.opacity(0.8))
            
            ZStack {
                // ✅ ADD: Continent Background SVG (matches actual countries view)
                ContinentBackgroundSVG(continent: convertTutorialToRealContinent(continent))
                    .frame(width: 300, height: 220)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                
                // Grid overlay
                VStack(spacing: 1) {
                    ForEach(0..<4, id: \.self) { row in
                        HStack(spacing: 1) {
                            ForEach(0..<5, id: \.self) { col in
                                let cell = TutorialGridCell(row: row, col: col)
                                
                                Button(action: {
                                    handleGridCellToggle(cell)
                                }) {
                                    Rectangle()
                                        .fill(getTutorialCountryCellColor(for: cell).opacity(0.7))
                                        .overlay(
                                            Rectangle()
                                                .stroke(Color.white.opacity(0.8), lineWidth: 0.5)
                                        )
                                        .overlay(getTutorialCountryCellContent(for: cell))
                                }
                            }
                        }
                    }
                }
                .frame(width: 300, height: 220)
            }
            
            // Legend if showing feedback
            if showFeedback {
                HStack(spacing: 20) {
                    tutorialLegendItem(color: .green, label: "Correct")
                    tutorialLegendItem(color: .orange, label: "Selected")
                    tutorialLegendItem(color: .red, label: "Wrong")
                }
            }
        }
    }
    
    private func tutorialLegendItem(color: Color, label: String) -> some View {
        HStack(spacing: 4) {
            Rectangle()
                .fill(color.opacity(0.8))
                .frame(width: 12, height: 12)
                .cornerRadius(2)
            Text(label)
                .font(.caption2)
                .foregroundColor(.white)
        }
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
    
    private func tutorialCountryGridFeedbackView() -> some View {
        VStack(spacing: 8) {
            Text("Good Coverage!")
                .font(.title2)
                .fontWeight(.bold)
                .foregroundColor(.white)
            
            Text("+40 points")
                .font(.headline)
                .foregroundColor(.white)
            
            HStack(spacing: 20) {
                VStack(spacing: 4) {
                    Text("Country spans")
                        .font(.caption)
                        .foregroundColor(.white.opacity(0.8))
                    Text("6 cells")
                        .font(.subheadline)
                        .fontWeight(.semibold)
                        .foregroundColor(.white)
                }
                
                VStack(spacing: 4) {
                    Text("You selected")
                        .font(.caption)
                        .foregroundColor(.white.opacity(0.8))
                    Text("\(selectedGridCells.count) cells")
                        .font(.subheadline)
                        .fontWeight(.semibold)
                        .foregroundColor(.white)
                }
            }
        }
        .padding()
        .background(Color.green)
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }
    
    // MARK: - Tutorial Logic
    private func setupTutorialCountries() {
        // Set up sample country data
        currentCountry = TutorialGeographyCountry(
            name: "Brazil",
            flag: "🇧🇷",
            capital: "Brasília",
            continent: .southAmerica
        )
    }
    
    private func handleContinentSelection(_ continent: TutorialContinent) {
        guard let country = currentCountry else { return }
        
        if continent == country.continent {
            selectedContinent = continent
            feedbackMessage = "Correct continent! Now place it precisely."
            showFeedback = true
            onTutorialAction("select_continent")
            
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
                showFeedback = false
                gameStage = .precisePlacement
            }
        } else {
            feedbackMessage = "Wrong continent. \(country.name) is in \(country.continent.rawValue)."
            showFeedback = true
            onTutorialAction("select_continent")
        }
    }
    
    private func handleGridCellToggle(_ gridCell: TutorialGridCell) {
        if selectedGridCells.contains(gridCell) {
            // Deselect
            selectedGridCells.removeAll { $0 == gridCell }
            onTutorialAction("deselect_cell")
        } else {
            // Select
            selectedGridCells.append(gridCell)
            
            if selectedGridCells.count == 1 {
                onTutorialAction("select_multiple_cells")
            } else if selectedGridCells.count >= 2 {
                onTutorialAction("select_more_cells")
            }
        }
        
        // Auto-submit after enough selections for tutorial
        if selectedGridCells.count >= 4 {
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) {
                showFeedback = true
                totalScore += 40
            }
        }
    }
    
    private func getTutorialCountryCellColor(for cell: TutorialGridCell) -> Color {
        // Define correct cells for Brazil (spanning multiple cells)
        let correctCells = [
            TutorialGridCell(row: 1, col: 1), TutorialGridCell(row: 1, col: 2),
            TutorialGridCell(row: 2, col: 1), TutorialGridCell(row: 2, col: 2),
            TutorialGridCell(row: 3, col: 1), TutorialGridCell(row: 3, col: 2)
        ]
        
        if showFeedback {
            if correctCells.contains(cell) && selectedGridCells.contains(cell) {
                return .green
            } else if correctCells.contains(cell) {
                return .green.opacity(0.6)
            } else if selectedGridCells.contains(cell) {
                return .red
            }
        } else if selectedGridCells.contains(cell) {
            return .orange
        }
        return .clear
    }
    
    @ViewBuilder
    private func getTutorialCountryCellContent(for cell: TutorialGridCell) -> some View {
        let correctCells = [
            TutorialGridCell(row: 1, col: 1), TutorialGridCell(row: 1, col: 2),
            TutorialGridCell(row: 2, col: 1), TutorialGridCell(row: 2, col: 2),
            TutorialGridCell(row: 3, col: 1), TutorialGridCell(row: 3, col: 2)
        ]
        
        if showFeedback {
            if correctCells.contains(cell) && selectedGridCells.contains(cell) {
                Image(systemName: "checkmark.circle.fill")
                    .font(.caption)
                    .foregroundColor(.white)
            } else if selectedGridCells.contains(cell) && !correctCells.contains(cell) {
                Image(systemName: "xmark.circle.fill")
                    .font(.caption)
                    .foregroundColor(.white)
            } else {
                EmptyView()
            }
        } else if selectedGridCells.contains(cell) {
            Circle()
                .fill(Color.white.opacity(0.9))
                .frame(width: 6, height: 6)
        } else {
            EmptyView()
        }
    }
}

// MARK: - Geography Countries Specific Tutorial Overlay Views
struct GeographyCountriesSmartTutorialOverlay: View {
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

struct GeographyCountriesMinimalInteractiveOverlay: View {
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
enum TutorialCountriesGameStage {
    case continentSelection, precisePlacement
}

struct TutorialGeographyCountry {
    let name: String
    let flag: String
    let capital: String
    let continent: TutorialContinent
}

    // MARK: - Helper Functions for Real Component Conversion
    
    private func convertToRealContinent(_ tutorialContinent: TutorialContinent) -> Continent {
        switch tutorialContinent {
        case .northAmerica: return .northAmerica
        case .southAmerica: return .southAmerica
        case .europe: return .europe
        case .africa: return .africa
        case .asia: return .asia
        case .oceania: return .oceania
        }
    }
    
    private func convertFromRealContinent(_ continent: Continent) -> TutorialContinent {
        switch continent {
        case .northAmerica: return .northAmerica
        case .southAmerica: return .southAmerica
        case .europe: return .europe
        case .africa: return .africa
        case .asia: return .asia
        case .oceania: return .oceania
        }
    }
