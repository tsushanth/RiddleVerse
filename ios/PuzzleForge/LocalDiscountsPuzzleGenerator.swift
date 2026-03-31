//
//  LocalDiscountsPuzzleGenerator.swift
//  PuzzleForge
//
//  Created by Sushanth Tiruvaipati on 8/2/25.
//


import Foundation

struct LocalDiscountsPuzzleGenerator {
    
    // Item templates with emojis and names
    private static let itemTemplates = [
        (name: "Laptop", icon: "💻"),
        (name: "Phone", icon: "📱"),
        (name: "Tablet", icon: "📱"),
        (name: "Watch", icon: "⌚"),
        (name: "Headphones", icon: "🎧"),
        (name: "Camera", icon: "📷"),
        (name: "Speaker", icon: "🔊"),
        (name: "Monitor", icon: "🖥️"),
        (name: "Keyboard", icon: "⌨️"),
        (name: "Mouse", icon: "🖱️"),
        (name: "Jacket", icon: "🧥"),
        (name: "Shoes", icon: "👟"),
        (name: "Backpack", icon: "🎒"),
        (name: "Sunglasses", icon: "🕶️"),
        (name: "Book", icon: "📚"),
        (name: "Coffee Mug", icon: "☕"),
        (name: "T-Shirt", icon: "👕"),
        (name: "Bowl", icon: "🥣"),
        (name: "Soccer Ball", icon: "⚽"),
        (name: "Guitar", icon: "🎸"),
        (name: "Bicycle", icon: "🚲"),
        (name: "Skateboard", icon: "🛹"),
        (name: "Umbrella", icon: "☂️"),
        (name: "Hat", icon: "🧢"),
        (name: "Wallet", icon: "👛")
    ]
    
    static func generateDiscountsPuzzle(difficulty: String) -> (String, String) {
        let itemCount = generateItemCount(for: difficulty)
        var items: [[String: Any]] = []
        var usedNames = Set<String>()
        
        // Generate items
        for i in 0..<itemCount {
            let item = generateUniqueItem(
                index: i + 1,
                difficulty: difficulty,
                usedNames: &usedNames
            )
            items.append(item)
        }
        
        // Calculate correct order (sorted by final price)
        let sortedItems = items.sorted { item1, item2 in
            let price1 = calculateFinalPrice(
                originalPrice: item1["originalPrice"] as! Double,
                discountPercentage: item1["discountPercentage"] as? Int
            )
            let price2 = calculateFinalPrice(
                originalPrice: item2["originalPrice"] as! Double,
                discountPercentage: item2["discountPercentage"] as? Int
            )
            return price1 < price2
        }
        
        let correctOrder = sortedItems.compactMap { item in
            item["id"] as? Int
        }
        
        // Create question JSON
        let questionData: [String: Any] = [
            "items": items,
            "difficulty": difficulty,
            "hint": "Calculate the final price after applying discounts, then order from least to most expensive"
        ]
        
        // Create answer JSON
        let answerData = correctOrder
        
        // Convert to JSON strings
        guard let questionJSON = try? JSONSerialization.data(withJSONObject: questionData),
              let questionString = String(data: questionJSON, encoding: .utf8),
              let answerJSON = try? JSONSerialization.data(withJSONObject: answerData),
              let answerString = String(data: answerJSON, encoding: .utf8) else {
            return ("", "")
        }
        
        print("🔵 DISCOUNTS: Generated \(itemCount) items for \(difficulty) difficulty")
        print("🔵 DISCOUNTS: Correct order: \(correctOrder)")
        
        return (questionString, answerString)
    }
    
    private static func generateItemCount(for difficulty: String) -> Int {
        switch difficulty.lowercased() {
        case "easy":
            return Int.random(in: 3...4)
        case "medium":
            return Int.random(in: 4...5)
        case "hard":
            return Int.random(in: 5...6)
        default:
            return 4
        }
    }
    
    private static func generateUniqueItem(
        index: Int,
        difficulty: String,
        usedNames: inout Set<String>
    ) -> [String: Any] {
        
        // Get unique item template
        var availableItems = itemTemplates.filter { !usedNames.contains($0.name) }
        if availableItems.isEmpty {
            availableItems = itemTemplates // Fallback if all used
        }
        
        let selectedItem = availableItems.randomElement()!
        usedNames.insert(selectedItem.name)
        
        // Generate price based on difficulty
        let originalPrice = generateOriginalPrice(for: difficulty)
        
        // Generate discount (some items may have no discount)
        let discountPercentage = generateDiscountPercentage(for: difficulty)
        
        var item: [String: Any] = [
            "id": index,
            "name": selectedItem.name,
            "icon": selectedItem.icon,
            "originalPrice": originalPrice
        ]
        
        if let discount = discountPercentage {
            item["discountPercentage"] = discount
        }
        
        return item
    }
    
    private static func generateOriginalPrice(for difficulty: String) -> Double {
        let range: (min: Double, max: Double)
        
        switch difficulty.lowercased() {
        case "easy":
            range = (min: 20.0, max: 200.0)
        case "medium":
            range = (min: 100.0, max: 500.0)
        case "hard":
            range = (min: 300.0, max: 1000.0)
        default:
            range = (min: 50.0, max: 300.0)
        }
        
        let price = Double.random(in: range.min...range.max)
        return round(price * 100) / 100 // Round to 2 decimal places
    }
    
    private static func generateDiscountPercentage(for difficulty: String) -> Int? {
        // 70% chance of having a discount
        guard Double.random(in: 0...1) < 0.7 else { return nil }
        
        let discountOptions: [Int]
        
        switch difficulty.lowercased() {
        case "easy":
            discountOptions = [10, 15, 20, 25]
        case "medium":
            discountOptions = [15, 20, 25, 30, 35]
        case "hard":
            discountOptions = [20, 25, 30, 35, 40, 45]
        default:
            discountOptions = [10, 15, 20, 25, 30]
        }
        
        return discountOptions.randomElement()!
    }
    
    private static func calculateFinalPrice(originalPrice: Double, discountPercentage: Int?) -> Double {
        guard let discount = discountPercentage else {
            return originalPrice
        }
        
        let finalPrice = originalPrice * (1.0 - Double(discount) / 100.0)
        return round(finalPrice * 100) / 100 // Round to 2 decimal places
    }
}

// MARK: - Extension for parsing in PriceOrderingPuzzleView
extension LocalDiscountsPuzzleGenerator {
    
    /// Parse the generated question data into PriceOrderingItemData format
    static func parseQuestionData(_ questionString: String) -> [PriceOrderingItemData]? {
        guard let questionData = questionString.data(using: .utf8),
              let json = try? JSONSerialization.jsonObject(with: questionData) as? [String: Any],
              let itemsArray = json["items"] as? [[String: Any]] else {
            return nil
        }
        
        return itemsArray.compactMap { itemDict -> PriceOrderingItemData? in
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
    }
    
    /// Parse the generated answer data into correct order array
    static func parseAnswerData(_ answerString: String) -> [Int]? {
        guard let answerData = answerString.data(using: .utf8),
              let correctOrder = try? JSONSerialization.jsonObject(with: answerData) as? [Int] else {
            return nil
        }
        
        return correctOrder
    }
}