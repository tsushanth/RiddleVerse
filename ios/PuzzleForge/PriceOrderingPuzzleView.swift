// Updated PriceOrderingPuzzleView.swift

import SwiftUI

struct PriceItem: Identifiable, Equatable {
    let id: Int
    let name: String
    let icon: String // Unicode emoji for the icon
    let originalPrice: Double
    let discountPercentage: Int? // nil means no discount
    var isSelected: Bool = false
    var selectionOrder: Int?
    
    var finalPrice: Double {
        if let discount = discountPercentage {
            return originalPrice * (1.0 - Double(discount) / 100.0)
        }
        return originalPrice
    }
    
    static func == (lhs: PriceItem, rhs: PriceItem) -> Bool {
        return lhs.id == rhs.id &&
               lhs.name == rhs.name &&
               lhs.originalPrice == rhs.originalPrice &&
               lhs.discountPercentage == rhs.discountPercentage &&
               lhs.isSelected == rhs.isSelected &&
               lhs.selectionOrder == rhs.selectionOrder
    }
}

// New data structure to match server response
struct PriceOrderingItemData: Codable {
    let name: String
    let icon: String
    let originalPrice: Double
    let discountPercentage: Int?
}

struct PriceOrderingPuzzleView: View {
    let puzzle: Puzzle
    let questionIndex: Int
    let totalQuestions: Int
    let onAnswerSubmitted: ([Int], Bool) -> Void
    let onNextPuzzle: () -> Void
    let onExit: () -> Void
    
    @StateObject private var timer = PuzzleTimer(totalTime: 90)
    @State private var gameItems: [PriceItem] = []
    @State private var selectedOrder: [Int] = []
    @State private var isGameComplete = false
    @StateObject private var feedbackManager = UnifiedFeedbackManager()
    @StateObject private var progressionManager = ProgressionManager()
    
    // Parse puzzle data from the new format
    private var puzzleItems: [PriceOrderingItemData]? {
        guard let questionData = puzzle.question.data(using: .utf8) else {
            return nil
        }
        
        do {
            // Try to parse as array of items (new format from server)
            if let itemsArray = try JSONSerialization.jsonObject(with: questionData) as? [[String: Any]] {
                let items = itemsArray.compactMap { itemDict -> PriceOrderingItemData? in
                    guard let name = itemDict["name"] as? String,
                          let icon = itemDict["icon"] as? String,
                          let originalPrice = itemDict["originalPrice"] as? Double else {
                        return nil
                    }
                    
                    let discountPercentage = itemDict["discountPercentage"] as? Int
                    
                    return PriceOrderingItemData(
                        name: name,
                        icon: icon,
                        originalPrice: originalPrice,
                        discountPercentage: discountPercentage
                    )
                }
                
                return items.isEmpty ? nil : items
            }
        } catch {
            print("❌ Error parsing puzzle items: \(error)")
        }
        
        return nil
    }
    
    // Parse correct order from answer field
    private var serverCorrectOrder: [Int]? {
        guard let answerData = puzzle.answer.data(using: .utf8) else {
            return nil
        }
        
        do {
            if let orderArray = try JSONSerialization.jsonObject(with: answerData) as? [Int] {
                return orderArray
            }
        } catch {
            print("❌ Error parsing correct order: \(error)")
        }
        
        return nil
    }
    
    // Calculate correct order (use server order if available, otherwise calculate)
    private var correctOrder: [Int] {
        // First try to use the server-provided correct order
        if let serverOrder = serverCorrectOrder {
            return serverOrder
        }
        
        // Fallback to calculating based on final prices
        return gameItems.sorted { $0.finalPrice < $1.finalPrice }.map { $0.id }
    }
    
    var body: some View {
        GeometryReader { geometry in
            ZStack {
                // Background gradient
                backgroundView
                    .ignoresSafeArea()
                
                VStack(spacing: 0) {
                    // Top bar
                    topBar
                        .padding(.horizontal, 20)
                        .padding(.top, 10)
                    
                    // Instructions - made more compact
                    instructionsCard
                        .padding(.horizontal, 16)
                        .padding(.vertical, 8)
                    
                    // Round indicator - reduced padding
                    Text("ROUND \(questionIndex + 1)/\(totalQuestions)")
                        .font(.headline)
                        .fontWeight(.bold)
                        .foregroundColor(.white.opacity(0.7))
                        .padding(.vertical, 12)
                    
                    // Items grid - this will now take remaining space
                    ScrollView {
                        itemsGrid
                            .padding(.horizontal, 20)
                            .padding(.top, 10)
                    }
                    .layoutPriority(1) // Give scroll view layout priority
                    
                    // Progress bar - fixed at bottom
                    progressBar
                        .padding(.horizontal, 40)
                        .padding(.bottom, 30)
                        .padding(.top, 10)
                }
            }
        }
        .withUnifiedFeedback(feedbackManager)
        .trackPuzzleViewOnce(
                    puzzleId: "id",
                    puzzleType: "priceOrdering",
                    difficulty: "Easy",
                    questionIndex: 0
                )
        .onAppear {
            setupGameItems()
            timer.start()
        }
        .onDisappear {
            timer.stop()
        }
        .onChange(of: timer.timeRemaining) { newTime in
            if newTime <= 0 && !isGameComplete {
                // Time's up - auto submit current order
                submitCurrentOrder()
            }
        }
        .onChange(of: puzzle.id) { _ in
            // Reset and setup new puzzle when puzzle changes
            setupGameItems()
            timer.reset()
            timer.start()
        }
        .navigationBarHidden(true)
    }
    
    // MARK: - Background View
    private var backgroundView: some View {
        LinearGradient(
            colors: [
                Color(red: 0.29, green: 0.56, blue: 0.89),
                Color(red: 0.21, green: 0.48, blue: 0.74),
                Color(red: 0.48, green: 0.41, blue: 0.93)
            ],
            startPoint: .top,
            endPoint: .bottom
        )
    }
    
    // MARK: - Top Bar
    private var topBar: some View {
        HStack {
            Button(action: onExit) {
                HStack(spacing: 10) {
                    Image(systemName: "xmark")
                        .foregroundColor(.white.opacity(0.7))
                        .font(.title2)
                    
                    Text("Exit")
                        .font(.title3)
                        .fontWeight(.medium)
                        .foregroundColor(.white.opacity(0.7))
                }
            }
            
            Spacer()
            
            // Question counter
            Text("\(questionIndex + 1)/\(totalQuestions)")
                .font(.title3)
                .fontWeight(.medium)
                .foregroundColor(.white.opacity(0.8))
            
            Spacer()
            
            // Timer
            Text(timer.formattedTime)
                .font(.title2)
                .fontWeight(.bold)
                .foregroundColor(timer.timeRemaining <= 15 ? .red : .white)
                .animation(.easeInOut(duration: 0.5), value: timer.timeRemaining <= 15)
        }
    }
    
    // MARK: - Instructions Card - Made more compact
    private var instructionsCard: some View {
        RoundedRectangle(cornerRadius: 10)
            .fill(Color.white.opacity(0.9))
            .overlay(
                VStack(spacing: 4) {
                    Text("CALCULATE THE FINAL PRICE AND ORDER FROM LEAST TO MOST EXPENSIVE")
                        .font(.system(size: 14, weight: .bold))
                        .foregroundColor(Color(red: 0.17, green: 0.24, blue: 0.31))
                        .multilineTextAlignment(.center)
                        .lineLimit(2)
                    
                    // Use hint from puzzle data
                    if !puzzle.hint.isEmpty {
                        Text("💡 \(puzzle.hint)")
                            .font(.system(size: 11, weight: .medium))
                            .foregroundColor(Color(red: 0.17, green: 0.24, blue: 0.31).opacity(0.7))
                            .multilineTextAlignment(.center)
                            .lineLimit(2)
                    }
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 10)
            )
            .frame(maxHeight: 60) // Limit the height of instructions
    }
    
    // MARK: - Items Grid
    private var itemsGrid: some View {
        LazyVGrid(columns: [
            GridItem(.flexible()),
            GridItem(.flexible())
        ], spacing: 16) {
            ForEach(gameItems) { item in
                PriceItemCardView(
                    item: item,
                    onTap: {
                        selectItem(itemId: item.id)
                    }
                )
                .aspectRatio(0.85, contentMode: .fit)
            }
        }
    }
    
    // MARK: - Progress Bar
    private var progressBar: some View {
        RoundedRectangle(cornerRadius: 2)
            .fill(Color.white.opacity(0.3))
            .frame(height: 4)
    }
    
    // MARK: - Helper Properties
    private var correctOrderText: String {
        correctOrder.compactMap { id in
            if let item = gameItems.first(where: { $0.id == id }) {
                return "\(item.icon) $\(String(format: "%.2f", item.finalPrice))"
            }
            return nil
        }.joined(separator: " → ")
    }
    
    // MARK: - Helper Methods
    private func setupGameItems() {
        print("🔄 Setting up game items...")
        
        // First try to parse as local generated data (new format)
        if let data = puzzle.question.data(using: .utf8),
           let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
           let itemsArray = json["items"] as? [[String: Any]] {
            
            print("✅ Using local generated discount data with \(itemsArray.count) items")
            
            // Parse local format
            gameItems = itemsArray.enumerated().compactMap { index, itemDict -> PriceItem? in
                guard let name = itemDict["name"] as? String,
                      let icon = itemDict["icon"] as? String,
                      let originalPrice = itemDict["originalPrice"] as? Double,
                      let id = itemDict["id"] as? Int else {
                    print("❌ Failed to parse item at index \(index): \(itemDict)")
                    return nil
                }
                
                let discountPercentage = itemDict["discountPercentage"] as? Int
                
                return PriceItem(
                    id: id,
                    name: name,
                    icon: icon,
                    originalPrice: originalPrice,
                    discountPercentage: discountPercentage
                )
            }
            
            // Debug: Print parsed items
            for item in gameItems {
                print("📦 Local Item \(item.id): \(item.name) \(item.icon) - $\(item.originalPrice) (\(item.discountPercentage ?? 0)% off) = $\(String(format: "%.2f", item.finalPrice))")
            }
            
        } else if let items = puzzleItems {
            // Fallback to server format (existing logic)
            print("✅ Using server puzzle data with \(items.count) items")
            
            gameItems = items.enumerated().map { index, item in
                PriceItem(
                    id: index + 1, // Use index + 1 as ID to match server order
                    name: item.name,
                    icon: item.icon,
                    originalPrice: item.originalPrice,
                    discountPercentage: item.discountPercentage
                )
            }
            
            // Debug: Print parsed items
            for item in gameItems {
                print("📦 Server Item \(item.id): \(item.name) \(item.icon) - $\(item.originalPrice) (\(item.discountPercentage ?? 0)% off) = $\(String(format: "%.2f", item.finalPrice))")
            }
            
        } else {
            print("⚠️ No puzzle data found, using fallback items")
            
            // Fallback to sample data if no puzzle data available
            gameItems = [
                PriceItem(
                    id: 1,
                    name: "Bowl",
                    icon: "🥣",
                    originalPrice: 70.00,
                    discountPercentage: 50
                ),
                PriceItem(
                    id: 2,
                    name: "Soccer Ball",
                    icon: "⚽",
                    originalPrice: 36.00,
                    discountPercentage: nil
                ),
                PriceItem(
                    id: 3,
                    name: "Book",
                    icon: "📚",
                    originalPrice: 25.00,
                    discountPercentage: nil
                ),
                PriceItem(
                    id: 4,
                    name: "T-Shirt",
                    icon: "👕",
                    originalPrice: 60.00,
                    discountPercentage: 20
                )
            ]
        }
        
        // Reset selection state
        selectedOrder = []
        isGameComplete = false
        
        // Debug: Print correct order
        print("🎯 Correct order: \(correctOrder)")
    }
    
    private func selectItem(itemId: Int) {
        guard !isGameComplete else { return }
        
        hapticFeedback()
        
        guard let itemIndex = gameItems.firstIndex(where: { $0.id == itemId }) else { return }
        let item = gameItems[itemIndex]
        
        if item.isSelected {
            // Deselect item and all items selected after it
            guard let orderToRemove = item.selectionOrder else { return }
            
            for i in gameItems.indices {
                if gameItems[i].id == itemId {
                    gameItems[i].isSelected = false
                    gameItems[i].selectionOrder = nil
                } else if let order = gameItems[i].selectionOrder, order > orderToRemove {
                    gameItems[i].isSelected = false
                    gameItems[i].selectionOrder = nil
                }
            }
            
            selectedOrder = selectedOrder.enumerated().compactMap { index, id in
                let gameItem = gameItems.first { $0.id == id }
                if let order = gameItem?.selectionOrder, order <= orderToRemove {
                    return index < orderToRemove - 1 ? id : nil
                }
                return nil
            }
        } else {
            // Select item
            let newOrder = selectedOrder.count + 1
            gameItems[itemIndex].isSelected = true
            gameItems[itemIndex].selectionOrder = newOrder
            selectedOrder.append(itemId)
        }
        
        // Check if all items are selected
        if selectedOrder.count == gameItems.count {
            submitCurrentOrder()
        }
    }
    
    private func submitCurrentOrder() {
        guard !isGameComplete else { return }
        
        print("📝 Submitting order: \(selectedOrder)")
        print("🎯 Correct order: \(correctOrder)")
        
        timer.stop()
        isGameComplete = true
        let isCorrect = selectedOrder == correctOrder
        
        feedbackManager.showFeedback(
            puzzleType: "discounts",
            isCorrect: isCorrect,
            userAnswer: selectedOrder.map { "\($0)" }.joined(separator: ", "),
            correctAnswer: correctOrder.map { "\($0)" }.joined(separator: ", "),
            timeSpent: Double(timer.totalTime - timer.timeRemaining),
            difficulty: puzzle.difficulty,
            timeRemaining: timer.timeRemaining,
            totalTime: timer.totalTime,
            onComplete: {
                onNextPuzzle()
            }
        )
        
        onAnswerSubmitted(selectedOrder, isCorrect)
    }
    
    private func resetState() {
        gameItems = gameItems.map { item in
            var resetItem = item
            resetItem.isSelected = false
            resetItem.selectionOrder = nil
            return resetItem
        }
        selectedOrder = []
        isGameComplete = false
        timer.reset()
        timer.start()
    }
    
    private func calculateScore() -> Int {
        return PuzzleScoring.calculateScore(
            for: "discounts",
            timeRemaining: timer.timeRemaining,
            totalTime: 90,
            difficulty: puzzle.difficulty
        )
    }
    
    private func hapticFeedback() {
        let impactFeedback = UIImpactFeedbackGenerator(style: .medium)
        impactFeedback.impactOccurred()
    }
}

// MARK: - Price Item Card View - No changes needed
struct PriceItemCardView: View {
    let item: PriceItem
    let onTap: () -> Void
    
    var body: some View {
        Button(action: onTap) {
            ZStack {
                RoundedRectangle(cornerRadius: 12)
                    .fill(item.isSelected ? Color.white.opacity(0.3) : Color.clear)
                    .overlay(
                        RoundedRectangle(cornerRadius: 12)
                            .stroke(
                                item.isSelected ? Color.white : Color.white.opacity(0.3),
                                lineWidth: item.isSelected ? 3 : 1
                            )
                    )
                
                // Selection order indicator
                if item.isSelected, let order = item.selectionOrder {
                    VStack {
                        HStack {
                            Spacer()
                            Circle()
                                .fill(Color.white)
                                .frame(width: 20, height: 20)
                                .overlay(
                                    Text("\(order)")
                                        .font(.system(size: 11, weight: .bold))
                                        .foregroundColor(Color(red: 0.29, green: 0.56, blue: 0.89))
                                )
                                .offset(x: -6, y: 6)
                        }
                        Spacer()
                    }
                }
                
                VStack(spacing: 8) {
                    // Icon
                    Text(item.icon)
                        .font(.system(size: 32))
                    
                    // Item name
                    Text(item.name)
                        .font(.caption)
                        .fontWeight(.medium)
                        .foregroundColor(.white.opacity(0.9))
                        .lineLimit(1)
                    
                    // Original price - always show this
                    Text("$\(String(format: "%.2f", item.originalPrice))")
                        .font(.headline)
                        .fontWeight(.bold)
                        .foregroundColor(.white)
                    
                    // Discount tag if applicable - made smaller
                    if let discount = item.discountPercentage {
                        RoundedRectangle(cornerRadius: 4)
                            .fill(Color(red: 1.0, green: 0.42, blue: 0.21))
                            .frame(width: 60, height: 28)
                            .overlay(
                                VStack(spacing: 0) {
                                    Text("\(discount)%")
                                        .font(.system(size: 11, weight: .bold))
                                        .foregroundColor(.white)
                                    Text("OFF")
                                        .font(.system(size: 8, weight: .bold))
                                        .foregroundColor(.white)
                                }
                            )
                    } else {
                        // Add some spacing when there's no discount tag
                        Spacer()
                            .frame(height: 28)
                    }
                }
                .padding(12)
            }
        }
        .scaleEffect(item.isSelected ? 1.02 : 1.0)
        .animation(.easeInOut(duration: 0.2), value: item.isSelected)
        .buttonStyle(PlainButtonStyle())
    }
}

// MARK: - Preview
struct PriceOrderingPuzzleView_Previews: PreviewProvider {
    static var previews: some View {
        let samplePuzzle = Puzzle(
            question: """
            [{"name":"T-Shirt","icon":"👕","originalPrice":25.0,"discountPercentage":20},{"name":"Book","icon":"📚","originalPrice":18.0,"discountPercentage":null},{"name":"Coffee Mug","icon":"☕","originalPrice":12.0,"discountPercentage":15},{"name":"Phone Case","icon":"📱","originalPrice":30.0,"discountPercentage":40}]
            """,
            answer: "[3,2,1,4]",
            hint: "Calculate the final price after applying discounts, then order from least to most expensive",
            options: [],
            format: "discounts",
            puzzleType: "discounts",
            puzzleId: "sample-discounts",
            id: "sample-discounts",
            name: "Price Ordering Puzzle",
            createdAt: Date().timeIntervalSince1970 * 1000,
            status: "ready",
            difficulty: "Medium"
        )
        
        PriceOrderingPuzzleView(
            puzzle: samplePuzzle,
            questionIndex: 0,
            totalQuestions: 1,
            onAnswerSubmitted: { order, isCorrect in
                print("Order: \(order), Correct: \(isCorrect)")
            },
            onNextPuzzle: {
                print("Next puzzle")
            },
            onExit: {
                print("Exit")
            }
        )
    }
}
