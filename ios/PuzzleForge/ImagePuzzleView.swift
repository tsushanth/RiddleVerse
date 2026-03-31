//
//  ImagePuzzleView.swift
//  PuzzleForge
//
//  Created by Assistant on 8/14/25.
//

import SwiftUI
// Add this extension to your Puzzle.swift file or create a new file: Puzzle+ImagePuzzle.swift

// Add this extension to your Puzzle.swift file or create a new file: Puzzle+ImagePuzzle.swift

import Foundation

// MARK: - Image Puzzle Data Models
struct ImagePuzzleValidationData {
    let puzzleId: String
    let theme: String
    let description: String
    let imageUrl: String
    let gridSize: Int
    let totalPieces: Int
    let timeLimit: Int
    let difficulty: String
    let allowRotation: Bool
    let showPreview: Bool
    let snapTolerance: Double
    
    init?(from questionString: String, difficulty: String = "Easy") {
        guard let questionData = questionString.data(using: .utf8) else {
            print("❌ IMAGE_PUZZLE: Failed to convert question to data")
            return nil
        }
        
        do {
            // Parse outer JSON structure (matches your format)
            let outerJson = try JSONSerialization.jsonObject(with: questionData) as? [String: Any]
            let innerQuestionString = outerJson?["question"] as? String ?? questionString
            
            // Parse inner puzzle data
            guard let innerData = innerQuestionString.data(using: .utf8),
                  let puzzleJson = try JSONSerialization.jsonObject(with: innerData) as? [String: Any] else {
                print("❌ IMAGE_PUZZLE: Failed to parse inner question data")
                return nil
            }
            
            // Extract required fields
            self.puzzleId = puzzleJson["puzzleId"] as? String ?? "unknown"
            self.theme = puzzleJson["theme"] as? String ?? "Image Puzzle"
            self.description = puzzleJson["description"] as? String ?? "Assemble the image pieces"
            self.imageUrl = puzzleJson["imageUrl"] as? String ?? ""
            self.gridSize = puzzleJson["gridSize"] as? Int ?? 2
            self.totalPieces = puzzleJson["totalPieces"] as? Int ?? 4
            self.timeLimit = puzzleJson["timeLimit"] as? Int ?? 120000
            self.difficulty = difficulty // Use the passed difficulty
            
            // Extract game settings
            if let gameSettings = puzzleJson["gameSettings"] as? [String: Any] {
                self.allowRotation = gameSettings["allowRotation"] as? Bool ?? false
                self.showPreview = gameSettings["showPreview"] as? Bool ?? true
                self.snapTolerance = gameSettings["snapTolerance"] as? Double ?? 20.0
            } else {
                self.allowRotation = false
                self.showPreview = true
                self.snapTolerance = 20.0
            }
            
            // Validate essential fields
            guard !imageUrl.isEmpty,
                  imageUrl.starts(with: "http"),
                  gridSize > 0,
                  totalPieces > 0,
                  timeLimit > 0 else {
                print("❌ IMAGE_PUZZLE: Invalid puzzle data - missing required fields")
                return nil
            }
            
        } catch {
            print("❌ IMAGE_PUZZLE: JSON parsing error: \(error)")
            return nil
        }
    }
    
    // Add a convenience initializer for creating with all parameters
    init(puzzleId: String, theme: String, description: String, imageUrl: String,
         gridSize: Int, totalPieces: Int, timeLimit: Int, difficulty: String,
         allowRotation: Bool, showPreview: Bool, snapTolerance: Double) {
        self.puzzleId = puzzleId
        self.theme = theme
        self.description = description
        self.imageUrl = imageUrl
        self.gridSize = gridSize
        self.totalPieces = totalPieces
        self.timeLimit = timeLimit
        self.difficulty = difficulty
        self.allowRotation = allowRotation
        self.showPreview = showPreview
        self.snapTolerance = snapTolerance
    }
}

// MARK: - Puzzle Extension for Image Puzzle
extension Puzzle {
    
    /// Computed property to get image puzzle data if this puzzle is an image puzzle
    var imagePuzzleData: ImagePuzzleValidationData? {
        guard puzzleType?.lowercased().contains("image") == true ||
              puzzleType == "imagepuzzle" ||
              puzzleType == "image_puzzle" else {
            return nil
        }
        
        // Pass the puzzle's difficulty to the initializer
        return ImagePuzzleValidationData(from: question, difficulty: self.difficulty ?? "Easy")
    }
    
    /// Helper to validate if this puzzle has valid image puzzle data
    var isValidImagePuzzle: Bool {
        return imagePuzzleData != nil
    }
    
    /// Helper to get image puzzle theme
    var imagePuzzleTheme: String? {
        return imagePuzzleData?.theme
    }
    
    /// Helper to get image puzzle grid size
    var imagePuzzleGridSize: Int? {
        return imagePuzzleData?.gridSize
    }
    
    /// Helper to get image puzzle piece count
    var imagePuzzlePieceCount: Int? {
        return imagePuzzleData?.totalPieces
    }
    
    /// Helper to get image puzzle time limit in seconds
    var imagePuzzleTimeLimit: Int? {
        guard let timeLimit = imagePuzzleData?.timeLimit else { return nil }
        return timeLimit / 1000 // Convert from milliseconds to seconds
    }
    
    /// Helper to get image URL
    var imagePuzzleImageUrl: String? {
        return imagePuzzleData?.imageUrl
    }
}

// MARK: - Debugging Extension
extension Puzzle {
    
    /// Debug function to print image puzzle information
    func debugImagePuzzleData() {
        print("🧩 DEBUG: Image Puzzle Data for puzzle \(id)")
        print("   Puzzle Type: \(puzzleType ?? "nil")")
        print("   Is Valid Image Puzzle: \(isValidImagePuzzle)")
        
        if let data = imagePuzzleData {
            print("   ✅ Valid Image Puzzle Data:")
            print("      Puzzle ID: \(data.puzzleId)")
            print("      Theme: \(data.theme)")
            print("      Description: \(data.description)")
            print("      Image URL: \(data.imageUrl)")
            print("      Grid Size: \(data.gridSize)×\(data.gridSize)")
            print("      Total Pieces: \(data.totalPieces)")
            print("      Time Limit: \(data.timeLimit / 1000)s")
            print("      Difficulty: \(data.difficulty)")
            print("      Allow Rotation: \(data.allowRotation)")
            print("      Show Preview: \(data.showPreview)")
            print("      Snap Tolerance: \(data.snapTolerance)")
        } else {
            print("   ❌ No valid image puzzle data found")
            print("   Raw Question: \(question)")
        }
    }
}

// MARK: - Data Models
//
//  ImagePuzzleView.swift
//  PuzzleForge
//
//  Created by Assistant on 8/14/25.
//

//
//  ImagePuzzleView.swift
//  PuzzleForge
//
//  Created by Assistant on 8/14/25.
//

import SwiftUI

// MARK: - Data Models
struct ImagePuzzleData: Codable {
    let puzzleId: String
    let theme: String
    let description: String
    let imageUrl: String
    let imageWidth: Int
    let imageHeight: Int
    let gridSize: Int
    let totalPieces: Int
    let pieceSize: Int
    let puzzleMetadata: PuzzleMetadata
    let timeLimit: Int
    let gameSettings: GameSettings
    let imageStatus: String
    let generationMethod: String
    let instructions: String
    
    struct PuzzleMetadata: Codable {
        let pieces: [PieceInfo]
        let gridSize: Int
        let pieceSize: Int
        let totalPieces: Int
        let cornerPieces: Int
        let edgePieces: Int
        let centerPieces: Int
    }
    
    struct PieceInfo: Codable {
        let id: Int
        let row: Int
        let col: Int
        let correctPosition: Position
        let sourceRect: SourceRect
        let isCorner: Bool
        let isEdge: Bool
        let adjacentPieces: [Int]
    }
    
    struct Position: Codable {
        let x: Double
        let y: Double
    }
    
    struct SourceRect: Codable {
        let x: Double
        let y: Double
        let width: Double
        let height: Double
    }
    
    struct GameSettings: Codable {
        let allowRotation: Bool
        let snapTolerance: Double
        let showPreview: Bool
        let shufflePieces: Bool
        let showProgress: Bool
    }
}

struct ImagePuzzleAnswer: Codable {
    let correctAssembly: [CorrectPiece]
    let totalPieces: Int
    let gridSize: Int
    let completionCriteria: CompletionCriteria
    let maxScore: Int
    let bonusScore: BonusScore
    
    struct CorrectPiece: Codable {
        let pieceId: Int
        let correctRow: Int
        let correctCol: Int
        let correctPosition: ImagePuzzleData.Position
    }
    
    struct CompletionCriteria: Codable {
        let allPiecesPlaced: Bool
        let correctPositions: Bool
        let tolerance: Double
    }
    
    struct BonusScore: Codable {
        let timeBonus: Int
        let efficiencyBonus: Int
    }
}

struct DraggablePuzzlePiece: Identifiable {
    let id: Int
    let pieceInfo: ImagePuzzleData.PieceInfo
    var currentPosition: CGPoint = .zero
    var isPlaced: Bool = false
    var placedInSlot: Int? = nil
    var isCorrectlyPlaced: Bool = false
    var rotation: Double = 0
    var dragOffset: CGSize = .zero
    var isDragging: Bool = false
}

struct GridSlot: Identifiable {
    let id: Int
    let row: Int
    let col: Int
    let position: CGPoint
    let size: CGSize
    var occupiedBy: Int? = nil
    var isCorrect: Bool = false
}

//
//  ImagePuzzleView.swift
//  PuzzleForge
//
//  iOS implementation of Adaptive Image Puzzle with drag & drop
//

import SwiftUI
import Foundation

// MARK: - Adaptive Configuration
struct AdaptiveImagePuzzleConfig {
    let gridSize: Int // 2x2, 3x3, 4x4
    let allowRotation: Bool
    let snapTolerance: Float // Distance tolerance for snapping (dp)
    let timeLimit: Int // Time limit in seconds
    let showPreview: Bool // Show original image as reference
    let hintSystem: Bool // Enable hint system
    let adaptiveComplexity: Bool // Adjust complexity based on performance
    let pieceShuffle: Bool // Shuffle pieces initially
    let name: String
    let description: String
}

// MARK: - Enhanced Puzzle Piece
struct AdaptiveImagePuzzlePiece: Identifiable {
    let id: Int
    let correctRow: Int
    let correctCol: Int
    let bitmap: UIImage? // The cropped piece image
    var currentPosition: CGPoint = .zero
    var currentRotation: Double = 0.0
    var isPlaced: Bool = false
    var isCorrect: Bool = false
    var placedInRow: Int = -1
    var placedInCol: Int = -1
    var scale: Float = 1.0
    var dragState: DragState = DragState()
}

// MARK: - Drag State
struct DragState {
    var isDragging: Bool = false
    var dragOffset: CGSize = .zero
    var startPosition: CGPoint = .zero
}

// MARK: - Grid Slot
struct AdaptiveGridSlot: Identifiable {
    let id: Int
    let row: Int
    let col: Int
    var position: CGPoint = .zero
    var size: CGSize = .zero
    var occupied: Bool = false
    var correctPieceId: Int = -1
    var currentPieceId: Int = -1
}

// MARK: - Puzzle Data
struct AdaptiveImagePuzzleData {
    let puzzleId: String
    let imageUrl: String
    let theme: String
    let description: String
    let gridSize: Int
    let totalPieces: Int
    let timeLimit: Int
    let pieces: [AdaptiveImagePuzzlePiece]
    let originalImage: UIImage?
    let config: AdaptiveImagePuzzleConfig
    let difficulty: String
}

// MARK: - Main Adaptive Image Puzzle View
struct ImagePuzzleView: View {
    let puzzle: Puzzle
    let questionIndex: Int
    let totalQuestions: Int
    let onAnswerSubmitted: (Bool) -> Void
    let onNextPuzzle: () -> Void
    let onExit: () -> Void
    
    // Adaptive difficulty state
    @State private var currentDifficultyLevel: DifficultyLevel
    @State private var adaptationInfo: AdaptationInfo?
    @State private var showAdaptationNotification = false
    @State private var competitiveInsight: CompetitiveInsight?
    
    // Adaptive configuration
    private var adaptiveConfig: AdaptiveImagePuzzleConfig {
        generateAdaptiveImagePuzzleConfig(currentDifficultyLevel)
    }
    
    
    // Game state
    @State private var puzzleData: AdaptiveImagePuzzleData?
    @State private var isLoading = true
    @State private var hasError = false
    @State private var errorMessage = ""
    @State private var gameState: GameState = .loading
    @State private var puzzlePieces: [AdaptiveImagePuzzlePiece] = []
    @State private var gridSlots: [AdaptiveGridSlot] = []
    @State private var selectedPieceId = -1
    @State private var draggingPiece: AdaptiveImagePuzzlePiece?
    @State private var dragOffset: CGSize = .zero
    @State private var timeRemaining = 0
    @State private var currentScore = 0
    @State private var hintsUsed = 0
    @State private var currentHearts: Int
    @State private var showPreview = false
    @State private var gameStartTime = Date()
    @State private var sessionStartTime = Date()
    @State private var gamesPlayedThisSession = 0
    
    // Performance tracking
    @State private var correctAnswers = 0
    @State private var totalAttempts = 0
    @State private var completedPieces = 0
    
    @State private var showFullScreenImage = false

    
    // Timers
    @State private var timer: Timer?
    
    enum GameState {
        case loading
        case instructions
        case playing
        case completed
        case timeUp
    }
    
    init(puzzle: Puzzle, questionIndex: Int, totalQuestions: Int, onAnswerSubmitted: @escaping (Bool) -> Void, onNextPuzzle: @escaping () -> Void, onExit: @escaping () -> Void) {
        self.puzzle = puzzle
        self.questionIndex = questionIndex
        self.totalQuestions = totalQuestions
        self.onAnswerSubmitted = onAnswerSubmitted
        self.onNextPuzzle = onNextPuzzle
        self.onExit = onExit
        
        // Initialize difficulty level
        let difficultyString = puzzle.difficulty ?? "Medium"
        let difficultyLevel = Self.getDifficultyLevel(for: difficultyString)
        self._currentDifficultyLevel = State(initialValue: difficultyLevel)
        self._currentHearts = State(initialValue: difficultyLevel.livesAllowed)
    }
    
    var body: some View {
        ZStack {
            // Background
            LinearGradient(
                colors: [Color(red: 0.1, green: 0.1, blue: 0.2), Color(red: 0.3,  green: 0.4, blue: 0.4)],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            .ignoresSafeArea()
            
            Group {
                switch gameState {
                case .loading:
                    loadingView()
                case .instructions:
                    instructionsView
                case .playing:
                    playingView
                case .completed:
                    completionView(isSuccess: true)
                case .timeUp:
                    completionView(isSuccess: false)
                }
            }
            
            if showFullScreenImage {
                FullScreenImageView(
                    image: puzzleData?.originalImage,
                    isPresented: $showFullScreenImage
                )
                .zIndex(100)
                .transition(.opacity.combined(with: .scale))
            }
        }
        .navigationBarHidden(true)
        .onAppear {
            setupAdaptivePuzzle()
        }
        .onDisappear {
            cleanup()
        }
    }
    
    @ViewBuilder private func loadingView() -> some View {
        VStack(spacing: 20) {
            ProgressView()
                .scaleEffect(2)
                .tint(.white)
            
            Text("Loading Image Puzzle...")
                .font(.title2)
                .foregroundColor(.white)
        }
    }
    
    struct FullScreenImageView: View {
        let image: UIImage?
        @Binding var isPresented: Bool
        @State private var scale: CGFloat = 1.0
        @State private var offset: CGSize = .zero
        @State private var lastScale: CGFloat = 1.0
        
        var body: some View {
            ZStack {
                Color.black.opacity(0.9)
                    .ignoresSafeArea()
                    .onTapGesture {
                        withAnimation(.easeInOut(duration: 0.3)) {
                            isPresented = false
                        }
                    }
                
                VStack {
                    HStack {
                        Spacer()
                        Button(action: {
                            withAnimation(.easeInOut(duration: 0.3)) {
                                isPresented = false
                            }
                        }) {
                            Image(systemName: "xmark.circle.fill")
                                .font(.title)
                                .foregroundColor(.white)
                                .background(Color.black.opacity(0.5), in: Circle())
                        }
                        .padding()
                    }
                    
                    Spacer()
                    
                    if let image = image {
                        Image(uiImage: image)
                            .resizable()
                            .aspectRatio(contentMode: .fit)
                            .scaleEffect(scale)
                            .offset(offset)
                            .gesture(
                                SimultaneousGesture(
                                    MagnificationGesture()
                                        .onChanged { value in
                                            scale = lastScale * value
                                        }
                                        .onEnded { value in
                                            lastScale = scale
                                            if scale < 0.5 {
                                                withAnimation {
                                                    scale = 0.5
                                                    lastScale = 0.5
                                                }
                                            } else if scale > 3.0 {
                                                withAnimation {
                                                    scale = 3.0
                                                    lastScale = 3.0
                                                }
                                            }
                                        },
                                    DragGesture()
                                        .onChanged { value in
                                            offset = value.translation
                                        }
                                        .onEnded { _ in
                                            withAnimation {
                                                offset = .zero
                                            }
                                        }
                                )
                            )
                            .onTapGesture(count: 2) {
                                withAnimation(.easeInOut(duration: 0.3)) {
                                    if scale == 1.0 {
                                        scale = 2.0
                                        lastScale = 2.0
                                    } else {
                                        scale = 1.0
                                        lastScale = 1.0
                                        offset = .zero
                                    }
                                }
                            }
                    }
                    
                    Spacer()
                    
                    Text("Tap to close • Pinch to zoom • Double-tap to zoom • Drag to pan")
                        .font(.caption)
                        .foregroundColor(.white.opacity(0.7))
                        .padding()
                }
            }
        }
    }
    
    
    
    // MARK: - Instructions View
    private var instructionsView: some View {
        ScrollView {
            VStack(spacing: 20) {
                // Adaptive Header
                adaptiveHeaderView
                
                // Adaptation Notification
                if showAdaptationNotification {
                    adaptationNotificationView
                        .transition(.opacity.combined(with: .scale))
                }
                
                // Title and description
                VStack(spacing: 12) {
                    Text("🧩 Adaptive Image Puzzle")
                        .font(.largeTitle)
                        .fontWeight(.bold)
                        .foregroundColor(.white)
                    
                    if let data = puzzleData {
                        Text(data.theme)
                            .font(.title3)
                            .foregroundColor(.cyan)
                        
                        Text(data.description)
                            .font(.body)
                            .foregroundColor(.white.opacity(0.9))
                            .multilineTextAlignment(.center)
                            .padding(.horizontal)
                    }
                }
                
                // Preview image (clickable for full screen)
                if let data = puzzleData, let image = data.originalImage {
                    Button(action: { showFullScreenImage = true }) {
                        Image(uiImage: image)
                            .resizable()
                            .aspectRatio(contentMode: .fit)
                            .frame(width: 200, height: 200)
                            .clipShape(RoundedRectangle(cornerRadius: 12))
                            .overlay(
                                RoundedRectangle(cornerRadius: 12)
                                    .stroke(Color.white.opacity(0.3), lineWidth: 2)
                            )
                            .overlay(
                                // Full screen indicator
                                VStack {
                                    HStack {
                                        Spacer()
                                        Image(systemName: "arrow.up.left.and.arrow.down.right")
                                            .foregroundColor(.white)
                                            .padding(8)
                                            .background(Color.black.opacity(0.7))
                                            .clipShape(Circle())
                                    }
                                    Spacer()
                                }
                                .padding(8)
                            )
                    }
                    .buttonStyle(PlainButtonStyle())
                }
                
                // Adaptive features info
                VStack(alignment: .leading, spacing: 12) {
                    Text("🧠 \(adaptiveConfig.name)")
                        .font(.headline)
                        .foregroundColor(.cyan)
                    
                    Text(adaptiveConfig.description)
                        .font(.subheadline)
                        .foregroundColor(.white.opacity(0.9))
                    
                    instructionsGrid
                    
                    adaptiveGameSettings
                }
                .padding()
                .background(Color.black.opacity(0.3))
                .cornerRadius(12)
                
                // Start button
                Button(action: startGame) {
                    Text("START ADAPTIVE PUZZLE")
                        .font(.title2)
                        .fontWeight(.bold)
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
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                }
                .padding(.horizontal)
                .padding(.bottom, 20) // Extra bottom padding
            }
            .padding()
        }
    }
    
    private var instructionsGrid: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("How to Play:")
                .font(.headline)
                .foregroundColor(.white)
            
            AdaptiveInstructionRow(icon: "hand.draw", text: "Long press a puzzle piece to start dragging it")
            AdaptiveInstructionRow(icon: "square.grid.2x2", text: "Drop pieces into the correct grid positions")
            AdaptiveInstructionRow(icon: "hand.tap", text: "Long press or tap ❌ to remove placed pieces")

            if adaptiveConfig.allowRotation {
                AdaptiveInstructionRow(icon: "rotate.right", text: "Double-tap pieces to rotate them")
            }

            AdaptiveInstructionRow(icon: "checkmark.circle", text: "Complete the image to win!")

            if adaptiveConfig.showPreview {
                AdaptiveInstructionRow(icon: "eye", text: "Use preview button to see the original")
            }
        }
    }
    
    private var adaptiveGameSettings: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("⚙️ Adaptive Settings:")
                .font(.subheadline)
                .fontWeight(.bold)
                .foregroundColor(.yellow)
            
            HStack {
                Image(systemName: "clock")
                    .foregroundColor(.orange)
                Text("Time Limit: \(adaptiveConfig.timeLimit) seconds")
                    .foregroundColor(.white)
            }
            
            HStack {
                Image(systemName: "puzzlepiece")
                    .foregroundColor(.green)
                Text("Grid: \(adaptiveConfig.gridSize)×\(adaptiveConfig.gridSize) (\(adaptiveConfig.gridSize * adaptiveConfig.gridSize) pieces)")
                    .foregroundColor(.white)
            }
            
            HStack {
                Image(systemName: adaptiveConfig.allowRotation ? "rotate.right" : "lock.rotation")
                    .foregroundColor(adaptiveConfig.allowRotation ? .green : .red)
                Text("Rotation: \(adaptiveConfig.allowRotation ? "Enabled" : "Disabled")")
                    .foregroundColor(.white)
            }
        }
    }
    
    // MARK: - Playing View
    private var playingView: some View {
        VStack(spacing: 0) {
            // Adaptive Header
            adaptiveHeaderView
            
            // Adaptation Notification
            if showAdaptationNotification {
                adaptationNotificationView
                    .padding(.horizontal, 16)
                    .transition(.opacity.combined(with: .scale))
            }
            
            // Main game area
            GeometryReader { geometry in
                VStack(spacing: 16) {
                    // Controls row
                    controlsRow
                    
                    // Preview overlay
                    if showPreview {
                        previewOverlay
                    }
                    
                    // Puzzle grid
                    adaptivePuzzleGridView(geometry: geometry)
                    
                    Spacer()
                    
                    // Pieces panel
                    adaptivePuzzlePiecesView
                }
            }
        }
    }
    
    private var controlsRow: some View {
        HStack {
            if adaptiveConfig.showPreview {
                Button(action: { showPreview.toggle() }) {
                    HStack {
                        Image(systemName: showPreview ? "eye.slash" : "eye")
                        Text(showPreview ? "Hide Preview" : "Show Preview")
                    }
                    .font(.caption)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 6)
                    .background(Color.blue.opacity(0.8))
                    .foregroundColor(.white)
                    .clipShape(Capsule())
                }
            }

            Spacer()
        }
        .padding(.horizontal)
    }
    
    private var previewOverlay: some View {
        VStack {
            Text("Original Image")
                .font(.caption)
                .foregroundColor(.white)
            
            if let data = puzzleData, let image = data.originalImage {
                Image(uiImage: image)
                    .resizable()
                    .aspectRatio(contentMode: .fit)
                    .frame(width: 120, height: 120)
                    .clipShape(RoundedRectangle(cornerRadius: 8))
                    .overlay(
                        RoundedRectangle(cornerRadius: 8)
                            .stroke(Color.white, lineWidth: 2)
                    )
            }
        }
        .padding()
        .background(Color.black.opacity(0.7))
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .padding(.horizontal)
    }
    
    // MARK: - Adaptive Puzzle Grid View
    private func adaptivePuzzleGridView(geometry: GeometryProxy) -> some View {
        let gridSize = puzzleData?.gridSize ?? 2
        let maxGridWidth = min(geometry.size.width - 64, 340.0)
        let cellSize = maxGridWidth / CGFloat(gridSize)

        return VStack(spacing: 2) {
            ForEach(0..<gridSize, id: \.self) { row in
                HStack(spacing: 2) {
                    ForEach(0..<gridSize, id: \.self) { col in
                        AdaptiveGridCellView(
                            row: row,
                            col: col,
                            cellSize: cellSize,
                            piece: puzzlePieces.first { piece in
                                piece.placedInRow == row && piece.placedInCol == col
                            },
                            adaptiveConfig: adaptiveConfig,
                            onPieceRemoved: { pieceId in
                                removePieceFromGrid(pieceId: pieceId)
                            }
                        )
                    }
                }
            }
        }
        .padding()
        .background(
            RoundedRectangle(cornerRadius: 12)
                .fill(Color.black.opacity(0.3))
                .overlay(
                    RoundedRectangle(cornerRadius: 12)
                        .stroke(Color.white.opacity(0.3), lineWidth: 2)
                )
        )
        .frame(maxWidth: .infinity)
        .onDrop(of: [.text], delegate: PuzzleGridDropDelegate(
            gridSize: gridSize,
            cellSize: cellSize,
            onPiecePlaced: handlePiecePlaced
        ))
    }
    
    // MARK: - Adaptive Puzzle Pieces View
    private var adaptivePuzzlePiecesView: some View {
        VStack(spacing: 8) {
            // Header
            HStack {
                Text("Available Pieces (\(puzzlePieces.filter { !$0.isPlaced }.count) remaining)")
                    .font(.headline)
                    .foregroundColor(.white)
                
                Spacer()
                
                if adaptiveConfig.allowRotation {
                    Text("Double-tap to rotate")
                        .font(.caption)
                        .foregroundColor(.white.opacity(0.7))
                }
            }
            .padding(.horizontal)
            
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 16) {
                    ForEach(puzzlePieces.filter { !$0.isPlaced }) { piece in
                        AdaptivePuzzlePieceView(
                            piece: piece,
                            adaptiveConfig: adaptiveConfig,
                            onPieceRotated: { pieceId in
                                rotatePiece(pieceId: pieceId)
                            }
                        )
                    }
                    
                    // Padding at the end
                    Rectangle()
                        .fill(Color.clear)
                        .frame(width: 20, height: 1)
                }
                .padding(.horizontal)
            }
            
            // Status text
            statusText
        }
        .frame(height: 140)
        .background(
            RoundedRectangle(cornerRadius: 12)
                .fill(Color.black.opacity(0.2))
                .overlay(
                    RoundedRectangle(cornerRadius: 12)
                        .stroke(Color.white.opacity(0.2), lineWidth: 1)
                )
        )
        .padding(.horizontal)
    }
    
    private var statusText: some View {
        Group {
            if puzzlePieces.filter({ !$0.isPlaced }).isEmpty {
                Text("🎉 All pieces placed! Check if they're in the correct positions.")
                    .font(.subheadline)
                    .foregroundColor(.green)
                    .padding()
                    .background(Color.green.opacity(0.2))
                    .cornerRadius(8)
                    .padding(.horizontal)
            } else {
                Text("Long press a piece to drag it to the grid")
                    .font(.caption)
                    .foregroundColor(.white.opacity(0.7))
                    .padding(.horizontal)
            }
        }
    }
    
    // MARK: - Completion View
    private func completionView(isSuccess: Bool) -> some View {
        VStack(spacing: 24) {
            // Success/Failure animation
            ZStack {
                Circle()
                    .fill(isSuccess ? Color.green : Color.red)
                    .frame(width: 100, height: 100)
                
                Image(systemName: isSuccess ? "checkmark" : "xmark")
                    .font(.system(size: 40, weight: .bold))
                    .foregroundColor(.white)
            }
            
            Text(isSuccess ? "Adaptive Puzzle Complete!" : "Time's Up!")
                .font(.largeTitle)
                .fontWeight(.bold)
                .foregroundColor(.white)
            
            // Completed image
            if isSuccess, let data = puzzleData, let image = data.originalImage {
                Image(uiImage: image)
                    .resizable()
                    .aspectRatio(contentMode: .fit)
                    .frame(width: 200, height: 200)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
            }
            
            // Adaptive stats
            VStack(spacing: 8) {
                Text("Final Score: \(currentScore)")
                    .font(.title2)
                    .fontWeight(.semibold)
                    .foregroundColor(.white)
                
                HStack(spacing: 20) {
                    AdaptiveStatView(title: "Pieces", value: "\(completedPieces)/\(puzzleData?.totalPieces ?? 0)")
                    AdaptiveStatView(title: "Time", value: formatTime(max(0, adaptiveConfig.timeLimit - timeRemaining)))
                }
                
                Text("Difficulty: \(adaptiveConfig.name)")
                    .font(.caption)
                    .foregroundColor(.cyan)
            }
            .padding()
            .background(Color.black.opacity(0.3))
            .clipShape(RoundedRectangle(cornerRadius: 12))
            
            Button(action: {
                recordAdaptivePerformance(
                    isCorrect: isSuccess,
                    completionTime: Date().timeIntervalSince(gameStartTime)
                )
                onAnswerSubmitted(isSuccess)
                onNextPuzzle()
            }) {
                Text("CONTINUE")
                    .font(.title2)
                    .fontWeight(.bold)
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .padding()
                    .background(Color.green)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
            }
            .padding(.horizontal)
        }
    }
    
    // MARK: - Supporting Views
    private var adaptiveHeaderView: some View {
        HStack {
            // Back button
            Button(action: onExit) {
                Image(systemName: "arrow.left")
                    .font(.title2)
                    .foregroundColor(.white)
                    .frame(width: 40, height: 40)
            }
            
            // Level and difficulty info
            VStack(alignment: .leading) {
                Text("🧩 Image Puzzle")
                    .font(.headline)
                    .fontWeight(.bold)
                    .foregroundColor(.white)
                
                Text(adaptiveConfig.name)
                    .font(.caption)
                    .foregroundColor(adaptiveConfig.allowRotation ? .orange : .green)
            }
            
            Spacer()
            
            // Competitive insight - simplified
            if let insight = competitiveInsight {
                Text("> \(insight.percentile)%")
                    .font(.caption)
                    .fontWeight(.bold)
                    .foregroundColor(.yellow)
                    .padding(.horizontal, 6)
                    .padding(.vertical, 2)
                    .background(Color.black.opacity(0.3))
                    .cornerRadius(4)
            }
            
            // Timer and progress
            VStack(spacing: 4) {
                Text(formatTime(timeRemaining))
                    .font(.system(size: 16, weight: .bold))
                    .foregroundColor(timeRemaining <= 30 ? .red : .white)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(Color.black.opacity(0.3))
                    .cornerRadius(6)
                
                Text("Progress: \(completedPieces)/\(puzzleData?.totalPieces ?? 0)")
                    .font(.caption)
                    .foregroundColor(.white.opacity(0.8))
                
                HStack(spacing: 2) {
                    ForEach(0..<3, id: \.self) { index in
                        Image(systemName: index < currentHearts ? "heart.fill" : "heart")
                            .font(.system(size: 12))
                            .foregroundColor(index < currentHearts ? .red : .gray)
                    }
                }
            }
            
            // Score
            VStack(alignment: .trailing, spacing: 4) {
                Text("Score")
                    .font(.caption)
                    .foregroundColor(.white.opacity(0.8))
                Text("\(currentScore)")
                    .font(.title2)
                    .fontWeight(.bold)
                    .foregroundColor(.white)
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
        .background(Color.black.opacity(0.2))
    }
    
    private var adaptationNotificationView: some View {
        VStack(spacing: 8) {
            HStack {
                Image(systemName: "brain.head.profile")
                    .foregroundColor(.blue)
                Text("Puzzle Adapted!")
                    .font(.headline)
                    .fontWeight(.bold)
                    .foregroundColor(.blue)
                Spacer()
                Button("✕") {
                    showAdaptationNotification = false
                }
                .foregroundColor(.gray)
            }
            
            if let info = adaptationInfo {
                Text("Adjusted to \(info.level.name) - \(adaptiveConfig.description)")
                    .font(.caption)
                    .foregroundColor(.gray)
            }
        }
        .padding()
        .background(Color.white)
        .cornerRadius(12)
        .shadow(radius: 4)
    }
    
    private func competitiveInsightView(_ insight: CompetitiveInsight) -> some View {
        HStack(spacing: 6) {
            Image(systemName: "trophy.fill")
                .foregroundColor(.yellow)
                .font(.caption)
            
            VStack(alignment: .leading, spacing: 2) {
                Text("\(insight.ranking) • Top \(insight.percentile)%")
                    .font(.caption)
                    .fontWeight(.bold)
                    .foregroundColor(.white)
                
                Text(insight.improvement)
                    .font(.caption2)
                    .foregroundColor(.white.opacity(0.8))
            }
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 4)
        .background(Color.white.opacity(0.1))
        .cornerRadius(6)
    }
    
    // MARK: - Game Logic
    private func setupAdaptivePuzzle() {
        isLoading = true
        hasError = false
        
        // Parse puzzle data
        guard let validationData = puzzle.imagePuzzleData else {
            errorMessage = "Invalid puzzle data format"
            hasError = true
            isLoading = false
            return
        }
        
        // Create pieces from image
        Task {
            do {
                let pieces = try await createAdaptivePuzzlePieces(
                    imageUrl: validationData.imageUrl,
                    gridSize: adaptiveConfig.gridSize,
                    config: adaptiveConfig
                )
                
                let originalImage = try await loadImage(from: validationData.imageUrl)
                
                await MainActor.run {
                    puzzleData = AdaptiveImagePuzzleData(
                        puzzleId: validationData.puzzleId,
                        imageUrl: validationData.imageUrl,
                        theme: validationData.theme,
                        description: validationData.description,
                        gridSize: adaptiveConfig.gridSize,
                        totalPieces: adaptiveConfig.gridSize * adaptiveConfig.gridSize,
                        timeLimit: adaptiveConfig.timeLimit,
                        pieces: pieces,
                        originalImage: originalImage,
                        config: adaptiveConfig,
                        difficulty: validationData.difficulty
                    )
                    
                    setupGameState()
                    isLoading = false
                    gameState = .instructions
                }
            } catch {
                await MainActor.run {
                    errorMessage = "Failed to create puzzle pieces: \(error.localizedDescription)"
                    hasError = true
                    isLoading = false
                }
            }
        }
    }
    
    private func setupGameState() {
        guard let data = puzzleData else { return }
        
        timeRemaining = data.timeLimit
        puzzlePieces = data.pieces
        
        if adaptiveConfig.pieceShuffle {
            puzzlePieces.shuffle()
        }
        
        // Create grid slots
        gridSlots = []
        for row in 0..<data.gridSize {
            for col in 0..<data.gridSize {
                gridSlots.append(AdaptiveGridSlot(
                    id: row * data.gridSize + col,
                    row: row,
                    col: col
                ))
            }
        }
        
        gameStartTime = Date()
        sessionStartTime = Date()
        
        // Load competitive insight
        loadCompetitiveInsight()
    }
    
    private func startGame() {
        gameState = .playing
        startTimer()
    }
    
    private func startTimer() {
        timer = Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { _ in
            if timeRemaining > 0 {
                timeRemaining -= 1
            } else {
                gameState = .timeUp
                timer?.invalidate()
            }
        }
    }
    
    private func handlePiecePlaced(pieceId: Int, targetRow: Int, targetCol: Int) {
        guard let pieceIndex = puzzlePieces.firstIndex(where: { $0.id == pieceId }) else {
            return
        }
        
        // Remove piece from any previous position
        if puzzlePieces[pieceIndex].isPlaced {
            completedPieces -= puzzlePieces[pieceIndex].isCorrect ? 1 : 0
        }
        
        // Remove any existing piece from target position
        for index in puzzlePieces.indices {
            if puzzlePieces[index].placedInRow == targetRow && puzzlePieces[index].placedInCol == targetCol {
                puzzlePieces[index].isPlaced = false
                puzzlePieces[index].placedInRow = -1
                puzzlePieces[index].placedInCol = -1
                puzzlePieces[index].isCorrect = false
                if puzzlePieces[index].isCorrect {
                    completedPieces -= 1
                }
            }
        }
        
        // Place piece in new position
        puzzlePieces[pieceIndex].isPlaced = true
        puzzlePieces[pieceIndex].placedInRow = targetRow
        puzzlePieces[pieceIndex].placedInCol = targetCol
        
        // Check if placement is correct
        let isCorrect = puzzlePieces[pieceIndex].correctRow == targetRow &&
                       puzzlePieces[pieceIndex].correctCol == targetCol
        
        puzzlePieces[pieceIndex].isCorrect = isCorrect
        
        if isCorrect {
            completedPieces += 1
            currentScore += 10
        }
        
        totalAttempts += 1
        
        // Check for completion
        if completedPieces == puzzleData?.totalPieces {
            completeGame()
        }
    }
    
    private func removePieceFromGrid(pieceId: Int) {
        guard let pieceIndex = puzzlePieces.firstIndex(where: { $0.id == pieceId }) else {
            return
        }
        
        if puzzlePieces[pieceIndex].isCorrect {
            completedPieces -= 1
        }
        
        puzzlePieces[pieceIndex].isPlaced = false
        puzzlePieces[pieceIndex].placedInRow = -1
        puzzlePieces[pieceIndex].placedInCol = -1
        puzzlePieces[pieceIndex].isCorrect = false
        
        currentScore = max(0, currentScore - 2)
    }
    
    private func rotatePiece(pieceId: Int) {
        guard let pieceIndex = puzzlePieces.firstIndex(where: { $0.id == pieceId }),
              adaptiveConfig.allowRotation else {
            return
        }
        
        puzzlePieces[pieceIndex].currentRotation += 90
        if puzzlePieces[pieceIndex].currentRotation >= 360 {
            puzzlePieces[pieceIndex].currentRotation = 0
        }
    }
    
    private func showHintAction() {
        guard hintsUsed < 3, adaptiveConfig.hintSystem else { return }
        
        hintsUsed += 1
        // Implement hint logic here
    }
    
    private func completeGame() {
        timer?.invalidate()
        
        // Calculate time bonus
        let timeBonus = timeRemaining * 2
        currentScore += timeBonus
        
        // Calculate adaptive score
        let finalScore = calculateAdaptiveImagePuzzleScore(
            completedPieces: completedPieces,
            totalPieces: puzzleData?.totalPieces ?? 0,
            timeUsed: adaptiveConfig.timeLimit - timeRemaining,
            timeLimit: adaptiveConfig.timeLimit,
            hintsUsed: hintsUsed,
            difficulty: currentDifficultyLevel,
            adaptiveConfig: adaptiveConfig
        )
        
        currentScore = finalScore
        gameState = .completed
    }
    
    private func recordAdaptivePerformance(isCorrect: Bool, completionTime: TimeInterval) {
        let performanceScore = calculatePerformanceScore(isCorrect: isCorrect, completionTime: completionTime)
        
        if gamesPlayedThisSession > 0 && gamesPlayedThisSession % 2 == 0 {
            let shouldIncrease = performanceScore > 0.8 && completedPieces >= (puzzleData?.totalPieces ?? 0) * 3/4
            let shouldDecrease = performanceScore < 0.4 || currentHearts <= 1
            
            if shouldIncrease && currentDifficultyLevel.index < 4 {
                adaptDifficulty(increase: true)
            } else if shouldDecrease && currentDifficultyLevel.index > 0 {
                adaptDifficulty(increase: false)
            }
        }
    }
    
    private func calculatePerformanceScore(isCorrect: Bool, completionTime: TimeInterval) -> Double {
        var score = isCorrect ? 1.0 : 0.0
        
        let timeTarget = Double(adaptiveConfig.timeLimit) * 0.75
        if completionTime < timeTarget {
            score += (timeTarget - completionTime) / timeTarget * 0.2
        } else {
            score -= min((completionTime - timeTarget) / timeTarget * 0.2, 0.3)
        }
        
        // Piece completion bonus
        let completionRatio = Double(completedPieces) / Double(puzzleData?.totalPieces ?? 1)
        score += completionRatio * 0.3
        
        return max(0.0, min(1.0, score))
    }
    
    private func adaptDifficulty(increase: Bool) {
        let newIndex = increase ?
            min(currentDifficultyLevel.index + 1, 4) :
            max(currentDifficultyLevel.index - 1, 0)
        
        if newIndex != currentDifficultyLevel.index {
            currentDifficultyLevel = Self.getDifficultyLevel(for: newIndex)
            adaptationInfo = AdaptationInfo(
                level: currentDifficultyLevel,
                confidenceScore: 0.8
            )
            showAdaptationNotification = true
            
            currentHearts = currentDifficultyLevel.livesAllowed
            timeRemaining = currentDifficultyLevel.timeLimit
        }
    }
    
    private func loadCompetitiveInsight() {
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
            competitiveInsight = CompetitiveInsight(
                percentile: 72,
                ranking: "Silver",
                improvement: "+8% this week",
                globalAverage: 75.5,
                userScore: 82.3
            )
        }
    }
    
    private func cleanup() {
        timer?.invalidate()
    }
    
    // MARK: - Helper Functions
    private func formatTime(_ seconds: Int) -> String {
        let minutes = seconds / 60
        let remainingSeconds = seconds % 60
        return String(format: "%d:%02d", minutes, remainingSeconds)
    }
    
    // MARK: - Static Helper Functions
    static func getDifficultyLevel(for difficulty: String) -> DifficultyLevel {
        return DifficultyLevel.create(name: difficulty)
    }
    
    static func getDifficultyLevel(for index: Int) -> DifficultyLevel {
        return DifficultyLevel.create(index: index)
    }
}

// MARK: - Supporting Views
struct AdaptiveInstructionRow: View {
    let icon: String
    let text: String
    
    var body: some View {
        HStack {
            Image(systemName: icon)
                .foregroundColor(.cyan)
                .frame(width: 20)
            Text(text)
                .foregroundColor(.white)
            Spacer()
        }
    }
}

struct AdaptiveStatView: View {
    let title: String
    let value: String
    
    var body: some View {
        VStack(spacing: 4) {
            Text(title)
                .font(.caption)
                .foregroundColor(.white.opacity(0.7))
            Text(value)
                .font(.headline)
                .foregroundColor(.white)
        }
    }
}

struct AdaptiveGridCellView: View {
    let row: Int
    let col: Int
    let cellSize: CGFloat
    let piece: AdaptiveImagePuzzlePiece?
    let adaptiveConfig: AdaptiveImagePuzzleConfig
    let onPieceRemoved: (Int) -> Void
    
    var body: some View {
        ZStack {
            Rectangle()
                .fill(piece?.isCorrect == true ? Color.green.opacity(0.3) : Color.gray.opacity(0.2))
                .frame(width: cellSize, height: cellSize)
                .overlay(
                    Rectangle()
                        .stroke(
                            piece?.isCorrect == true ? Color.green : Color.white.opacity(0.5),
                            lineWidth: piece?.isCorrect == true ? 3 : 1
                        )
                )
            
            if let piece = piece, let image = piece.bitmap {
                ZStack {
                    Image(uiImage: image)
                        .resizable()
                        .aspectRatio(contentMode: .fill)
                        .frame(width: cellSize - 4, height: cellSize - 4)
                        .clipped()
                        .rotationEffect(.degrees(piece.currentRotation))
                    
                    // Remove button
                    VStack {
                        HStack {
                            Spacer()
                            Button(action: {
                                onPieceRemoved(piece.id)
                            }) {
                                Image(systemName: "xmark.circle.fill")
                                    .foregroundColor(.red)
                                    .background(Color.white, in: Circle())
                                    .font(.caption)
                            }
                        }
                        Spacer()
                    }
                    .padding(2)
                    
                    // Correctness indicator
                    if piece.isCorrect {
                        VStack {
                            Spacer()
                            HStack {
                                Spacer()
                                Image(systemName: "checkmark.circle.fill")
                                    .foregroundColor(.green)
                                    .background(Color.white, in: Circle())
                                    .font(.caption)
                            }
                        }
                        .padding(2)
                    }
                }
                .onLongPressGesture {
                    onPieceRemoved(piece.id)
                }
            } else {
                VStack(spacing: 2) {
                    Text("Drop")
                        .font(.caption2)
                        .fontWeight(.medium)
                        .foregroundColor(.white.opacity(0.6))
                    Text("Here")
                        .font(.caption2)
                        .fontWeight(.medium)
                        .foregroundColor(.white.opacity(0.6))
                }
            }
        }
    }
}

struct AdaptivePuzzlePieceView: View {
    let piece: AdaptiveImagePuzzlePiece
    let adaptiveConfig: AdaptiveImagePuzzleConfig
    let onPieceRotated: (Int) -> Void
    
    @State private var dragOffset = CGSize.zero
    @State private var isDragging = false
    
    var body: some View {
        ZStack {
            if let image = piece.bitmap {
                Image(uiImage: image)
                    .resizable()
                    .aspectRatio(contentMode: .fill)
                    .frame(width: 80, height: 80)
                    .clipped()
                    .rotationEffect(.degrees(piece.currentRotation))
            } else {
                Rectangle()
                    .fill(Color.gray.opacity(0.3))
                    .frame(width: 80, height: 80)
            }
            
            // Rotation indicator
            if adaptiveConfig.allowRotation {
                VStack {
                    Spacer()
                    HStack {
                        Spacer()
                        Image(systemName: "rotate.right")
                            .font(.caption2)
                            .foregroundColor(.white)
                            .padding(4)
                            .background(Color.black.opacity(0.7))
                            .clipShape(Circle())
                    }
                }
            }
        }
        .scaleEffect(isDragging ? 1.2 : 1.0)
        .offset(dragOffset)
        .zIndex(isDragging ? 1 : 0)
        .clipShape(RoundedRectangle(cornerRadius: 8))
        .overlay(
            RoundedRectangle(cornerRadius: 8)
                .stroke(isDragging ? Color.yellow : Color.white, lineWidth: 2)
        )
        .onDrag {
            NSItemProvider(object: "\(piece.id)" as NSString)
        }
        .onTapGesture(count: 2) {
            if adaptiveConfig.allowRotation {
                onPieceRotated(piece.id)
            }
        }
        .animation(.spring(response: 0.3, dampingFraction: 0.6), value: isDragging)
    }
}

// MARK: - Drop Delegate
struct PuzzleGridDropDelegate: DropDelegate {
    let gridSize: Int
    let cellSize: CGFloat
    let onPiecePlaced: (Int, Int, Int) -> Void
    
    func performDrop(info: DropInfo) -> Bool {
        guard let item = info.itemProviders(for: [.text]).first else { return false }
        
        item.loadObject(ofClass: NSString.self) { (data, error) in
            if let pieceIdString = data as? String,
               let pieceId = Int(pieceIdString) {
                DispatchQueue.main.async {
                    // Calculate grid position based on drop location
                    let dropLocation = info.location
                    let col = Int(dropLocation.x / cellSize)
                    let row = Int(dropLocation.y / cellSize)
                    
                    // Ensure we're within grid bounds
                    let clampedRow = max(0, min(row, gridSize - 1))
                    let clampedCol = max(0, min(col, gridSize - 1))
                    
                    onPiecePlaced(pieceId, clampedRow, clampedCol)
                }
            }
        }
        
        return true
    }
}

// MARK: - Helper Functions
func generateAdaptiveImagePuzzleConfig(_ difficulty: DifficultyLevel) -> AdaptiveImagePuzzleConfig {
    switch difficulty.index {
    case 0: // Beginner
        return AdaptiveImagePuzzleConfig(
            gridSize: 2,
            allowRotation: false,
            snapTolerance: 40.0,
            timeLimit: 300,
            showPreview: true,
            hintSystem: false,
            adaptiveComplexity: false,
            pieceShuffle: false,
            name: "Beginner Mode",
            description: "Simple 2×2 puzzle with hints and preview"
        )
    case 1: // Easy
        return AdaptiveImagePuzzleConfig(
            gridSize: 2,
            allowRotation: true,
            snapTolerance: 30.0,
            timeLimit: 240,
            showPreview: true,
            hintSystem: false,
            adaptiveComplexity: true,
            pieceShuffle: true,
            name: "Easy Mode",
            description: "2×2 puzzle with rotation and shuffled pieces"
        )
    case 2: // Medium
        return AdaptiveImagePuzzleConfig(
            gridSize: 3,
            allowRotation: true,
            snapTolerance: 25.0,
            timeLimit: 360,
            showPreview: true,
            hintSystem: false,
            adaptiveComplexity: true,
            pieceShuffle: true,
            name: "Medium Mode",
            description: "3×3 puzzle with full features"
        )
    case 3: // Hard
        return AdaptiveImagePuzzleConfig(
            gridSize: 4,
            allowRotation: true,
            snapTolerance: 20.0,
            timeLimit: 480,
            showPreview: false,
            hintSystem: false,
            adaptiveComplexity: true,
            pieceShuffle: true,
            name: "Hard Mode",
            description: "4×4 puzzle without assistance"
        )
    case 4: // Expert
        return AdaptiveImagePuzzleConfig(
            gridSize: 4,
            allowRotation: true,
            snapTolerance: 15.0,
            timeLimit: 360,
            showPreview: false,
            hintSystem: false,
            adaptiveComplexity: true,
            pieceShuffle: true,
            name: "Expert Mode",
            description: "4×4 puzzle with time pressure"
        )
    default:
        return AdaptiveImagePuzzleConfig(
            gridSize: 3,
            allowRotation: true,
            snapTolerance: 25.0,
            timeLimit: 360,
            showPreview: true,
            hintSystem: false,
            adaptiveComplexity: true,
            pieceShuffle: true,
            name: "Medium Mode",
            description: "3×3 puzzle with full features"
        )
    }
}

func calculateAdaptiveImagePuzzleScore(
    completedPieces: Int,
    totalPieces: Int,
    timeUsed: Int,
    timeLimit: Int,
    hintsUsed: Int,
    difficulty: DifficultyLevel,
    adaptiveConfig: AdaptiveImagePuzzleConfig
) -> Int {
    if completedPieces == 0 { return 0 }
    
    let baseScore = completedPieces * difficulty.basePoints
    
    // Completion bonus
    let completionRatio = Float(completedPieces) / Float(totalPieces)
    let completionBonus = Int(Float(baseScore) * completionRatio * 0.5)
    
    // Time bonus
    let timeEfficiency = Float(timeLimit - timeUsed) / Float(timeLimit)
    let timeBonus = Int(Float(baseScore) * timeEfficiency * 0.3)
    
    // Adaptive complexity multiplier
    let complexityMultiplier: Float = {
        switch adaptiveConfig.gridSize {
        case 4: return 2.0
        case 3: return 1.5
        case 2: return 1.0
        default: return 1.0
        }
    }()
    
    // Feature difficulty bonuses
    let featureBonus: Float = [
        adaptiveConfig.allowRotation ? 0.2 : 0.0,
        !adaptiveConfig.showPreview ? 0.3 : 0.0,
        !adaptiveConfig.hintSystem ? 0.2 : 0.0,
        adaptiveConfig.pieceShuffle ? 0.1 : 0.0
    ].reduce(0, +)
    
    let adaptiveMultiplier: Float = 1.0 + featureBonus
    
    // Hint penalty
    let hintPenalty = hintsUsed * (difficulty.basePoints / 4)
    
    let finalScore = Int(Float(baseScore + completionBonus + timeBonus) * complexityMultiplier * adaptiveMultiplier) - hintPenalty
    
    return max(finalScore, baseScore / 2)
}

// MARK: - Image Loading Functions
func loadImage(from urlString: String) async throws -> UIImage {
    guard let url = URL(string: urlString) else {
        throw URLError(.badURL)
    }
    
    let (data, _) = try await URLSession.shared.data(from: url)
    
    guard let image = UIImage(data: data) else {
        throw URLError(.cannotDecodeContentData)
    }
    
    return image
}

func createAdaptivePuzzlePieces(
    imageUrl: String,
    gridSize: Int,
    config: AdaptiveImagePuzzleConfig
) async throws -> [AdaptiveImagePuzzlePiece] {
    let originalImage = try await loadImage(from: imageUrl)
    
    var pieces: [AdaptiveImagePuzzlePiece] = []
    let pieceWidth = originalImage.size.width / CGFloat(gridSize)
    let pieceHeight = originalImage.size.height / CGFloat(gridSize)
    
    for row in 0..<gridSize {
        for col in 0..<gridSize {
            let rect = CGRect(
                x: CGFloat(col) * pieceWidth,
                y: CGFloat(row) * pieceHeight,
                width: pieceWidth,
                height: pieceHeight
            )
            
            if let croppedImage = cropImage(originalImage, to: rect) {
                let piece = AdaptiveImagePuzzlePiece(
                    id: row * gridSize + col,
                    correctRow: row,
                    correctCol: col,
                    bitmap: croppedImage,
                    currentRotation: config.allowRotation && Bool.random() ? Double([0, 90, 180, 270].randomElement()!) : 0.0
                )
                pieces.append(piece)
            }
        }
    }
    
    return pieces
}

func cropImage(_ image: UIImage, to rect: CGRect) -> UIImage? {
    guard let cgImage = image.cgImage else { return nil }
    
    let scale = image.scale
    let scaledRect = CGRect(
        x: rect.origin.x * scale,
        y: rect.origin.y * scale,
        width: rect.size.width * scale,
        height: rect.size.height * scale
    )
    
    guard let croppedCGImage = cgImage.cropping(to: scaledRect) else { return nil }
    
    return UIImage(cgImage: croppedCGImage, scale: scale, orientation: image.imageOrientation)
}
