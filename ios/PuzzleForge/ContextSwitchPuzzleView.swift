//
//  ContextSwitchPuzzleView.swift
//  PuzzleForge
//
//  Enhanced with adaptive cognitive load management - Simplified interference tasks
//

import SwiftUI
import Foundation
import AVFoundation

// MARK: - Context Switch Difficulty Manager
class ContextSwitchDifficultyManager: ObservableObject {
    
    struct DifficultyLevel {
        let name: String
        let workingMemoryLoad: Int // 1-5 scale for memory items
        let interferenceComplexity: String // simple, moderate, complex, extreme
        let dualTaskDemand: Bool
        let attentionalControl: Bool
        let executiveDemand: Int // 1-5 scale for executive function load
        let temporalComplexity: Bool
        let livesAllowed: Int
        let basePoints: Int
        let timeLimit: Int
        
        static let beginner = DifficultyLevel(name: "Beginner", workingMemoryLoad: 1, interferenceComplexity: "simple", dualTaskDemand: false, attentionalControl: false, executiveDemand: 1, temporalComplexity: false, livesAllowed: 4, basePoints: 20, timeLimit: 240)
        static let easy = DifficultyLevel(name: "Easy", workingMemoryLoad: 2, interferenceComplexity: "moderate", dualTaskDemand: true, attentionalControl: false, executiveDemand: 2, temporalComplexity: false, livesAllowed: 3, basePoints: 30, timeLimit: 210)
        static let medium = DifficultyLevel(name: "Medium", workingMemoryLoad: 3, interferenceComplexity: "moderate", dualTaskDemand: true, attentionalControl: true, executiveDemand: 3, temporalComplexity: true, livesAllowed: 3, basePoints: 40, timeLimit: 180)
        static let hard = DifficultyLevel(name: "Hard", workingMemoryLoad: 4, interferenceComplexity: "complex", dualTaskDemand: true, attentionalControl: true, executiveDemand: 4, temporalComplexity: true, livesAllowed: 2, basePoints: 50, timeLimit: 150)
        static let expert = DifficultyLevel(name: "Expert", workingMemoryLoad: 5, interferenceComplexity: "extreme", dualTaskDemand: true, attentionalControl: true, executiveDemand: 5, temporalComplexity: true, livesAllowed: 2, basePoints: 60, timeLimit: 120)
        static let master = DifficultyLevel(name: "Master", workingMemoryLoad: 5, interferenceComplexity: "extreme", dualTaskDemand: true, attentionalControl: true, executiveDemand: 5, temporalComplexity: true, livesAllowed: 1, basePoints: 80, timeLimit: 90)
        
        static let allLevels = [beginner, easy, medium, hard, expert, master]
        
        var description: String {
            switch name {
            case "Beginner": return "Basic memory with simple interference"
            case "Easy": return "Moderate memory load with dual-task demands"
            case "Medium": return "Attention switching with time pressure"
            case "Hard": return "High cognitive load with complex interference"
            case "Expert": return "Maximum executive demands with extreme interference"
            case "Master": return "Ultimate cognitive challenge"
            default: return "Adaptive context switching challenge"
            }
        }
    }
    
    struct PlayerPerformance: Codable {
        let accuracy: Float
        let averageResponseTime: Float
        let streakLength: Int
        let livesRemaining: Int
        let gameScore: Int
        let difficulty: String
        let cognitiveLoad: Float
        let executiveEfficiency: Float
        let contextSwitchCount: Int
    }
    
    struct AdaptationResult {
        let level: DifficultyLevel
        let adjustmentReason: String
        let confidenceScore: Float
        let shouldNotify: Bool
    }
    
    @Published var currentDifficulty: DifficultyLevel = .easy
    private var performanceHistory: [PlayerPerformance] = []
    private let maxHistorySize = 10
    
    init() {
        loadSavedDifficulty()
    }
    
    func recordPerformance(_ performance: PlayerPerformance) -> AdaptationResult {
        performanceHistory.append(performance)
        if performanceHistory.count > maxHistorySize {
            performanceHistory.removeFirst()
        }
        
        savePerformanceHistory()
        
        let result = analyzeAndAdapt()
        if result.level.name != currentDifficulty.name {
            currentDifficulty = result.level
            saveDifficulty()
        }
        
        return result
    }
    
    func getDifficultyProgress() -> (current: Int, total: Int, name: String) {
        let currentIndex = DifficultyLevel.allLevels.firstIndex { $0.name == currentDifficulty.name } ?? 1
        return (current: currentIndex + 1, total: DifficultyLevel.allLevels.count, name: currentDifficulty.name)
    }
    
    private func analyzeAndAdapt() -> AdaptationResult {
        guard performanceHistory.count >= 3 else {
            return AdaptationResult(
                level: currentDifficulty,
                adjustmentReason: "Gathering cognitive performance data...",
                confidenceScore: 0.0,
                shouldNotify: false
            )
        }
        
        let recentWindow = Array(performanceHistory.suffix(5))
        let avgAccuracy = recentWindow.map { $0.accuracy }.reduce(0, +) / Float(recentWindow.count)
        let avgExecutiveEfficiency = recentWindow.map { $0.executiveEfficiency }.reduce(0, +) / Float(recentWindow.count)
        let consistentSuccess = recentWindow.filter { $0.accuracy >= 0.8 && $0.executiveEfficiency >= 0.7 }.count >= 3
        let consistentFailure = recentWindow.filter { $0.accuracy <= 0.4 || $0.executiveEfficiency <= 0.3 }.count >= 3
        
        let currentIndex = DifficultyLevel.allLevels.firstIndex { $0.name == currentDifficulty.name } ?? 1
        
        var newLevel = currentDifficulty
        var reason = "Cognitive performance stable at current level"
        var confidence: Float = 0.3
        
        // Check for advancement - require both accuracy and executive efficiency
        if avgAccuracy >= 0.85 && avgExecutiveEfficiency >= 0.75 && consistentSuccess && currentIndex < DifficultyLevel.allLevels.count - 1 {
            newLevel = DifficultyLevel.allLevels[currentIndex + 1]
            reason = "Excellent cognitive control - advancing to \(newLevel.name)"
            confidence = 0.8
        }
        // Check for reduction
        else if (avgAccuracy <= 0.3 || avgExecutiveEfficiency <= 0.3) && consistentFailure && currentIndex > 0 {
            newLevel = DifficultyLevel.allLevels[currentIndex - 1]
            reason = "Building cognitive skills at \(newLevel.name) level"
            confidence = 0.7
        }
        
        return AdaptationResult(
            level: newLevel,
            adjustmentReason: reason,
            confidenceScore: confidence,
            shouldNotify: confidence > 0.5
        )
    }
    
    private func loadSavedDifficulty() {
        if let savedName = UserDefaults.standard.string(forKey: "context_switch_difficulty"),
           let level = DifficultyLevel.allLevels.first(where: { $0.name == savedName }) {
            currentDifficulty = level
        }
        loadPerformanceHistory()
    }
    
    private func saveDifficulty() {
        UserDefaults.standard.set(currentDifficulty.name, forKey: "context_switch_difficulty")
    }
    
    private func savePerformanceHistory() {
        let encoder = JSONEncoder()
        if let encoded = try? encoder.encode(performanceHistory) {
            UserDefaults.standard.set(encoded, forKey: "context_switch_performance_history")
        }
    }
    
    private func loadPerformanceHistory() {
        if let data = UserDefaults.standard.data(forKey: "context_switch_performance_history") {
            let decoder = JSONDecoder()
            if let decoded = try? decoder.decode([PlayerPerformance].self, from: data) {
                performanceHistory = decoded
            }
        }
    }
}

// MARK: - Enhanced Data Models
struct AdaptiveContextSwitchPuzzleData {
    let memoryItems: [String]
    let interferenceTask: SimpleAlphabetizeTask
    let recognitionItems: [String]
    let correctAnswers: [String]
    let difficulty: ContextSwitchDifficultyManager.DifficultyLevel
    let category: String
    let adaptiveConfig: AdaptiveContextConfig
}

struct SimpleAlphabetizeTask {
    let items: [String]
    let instruction: String
    let correctOrder: [String]
}

struct AdaptiveContextConfig {
    let name: String
    let description: String
    let workingMemoryLoad: Int
    let interferenceComplexity: String
    let dualTaskDemand: Bool
    let attentionalControl: Bool
    let executiveDemand: Int
    let temporalComplexity: Bool
    let adaptiveFeatures: [String]
}

// MARK: - Enhanced Content Database
struct AdaptiveContentDatabase {
    // High verbal load categories
    static let abstractConcepts = ["Justice", "Freedom", "Wisdom", "Courage", "Truth", "Beauty", "Honor", "Peace", "Hope", "Faith", "Love", "Mercy", "Grace", "Unity", "Harmony"]
    static let academicSubjects = ["Philosophy", "Psychology", "Neuroscience", "Economics", "Sociology", "Anthropology", "Linguistics", "Statistics", "Biology", "Chemistry", "Physics", "Mathematics", "History", "Literature", "Art"]
    
    // High spatial load categories
    static let geometricShapes = ["Hexagon", "Octagon", "Pentagon", "Triangle", "Rectangle", "Rhombus", "Trapezoid", "Parallelogram", "Ellipse", "Polygon", "Prism", "Pyramid", "Cube", "Sphere", "Cylinder"]
    static let architecturalElements = ["Arch", "Column", "Dome", "Spire", "Buttress", "Cornice", "Frieze", "Pedestal", "Balustrade", "Portico", "Atrium", "Vestibule", "Clerestory", "Transept", "Apse"]
    
    // High semantic load categories
    static let scientificTerms = ["Hypothesis", "Algorithm", "Catalyst", "Enzyme", "Molecule", "Electron", "Photon", "Quantum", "Genome", "Protein", "Neuron", "Synapse", "Mitochondria", "Chromosome", "Antibody"]
    static let medicalTerminology = ["Diagnosis", "Prognosis", "Symptom", "Syndrome", "Pathology", "Anatomy", "Physiology", "Pharmacology", "Epidemiology", "Immunology", "Cardiology", "Neurology", "Oncology", "Pediatrics", "Geriatrics"]
    
    // Simple words for alphabetization tasks
    static let simpleWords = ["Apple", "Banana", "Cherry", "Grape", "Orange", "Peach", "Plum", "Berry", "Lemon", "Mango", "Kiwi", "Melon", "Date", "Fig", "Lime"]
    static let animals = ["Bear", "Cat", "Dog", "Eagle", "Fox", "Goat", "Horse", "Iguana", "Jaguar", "Koala", "Lion", "Mouse", "Newt", "Owl", "Pig"]
    static let colors = ["Blue", "Green", "Red", "Yellow", "Purple", "Orange", "Pink", "Brown", "Black", "White", "Gray", "Violet", "Indigo", "Crimson", "Teal"]
    
    static let allCategories: [String: [String]] = [
        "abstract_concepts": abstractConcepts,
        "academic_subjects": academicSubjects,
        "geometric_shapes": geometricShapes,
        "architectural_elements": architecturalElements,
        "scientific_terms": scientificTerms,
        "medical_terminology": medicalTerminology
    ]
    
    static let alphabetizeCategories: [String: [String]] = [
        "simple_words": simpleWords,
        "animals": animals,
        "colors": colors
    ]
    
    static func selectAdaptiveCategory(for workingMemoryLoad: Int) -> (name: String, items: [String]) {
        let categoryName: String
        switch workingMemoryLoad {
        case 1, 2:
            categoryName = ["geometric_shapes", "architectural_elements"].randomElement()!
        case 3:
            categoryName = ["academic_subjects", "scientific_terms"].randomElement()!
        case 4, 5:
            categoryName = ["abstract_concepts", "medical_terminology"].randomElement()!
        default:
            categoryName = allCategories.keys.randomElement()!
        }
        
        return (categoryName, allCategories[categoryName] ?? abstractConcepts)
    }
    
    static func selectAlphabetizeCategory() -> (name: String, items: [String]) {
        let categoryName = alphabetizeCategories.keys.randomElement()!
        return (categoryName, alphabetizeCategories[categoryName] ?? simpleWords)
    }
}

// MARK: - Adaptive Context Switch Generator
class AdaptiveContextSwitchGenerator: ObservableObject {
    
    func generateAdaptivePuzzle(difficulty: ContextSwitchDifficultyManager.DifficultyLevel) -> AdaptiveContextSwitchPuzzleData {
        // Select content category based on working memory load
        let (categoryName, allItems) = AdaptiveContentDatabase.selectAdaptiveCategory(for: difficulty.workingMemoryLoad)
        
        // Determine memory item count
        let memoryItemCount: Int
        switch difficulty.workingMemoryLoad {
        case 1: memoryItemCount = 3
        case 2: memoryItemCount = 4
        case 3: memoryItemCount = 5
        case 4: memoryItemCount = 6
        case 5: memoryItemCount = 7
        default: memoryItemCount = 4
        }
        
        let distractorCount: Int
        switch difficulty.workingMemoryLoad {
        case 1: distractorCount = 2
        case 2: distractorCount = 3
        case 3: distractorCount = 3
        case 4: distractorCount = 4
        case 5: distractorCount = 5
        default: distractorCount = 3
        }
        
        // Generate memory items
        let memoryItems = Array(allItems.shuffled().prefix(memoryItemCount))
        
        // Generate smart distractors
        let availableDistractors = allItems.filter { !memoryItems.contains($0) }
        let distractors = generateAdaptiveDistractors(
            availableDistractors: availableDistractors,
            memoryItems: memoryItems,
            difficulty: difficulty,
            count: distractorCount
        )
        
        // Create recognition list with adaptive ordering
        let recognitionItems = createAdaptiveRecognitionList(
            memoryItems: memoryItems,
            distractors: distractors,
            difficulty: difficulty
        )
        
        // Generate simple alphabetization task
        let interferenceTask = createSimpleAlphabetizeTask(difficulty: difficulty)
        
        let adaptiveConfig = AdaptiveContextConfig(
            name: difficulty.name,
            description: difficulty.description,
            workingMemoryLoad: difficulty.workingMemoryLoad,
            interferenceComplexity: difficulty.interferenceComplexity,
            dualTaskDemand: difficulty.dualTaskDemand,
            attentionalControl: difficulty.attentionalControl,
            executiveDemand: difficulty.executiveDemand,
            temporalComplexity: difficulty.temporalComplexity,
            adaptiveFeatures: [
                "Working memory load adapts to performance",
                "Executive control demands scale with ability",
                "Simple drag-and-drop alphabetization",
                "Attention switching challenges optimize difficulty",
                "Temporal pressure adapts to cognitive speed"
            ]
        )
        
        return AdaptiveContextSwitchPuzzleData(
            memoryItems: memoryItems,
            interferenceTask: interferenceTask,
            recognitionItems: recognitionItems,
            correctAnswers: memoryItems,
            difficulty: difficulty,
            category: categoryName,
            adaptiveConfig: adaptiveConfig
        )
    }
    
    private func generateAdaptiveDistractors(
        availableDistractors: [String],
        memoryItems: [String],
        difficulty: ContextSwitchDifficultyManager.DifficultyLevel,
        count: Int
    ) -> [String] {
        switch difficulty.workingMemoryLoad {
        case 1, 2:
            // Easy distractors - clearly different
            return availableDistractors.filter { distractor in
                memoryItems.allSatisfy { memoryItem in
                    !distractor.hasPrefix(String(memoryItem.prefix(1))) &&
                    !memoryItem.hasPrefix(String(distractor.prefix(1)))
                }
            }.shuffled().prefix(count).map { $0 }
            
        case 3, 4:
            // Moderate distractors - some similarity
            return Array(availableDistractors.shuffled().prefix(count))
            
        case 5:
            // Hard distractors - semantically similar
            let similarDistractors = availableDistractors.filter { distractor in
                memoryItems.contains { memoryItem in
                    distractor.count == memoryItem.count ||
                    distractor.lowercased().contains(String(memoryItem.lowercased().prefix(3)))
                }
            }
            return Array((similarDistractors.isEmpty ? availableDistractors : similarDistractors).shuffled().prefix(count))
            
        default:
            return Array(availableDistractors.shuffled().prefix(count))
        }
    }
    
    private func createAdaptiveRecognitionList(
        memoryItems: [String],
        distractors: [String],
        difficulty: ContextSwitchDifficultyManager.DifficultyLevel
    ) -> [String] {
        let combined = memoryItems + distractors
        
        if difficulty.attentionalControl {
            // Interleave memory and distractors to increase attention switching
            let shuffled = combined.shuffled()
            var interleaved: [String] = []
            let memSet = Set(memoryItems)
            
            var lastWasMemory = false
            for item in shuffled {
                let isMemory = memSet.contains(item)
                if difficulty.executiveDemand >= 4 {
                    // Force alternation for maximum executive load
                    if (lastWasMemory && !isMemory) || (!lastWasMemory && isMemory) {
                        interleaved.append(item)
                        lastWasMemory = isMemory
                    }
                } else {
                    interleaved.append(item)
                    lastWasMemory = isMemory
                }
            }
            return interleaved.isEmpty ? combined.shuffled() : interleaved
        } else {
            return combined.shuffled()
        }
    }
    
    private func createSimpleAlphabetizeTask(difficulty: ContextSwitchDifficultyManager.DifficultyLevel) -> SimpleAlphabetizeTask {
        let (_, allWords) = AdaptiveContentDatabase.selectAlphabetizeCategory()
        
        // Number of words to alphabetize based on difficulty
        let wordCount: Int
        switch difficulty.executiveDemand {
        case 1: wordCount = 3
        case 2: wordCount = 4
        case 3: wordCount = 4
        case 4: wordCount = 5
        case 5: wordCount = 6
        default: wordCount = 4
        }
        
        let selectedWords = Array(allWords.shuffled().prefix(wordCount))
        let correctOrder = selectedWords.sorted()
        
        return SimpleAlphabetizeTask(
            items: selectedWords.shuffled(), // Present in random order
            instruction: "Drag to alphabetize these words:",
            correctOrder: correctOrder
        )
    }
}

extension View {
    func hideKeyboard() {
        UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)
    }
}

// MARK: - Main View (Enhanced with adaptive difficulty)
struct ContextSwitchPuzzleView: View {
    let puzzle: Puzzle
    let questionIndex: Int
    let totalQuestions: Int
    let onAnswerSubmitted: (Set<String>, Bool) -> Void
    let onNextPuzzle: () -> Void
    let onExit: () -> Void
    
    // Difficulty management
    @StateObject private var difficultyManager = ContextSwitchDifficultyManager()
    @StateObject private var generator = AdaptiveContextSwitchGenerator()
    @State private var currentPuzzleData: AdaptiveContextSwitchPuzzleData?
    @State private var adaptationResult: ContextSwitchDifficultyManager.AdaptationResult?
    @State private var showAdaptationNotification = false
    
    // Game state
    @StateObject private var feedbackManager = UnifiedFeedbackManager()
    @StateObject private var progressionManager = ProgressionManager()
    @State private var gamePhase: GamePhase = .memory
    @State private var selectedAnswers: Set<String> = []
    @State private var userAlphabetOrder: [String] = []
    @State private var showFeedback = false
    @State private var isCorrect = false
    @State private var timeRemaining: Int
    @State private var finalScore = 0
    @State private var showItems = true
    @State private var alphabetTaskCompleted = false
    @State private var hasInteracted = false
    @State private var currentLives: Int
    @State private var currentStreak = 0
    @State private var gamesPlayedThisSession = 0
    
    // Performance tracking
    @State private var gameStartTime = Date()
    @State private var sessionStartTime = Date()
    @State private var contextSwitchCount = 0
    @State private var executiveTaskTime: TimeInterval = 0
    
    // Timer
    @State private var gameTimer: Timer?
    
    enum GamePhase {
        case memory, interference, recognition
    }
    
    init(puzzle: Puzzle, questionIndex: Int, totalQuestions: Int, onAnswerSubmitted: @escaping (Set<String>, Bool) -> Void, onNextPuzzle: @escaping () -> Void, onExit: @escaping () -> Void) {
        self.puzzle = puzzle
        self.questionIndex = questionIndex
        self.totalQuestions = totalQuestions
        self.onAnswerSubmitted = onAnswerSubmitted
        self.onNextPuzzle = onNextPuzzle
        self.onExit = onExit
        
        // Initialize with current difficulty
        let initialDifficulty = ContextSwitchDifficultyManager.DifficultyLevel.easy
        self._timeRemaining = State(initialValue: initialDifficulty.timeLimit)
        self._currentLives = State(initialValue: initialDifficulty.livesAllowed)
    }
    
    var body: some View {
        GeometryReader { geometry in
            ZStack {
                // Enhanced gradient background
                LinearGradient(
                    colors: [Color(red: 0.1, green: 0.1, blue: 0.18), Color(red: 0.09, green: 0.13, blue: 0.24)],
                    startPoint: .top,
                    endPoint: .bottom
                )
                .ignoresSafeArea()
                
                VStack(spacing: 0) {
                    Spacer(minLength: 32)
                    
                    // Enhanced adaptive header
                    adaptiveHeaderView
                    
                    // Adaptation notification
                    if showAdaptationNotification, let result = adaptationResult {
                        adaptationNotificationView(result: result)
                            .transition(.move(edge: .top).combined(with: .opacity))
                    }
                    
                    // Main Content
                    Group {
                        switch gamePhase {
                        case .memory:
                            adaptiveMemoryPhaseView
                        case .interference:
                            adaptiveInterferencePhaseView
                        case .recognition:
                            adaptiveRecognitionPhaseView
                        }
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    
                    Spacer()
                }
                .padding(.horizontal, 16)
            }
            .ignoresSafeArea(.keyboard, edges: .bottom)
        }
        .navigationBarHidden(true)
        .onAppear {
            setupAdaptivePuzzle()
        }
        .onDisappear {
            gameTimer?.invalidate()
        }
        .alert(isCorrect ? "Excellent!" : "Not quite", isPresented: $showFeedback) {
            Button("Continue") {
                onNextPuzzle()
            }
        } message: {
            if let data = currentPuzzleData {
                Text("Score: \(finalScore) points\n\nDifficulty: \(data.adaptiveConfig.name)\n\nCognitive Load: \(data.adaptiveConfig.workingMemoryLoad)/5\n\nCorrect items: \(data.correctAnswers.sorted().joined(separator: ", "))")
            }
        }
    }
    
    // MARK: - View Components
    
    private var adaptiveHeaderView: some View {
        VStack(spacing: 12) {
            HStack {
                Button(action: onExit) {
                    HStack(spacing: 4) {
                        Image(systemName: "chevron.left")
                        Text("Back")
                    }
                    .foregroundColor(.white)
                }
                
                Spacer()
                
                VStack(spacing: 4) {
                    Text("Level \(progressionManager.currentLevel.level)")
                        .font(.caption)
                        .foregroundColor(.white.opacity(0.8))
                    
                    if let data = currentPuzzleData {
                        Text(data.adaptiveConfig.name)
                            .font(.headline)
                            .fontWeight(.bold)
                            .foregroundColor(.white)
                    }
                    
                    Text(formatTime(timeRemaining))
                        .font(.title2)
                        .fontWeight(.bold)
                        .foregroundColor(timeRemaining < 30 ? .red : .white)
                }
                
                Spacer()
                
                VStack(spacing: 4) {
                    HStack(spacing: 4) {
                        ForEach(0..<(currentPuzzleData?.difficulty.livesAllowed ?? 3), id: \.self) { index in
                            Image(systemName: index < currentLives ? "heart.fill" : "heart")
                                .font(.system(size: 16))
                                .foregroundColor(index < currentLives ? .red : .gray)
                        }
                    }
                    
                    if currentStreak > 0 {
                        Text("\(currentStreak)")
                            .font(.caption)
                            .foregroundColor(.orange)
                    }
                }
            }
            
            // Cognitive load indicators
            if let data = currentPuzzleData {
                HStack(spacing: 16) {
                    VStack(spacing: 2) {
                        Text("Working Memory")
                            .font(.caption2)
                            .foregroundColor(.white.opacity(0.7))
                        Text("\(data.adaptiveConfig.workingMemoryLoad)/5")
                            .font(.caption)
                            .fontWeight(.bold)
                            .foregroundColor(.cyan)
                    }
                    
                    VStack(spacing: 2) {
                        Text("Executive Load")
                            .font(.caption2)
                            .foregroundColor(.white.opacity(0.7))
                        Text("\(data.adaptiveConfig.executiveDemand)/5")
                            .font(.caption)
                            .fontWeight(.bold)
                            .foregroundColor(.purple)
                    }
                    
                    VStack(spacing: 2) {
                        Text("Score")
                            .font(.caption2)
                            .foregroundColor(.white.opacity(0.7))
                        Text("\(finalScore)")
                            .font(.caption)
                            .fontWeight(.bold)
                            .foregroundColor(.green)
                    }
                }
                
                // Adaptive features indicators
                HStack(spacing: 8) {
                    Group {
                        if data.adaptiveConfig.dualTaskDemand {
                            Text("Dual Task")
                                .font(.caption2)
                                .padding(.horizontal, 6)
                                .padding(.vertical, 2)
                                .background(Color.blue.opacity(0.3))
                                .cornerRadius(4)
                                .foregroundColor(.white)
                        }
                        if data.adaptiveConfig.attentionalControl {
                            Text("Attention")
                                .font(.caption2)
                                .padding(.horizontal, 6)
                                .padding(.vertical, 2)
                                .background(Color.purple.opacity(0.3))
                                .cornerRadius(4)
                                .foregroundColor(.white)
                        }
                        if data.adaptiveConfig.temporalComplexity {
                            Text("Time Pressure")
                                .font(.caption2)
                                .padding(.horizontal, 6)
                                .padding(.vertical, 2)
                                .background(Color.orange.opacity(0.3))
                                .cornerRadius(4)
                                .foregroundColor(.white)
                        }
                    }
                }
            }
        }
        .padding()
        .background(Color.white.opacity(0.1))
        .cornerRadius(16)
    }
    
    private func adaptationNotificationView(result: ContextSwitchDifficultyManager.AdaptationResult) -> some View {
        HStack {
            VStack(alignment: .leading, spacing: 4) {
                HStack {
                    Image(systemName: "brain.head.profile")
                        .foregroundColor(.white)
                        .font(.caption)
                    
                    Text("Context Switch Adapted!")
                        .font(.caption)
                        .fontWeight(.semibold)
                        .foregroundColor(.white)
                }
                
                Text(result.adjustmentReason)
                    .font(.caption2)
                    .foregroundColor(.white.opacity(0.9))
                    .lineLimit(2)
            }
            
            Spacer()
            
            Text("→ \(result.level.name)")
                .font(.caption)
                .fontWeight(.bold)
                .foregroundColor(.white)
            
            Button(action: { showAdaptationNotification = false }) {
                Image(systemName: "xmark")
                    .foregroundColor(.white)
                    .font(.caption)
            }
        }
        .padding(12)
        .background(
            LinearGradient(
                colors: [Color(red: 0.31, green: 0.76, blue: 0.97), Color(red: 0.55, green: 0.29, blue: 0.62)],
                startPoint: .leading,
                endPoint: .trailing
            )
        )
        .cornerRadius(12)
        .padding(.bottom, 16)
    }
    
    // MARK: - Memory Phase
    private var adaptiveMemoryPhaseView: some View {
        VStack(spacing: 24) {
            Text("Study these items")
                .font(.title)
                .fontWeight(.bold)
                .foregroundColor(.white)
            
            if let data = currentPuzzleData {
                Text(data.adaptiveConfig.description)
                    .font(.subheadline)
                    .foregroundColor(.white.opacity(0.8))
                    .multilineTextAlignment(.center)
                
                Text("Memory load: \(data.adaptiveConfig.workingMemoryLoad)/5 items")
                    .font(.caption)
                    .foregroundColor(.cyan)
                
                if showItems {
                    ScrollView {
                        LazyVGrid(columns: [
                            GridItem(.flexible()),
                            GridItem(.flexible())
                        ], spacing: 12) {
                            ForEach(data.memoryItems, id: \.self) { item in
                                Text("• \(item)")
                                    .font(.headline)
                                    .foregroundColor(.white)
                                    .padding()
                                    .frame(maxWidth: .infinity)
                                    .background(
                                        RoundedRectangle(cornerRadius: 12)
                                            .foregroundColor(Color.white.opacity(0.1))
                                            .overlay(
                                                RoundedRectangle(cornerRadius: 12)
                                                    .stroke(Color.blue.opacity(0.5), lineWidth: 1)
                                            )
                                    )
                            }
                        }
                        .padding()
                    }
                    
                    Button(action: {
                        showItems = false
                        gamePhase = .interference
                        contextSwitchCount += 1
                        
                        // Initialize alphabet order with shuffled items
                        userAlphabetOrder = data.interferenceTask.items
                    }) {
                        Text("I'm Ready!")
                            .font(.headline)
                            .fontWeight(.semibold)
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity)
                            .padding()
                            .background(
                                LinearGradient(
                                    colors: [.green, .blue],
                                    startPoint: .leading,
                                    endPoint: .trailing
                                )
                            )
                            .cornerRadius(12)
                    }
                    .padding(.horizontal)
                }
            }
        }
        .padding()
    }
    
    // MARK: - Interference Phase (Simplified Drag & Drop)
    private var adaptiveInterferencePhaseView: some View {
        ZStack {
            VStack(spacing: 24) {
                Text("Alphabetization Task")
                    .font(.title)
                    .fontWeight(.bold)
                    .foregroundColor(.white)
                
                if let data = currentPuzzleData {
                    VStack(spacing: 8) {
                        Text(data.interferenceTask.instruction)
                            .font(.subheadline)
                            .foregroundColor(.white.opacity(0.8))
                            .multilineTextAlignment(.center)
                        
                        Text("Executive demand: \(data.adaptiveConfig.executiveDemand)/5")
                            .font(.caption)
                            .foregroundColor(.purple)
                    }
                    
                    // Drag and drop alphabetization
                    VStack(spacing: 16) {
                        Text("Drag to reorder alphabetically:")
                            .font(.headline)
                            .foregroundColor(.white)
                        
                        VStack(spacing: 8) {
                            ForEach(Array(userAlphabetOrder.enumerated()), id: \.offset) { index, word in
                                HStack {
                                    Text("\(index + 1).")
                                        .foregroundColor(.white.opacity(0.7))
                                        .frame(width: 30, alignment: .trailing)
                                    
                                    Text(word)
                                        .font(.headline)
                                        .foregroundColor(.white)
                                        .padding()
                                        .frame(maxWidth: .infinity)
                                        .background(
                                            RoundedRectangle(cornerRadius: 8)
                                                .foregroundColor(Color.blue.opacity(0.2))
                                                .overlay(
                                                    RoundedRectangle(cornerRadius: 8)
                                                        .stroke(Color.blue.opacity(0.5), lineWidth: 1)
                                                )
                                        )
                                    
                                    VStack(spacing: 4) {
                                        Button(action: {
                                            if index > 0 {
                                                userAlphabetOrder.swapAt(index, index - 1)
                                                hasInteracted = true
                                            }
                                        }) {
                                            Image(systemName: "chevron.up")
                                                .foregroundColor(index > 0 ? .white : .gray)
                                        }
                                        .disabled(index == 0)
                                        
                                        Button(action: {
                                            if index < userAlphabetOrder.count - 1 {
                                                userAlphabetOrder.swapAt(index, index + 1)
                                                hasInteracted = true
                                            }
                                        }) {
                                            Image(systemName: "chevron.down")
                                                .foregroundColor(index < userAlphabetOrder.count - 1 ? .white : .gray)
                                        }
                                        .disabled(index == userAlphabetOrder.count - 1)
                                    }
                                    .frame(width: 40)
                                }
                                .id("alphabetItem\(index)")
                            }
                        }
                        .padding()
                        .background(Color.white.opacity(0.1))
                        .cornerRadius(12)
                        
                        // Completion status
                        let isCorrectOrder = userAlphabetOrder == data.interferenceTask.correctOrder
                        
                        if isCorrectOrder {
                            // Set completion state when correct order is achieved
                            let _ = DispatchQueue.main.async {
                                alphabetTaskCompleted = true
                            }
                        } else if hasInteracted {
                            let _ = DispatchQueue.main.async {
                                alphabetTaskCompleted = false
                            }
                        }
                        
                        // Add padding at bottom to account for floating button
                        Spacer()
                            .frame(height: 80)
                    }
                }
                
                Spacer()
            }
            .padding()
            
            // Floating Continue Button - only show when task is completed
            if alphabetTaskCompleted {
                VStack {
                    Spacer()
                    
                    Button(action: {
                        gamePhase = .recognition
                        contextSwitchCount += 1
                        executiveTaskTime = Date().timeIntervalSince(gameStartTime)
                    }) {
                        HStack {
                            Image(systemName: "checkmark.circle.fill")
                                .foregroundColor(.white)
                            Text("Continue →")
                                .font(.headline)
                                .fontWeight(.semibold)
                                .foregroundColor(.white)
                        }
                        .frame(maxWidth: .infinity)
                        .padding()
                        .background(
                            LinearGradient(
                                colors: [.green, .blue],
                                startPoint: .leading,
                                endPoint: .trailing
                            )
                        )
                        .cornerRadius(12)
                        .shadow(color: .black.opacity(0.3), radius: 8, x: 0, y: 4)
                    }
                    .padding(.horizontal)
                    .padding(.bottom, 20)
                }
            }
        }
    }
    
    // MARK: - Recognition Phase
    private var adaptiveRecognitionPhaseView: some View {
        VStack(spacing: 24) {
            Text("Recognition Test")
                .font(.title)
                .fontWeight(.bold)
                .foregroundColor(.white)
            
            Text("Select all items from the original list")
                .font(.subheadline)
                .foregroundColor(.white.opacity(0.8))
            
            if let data = currentPuzzleData {
                if data.adaptiveConfig.attentionalControl {
                    Text("Items are arranged to test attention control")
                        .font(.caption)
                        .foregroundColor(.orange)
                        .multilineTextAlignment(.center)
                }
                
                let columns = data.adaptiveConfig.workingMemoryLoad >= 4 ? 2 : 2
                LazyVGrid(columns: Array(repeating: GridItem(.flexible()), count: columns), spacing: 12) {
                    ForEach(data.recognitionItems, id: \.self) { item in
                        let isSelected = selectedAnswers.contains(item)
                        
                        Button(action: {
                            if isSelected {
                                selectedAnswers.remove(item)
                            } else {
                                selectedAnswers.insert(item)
                            }
                        }) {
                            Text(item)
                                .font(data.adaptiveConfig.workingMemoryLoad >= 4 ? .subheadline : .headline)
                                .fontWeight(.medium)
                                .foregroundColor(isSelected ? .white : .primary)
                                .padding()
                                .frame(maxWidth: .infinity)
                                .background(
                                    RoundedRectangle(cornerRadius: 12)
                                        .foregroundColor(isSelected ? Color.green : Color.white.opacity(0.1))
                                        .overlay(
                                            RoundedRectangle(cornerRadius: 12)
                                                .stroke(
                                                    isSelected ? Color.green : Color.gray.opacity(0.3),
                                                    lineWidth: isSelected ? 2 : 1
                                                )
                                        )
                                )
                        }
                        .buttonStyle(PlainButtonStyle())
                    }
                }
                .padding()
                
                Button(action: submitAnswer) {
                    Text("Submit Answer ✓")
                        .font(.headline)
                        .fontWeight(.semibold)
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .padding()
                        .background(
                            LinearGradient(
                                colors: [.orange, .red],
                                startPoint: .leading,
                                endPoint: .trailing
                            )
                        )
                        .cornerRadius(12)
                }
                .padding(.horizontal)
                .disabled(selectedAnswers.isEmpty)
            }
        }
        .padding()
    }
    
    // MARK: - Game Logic
    
    private func setupAdaptivePuzzle() {
        let currentDifficulty = difficultyManager.currentDifficulty
        currentPuzzleData = generator.generateAdaptivePuzzle(difficulty: currentDifficulty)
        currentLives = currentDifficulty.livesAllowed
        timeRemaining = currentDifficulty.timeLimit
        
        sessionStartTime = Date()
        gameStartTime = Date()
        
        print("ADAPTIVE: Set up context switch with difficulty: \(currentDifficulty.name)")
        print("ADAPTIVE: Working memory load: \(currentDifficulty.workingMemoryLoad), Executive demand: \(currentDifficulty.executiveDemand)")
        
        startTimer()
    }
    
    private func startTimer() {
        gameTimer = Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { timer in
            if timeRemaining > 0 && !showFeedback {
                timeRemaining -= 1
            } else if timeRemaining == 0 && !showFeedback {
                timer.invalidate()
                handleCompletion(false, 0)
            }
        }
    }
    
    private func submitAnswer() {
        guard let data = currentPuzzleData else { return }
        
        let correctSet = Set(data.correctAnswers)
        let userSet = selectedAnswers
        
        let correctCount = userSet.intersection(correctSet).count
        let incorrectCount = userSet.subtracting(correctSet).count
        let missedCount = correctSet.count - correctCount
        
        // Enhanced adaptive scoring
        let baseScore = (correctCount * data.difficulty.basePoints) - (incorrectCount * 25) - (missedCount * 15)
        let timeBonus = timeRemaining * 2
        
        // Adaptive multipliers
        let workingMemoryMultiplier = 1.0 + Float(data.adaptiveConfig.workingMemoryLoad - 1) * 0.2
        let executiveMultiplier = 1.0 + Float(data.adaptiveConfig.executiveDemand - 1) * 0.3
        let adaptiveBonus: Float
        switch (data.adaptiveConfig.dualTaskDemand, data.adaptiveConfig.attentionalControl) {
        case (true, true): adaptiveBonus = 1.5
        case (true, false), (false, true): adaptiveBonus = 1.3
        default: adaptiveBonus = 1.0
        }
        
        // Alphabetization bonus
        let alphabetBonus: Float = alphabetTaskCompleted ? 1.2 : 0.8
        
        finalScore = max(0, Int(Float(baseScore + timeBonus) * workingMemoryMultiplier * executiveMultiplier * adaptiveBonus * alphabetBonus))
        isCorrect = userSet == correctSet
        
        if isCorrect {
            currentStreak += 1
        } else {
            currentLives = max(0, currentLives - 1)
            currentStreak = 0
        }
        
        recordPerformanceAndAdapt(isCorrect: isCorrect)
        
        onAnswerSubmitted(selectedAnswers, isCorrect)
        handleCompletion(isCorrect, finalScore)
    }
    
    private func recordPerformanceAndAdapt(isCorrect: Bool) {
        guard let data = currentPuzzleData else { return }
        
        let totalTime = Date().timeIntervalSince(sessionStartTime)
        let executiveEfficiency = alphabetTaskCompleted ? Float(1.0) : Float(0.5)
        let cognitiveLoad = Float(data.adaptiveConfig.workingMemoryLoad) + Float(data.adaptiveConfig.executiveDemand) * 0.5
        
        let performance = ContextSwitchDifficultyManager.PlayerPerformance(
            accuracy: isCorrect ? 1.0 : 0.0,
            averageResponseTime: Float(totalTime),
            streakLength: currentStreak,
            livesRemaining: currentLives,
            gameScore: finalScore,
            difficulty: data.difficulty.name,
            cognitiveLoad: cognitiveLoad,
            executiveEfficiency: executiveEfficiency,
            contextSwitchCount: contextSwitchCount
        )
        
        let result = difficultyManager.recordPerformance(performance)
        adaptationResult = result
        
        if result.shouldNotify {
            showAdaptationNotification = true
            
            DispatchQueue.main.asyncAfter(deadline: .now() + 4) {
                showAdaptationNotification = false
            }
        }
    }
    
    private func handleCompletion(_ correct: Bool, _ score: Int) {
        showFeedback = true
    }
    
    // MARK: - Helper Methods
    
    private func formatTime(_ seconds: Int) -> String {
        let minutes = seconds / 60
        let remainingSeconds = seconds % 60
        return String(format: "%d:%02d", minutes, remainingSeconds)
    }
}
