//
//  WaldoPuzzleView.swift
//  PuzzleForge
//

import SwiftUI

// MARK: - Data Models
struct WaldoPuzzleData: Codable {
    let puzzleId: String
    let theme: String
    let description: String
    let imageUrl: String
    let imageWidth: Int
    let imageHeight: Int
    let gridConfig: WaldoGridConfig
    let hiddenObjects: [WaldoHiddenObject]
    let totalObjects: Int
    let timeLimit: Int
    let gameSettings: WaldoGameSettings
    let imageStatus: String
    let generationMethod: String
}

struct WaldoGridConfig: Codable {
    let rows: Int
    let cols: Int
    let totalCells: Int
}

struct WaldoHiddenObject: Codable {
    let id: Int
    let name: String
    let description: String
    let gridCell: Int
    let gridRow: Int?
    let gridCol: Int?
    let xPercent: Float?
    let yPercent: Float?
    let confidence: String
    let location: String
    let hint: String
    let difficulty: String
    let detectionMethod: String?
    var found: Bool
    var foundAt: Int?
}

struct WaldoGameSettings: Codable {
    let allowHints: Bool
    let showProgress: Bool
    let highlightFound: Bool
    let gridInteraction: Bool
}

struct WaldoFoundObject: Identifiable {
    let id: Int
    let name: String
    let x: CGFloat
    let y: CGFloat
    let foundAt: Date
    let gridCell: Int
    let detectionMethod: String
}

enum WaldoViewMode {
    case zoomableView
    case objectsList
}

// MARK: - Main View
struct WaldoPuzzleView: View {
    let puzzle: Puzzle
    let onBack: () -> Void
    let onComplete: (Bool, Bool, Int) -> Void
    
    @State private var puzzleData: WaldoPuzzleData?
    @State private var hiddenObjects: [WaldoHiddenObject] = []
    @State private var foundObjects: [WaldoFoundObject] = []
    @State private var wrongClicks: Int = 0
    @State private var hintsUsed: Int = 0
    @State private var timeRemaining: Int = 120
    @State private var gameCompleted: Bool = false
    @State private var showHint: WaldoHiddenObject?
    @State private var viewMode: WaldoViewMode = .zoomableView
    @State private var isLoading: Bool = true
    @State private var errorMessage: String?
    
    private let timer = Timer.publish(every: 1, on: .main, in: .common).autoconnect()
    
    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            
            if isLoading {
                LoadingView(message: "Loading Waldo puzzle...")
            } else if let error = errorMessage {
                ErrorView(message: error, retryAction: onBack)
            } else if let data = puzzleData {
                puzzleContent(data: data)
            }
        }
        .onAppear(perform: parsePuzzle)
        .onReceive(timer) { _ in
            if timeRemaining > 0 && !gameCompleted {
                timeRemaining -= 1
            } else if timeRemaining == 0 && !gameCompleted {
                completeGame()
            }
        }
    }
    
    private func puzzleContent(data: WaldoPuzzleData) -> some View {
        VStack(spacing: 0) {
            // Header
            headerSection(data: data)
            
            // Main content based on view mode
            if viewMode == .zoomableView {
                zoomableImageView(data: data)
            } else {
                objectsListView(data: data)
            }
            
            // Progress bar
            if data.gameSettings.showProgress {
                progressBar(data: data)
            }
        }
    }
    
    private func headerSection(data: WaldoPuzzleData) -> some View {
        VStack(spacing: 8) {
            // Top row
            HStack {
                Button(action: {
                    print("🔙 BACK: Waldo back button tapped")
                    onBack()
                }) {
                    Image(systemName: "arrow.left")
                        .foregroundColor(.white)
                        .padding()
                }
                
                Spacer()
                
                VStack {
                    Text(puzzle.difficulty.uppercased() ?? "MEDIUM")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundColor(.white)
                    
                    Text("ENHANCED WALDO")
                        .font(.system(size: 8, weight: .bold))
                        .foregroundColor(.red)
                }
                
                Spacer()
                
                Text(formatTime(timeRemaining))
                    .font(.system(size: 14, weight: .bold))
                    .foregroundColor(timeRemaining < 30 ? .red : .white)
                    .padding()
            }
            
            // Theme and description
            VStack(alignment: .leading, spacing: 4) {
                Text(data.theme)
                    .font(.system(size: 16, weight: .bold))
                    .foregroundColor(.white)
                
                Text(data.description)
                    .font(.system(size: 12))
                    .foregroundColor(.gray)
            }
            .padding(.horizontal)
            
            // View mode selector
            HStack {
                HStack(spacing: 8) {
                    viewModeButton(
                        icon: "photo",
                        label: "Image",
                        isSelected: viewMode == .zoomableView,
                        action: { viewMode = .zoomableView }
                    )
                    
                    viewModeButton(
                        icon: "list.bullet",
                        label: "Objects",
                        isSelected: viewMode == .objectsList,
                        action: { viewMode = .objectsList }
                    )
                }
                
                Spacer()
                
                HStack(spacing: 8) {
                    Text("\(foundObjects.count)/\(data.totalObjects)")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundColor(.white)
                    
                    Button(action: useHint) {
                        Image(systemName: "lightbulb.fill")
                            .foregroundColor(canUseHint ? .yellow : .gray)
                    }
                    .disabled(!canUseHint)
                }
            }
            .padding(.horizontal)
        }
        .background(Color.black)
    }
    
    private func viewModeButton(icon: String, label: String, isSelected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            VStack(spacing: 2) {
                Image(systemName: icon)
                    .font(.system(size: 16))
                Text(label)
                    .font(.system(size: 8))
            }
            .foregroundColor(.white)
            .frame(width: 70, height: 36)
            .background(isSelected ? Color.red : Color.gray)
            .cornerRadius(8)
        }
    }
    
    private func zoomableImageView(data: WaldoPuzzleData) -> some View {
        WaldoZoomableImage(
            imageUrl: data.imageUrl,
            imageWidth: data.imageWidth,
            imageHeight: data.imageHeight,
            hiddenObjects: hiddenObjects,
            foundObjects: foundObjects,
            showHint: showHint,
            gridConfig: data.gridConfig,
            onTap: handleImageTap
        )
    }
    
    private func objectsListView(data: WaldoPuzzleData) -> some View {
        ScrollView {
            VStack(spacing: 12) {
                Text("Hidden Objects")
                    .font(.system(size: 18, weight: .bold))
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal)
                
                ForEach(hiddenObjects, id: \.id) { obj in
                    WaldoObjectCard(
                        object: obj,
                        canUseHint: canUseHint,
                        onUseHint: {
                            showHint = obj
                            hintsUsed += 1
                            viewMode = .zoomableView
                        }
                    )
                }
            }
            .padding()
        }
    }
    
    private func progressBar(data: WaldoPuzzleData) -> some View {
        VStack(spacing: 8) {
            ProgressView(value: Double(foundObjects.count), total: Double(data.totalObjects))
                .tint(.red)
                .frame(height: 6)
            
            HStack {
                Text("Found: \(foundObjects.count)/\(data.totalObjects)")
                    .font(.system(size: 12))
                    .foregroundColor(.white)
                
                Spacer()
                
                Text("Wrong: \(wrongClicks)")
                    .font(.system(size: 12))
                    .foregroundColor(wrongClicks > 5 ? .red : .white)
                
                Spacer()
                
                Text("Hints: \(hintsUsed)/3")
                    .font(.system(size: 12))
                    .foregroundColor(.yellow)
            }
        }
        .padding()
        .background(Color.black)
    }
    
    // MARK: - Helper Functions
    private var canUseHint: Bool {
        guard let data = puzzleData else { return false }
        return data.gameSettings.allowHints && hintsUsed < 3
    }
    
    private func parsePuzzle() {
        guard let jsonData = puzzle.question.data(using: .utf8) else {
            errorMessage = "Failed to parse puzzle data"
            isLoading = false
            return
        }
        
        do {
            let decoder = JSONDecoder()
            let data = try decoder.decode(WaldoPuzzleData.self, from: jsonData)
            
            // Validate image URL
            guard !data.imageUrl.isEmpty,
                  data.imageUrl.hasPrefix("http"),
                  !data.imageUrl.contains("PLACEHOLDER") else {
                errorMessage = "Image is still being generated. Please try again later."
                isLoading = false
                return
            }
            
            puzzleData = data
            hiddenObjects = data.hiddenObjects
            timeRemaining = data.timeLimit
            isLoading = false
            
            print("✅ Waldo puzzle loaded: \(data.theme)")
        } catch {
            errorMessage = "Failed to load puzzle: \(error.localizedDescription)"
            isLoading = false
            print("❌ Parse error: \(error)")
        }
    }
    
    private func useHint() {
        guard let data = puzzleData else { return }
        
        let unfoundObjects = hiddenObjects.filter { !$0.found }
        if let firstUnfound = unfoundObjects.first {
            showHint = firstUnfound
            hintsUsed += 1
            viewMode = .zoomableView
            
            // Auto-hide hint after 5 seconds
            DispatchQueue.main.asyncAfter(deadline: .now() + 5) {
                showHint = nil
            }
        }
    }
    
    private func handleImageTap(at point: CGPoint, imageSize: CGSize) {
        guard let data = puzzleData else { return }
        
        let xPercent = Float((point.x / imageSize.width) * 100)
        let yPercent = Float((point.y / imageSize.height) * 100)
        
        let gridCell = getGridCell(x: point.x, y: point.y, imageSize: imageSize, gridConfig: data.gridConfig)
        
        print("🎯 Tap at: (\(xPercent)%, \(yPercent)%) - Grid cell: \(gridCell)")
        
        // Check all unfound objects
        for (index, obj) in hiddenObjects.enumerated() where !obj.found {
            var isMatch = false
            var detectionMethod = ""
            
            // Try percentage-based detection first
            if let objX = obj.xPercent, let objY = obj.yPercent {
                let xDiff = abs(xPercent - objX)
                let yDiff = abs(yPercent - objY)
                
                if xDiff <= 8 && yDiff <= 8 {
                    isMatch = true
                    detectionMethod = "percentage_match"
                }
            }
            
            // Try grid-based detection
            if !isMatch {
                if obj.gridCell == gridCell {
                    isMatch = true
                    detectionMethod = "grid_exact_match"
                } else {
                    let objRow = obj.gridRow ?? (obj.gridCell / data.gridConfig.cols)
                    let objCol = obj.gridCol ?? (obj.gridCell % data.gridConfig.cols)
                    let clickRow = gridCell / data.gridConfig.cols
                    let clickCol = gridCell % data.gridConfig.cols
                    
                    let rowDiff = abs(objRow - clickRow)
                    let colDiff = abs(objCol - clickCol)
                    
                    if rowDiff <= 2 && colDiff <= 2 {
                        isMatch = true
                        detectionMethod = "grid_adjacent_match"
                    }
                }
            }
            
            if isMatch {
                print("✅ FOUND: Marking object \(obj.id) - \(obj.name) as found")
                
                // Create the found object first
                let foundObj = WaldoFoundObject(
                    id: obj.id,
                    name: obj.name,
                    x: point.x,
                    y: point.y,
                    foundAt: Date(),
                    gridCell: gridCell,
                    detectionMethod: detectionMethod
                )
                
                // Add to found objects list
                foundObjects.append(foundObj)
                
                // Update the hidden objects array - make a copy and modify it
                var updatedHiddenObjects = hiddenObjects
                updatedHiddenObjects[index].found = true
                updatedHiddenObjects[index].foundAt = Int(Date().timeIntervalSince1970)
                hiddenObjects = updatedHiddenObjects
                
                showHint = nil
                
                print("✅ Updated hiddenObjects: object \(obj.id) marked as found")
                print("📊 Found objects count: \(foundObjects.count)")
                print("📊 Hidden objects marked found: \(hiddenObjects.filter { $0.found }.count)")
                
                // Check completion
                if foundObjects.count >= data.totalObjects {
                    completeGame()
                }
                
                return
            }
        }
        
        // Wrong click
        wrongClicks += 1
        print("❌ Wrong click! Total: \(wrongClicks)")
    }
    
    private func getGridCell(x: CGFloat, y: CGFloat, imageSize: CGSize, gridConfig: WaldoGridConfig) -> Int {
        let cellWidth = imageSize.width / CGFloat(gridConfig.cols)
        let cellHeight = imageSize.height / CGFloat(gridConfig.rows)
        
        let col = min(Int(x / cellWidth), gridConfig.cols - 1)
        let row = min(Int(y / cellHeight), gridConfig.rows - 1)
        
        return row * gridConfig.cols + col
    }
    
    private func completeGame() {
        guard let data = puzzleData else { return }
        gameCompleted = true
        
        let score = calculateScore(data: data)
        let isComplete = foundObjects.count >= data.totalObjects
        
        onComplete(isComplete, isComplete, score)
    }
    
    private func calculateScore(data: WaldoPuzzleData) -> Int {
        let baseScore = Int((Double(foundObjects.count) / Double(data.totalObjects)) * 100)
        let wrongClickPenalty = wrongClicks * 3
        let hintPenalty = hintsUsed * 5
        let timeBonus = timeRemaining / 10
        
        return max(0, baseScore - wrongClickPenalty - hintPenalty + timeBonus)
    }
    
    private func formatTime(_ seconds: Int) -> String {
        let minutes = seconds / 60
        let secs = seconds % 60
        return String(format: "%d:%02d", minutes, secs)
    }
}

// MARK: - Zoomable Image View
struct WaldoZoomableImage: View {
    let imageUrl: String
    let imageWidth: Int
    let imageHeight: Int
    let hiddenObjects: [WaldoHiddenObject]
    let foundObjects: [WaldoFoundObject]
    let showHint: WaldoHiddenObject?
    let gridConfig: WaldoGridConfig
    let onTap: (CGPoint, CGSize) -> Void
    
    @State private var scale: CGFloat = 1.0
    @State private var lastScale: CGFloat = 1.0
    @State private var offset: CGSize = .zero
    @State private var lastOffset: CGSize = .zero
    @State private var imageSize: CGSize = .zero
    
    var body: some View {
        GeometryReader { geometry in
            ZStack {
                // Image
                AsyncImage(url: URL(string: imageUrl)) { phase in
                    switch phase {
                    case .success(let image):
                        image
                            .resizable()
                            .aspectRatio(contentMode: .fit)
                            .frame(maxWidth: .infinity, maxHeight: .infinity)
                            .background(
                                GeometryReader { geo in
                                    Color.clear.onAppear {
                                        imageSize = geo.size
                                    }
                                }
                            )
                    case .failure:
                        VStack {
                            Image(systemName: "exclamationmark.triangle")
                                .font(.largeTitle)
                            Text("Failed to load image")
                        }
                        .foregroundColor(.white)
                    case .empty:
                        ProgressView()
                            .tint(.white)
                    @unknown default:
                        EmptyView()
                    }
                }
                
                // Found objects markers
                ForEach(foundObjects) { found in
                    Circle()
                        .fill(Color.green.opacity(0.8))
                        .frame(width: 30, height: 30)
                        .overlay(
                            Image(systemName: "checkmark")
                                .foregroundColor(.white)
                                .font(.system(size: 16, weight: .bold))
                        )
                        .position(x: found.x, y: found.y)
                }
                
                // Hint marker
                if let hint = showHint, imageSize != .zero {
                    let hintPos = getHintPosition(hint: hint)
                    
                    Circle()
                        .fill(Color.yellow.opacity(0.7))
                        .frame(width: 50, height: 50)
                        .overlay(
                            VStack(spacing: 2) {
                                Image(systemName: "lightbulb.fill")
                                    .foregroundColor(.black)
                                    .font(.system(size: 24))
                                Text("?")
                                    .foregroundColor(.black)
                                    .font(.system(size: 12, weight: .bold))
                            }
                        )
                        .position(hintPos)
                }
            }
            .scaleEffect(scale)
            .offset(offset)
            .gesture(
                MagnificationGesture()
                    .onChanged { value in
                        let delta = value / lastScale
                        lastScale = value
                        scale = min(max(scale * delta, 1), 5)
                    }
                    .onEnded { _ in
                        lastScale = 1.0
                    }
            )
            .simultaneousGesture(
                DragGesture()
                    .onChanged { value in
                        offset = CGSize(
                            width: lastOffset.width + value.translation.width,
                            height: lastOffset.height + value.translation.height
                        )
                    }
                    .onEnded { _ in
                        lastOffset = offset
                    }
            )
            .onTapGesture { location in
                if imageSize != .zero {
                    // Convert tap location to image coordinates
                    let imageRelativeX = location.x - (geometry.size.width - imageSize.width) / 2
                    let imageRelativeY = location.y - (geometry.size.height - imageSize.height) / 2
                    
                    if imageRelativeX >= 0 && imageRelativeX <= imageSize.width &&
                       imageRelativeY >= 0 && imageRelativeY <= imageSize.height {
                        onTap(CGPoint(x: imageRelativeX, y: imageRelativeY), imageSize)
                    }
                }
            }
            
            // Instructions
            if scale <= 1.2 {
                VStack {
                    Spacer()
                    Text("Pinch to zoom • Drag to pan • Tap to find objects")
                        .font(.system(size: 10))
                        .foregroundColor(.white)
                        .padding(8)
                        .background(Color.black.opacity(0.7))
                        .cornerRadius(8)
                        .padding(.bottom)
                }
            }
        }
    }
    
    private func getHintPosition(hint: WaldoHiddenObject) -> CGPoint {
        if let xPercent = hint.xPercent, let yPercent = hint.yPercent {
            return CGPoint(
                x: (CGFloat(xPercent) / 100) * imageSize.width,
                y: (CGFloat(yPercent) / 100) * imageSize.height
            )
        } else {
            let row = hint.gridRow ?? (hint.gridCell / gridConfig.cols)
            let col = hint.gridCol ?? (hint.gridCell % gridConfig.cols)
            
            let cellWidth = imageSize.width / CGFloat(gridConfig.cols)
            let cellHeight = imageSize.height / CGFloat(gridConfig.rows)
            
            return CGPoint(
                x: CGFloat(col) * cellWidth + cellWidth / 2,
                y: CGFloat(row) * cellHeight + cellHeight / 2
            )
        }
    }
}

// MARK: - Object Card
struct WaldoObjectCard: View {
    let object: WaldoHiddenObject
    let canUseHint: Bool
    let onUseHint: () -> Void
    
    @State private var isLocationRevealed = false
    
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                VStack(alignment: .leading, spacing: 4) {
                    Text(object.name.capitalized)
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(.white)
                    
                    if !object.description.isEmpty {
                        Text(object.description)
                            .font(.system(size: 12))
                            .foregroundColor(.gray)
                    }
                    
                    HStack(spacing: 8) {
                        difficultyBadge(object.difficulty)
                        
                        if isLocationRevealed || object.found {
                            Text("Grid: \(object.gridCell)")
                                .font(.system(size: 10))
                                .foregroundColor(.cyan)
                            
                            if let x = object.xPercent, let y = object.yPercent {
                                Text("Pos: \(Int(x))%,\(Int(y))%")
                                    .font(.system(size: 10))
                                    .foregroundColor(.green)
                            }
                        }
                    }
                }
                
                Spacer()
                
                if object.found {
                    Image(systemName: "checkmark.circle.fill")
                        .foregroundColor(.green)
                        .font(.system(size: 32))
                } else {
                    Image(systemName: "magnifyingglass")
                        .foregroundColor(.gray)
                        .font(.system(size: 24))
                }
            }
            
            if !object.found {
                HStack(spacing: 8) {
                    Button(action: onUseHint) {
                        HStack {
                            Image(systemName: "lightbulb.fill")
                            Text("Hint")
                        }
                        .font(.system(size: 12))
                        .foregroundColor(.black)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 6)
                        .background(Color.yellow.opacity(0.8))
                        .cornerRadius(8)
                    }
                    .disabled(!canUseHint)
                    
                    Button(action: { isLocationRevealed.toggle() }) {
                        HStack {
                            Image(systemName: isLocationRevealed ? "eye.slash" : "eye")
                            Text(isLocationRevealed ? "Hide" : "Reveal")
                        }
                        .font(.system(size: 12))
                        .foregroundColor(.white)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 6)
                        .background(isLocationRevealed ? Color.red.opacity(0.8) : Color.blue.opacity(0.8))
                        .cornerRadius(8)
                    }
                }
                
                if canUseHint {
                    Text("💡 \(object.hint)")
                        .font(.system(size: 10))
                        .foregroundColor(.yellow)
                        .italic()
                }
            }
        }
        .padding()
        .background(
            RoundedRectangle(cornerRadius: 12)
                .fill(object.found ? Color.green.opacity(0.3) : Color.gray.opacity(0.2))
        )
    }
    
    private func difficultyBadge(_ difficulty: String) -> some View {
        let color: Color = {
            switch difficulty.lowercased() {
            case "easy": return .green
            case "medium": return .yellow
            case "hard": return .red
            default: return .white
            }
        }()
        
        return Text(difficulty)
            .font(.system(size: 10, weight: .bold))
            .foregroundColor(color)
    }
}
