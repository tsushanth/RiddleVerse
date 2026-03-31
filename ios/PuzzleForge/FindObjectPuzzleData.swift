//
//  FindObjectPuzzleData.swift
//  PuzzleForge
//
//  Created by Sushanth Tiruvaipati on 8/11/25.
//

import SwiftUI
import Foundation

// MARK: - Data Models matching Android implementation

struct FindObjectPuzzleData: Codable {
    let puzzleId: String
    let imageUrl: String
    let theme: String
    let description: String
    let instructions: String
    let totalObjects: Int
    let timeLimit: Int // in seconds
    let objectsToFind: [DiscoveredObject]
    let allDiscoveredObjects: [DiscoveredObject]?
    let gridConfig: GridConfig
    let gameSettings: ObjectGameSettings
    let difficulty: String
    let discoveryMethod: String
    let sceneAnalysis: String?
    
    enum CodingKeys: String, CodingKey {
        case puzzleId, imageUrl, theme, description, instructions, totalObjects, timeLimit
        case objectsToFind, allDiscoveredObjects = "allFoundInstances", gridConfig, gameSettings, difficulty
        case discoveryMethod, sceneAnalysis
    }
    
    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        
        puzzleId = try container.decode(String.self, forKey: .puzzleId)
        imageUrl = try container.decode(String.self, forKey: .imageUrl)
        theme = try container.decode(String.self, forKey: .theme)
        description = try container.decode(String.self, forKey: .description)
        instructions = try container.decode(String.self, forKey: .instructions)
        totalObjects = try container.decode(Int.self, forKey: .totalObjects)
        timeLimit = try container.decode(Int.self, forKey: .timeLimit)
        objectsToFind = try container.decode([DiscoveredObject].self, forKey: .objectsToFind)
        allDiscoveredObjects = try container.decodeIfPresent([DiscoveredObject].self, forKey: .allDiscoveredObjects)
        gridConfig = try container.decode(GridConfig.self, forKey: .gridConfig)
        gameSettings = try container.decode(ObjectGameSettings.self, forKey: .gameSettings)
        discoveryMethod = try container.decode(String.self, forKey: .discoveryMethod)
        sceneAnalysis = try container.decodeIfPresent(String.self, forKey: .sceneAnalysis)
        
        // Handle difficulty - if not present at root level, try to get from first object or default to "Easy"
        if let rootDifficulty = try container.decodeIfPresent(String.self, forKey: .difficulty) {
            difficulty = rootDifficulty
        } else if let firstObject = objectsToFind.first {
            difficulty = firstObject.difficulty
        } else {
            difficulty = "Easy" // Default fallback
        }
    }
}

struct DiscoveredObject: Codable, Identifiable {
    let id: String
    let name: String
    let x: Double // Percentage
    let y: Double // Percentage
    let tolerance: Double
    let difficulty: String
    let hint: String
    let description: String
    let confidence: String // excellent/good/fair
    let size: String // large/medium/small
    let visibility: String // clear/partial/unclear
    let gridIndex: Int
    let gridRow: Int
    let gridCol: Int
    let discoveryMethod: String
    
    // Make these optional with default values since server doesn't always send them
    let objectTypeName: String?
    let totalInstancesOfType: Int
    let allValidInstances: [ObjectInstance]?
    let isPrimaryInstance: Bool
    
    enum CodingKeys: String, CodingKey {
        case id, name, x, y, tolerance, difficulty, hint, description, confidence
        case size, visibility, gridIndex, gridRow, gridCol, discoveryMethod
        case objectTypeName, totalInstancesOfType, allValidInstances, isPrimaryInstance
    }
    
    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        
        id = try container.decode(String.self, forKey: .id)
        name = try container.decode(String.self, forKey: .name)
        x = try container.decode(Double.self, forKey: .x)
        y = try container.decode(Double.self, forKey: .y)
        tolerance = try container.decode(Double.self, forKey: .tolerance)
        difficulty = try container.decode(String.self, forKey: .difficulty)
        hint = try container.decode(String.self, forKey: .hint)
        description = try container.decode(String.self, forKey: .description)
        confidence = try container.decode(String.self, forKey: .confidence)
        size = try container.decode(String.self, forKey: .size)
        visibility = try container.decode(String.self, forKey: .visibility)
        gridIndex = try container.decode(Int.self, forKey: .gridIndex)
        gridRow = try container.decode(Int.self, forKey: .gridRow)
        gridCol = try container.decode(Int.self, forKey: .gridCol)
        discoveryMethod = try container.decode(String.self, forKey: .discoveryMethod)
        
        // Optional fields with defaults
        objectTypeName = try container.decodeIfPresent(String.self, forKey: .objectTypeName)
        totalInstancesOfType = try container.decodeIfPresent(Int.self, forKey: .totalInstancesOfType) ?? 1
        allValidInstances = try container.decodeIfPresent([ObjectInstance].self, forKey: .allValidInstances)
        isPrimaryInstance = try container.decodeIfPresent(Bool.self, forKey: .isPrimaryInstance) ?? false
    }
}

struct ObjectInstance: Codable, Identifiable {
    let id: String
    let x: Double
    let y: Double
    let gridIndex: Int
    let instanceNumber: Int
    let confidence: String
    
    enum CodingKeys: String, CodingKey {
        case id, x, y, gridIndex, instanceNumber, confidence
    }
}

struct GridConfig: Codable {
    let rows: Int
    let cols: Int
    let totalCells: Int
    
    enum CodingKeys: String, CodingKey {
        case rows, cols, totalCells
    }
}

struct ObjectGameSettings: Codable {
    let clickTolerance: Double
    let maxWrongClicks: Int
    let hintSystem: Bool
    let scoringSystem: String
    let gridBasedValidation: Bool
    
    enum CodingKeys: String, CodingKey {
        case clickTolerance, maxWrongClicks, hintSystem, scoringSystem, gridBasedValidation
    }
}

struct FoundObject: Identifiable {
    let id = UUID()
    let objectId: String
    let name: String
    let x: Double // Click coordinates as percentages
    let y: Double
    let foundAt: Date
    let gridIndex: Int?
    let confidence: String?
    let size: String?
}

enum ObjectViewMode: CaseIterable {
    case landscapeFull
    case objectsList
    
    var title: String {
        switch self {
        case .landscapeFull: return "Full"
        case .objectsList: return "Objects"
        }
    }
    
    var icon: String {
        switch self {
        case .landscapeFull: return "rectangle.expand"
        case .objectsList: return "list.bullet"
        }
    }
}

struct LandscapeObjectViewWithZoom: View {
    let puzzleData: FindObjectPuzzleData
    let foundObjects: [FoundObject]
    let showHint: DiscoveredObject?
    let gameCompleted: Bool
    let onImageClick: (CGPoint, CGSize) -> Void
    let onImageSizeChanged: (CGSize) -> Void
    
    @State private var scale: CGFloat = 1.0
    @State private var offset = CGSize.zero
    @State private var lastScale: CGFloat = 1.0
    @State private var lastOffset = CGSize.zero
    @State private var imageSize = CGSize.zero
    
    var body: some View {
        GeometryReader { geometry in
            ZStack {
                // Base image with zoom and pan
                AsyncImage(url: URL(string: puzzleData.imageUrl)) { image in
                    image
                        .resizable()
                        .aspectRatio(contentMode: .fit)
                        .clipShape(RoundedRectangle(cornerRadius: 8))
                        .background(
                            GeometryReader { imageGeometry in
                                Color.clear.onAppear {
                                    imageSize = imageGeometry.size
                                    onImageSizeChanged(imageGeometry.size)
                                }
                            }
                        )
                } placeholder: {
                    RoundedRectangle(cornerRadius: 8)
                        .fill(Color.gray.opacity(0.3))
                        .overlay(
                            ProgressView()
                                .progressViewStyle(CircularProgressViewStyle(tint: .white))
                        )
                }
                .scaleEffect(scale)
                .offset(offset)
                
                // Found objects markers - positioned on the transformed coordinate system
                ForEach(foundObjects) { found in
                    Circle()
                        .fill(Color.green.opacity(0.8))
                        .frame(width: 30, height: 30)
                        .overlay(
                            Image(systemName: "checkmark")
                                .font(.system(size: 16, weight: .bold))
                                .foregroundColor(.white)
                        )
                        .position(
                            x: (CGFloat(found.x / 100.0) * imageSize.width * scale) + offset.width + (geometry.size.width - imageSize.width * scale) / 2,
                            y: (CGFloat(found.y / 100.0) * imageSize.height * scale) + offset.height + (geometry.size.height - imageSize.height * scale) / 2
                        )
                }
                
                // Hint marker - positioned on the transformed coordinate system
                if let hint = showHint {
                    let hintPosition = calculateTransformedHintPosition(
                        hint: hint,
                        imageSize: imageSize,
                        containerSize: geometry.size,
                        scale: scale,
                        offset: offset
                    )
                    if let position = hintPosition {
                        Circle()
                            .fill(Color.yellow.opacity(0.7))
                            .frame(width: 40, height: 40)
                            .overlay(
                                Image(systemName: "lightbulb.fill")
                                    .font(.system(size: 20, weight: .bold))
                                    .foregroundColor(.black)
                            )
                            .position(x: position.x, y: position.y)
                    }
                }
                
                // Invisible overlay for tap detection
                Color.clear
                    .contentShape(Rectangle())
                    .gesture(
                        SimultaneousGesture(
                            // Pinch to zoom
                            MagnificationGesture()
                                .onChanged { value in
                                    let newScale = lastScale * value
                                    scale = min(max(newScale, 1.0), 4.0)
                                }
                                .onEnded { _ in
                                    lastScale = scale
                                },
                            // Drag to pan
                            DragGesture()
                                .onChanged { value in
                                    let newOffset = CGSize(
                                        width: lastOffset.width + value.translation.width,
                                        height: lastOffset.height + value.translation.height
                                    )
                                    
                                    // Calculate bounds
                                    let scaledWidth = imageSize.width * scale
                                    let scaledHeight = imageSize.height * scale
                                    let maxOffsetX = max(0, (scaledWidth - imageSize.width) / 2)
                                    let maxOffsetY = max(0, (scaledHeight - imageSize.height) / 2)
                                    
                                    offset = CGSize(
                                        width: min(max(newOffset.width, -maxOffsetX), maxOffsetX),
                                        height: min(max(newOffset.height, -maxOffsetY), maxOffsetY)
                                    )
                                }
                                .onEnded { _ in
                                    lastOffset = offset
                                }
                        )
                    )
                    .onTapGesture { location in
                        if !gameCompleted && imageSize != .zero {
                            // Convert tap location back to original image coordinates
                            let imageCenter = CGPoint(
                                x: geometry.size.width / 2,
                                y: geometry.size.height / 2
                            )
                            
                            // Account for the transform
                            let adjustedX = (location.x - imageCenter.x - offset.width) / scale + (imageSize.width / 2)
                            let adjustedY = (location.y - imageCenter.y - offset.height) / scale + (imageSize.height / 2)
                            
                            if adjustedX >= 0 && adjustedX <= imageSize.width &&
                               adjustedY >= 0 && adjustedY <= imageSize.height {
                                onImageClick(CGPoint(x: adjustedX, y: adjustedY), imageSize)
                            }
                        }
                    }
                
                // Instructions overlay when not zoomed
                if scale <= 1.2 {
                    VStack {
                        Spacer()
                        Text("Pinch to zoom • Drag to pan • Tap objects to find them")
                            .font(.caption2)
                            .foregroundColor(.white)
                            .padding(8)
                            .background(
                                RoundedRectangle(cornerRadius: 8)
                                    .fill(Color.black.opacity(0.7))
                            )
                            .padding(.bottom, 20)
                    }
                }
            }
        }
        .padding(8)
    }
    
    private func calculateTransformedHintPosition(
        hint: DiscoveredObject,
        imageSize: CGSize,
        containerSize: CGSize,
        scale: CGFloat,
        offset: CGSize
    ) -> CGPoint? {
        // Apply the same transform logic as found objects
        let x = (CGFloat(hint.x / 100.0) * imageSize.width * scale) + offset.width + (containerSize.width - imageSize.width * scale) / 2
        let y = (CGFloat(hint.y / 100.0) * imageSize.height * scale) + offset.height + (containerSize.height - imageSize.height * scale) / 2
        
        // Check if position is within visible bounds
        if x < 0 || x > containerSize.width || y < 0 || y > containerSize.height {
            return nil
        }
        
        return CGPoint(x: x, y: y)
    }
}

struct FindObjectPuzzleView: View {
    let puzzle: Puzzle
    let questionIndex: Int
    let totalQuestions: Int
    let onAnswerSubmitted: (Bool) -> Void
    let onNextPuzzle: () -> Void
    let onExit: () -> Void
    
    @State private var puzzleData: FindObjectPuzzleData?
    @State private var foundObjects: [FoundObject] = []
    @State private var wrongClicks = 0
    @State private var hintsUsed = 0
    @State private var timeRemaining: Int = 300
    @State private var gameCompleted = false
    @State private var showHint: DiscoveredObject?
    @State private var viewMode: ObjectViewMode = .landscapeFull
    @State private var isLoading = true
    @State private var errorMessage: String?
    
    // Zoom state for portrait mode
    @State private var scale: CGFloat = 1.0
    @State private var offset = CGSize.zero
    @State private var imageSize = CGSize.zero
    
    // Timer
    @State private var timer: Timer?
    
    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            
            if isLoading {
                FLoadingView(message: "Loading natural discovery puzzle...")
            } else if let error = errorMessage {
                FoErrorView(message: error, onRetry: loadPuzzleData, onSkip: onExit)
            } else if let data = puzzleData {
                VStack(spacing: 0) {
                    // Header
                    FindObjectHeader(
                        difficulty: puzzle.difficulty ?? "Medium",
                        round: "ROUND \(questionIndex + 1) of \(totalQuestions)",
                        timeRemaining: timeRemaining,
                        foundCount: foundObjects.count,
                        totalObjects: data.totalObjects,
                        wrongClicks: wrongClicks,
                        maxWrongClicks: data.gameSettings.maxWrongClicks,
                        viewMode: viewMode,
                        discoveryMethod: data.discoveryMethod,
                        gridConfig: data.gridConfig,
                        canUseHint: data.gameSettings.hintSystem && hintsUsed < 3,
                        onViewModeChange: { newMode in
                            viewMode = newMode
                            scale = 1.0
                            offset = .zero
                        },
                        onHint: {
                            if data.gameSettings.hintSystem && hintsUsed < 3 {
                                let unfoundObjects = data.objectsToFind.filter { obj in
                                    !foundObjects.contains { found in
                                        found.objectId == obj.id
                                    }
                                }
                                if let nextObject = unfoundObjects.first {
                                    showHint = nextObject
                                    hintsUsed += 1
                                }
                            }
                        }
                    )
                    
                    // Main content area
                    // In the main content area switch statement
                    Group {
                        switch viewMode {
                        case .landscapeFull:
                            LandscapeObjectViewWithZoom( // Updated view name
                                puzzleData: data,
                                foundObjects: foundObjects,
                                showHint: showHint,
                                gameCompleted: gameCompleted,
                                onImageClick: handleImageClick,
                                onImageSizeChanged: { size in imageSize = size }
                            )
                        case .objectsList:
                            ObjectsListView(
                                objectsToFind: data.objectsToFind,
                                allDiscoveredObjects: data.allDiscoveredObjects,
                                foundObjects: foundObjects,
                                hintsUsed: hintsUsed,
                                sceneAnalysis: data.sceneAnalysis,
                                onObjectHintUsed: { obj in
                                    hintsUsed += 1
                                    showHint = obj
                                }
                            )
                        }
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    
                    // Progress bar and status
                    VStack(spacing: 8) {
                        ProgressView(value: Double(foundObjects.count) / Double(data.totalObjects))
                            .progressViewStyle(LinearProgressViewStyle(tint: .green))
                            .background(Color.gray)
                            .frame(height: 4)
                        
                        HStack {
                            Text("Found: \(foundObjects.count)/\(data.totalObjects)")
                                .font(.caption)
                                .foregroundColor(.white)
                            
                            Spacer()
                            
                            Text("Wrong: \(wrongClicks)/\(data.gameSettings.maxWrongClicks)")
                                .font(.caption)
                                .foregroundColor(wrongClicks >= Int(Double(data.gameSettings.maxWrongClicks) * 0.8) ? .red : .white)
                            
                            Spacer()
                            
                            Text(data.discoveryMethod.replacingOccurrences(of: "_", with: " ").capitalized)
                                .font(.caption2)
                                .foregroundColor(.cyan)
                        }
                    }
                    .padding()
                }
            }
        }
        .onAppear {
            loadPuzzleData()
            startTimer()
        }
        .onDisappear {
            stopTimer()
        }
        .onChange(of: foundObjects.count) { count in
            if let data = puzzleData, count >= data.totalObjects && !gameCompleted {
                // Show success feedback for the last object before completing
                DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
                    completeGame()
                }
            }
        }
    }
    
    // MARK: - Timer Functions
    
    private func startTimer() {
        timer = Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { _ in
            if timeRemaining > 0 && !gameCompleted {
                timeRemaining -= 1
            } else if timeRemaining <= 0 && !gameCompleted {
                completeGame()
            }
        }
    }
    
    private func stopTimer() {
        timer?.invalidate()
        timer = nil
    }
    
    // MARK: - Helper Functions
    
    private func loadPuzzleData() {
        print("🔍 Loading Find Object puzzle data...")
        print("📄 Raw question data: \(puzzle.question)")
        
        guard let jsonData = puzzle.question.data(using: .utf8) else {
            errorMessage = "Invalid puzzle data format"
            isLoading = false
            return
        }
        
        do {
            let data = try JSONDecoder().decode(FindObjectPuzzleData.self, from: jsonData)
            
            // Validate image URL
            guard !data.imageUrl.isEmpty &&
                  !data.imageUrl.contains("PLACEHOLDER") &&
                  data.imageUrl.hasPrefix("http") else {
                errorMessage = "Image is still being generated. Please try again in a moment."
                isLoading = false
                return
            }
            
            puzzleData = data
            timeRemaining = data.timeLimit
            isLoading = false
            
            print("✅ Successfully loaded Find Object puzzle:")
            print("  Image URL: \(data.imageUrl)")
            print("  Discovery method: \(data.discoveryMethod)")
            print("  Total objects: \(data.totalObjects)")
            print("  Time limit: \(data.timeLimit)s")
            print("  Grid: \(data.gridConfig.cols)×\(data.gridConfig.rows)")
            
        } catch {
            print("❌ Failed to parse puzzle data: \(error)")
            errorMessage = "Failed to load puzzle: \(error.localizedDescription)"
            isLoading = false
        }
    }
    
    private func handleImageClick(at point: CGPoint, imageSize: CGSize) {
        guard let data = puzzleData, !gameCompleted else { return }
        
        let xPercent = (point.x / imageSize.width) * 100.0
        let yPercent = (point.y / imageSize.height) * 100.0
        
        print("🎯 Click at (\(xPercent)%, \(yPercent)%)")
        
        // Find unfound object types
        let unfoundObjectTypes = data.objectsToFind.filter { obj in
            !foundObjects.contains { found in
                found.name.lowercased() == obj.name.lowercased() ||
                found.name.lowercased() == (obj.objectTypeName ?? obj.name).lowercased()
            }
        }
        
        let clickedGridIndex = calculateGridIndex(x: point.x, y: point.y, imageSize: imageSize, gridConfig: data.gridConfig)
        
        print("Checking \(unfoundObjectTypes.count) unfound object types")
        
        // Store all potential matches with their distances
        struct ObjectMatch {
            let obj: DiscoveredObject
            let instance: ObjectInstance
            let distance: Double
            let matchType: String // "grid" or "distance"
        }
        
        var potentialMatches: [ObjectMatch] = []
        
        // Check each unfound object type
        for obj in unfoundObjectTypes {
            let objectTypeName = obj.objectTypeName ?? obj.name
            print("Checking object type: \(objectTypeName) (\(obj.totalInstancesOfType) instances)")
            
            let instancesToCheck = obj.allValidInstances ?? [
                ObjectInstance(
                    id: obj.id,
                    x: obj.x,
                    y: obj.y,
                    gridIndex: obj.gridIndex,
                    instanceNumber: 1,
                    confidence: obj.confidence
                )
            ]
            
            for (instIndex, instance) in instancesToCheck.enumerated() {
                // Calculate distance first
                let distance = sqrt(
                    pow(instance.x - xPercent, 2.0) +
                    pow(instance.y - yPercent, 2.0)
                )
                
                // Enhanced tolerance based on object characteristics
                let enhancedTolerance = calculateEnhancedTolerance(for: obj)
                
                // Grid-based matching with adjacent cells
                let gridMatch = checkGridMatch(
                    instanceGridIndex: instance.gridIndex,
                    clickedGridIndex: clickedGridIndex,
                    gridConfig: data.gridConfig
                )
                
                let distanceMatch = distance <= enhancedTolerance
                
                print("  Instance \(instIndex + 1): grid=\(gridMatch), distance=\(distanceMatch) (\(Int(distance))% <= \(Int(enhancedTolerance))%)")
                
                // Add to potential matches if either condition is met
                if gridMatch || distanceMatch {
                    let matchType = gridMatch ? "grid" : "distance"
                    potentialMatches.append(
                        ObjectMatch(
                            obj: obj,
                            instance: instance,
                            distance: distance,
                            matchType: matchType
                        )
                    )
                    print("    Added as potential match: \(obj.name) (distance: \(Int(distance))%, type: \(matchType))")
                }
            }
        }
        
        // Sort by distance (closest first) and pick the best match
        if let bestMatch = potentialMatches.min(by: { $0.distance < $1.distance }) {
            print("✅ Best match found: \(bestMatch.obj.name) at distance \(Int(bestMatch.distance))% (\(bestMatch.matchType) match)")
            
            let foundObject = FoundObject(
                objectId: bestMatch.obj.id,
                name: bestMatch.obj.name,
                x: xPercent,
                y: yPercent,
                foundAt: Date(),
                gridIndex: clickedGridIndex,
                confidence: bestMatch.obj.confidence,
                size: bestMatch.obj.size
            )
            
            foundObjects.append(foundObject)
            showHint = nil
            onAnswerSubmitted(true)
            
            print("✅ Found natural object \(bestMatch.obj.name) at grid cell \(clickedGridIndex) (\(bestMatch.obj.confidence) confidence)")
            
            return
        }
        
        print("❌ No natural object match found")
        wrongClicks += 1
        onAnswerSubmitted(false)
        
        // Check if too many wrong clicks
        if wrongClicks >= data.gameSettings.maxWrongClicks {
            completeGame()
        }
    }
    
    private func checkGridMatch(instanceGridIndex: Int, clickedGridIndex: Int, gridConfig: GridConfig) -> Bool {
        if instanceGridIndex == clickedGridIndex {
            return true
        }
        
        // Check adjacent grid cells (3x3 area around target)
        let instRow = instanceGridIndex / gridConfig.cols
        let instCol = instanceGridIndex % gridConfig.cols
        let clickRow = clickedGridIndex / gridConfig.cols
        let clickCol = clickedGridIndex % gridConfig.cols
        
        return abs(instRow - clickRow) <= 1 && abs(instCol - clickCol) <= 1
    }
    
    private func calculateEnhancedTolerance(for obj: DiscoveredObject) -> Double {
        var tolerance = obj.tolerance
        
        // Size-based adjustment
        switch obj.size {
        case "large": tolerance += 8.0
        case "small": tolerance += 3.0
        default: tolerance += 5.0
        }
        
        // Confidence-based adjustment
        switch obj.confidence {
        case "excellent": tolerance += 3.0
        case "fair": tolerance -= 2.0
        default: break
        }
        
        // Multiple instances bonus
        if obj.totalInstancesOfType > 1 {
            tolerance += 5.0
        }
        
        return tolerance
    }
    
    private func calculateGridIndex(x: CGFloat, y: CGFloat, imageSize: CGSize, gridConfig: GridConfig) -> Int {
        let xPercent = (x / imageSize.width) * 100.0
        let yPercent = (y / imageSize.height) * 100.0
        
        let col = min(Int((xPercent / 100.0) * Double(gridConfig.cols)), gridConfig.cols - 1)
        let row = min(Int((yPercent / 100.0) * Double(gridConfig.rows)), gridConfig.rows - 1)
        
        return max(0, row * gridConfig.cols + col)
    }
    
    private func completeGame() {
        guard !gameCompleted else { return }
        gameCompleted = true
        stopTimer()
        
        let score = calculateScore()
        print("🎉 Find Object puzzle completed! Found: \(foundObjects.count)/\(puzzleData?.totalObjects ?? 0), Score: \(score)")
        
        // Reduced delay since we already waited 1.5s for the last object feedback
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
            onNextPuzzle()
        }
    }
    
    private func calculateScore() -> Int {
        guard let data = puzzleData else { return 0 }
        
        let baseScore = (Double(foundObjects.count) / Double(data.totalObjects) * 100.0)
        let wrongClickPenalty = Double(wrongClicks * 2)
        let hintPenalty = Double(hintsUsed * 3)
        
        return max(0, Int(baseScore - wrongClickPenalty - hintPenalty))
    }
    
    private func formatTime(_ seconds: Int) -> String {
        let minutes = seconds / 60
        let remainingSeconds = seconds % 60
        return String(format: "%d:%02d", minutes, remainingSeconds)
    }
}

// MARK: - Header Component

struct FindObjectHeader: View {
    let difficulty: String
    let round: String
    let timeRemaining: Int
    let foundCount: Int
    let totalObjects: Int
    let wrongClicks: Int
    let maxWrongClicks: Int
    let viewMode: ObjectViewMode
    let discoveryMethod: String
    let gridConfig: GridConfig
    let canUseHint: Bool
    let onViewModeChange: (ObjectViewMode) -> Void
    let onHint: () -> Void
    
    var body: some View {
        VStack(spacing: 8) {
            // Top row with basic controls
            HStack {
                
                Spacer()
                
                VStack(spacing: 2) {
                    Text(difficulty.uppercased())
                        .font(.caption)
                        .fontWeight(.bold)
                        .foregroundColor(.white)
                    
                    Text(round)
                        .font(.caption2)
                        .foregroundColor(.white)
                    
                    Text("Natural Discovery")
                        .font(.caption2)
                        .foregroundColor(.cyan)
                }
                
                Spacer()
                
                Text(formatTime(timeRemaining))
                    .font(.headline)
                    .fontWeight(.bold)
                    .foregroundColor(timeRemaining < 30 ? .red : .white)
            }
            .padding(.horizontal)
            
            // View mode selector - REMOVE ZOOM BUTTON
            HStack {
                HStack(spacing: 8) {
                    ObjectViewModeButton(
                        icon: "rectangle.expand",
                        label: "Image",
                        isSelected: viewMode == .landscapeFull,
                        onClick: { onViewModeChange(.landscapeFull) }
                    )

                    ObjectViewModeButton(
                        icon: "list.bullet",
                        label: "Objects to Find",
                        isSelected: viewMode == .objectsList,
                        onClick: { onViewModeChange(.objectsList) }
                    )
                }
                .padding(6)
                .background(.ultraThinMaterial, in: Capsule())
                .overlay(
                    Capsule().stroke(Color.primary.opacity(0.08), lineWidth: 1)
                )

                Spacer()
            }
            .padding(.horizontal)
        }
        .padding(.vertical, 8)
        .background(Color.black.opacity(0.9))
    }
    
    private func formatTime(_ seconds: Int) -> String {
        let minutes = seconds / 60
        let remainingSeconds = seconds % 60
        return String(format: "%d:%02d", minutes, remainingSeconds)
    }
}

struct ObjectViewModeButton: View {
    let icon: String
    let label: String
    let isSelected: Bool
    let onClick: () -> Void

    var body: some View {
        Button(action: {
            UIImpactFeedbackGenerator(style: .light).impactOccurred()
            onClick()
        }) {
            VStack(spacing: 6) {
                Image(systemName: icon)
                    .font(.system(size: 14, weight: .semibold))
                    .symbolRenderingMode(.hierarchical)
                    .foregroundStyle(isSelected ? .white : .primary.opacity(0.85))

                Text(label)
                    .font(.caption2.weight(.semibold))
                    .foregroundColor(isSelected ? .white.opacity(0.95) : .primary.opacity(0.75))
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
            }
            .frame(minWidth: 72, minHeight: 44) // ≥44pt tap target
            .padding(.horizontal, 4)
            .background(background)
            .overlay(
                RoundedRectangle(cornerRadius: 10)
                    .strokeBorder(borderColor, lineWidth: isSelected ? 1.5 : 1)
            )
            .clipShape(RoundedRectangle(cornerRadius: 10))
            .shadow(color: isSelected ? Color.accentColor.opacity(0.35) : .clear,
                    radius: 10, x: 0, y: 6)
            .animation(.spring(response: 0.25, dampingFraction: 0.8), value: isSelected)
            .accessibilityLabel(Text(label))
            .accessibilityAddTraits(isSelected ? .isSelected : [])
        }
        .buttonStyle(PressableStyle(scale: 0.96))
    }

    private var background: some View {
        Group {
            if isSelected {
                LinearGradient(
                    colors: [Color.accentColor, Color.accentColor.opacity(0.75)],
                    startPoint: .topLeading, endPoint: .bottomTrailing
                )
            } else {
                Color(.secondarySystemBackground)
            }
        }
    }

    private var borderColor: Color {
        isSelected ? .white.opacity(0.35) : .primary.opacity(0.12)
    }
}

// MARK: - Press animation

struct PressableStyle: ButtonStyle {
    var scale: CGFloat = 0.95
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .opacity(configuration.isPressed ? 0.9 : 1)
            .scaleEffect(configuration.isPressed ? scale : 1)
    }
}

// MARK: - Landscape View Component

struct LandscapeObjectView: View {
    let puzzleData: FindObjectPuzzleData
    let foundObjects: [FoundObject]
    let showHint: DiscoveredObject?
    let gameCompleted: Bool
    let onImageClick: (CGPoint, CGSize) -> Void
    let onImageSizeChanged: (CGSize) -> Void
    
    private func getImageFrame(in containerSize: CGSize) -> CGSize {
        // For AspectFit, we need to calculate the actual image size within the container
        // Assuming square image (1024x1024 from DALL-E)
        let imageAspectRatio: CGFloat = 1.0
        let containerAspectRatio = containerSize.width / containerSize.height
        
        if containerAspectRatio > imageAspectRatio {
            // Container is wider, image height fills container
            return CGSize(width: containerSize.height * imageAspectRatio, height: containerSize.height)
        } else {
            // Container is taller, image width fills container
            return CGSize(width: containerSize.width, height: containerSize.width / imageAspectRatio)
        }
    }
    
    var body: some View {
        GeometryReader { geometry in
            ZStack {
                // Base image
                AsyncImage(url: URL(string: puzzleData.imageUrl)) { image in
                    image
                        .resizable()
                        .aspectRatio(contentMode: .fit)
                        .clipShape(RoundedRectangle(cornerRadius: 8))
                        .background(
                            GeometryReader { imageGeometry in
                                Color.clear.onAppear {
                                    onImageSizeChanged(imageGeometry.size)
                                }
                            }
                        )
                        .onTapGesture { location in
                            if !gameCompleted {
                                // Get the actual image frame within the geometry
                                let imageFrame = getImageFrame(in: geometry.size)
                                onImageClick(location, imageFrame)
                            }
                        }
                } placeholder: {
                    RoundedRectangle(cornerRadius: 8)
                        .fill(Color.gray.opacity(0.3))
                        .overlay(
                            ProgressView()
                                .progressViewStyle(CircularProgressViewStyle(tint: .white))
                        )
                }
                
                // Found object markers
                ForEach(foundObjects) { found in
                    Circle()
                        .fill(Color.green.opacity(0.8))
                        .frame(width: 30, height: 30)
                        .overlay(
                            Image(systemName: "checkmark")
                                .font(.system(size: 16, weight: .bold))
                                .foregroundColor(.white)
                        )
                        .position(
                            x: CGFloat(found.x / 100.0) * geometry.size.width,
                            y: CGFloat(found.y / 100.0) * geometry.size.height
                        )
                }
                
                // Hint marker with cropping adjustment
                if let hint = showHint {
                    let hintPosition = calculateHintPosition(hint: hint, displaySize: geometry.size)
                    if let position = hintPosition {
                        Circle()
                            .fill(Color.yellow.opacity(0.7))
                            .frame(width: 40, height: 40)
                            .overlay(
                                Image(systemName: "lightbulb.fill")
                                    .font(.system(size: 20, weight: .bold))
                                    .foregroundColor(.black)
                            )
                            .position(x: position.x, y: position.y)
                            .onAppear {
                                // Auto-hide hint after 3 seconds
                                DispatchQueue.main.asyncAfter(deadline: .now() + 3.0) {
                                    // Hint will be cleared by parent
                                }
                            }
                    }
                }
            }
        }
        .padding(8)
    }
    
    private func calculateHintPosition(hint: DiscoveredObject, displaySize: CGSize) -> CGPoint? {
        let displayAspectRatio = displaySize.width / displaySize.height
        let originalAspectRatio: CGFloat = 1.0 // DALL-E generates 1024x1024 (square)
        
        var adjustedHintX = CGFloat(hint.x / 100.0) * displaySize.width
        var adjustedHintY = CGFloat(hint.y / 100.0) * displaySize.height
        
        // If display is wider than original (cropped vertically)
        if displayAspectRatio > originalAspectRatio {
            // Image is cropped top/bottom, adjust Y coordinate
            let cropFactor = displayAspectRatio / originalAspectRatio
            let visibleHeight = displaySize.height / cropFactor
            let cropOffset = (displaySize.height - visibleHeight) / 2
            
            adjustedHintY = CGFloat(hint.y / 100.0) * visibleHeight + cropOffset
            
            // Check if hint is in visible area
            if adjustedHintY < 0 || adjustedHintY > displaySize.height {
                print("⚠️ Hint for \(hint.name) is outside visible area due to cropping")
                return nil // Don't show hint if it's outside visible area
            }
        }
        
        return CGPoint(x: adjustedHintX, y: adjustedHintY)
    }
}

// MARK: - Zoomable View Component

struct ZoomableObjectView: View {
    let imageUrl: String
    let puzzleData: FindObjectPuzzleData
    let foundObjects: [FoundObject]
    let showHint: DiscoveredObject?
    let gameCompleted: Bool
    @Binding var scale: CGFloat
    @Binding var offset: CGSize
    @Binding var imageSize: CGSize
    let onImageClick: (CGPoint, CGSize) -> Void
    
    @State private var lastScale: CGFloat = 1.0
    @State private var lastOffset = CGSize.zero
    
    var body: some View {
        GeometryReader { geometry in
            ZStack {
                AsyncImage(url: URL(string: imageUrl)) { image in
                    image
                        .resizable()
                        .aspectRatio(contentMode: .fit)
                        .clipShape(RoundedRectangle(cornerRadius: 8))
                        .scaleEffect(scale)
                        .offset(offset)
                        .background(
                            GeometryReader { imageGeometry in
                                Color.clear.onAppear {
                                    imageSize = imageGeometry.size
                                }
                            }
                        )
                        .gesture(
                            SimultaneousGesture(
                                MagnificationGesture()
                                    .onChanged { value in
                                        let newScale = lastScale * value
                                        scale = min(max(newScale, 1.0), 4.0)
                                    }
                                    .onEnded { _ in
                                        lastScale = scale
                                    },
                                DragGesture()
                                    .onChanged { value in
                                        let newOffset = CGSize(
                                            width: lastOffset.width + value.translation.width,
                                            height: lastOffset.height + value.translation.height
                                                )
                                        
                                        // Calculate bounds
                                        let scaledWidth = imageSize.width * scale
                                        let scaledHeight = imageSize.height * scale
                                        let maxOffsetX = max(0, (scaledWidth - imageSize.width) / 2)
                                        let maxOffsetY = max(0, (scaledHeight - imageSize.height) / 2)
                                        
                                        offset = CGSize(
                                            width: min(max(newOffset.width, -maxOffsetX), maxOffsetX),
                                            height: min(max(newOffset.height, -maxOffsetY), maxOffsetY)
                                        )
                                    }
                                    .onEnded { _ in
                                        lastOffset = offset
                                    }
                            )
                        )
                        .onTapGesture { location in
                            if !gameCompleted {
                                // Convert tap location to image coordinates by inverting the transform
                                let adjustedX = (location.x - offset.width) / scale
                                let adjustedY = (location.y - offset.height) / scale
                                
                                if adjustedX >= 0 && adjustedX <= imageSize.width &&
                                   adjustedY >= 0 && adjustedY <= imageSize.height {
                                    onImageClick(CGPoint(x: adjustedX, y: adjustedY), imageSize)
                                }
                            }
                        }
                } placeholder: {
                    RoundedRectangle(cornerRadius: 8)
                        .fill(Color.gray.opacity(0.3))
                        .overlay(
                            ProgressView()
                                .progressViewStyle(CircularProgressViewStyle(tint: .white))
                        )
                }
                
                // Found object markers (transformed with image)
                ForEach(foundObjects) { found in
                    Circle()
                        .fill(Color.green.opacity(0.8))
                        .frame(width: 30, height: 30)
                        .overlay(
                            Image(systemName: "checkmark")
                                .font(.system(size: 16, weight: .bold))
                                .foregroundColor(.white)
                        )
                        .position(
                            x: CGFloat(found.x / 100.0) * imageSize.width,
                            y: CGFloat(found.y / 100.0) * imageSize.height
                        )
                        .scaleEffect(scale)
                        .offset(offset)
                }
                
                // Hint marker
                if let hint = showHint {
                    Circle()
                        .fill(Color.yellow.opacity(0.7))
                        .frame(width: 40, height: 40)
                        .overlay(
                            Image(systemName: "lightbulb.fill")
                                .font(.system(size: 20, weight: .bold))
                                .foregroundColor(.black)
                        )
                        .position(
                            x: CGFloat(hint.x / 100.0) * imageSize.width,
                            y: CGFloat(hint.y / 100.0) * imageSize.height
                        )
                        .scaleEffect(scale)
                        .offset(offset)
                }
                
                // Instructions overlay when not zoomed
                if scale <= 1.2 {
                    VStack {
                        Spacer()
                        Text("Pinch to zoom • Drag to pan • Tap objects found by AI vision")
                            .font(.caption2)
                            .foregroundColor(.white)
                            .padding(8)
                            .background(
                                RoundedRectangle(cornerRadius: 8)
                                    .fill(Color.black.opacity(0.7))
                            )
                            .padding(.bottom, 20)
                    }
                }
            }
        }
        .padding(8)
    }
}

// MARK: - Objects List View Component

struct ObjectsListView: View {
    let objectsToFind: [DiscoveredObject]
    let allDiscoveredObjects: [DiscoveredObject]?
    let foundObjects: [FoundObject]
    let hintsUsed: Int
    let sceneAnalysis: String?
    let onObjectHintUsed: (DiscoveredObject) -> Void
    
    var body: some View {
        ScrollView {
            LazyVStack(spacing: 12) {
                // Header section
                VStack(alignment: .leading, spacing: 8) {
                    Text("Objects to Find")
                        .font(.title2)
                        .fontWeight(.bold)
                        .foregroundColor(.white)
                    
                    Text("These objects were naturally discovered by AI vision in the scene")
                        .font(.caption)
                        .foregroundColor(.cyan)
                    
                    if let analysis = sceneAnalysis {
                        Text(analysis)
                            .font(.caption2)
                            .foregroundColor(.gray)
                            .lineLimit(2)
                    }
                    
                    if let allObjects = allDiscoveredObjects {
                        Text("Total objects discovered: \(allObjects.count) • Selected for puzzle: \(objectsToFind.count)")
                            .font(.caption2)
                            .foregroundColor(.yellow)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal)
                
                // Objects list
                ForEach(objectsToFind) { obj in
                    NaturalObjectCard(
                        discoveredObject: obj,
                        isFound: foundObjects.contains { $0.objectId == obj.id },
                        canUseHint: hintsUsed < 3,
                        onUseHint: { onObjectHintUsed(obj) }
                    )
                }
            }
            .padding(.vertical)
        }
        .background(Color.black)
    }
}

struct NaturalObjectCard: View {
    let discoveredObject: DiscoveredObject
    let isFound: Bool
    let canUseHint: Bool
    let onUseHint: () -> Void
    
    var body: some View {
        VStack(spacing: 12) {
            HStack {
                VStack(alignment: .leading, spacing: 4) {
                    Text(discoveredObject.name.capitalized)
                        .font(.headline)
                        .fontWeight(.bold)
                        .foregroundColor(.white)
                    
                    HStack(spacing: 12) {
                        ConfidenceBadge(confidence: discoveredObject.confidence)
                        SizeBadge(size: discoveredObject.size)
                        VisibilityBadge(visibility: discoveredObject.visibility)
                    }
                    
                    HStack(spacing: 12) {
                        Text("Grid: \(discoveredObject.gridIndex) (\(discoveredObject.gridRow), \(discoveredObject.gridCol))")
                            .font(.caption2)
                            .foregroundColor(.gray)
                        
                        if discoveredObject.totalInstancesOfType > 1 {
                            Text("\(discoveredObject.totalInstancesOfType) instances available")
                                .font(.caption2)
                                .fontWeight(.bold)
                                .foregroundColor(.orange)
                        }
                    }
                }
                
                Spacer()
                
                if isFound {
                    Image(systemName: "checkmark.circle.fill")
                        .font(.title2)
                        .foregroundColor(.green)
                } else {
                    DifficultyBadge(difficulty: discoveredObject.difficulty)
                }
            }
            
            if !discoveredObject.description.isEmpty {
                Text(discoveredObject.description)
                    .font(.caption)
                    .foregroundColor(.gray)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            
            if !isFound {
                Button(action: onUseHint) {
                    HStack {
                        Image(systemName: "lightbulb.fill")
                            .foregroundColor(.black)
                        Text("Show Hint")
                            .fontWeight(.semibold)
                            .foregroundColor(.black)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 10)
                    .background(
                        RoundedRectangle(cornerRadius: 8)
                            .fill(canUseHint ? Color.yellow.opacity(0.8) : Color.gray.opacity(0.5))
                    )
                }
                .disabled(!canUseHint)
            }
        }
        .padding()
        .background(
            RoundedRectangle(cornerRadius: 12)
                .fill(isFound ? Color.green.opacity(0.3) : Color.gray.opacity(0.2))
        )
        .padding(.horizontal)
    }
}

// MARK: - Badge Components

struct ConfidenceBadge: View {
    let confidence: String
    
    var body: some View {
        Text("\(confidence) confidence")
            .font(.caption2)
            .padding(.horizontal, 6)
            .padding(.vertical, 2)
            .background(
                RoundedRectangle(cornerRadius: 4)
                    .fill(confidenceColor.opacity(0.8))
            )
            .foregroundColor(.white)
    }
    
    private var confidenceColor: Color {
        switch confidence {
        case "excellent": return .green
        case "good": return .yellow
        case "fair": return .orange
        default: return .gray
        }
    }
}

struct SizeBadge: View {
    let size: String
    
    var body: some View {
        Text("\(size) size")
            .font(.caption2)
            .padding(.horizontal, 6)
            .padding(.vertical, 2)
            .background(
                RoundedRectangle(cornerRadius: 4)
                    .fill(Color.cyan.opacity(0.8))
            )
            .foregroundColor(.white)
    }
}

struct VisibilityBadge: View {
    let visibility: String
    
    var body: some View {
        Text("\(visibility) visibility")
            .font(.caption2)
            .padding(.horizontal, 6)
            .padding(.vertical, 2)
            .background(
                RoundedRectangle(cornerRadius: 4)
                    .fill(Color.purple.opacity(0.8))
            )
            .foregroundColor(.white)
    }
}

struct DifficultyBadge: View {
    let difficulty: String
    
    var body: some View {
        Text(difficulty)
            .font(.caption)
            .fontWeight(.semibold)
            .padding(.horizontal, 8)
            .padding(.vertical, 4)
            .background(
                RoundedRectangle(cornerRadius: 8)
                    .fill(difficultyColor)
            )
            .foregroundColor(.white)
    }
    
    private var difficultyColor: Color {
        switch difficulty {
        case "easy": return .green
        case "medium": return .yellow
        case "hard": return .red
        default: return .gray
        }
    }
}

// MARK: - Loading and Error Views

struct FLoadingView: View {
    let message: String
    
    var body: some View {
        VStack(spacing: 16) {
            ProgressView()
                .progressViewStyle(CircularProgressViewStyle(tint: .white))
                .scaleEffect(1.5)
            
            Text(message)
                .font(.headline)
                .foregroundColor(.white)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.black)
    }
}

struct FoErrorView: View {
    let message: String
    let onRetry: () -> Void
    let onSkip: () -> Void
    
    var body: some View {
        VStack(spacing: 20) {
            Image(systemName: "exclamationmark.triangle.fill")
                .font(.system(size: 40))
                .foregroundColor(.red)
            
            Text("Error")
                .font(.title)
                .fontWeight(.bold)
                .foregroundColor(.red)
            
            Text(message)
                .font(.body)
                .foregroundColor(.white)
                .multilineTextAlignment(.center)
                .padding(.horizontal)
            
            HStack(spacing: 16) {
                Button("Retry") {
                    onRetry()
                }
                .padding()
                .background(Color.blue)
                .foregroundColor(.white)
                .cornerRadius(8)
                
                Button("Skip") {
                    onSkip()
                }
                .padding()
                .background(Color.gray)
                .foregroundColor(.white)
                .cornerRadius(8)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.black)
    }
}

// MARK: - Puzzle Extension for Find Object Data

extension Puzzle {
    var findObjectPuzzleData: FindObjectPuzzleData? {
        guard let data = question.data(using: .utf8) else {
            print("❌ FIND_OBJECT: Failed to convert question to data")
            return nil
        }
        
        do {
            let puzzleData = try JSONDecoder().decode(FindObjectPuzzleData.self, from: data)
            print("✅ FIND_OBJECT: Successfully parsed find object data")
            return puzzleData
        } catch {
            print("❌ FIND_OBJECT: Failed to decode find object data: \(error)")
            return nil
        }
    }
}
