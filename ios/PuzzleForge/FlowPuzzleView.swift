//
//  FlowPuzzleView.swift
//  RiddleVerse
//
//  Created by Sushanth Tiruvaipati on 10/17/25.
//

import SwiftUI

//
//  FlowPuzzleModels.swift
//  PuzzleForge
//
//  Created by Assistant
//

import SwiftUI

// MARK: - Core Models
struct FlowPoint: Equatable, Hashable, Codable {
    let x: Int
    let y: Int
}

struct FlowPair: Identifiable, Codable {
    let id: String
    let color: String // Hex color
    let label: String
    let start: FlowPoint
    let end: FlowPoint
    let length: Int?
    
    var swiftUIColor: Color {
        // Use existing Color(hex:) initializer with fallback
        Color(hex: color) ?? .gray
    }
}

struct FlowPath {
    var points: [FlowPoint] = []
    var isComplete: Bool = false
}

enum MoveResult {
    case valid
    case invalidBounds
    case notAdjacent
    case pathConflict
    case endpointConflict
    case backtrack
}

// MARK: - Puzzle Data Structure (matches server response)
struct FlowPuzzleData: Codable {
    let gridSize: Int
    let pairs: [FlowPair]
    let difficulty: String
    let totalCells: Int?
    let instructions: String?
    let timeLimit: Int
    let metadata: FlowMetadata?
}

struct FlowMetadata: Codable {
    let pathLength: Int?
    let totalBends: Int?
    let segmentCount: Int?
    let minDist: Int?
    let attempt: Int?
    let generatedAt: String?
    let algorithm: String?
    let pathMethod: String?
}

// MARK: - Solution Structure (from answer field)
struct FlowSolution: Codable {
    let solution: [String: [FlowPoint]]
    let totalPairs: Int?
    let gridSize: Int?
    let pathLengths: [Int]?
    let coverage: String?
}

// MARK: - Extensions
extension Int {
    func clamped(to range: Range<Int>) -> Int {
        return Swift.max(range.lowerBound, Swift.min(self, range.upperBound - 1))
    }
}

struct FlowPuzzleView: View {
    let puzzle: Puzzle
    let onComplete: (Bool, Int) -> Void
    let onBack: () -> Void
    
    @State private var puzzleData: FlowPuzzleData?
    @State private var solution: FlowSolution?
    @State private var paths: [String: FlowPath] = [:]
    @State private var currentPath: String?
    @State private var isDragging = false
    @State private var dragStart: FlowPoint?
    @State private var timeLeft: Int = 120
    @State private var moves = 0
    @State private var isCompleted = false
    @State private var showConflictAlert = false
    @State private var conflictMessage = ""
    @State private var showingSolution = false
    @State private var score = 0
    
    @State private var timer: Timer?
    
    var body: some View {
        ZStack {
            Color(red: 0.16, green: 0.16, blue: 0.23)
                .ignoresSafeArea()
            
            VStack(spacing: 16) {
                // Header
                headerView
                
                // Game Info
                gameInfoView
                
                Spacer().frame(height: 20)
                
                // Game Canvas
                if let data = puzzleData {
                    gameCanvasView(data: data)
                } else {
                    ProgressView()
                        .progressViewStyle(CircularProgressViewStyle(tint: .white))
                }
                
                Spacer().frame(height: 20)
                
                // Control Buttons
                controlButtonsView
                
                Spacer()
            }
            .padding()
            .padding(.top, 32)
            
            // Completion overlay
            if isCompleted {
                completionOverlay
            }
            
            if showingSolution {
                solutionOverlay
            }
        }
        .navigationBarHidden(true)
        .onAppear {
            loadPuzzleData()
            startTimer()
        }
        .onDisappear {
            timer?.invalidate()
        }
        .alert("Path Conflict!", isPresented: $showConflictAlert) {
            Button("OK", role: .cancel) { }
        } message: {
            Text(conflictMessage)
        }
    }
    
    // MARK: - Header View
    private var headerView: some View {
        HStack {
            Button(action: onBack) {
                Image(systemName: "arrow.left")
                    .foregroundColor(.white)
                    .font(.title2)
            }
            
            Spacer()
            
            VStack {
                Text(formatTime(timeLeft))
                    .font(.system(size: 24, weight: .bold))
                    .foregroundColor(.white)
                
                Text((puzzleData?.difficulty ?? "EASY").uppercased())
                    .font(.system(size: 14))
                    .foregroundColor(.gray)
            }
            
            Spacer()
            
            Button(action: resetPuzzle) {
                Image(systemName: "arrow.clockwise")
                    .foregroundColor(.white)
                    .font(.title2)
            }
        }
    }
    
    // MARK: - Game Info View
    private var gameInfoView: some View {
        HStack {
            Text("Moves: \(moves)")
                .foregroundColor(.white)
            
            Spacer()
            
            let completed = paths.values.filter { $0.isComplete }.count
            let total = puzzleData?.pairs.count ?? 0
            Text("Connected: \(completed)/\(total)")
                .foregroundColor(.white)
        }
        .font(.system(size: 16))
    }
    
    // MARK: - Game Canvas View
    private func gameCanvasView(data: FlowPuzzleData) -> some View {
        GeometryReader { geometry in
            let size = min(geometry.size.width, geometry.size.height)
            let cellSize = size / CGFloat(data.gridSize)
            
            ZStack {
                // Grid background
                ForEach(0..<data.gridSize, id: \.self) { y in
                    ForEach(0..<data.gridSize, id: \.self) { x in
                        Rectangle()
                            .fill(Color(red: 0.23, green: 0.23, blue: 0.35))
                            .frame(width: cellSize - 2, height: cellSize - 2)
                            .position(
                                x: CGFloat(x) * cellSize + cellSize / 2,
                                y: CGFloat(y) * cellSize + cellSize / 2
                            )
                    }
                }
                
                // Paths
                ForEach(data.pairs) { pair in
                    if let path = paths[pair.id], path.points.count > 1 {
                        PathShape(points: path.points, cellSize: cellSize)
                            .stroke(pair.swiftUIColor, lineWidth: cellSize * 0.3)
                        
                        // Dots along path
                        ForEach(path.points.filter { $0 != pair.start && $0 != pair.end }, id: \.self) { point in
                            Circle()
                                .fill(pair.swiftUIColor)
                                .frame(width: cellSize * 0.15, height: cellSize * 0.15)
                                .position(
                                    x: CGFloat(point.x) * cellSize + cellSize / 2,
                                    y: CGFloat(point.y) * cellSize + cellSize / 2
                                )
                        }
                    }
                }
                
                // Endpoints
                ForEach(data.pairs) { pair in
                    EndpointView(pair: pair, cellSize: cellSize)
                }
            }
            .frame(width: size, height: size)
            .background(Color(red: 0.10, green: 0.10, blue: 0.18))
            .cornerRadius(12)
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { value in
                        handleDrag(value: value, cellSize: cellSize, gridSize: data.gridSize, pairs: data.pairs)
                    }
                    .onEnded { _ in
                        handleDragEnd(pairs: data.pairs)
                    }
            )
        }
        .aspectRatio(1, contentMode: .fit)
    }
    
    // MARK: - Control Buttons
    private var controlButtonsView: some View {
        HStack(spacing: 16) {
            Button(action: clearAll) {
                Text("Clear All")
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .padding()
                    .background(Color(red: 0.29, green: 0.56, blue: 0.89))
                    .cornerRadius(8)
            }
            
            Button(action: giveUp) {
                Text("Give Up")
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .padding()
                    .background(Color(red: 1.0, green: 0.54, blue: 0.31))
                    .cornerRadius(8)
            }
            
            Button(action: {
                onComplete(isCompleted, score)
            }) {
                Text("Next")
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .padding()
                    .background(Color(red: 0.31, green: 0.78, blue: 0.47))
                    .cornerRadius(8)
            }
        }
    }
    
    // MARK: - Overlays
    private var completionOverlay: some View {
        VStack(spacing: 16) {
            Text("🎉 Puzzle Completed!")
                .font(.system(size: 24, weight: .bold))
                .foregroundColor(.white)
            
            Text("Score: \(score)")
                .font(.system(size: 20))
                .foregroundColor(.white)
        }
        .padding(32)
        .background(Color(red: 0.30, green: 0.69, blue: 0.31))
        .cornerRadius(16)
    }
    
    private var solutionOverlay: some View {
        VStack(spacing: 16) {
            Text("Solution Shown")
                .font(.system(size: 20, weight: .bold))
                .foregroundColor(.white)
            
            Text("Moving to next puzzle...")
                .font(.system(size: 16))
                .foregroundColor(.white)
        }
        .padding(32)
        .background(Color(red: 1.0, green: 0.54, blue: 0.31))
        .cornerRadius(16)
    }
    
    // MARK: - Helper Functions
    private func loadPuzzleData() {
        print("🔵 FLOW: Loading puzzle data")
        print("🔵 FLOW: Question: \(puzzle.question)")
        print("🔵 FLOW: Answer: \(puzzle.answer)")
        
        // Parse question field for puzzle data
        if let questionData = puzzle.question.data(using: .utf8) {
            do {
                let data = try JSONDecoder().decode(FlowPuzzleData.self, from: questionData)
                self.puzzleData = data
                self.timeLeft = data.timeLimit / 1000
                print("✅ FLOW: Successfully parsed puzzle data")
                print("✅ FLOW: Grid size: \(data.gridSize)")
                print("✅ FLOW: Pairs: \(data.pairs.count)")
            } catch {
                print("❌ FLOW: Failed to parse puzzle data: \(error)")
            }
        }
        
        // Parse answer field for solution
        if let answerData = puzzle.answer.data(using: .utf8) {
            do {
                let sol = try JSONDecoder().decode(FlowSolution.self, from: answerData)
                self.solution = sol
                print("✅ FLOW: Successfully parsed solution")
                print("✅ FLOW: Solution pairs: \(sol.solution.keys.count)")
            } catch {
                print("❌ FLOW: Failed to parse solution: \(error)")
            }
        }
    }
    
    private func startTimer() {
        timer = Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { _ in
            if timeLeft > 0 && !isCompleted {
                timeLeft -= 1
            } else if timeLeft <= 0 && !isCompleted {
                showingSolution = true
                showSolution()
                DispatchQueue.main.asyncAfter(deadline: .now() + 3) {
                    onComplete(false, 0)
                }
            }
        }
    }
    
    private func handleDrag(value: DragGesture.Value, cellSize: CGFloat, gridSize: Int, pairs: [FlowPair]) {
        let gridPos = offsetToGrid(offset: value.location, cellSize: cellSize, gridSize: gridSize)
        
        if !isDragging {
            // Start drag
            if let pair = pairs.first(where: { $0.start == gridPos || $0.end == gridPos }) {
                currentPath = pair.id
                isDragging = true
                dragStart = gridPos
                paths[pair.id] = FlowPath(points: [gridPos])
                moves += 1
            }
        } else if let pathId = currentPath {
            // Continue drag
            guard var path = paths[pathId] else { return }
            
            if gridPos != path.points.last {
                let moveResult = checkMove(
                    newPoint: gridPos,
                    currentPath: path,
                    flowPairs: pairs,
                    allPaths: paths,
                    currentPairId: pathId,
                    gridSize: gridSize
                )
                
                switch moveResult {
                case .valid:
                    path.points.append(gridPos)
                    paths[pathId] = path
                    
                case .pathConflict:
                    path.points = [dragStart!]
                    paths[pathId] = path
                    conflictMessage = "Path crossed another line! Starting over."
                    showConflictAlert = true
                    
                case .endpointConflict:
                    conflictMessage = "Cannot cross through another pair's endpoint!"
                    showConflictAlert = true
                    
                default:
                    break
                }
            }
        }
    }
    
    private func handleDragEnd(pairs: [FlowPair]) {
        if isDragging, let pathId = currentPath {
            if var path = paths[pathId],
               let pair = pairs.first(where: { $0.id == pathId }) {
                path.isComplete = path.points.contains(pair.start) && path.points.contains(pair.end)
                paths[pathId] = path
                
                // Check if all complete
                let completed = paths.values.filter { $0.isComplete }.count
                if completed == pairs.count {
                    isCompleted = true
                    score = calculateScore()
                    DispatchQueue.main.asyncAfter(deadline: .now() + 2) {
                        onComplete(true, score)
                    }
                }
            }
        }
        
        isDragging = false
        currentPath = nil
        dragStart = nil
    }
    
    private func offsetToGrid(offset: CGPoint, cellSize: CGFloat, gridSize: Int) -> FlowPoint {
        let x = Int(offset.x / cellSize).clamped(to: 0..<gridSize)
        let y = Int(offset.y / cellSize).clamped(to: 0..<gridSize)
        return FlowPoint(x: x, y: y)
    }
    
    private func checkMove(
        newPoint: FlowPoint,
        currentPath: FlowPath,
        flowPairs: [FlowPair],
        allPaths: [String: FlowPath],
        currentPairId: String,
        gridSize: Int
    ) -> MoveResult {
        // Check bounds
        if newPoint.x < 0 || newPoint.x >= gridSize || newPoint.y < 0 || newPoint.y >= gridSize {
            return .invalidBounds
        }
        
        guard !currentPath.points.isEmpty else { return .valid }
        
        let lastPoint = currentPath.points.last!
        let distance = abs(newPoint.x - lastPoint.x) + abs(newPoint.y - lastPoint.y)
        
        // Only adjacent moves
        if distance != 1 { return .notAdjacent }
        
        // No backtracking
        if currentPath.points.count > 1 && currentPath.points[currentPath.points.count - 2] == newPoint {
            return .backtrack
        }
        
        let currentPair = flowPairs.first { $0.id == currentPairId }
        let isCurrentPairEndpoint = currentPair?.start == newPoint || currentPair?.end == newPoint
        
        // Check other pair endpoints
        for pair in flowPairs where pair.id != currentPairId {
            if pair.start == newPoint || pair.end == newPoint {
                return .endpointConflict
            }
        }
        
        // Check path conflicts
        if !isCurrentPairEndpoint {
            for (pairId, path) in allPaths where pairId != currentPairId {
                if path.points.contains(newPoint) {
                    return .pathConflict
                }
            }
        }
        
        return .valid
    }
    
    private func resetPuzzle() {
        paths.removeAll()
        moves = 0
        isCompleted = false
        if let data = puzzleData {
            timeLeft = data.timeLimit / 1000
        }
    }
    
    private func clearAll() {
        paths.removeAll()
        moves = 0
    }
    
    private func giveUp() {
        showingSolution = true
        showSolution()
        score = 0
        DispatchQueue.main.asyncAfter(deadline: .now() + 3) {
            onComplete(false, 0)
        }
    }
    
    private func showSolution() {
        guard let sol = solution else {
            print("❌ FLOW: No solution available")
            return
        }
        
        paths.removeAll()
        
        for (pairId, solutionPath) in sol.solution {
            var path = FlowPath(points: solutionPath)
            path.isComplete = true
            paths[pairId] = path
            print("✅ FLOW: Loaded solution for \(pairId): \(solutionPath.count) points")
        }
    }
    
    private func calculateScore() -> Int {
        guard let data = puzzleData else { return 0 }
        
        let baseScore: Int
        switch data.difficulty.lowercased() {
        case "easy": baseScore = 100
        case "medium": baseScore = 200
        case "hard": baseScore = 300
        default: baseScore = 150
        }
        
        let timeBonus = timeLeft * 2
        let movesPenalty = moves * 1
        
        return max(0, baseScore + timeBonus - movesPenalty)
    }
    
    private func formatTime(_ seconds: Int) -> String {
        let minutes = seconds / 60
        let secs = seconds % 60
        return String(format: "%d:%02d", minutes, secs)
    }
}

// MARK: - Supporting Views
struct PathShape: Shape {
    let points: [FlowPoint]
    let cellSize: CGFloat
    
    func path(in rect: CGRect) -> Path {
        var path = Path()
        
        guard points.count > 1 else { return path }
        
        let firstPoint = CGPoint(
            x: CGFloat(points[0].x) * cellSize + cellSize / 2,
            y: CGFloat(points[0].y) * cellSize + cellSize / 2
        )
        path.move(to: firstPoint)
        
        for i in 1..<points.count {
            let point = CGPoint(
                x: CGFloat(points[i].x) * cellSize + cellSize / 2,
                y: CGFloat(points[i].y) * cellSize + cellSize / 2
            )
            path.addLine(to: point)
        }
        
        return path
    }
}

struct EndpointView: View {
    let pair: FlowPair
    let cellSize: CGFloat
    
    var body: some View {
        Group {
            // Start endpoint
            ZStack {
                Circle()
                    .stroke(Color.white, lineWidth: 3)
                    .frame(width: cellSize * 0.42, height: cellSize * 0.42)
                
                Circle()
                    .fill(pair.swiftUIColor)
                    .frame(width: cellSize * 0.4, height: cellSize * 0.4)
                
                Text(pair.label)
                    .font(.system(size: cellSize * 0.3, weight: .bold))
                    .foregroundColor(.white)
            }
            .position(
                x: CGFloat(pair.start.x) * cellSize + cellSize / 2,
                y: CGFloat(pair.start.y) * cellSize + cellSize / 2
            )
            
            // End endpoint
            ZStack {
                Circle()
                    .stroke(Color.white, lineWidth: 3)
                    .frame(width: cellSize * 0.42, height: cellSize * 0.42)
                
                Circle()
                    .fill(pair.swiftUIColor)
                    .frame(width: cellSize * 0.4, height: cellSize * 0.4)
                
                Text(pair.label)
                    .font(.system(size: cellSize * 0.3, weight: .bold))
                    .foregroundColor(.white)
            }
            .position(
                x: CGFloat(pair.end.x) * cellSize + cellSize / 2,
                y: CGFloat(pair.end.y) * cellSize + cellSize / 2
            )
        }
    }
}
