//
//  LocalContextSwitchPuzzleGenerator.swift
//  PuzzleForge
//
//  Created by Sushanth Tiruvaipati on 7/26/25.
//

import Foundation

class LocalContextSwitchPuzzleGenerator {
    
    // Content database matching Android implementation
    private static let contentDatabase: [String: [String]] = [
        // Geographic
        "european_countries": ["France", "Germany", "Italy", "Spain", "Netherlands", "Belgium", "Austria", "Portugal", "Greece", "Czech Republic", "Hungary", "Poland", "Sweden", "Norway", "Denmark", "Switzerland", "Ireland", "Finland"],
        "asian_cities": ["Tokyo", "Seoul", "Bangkok", "Singapore", "Mumbai", "Shanghai", "Hong Kong", "Jakarta", "Manila", "Kuala Lumpur", "Delhi", "Osaka", "Taipei", "Hanoi", "Colombo"],
        "us_states": ["California", "Texas", "Florida", "New York", "Arizona", "Colorado", "Nevada", "Oregon", "Utah", "Montana", "Alaska", "Hawaii", "Maine", "Vermont", "Wyoming"],
        
        // Food & Cuisine
        "asian_cuisines": ["Sushi", "Pad Thai", "Kimchi", "Ramen", "Biryani", "Pho", "Dumplings", "Curry", "Teriyaki", "Miso", "Satay", "Laksa", "Bulgogi", "Rendang", "Tandoori"],
        "italian_foods": ["Pizza", "Pasta", "Risotto", "Lasagna", "Gelato", "Tiramisu", "Bruschetta", "Cannoli", "Minestrone", "Focaccia", "Gnocchi", "Carbonara", "Pesto", "Polenta", "Osso Buco"],
        "desserts": ["Chocolate Cake", "Ice Cream", "Cheesecake", "Apple Pie", "Brownies", "Cookies", "Donuts", "Cupcakes", "Pudding", "Sorbet", "Macarons", "Trifle", "Mousse", "Flan", "Baklava"],
        
        // Technology
        "programming_languages": ["Python", "JavaScript", "Java", "Kotlin", "Swift", "C++", "Go", "Rust", "TypeScript", "PHP", "Ruby", "Scala", "Dart", "C#", "Objective-C"],
        "tech_companies": ["Google", "Apple", "Microsoft", "Amazon", "Meta", "Tesla", "Netflix", "Adobe", "Spotify", "Airbnb", "Uber", "Twitter", "Intel", "Samsung", "Sony"],
        "operating_systems": ["Windows", "macOS", "Linux", "Android", "iOS", "Ubuntu", "Chrome OS", "FreeBSD", "Unix", "Fedora", "Debian", "CentOS", "Red Hat", "SUSE", "Mint"],
        
        // Nature & Animals
        "dog_breeds": ["Labrador", "Golden Retriever", "Bulldog", "Beagle", "Poodle", "Rottweiler", "Yorkshire", "Boxer", "Husky", "Dachshund", "Shepherd", "Chihuahua", "Collie", "Mastiff", "Spaniel"],
        "birds": ["Eagle", "Sparrow", "Robin", "Cardinal", "Blue Jay", "Hawk", "Owl", "Parrot", "Penguin", "Flamingo", "Hummingbird", "Woodpecker", "Crow", "Swan", "Peacock"],
        "ocean_animals": ["Whale", "Dolphin", "Shark", "Octopus", "Seahorse", "Jellyfish", "Starfish", "Crab", "Lobster", "Turtle", "Seal", "Stingray", "Barracuda", "Angelfish", "Clownfish"],
        
        // Entertainment
        "musical_instruments": ["Piano", "Guitar", "Violin", "Drums", "Trumpet", "Saxophone", "Flute", "Cello", "Clarinet", "Trombone", "Harp", "Banjo", "Accordion", "Mandolin", "Xylophone"],
        "movie_genres": ["Action", "Comedy", "Drama", "Horror", "Romance", "Thriller", "Adventure", "Animation", "Documentary", "Fantasy", "Mystery", "Musical", "Western", "Biography", "Crime"],
        "sports": ["Soccer", "Basketball", "Tennis", "Baseball", "Football", "Swimming", "Golf", "Boxing", "Running", "Cycling", "Volleyball", "Hockey", "Cricket", "Rugby", "Badminton"],
        
        // Education & Science
        "school_subjects": ["Math", "Science", "History", "English", "Art", "Music", "Geography", "Physics", "Chemistry", "Biology", "Economics", "Psychology", "Philosophy", "Literature", "Statistics"],
        "planets": ["Mercury", "Venus", "Earth", "Mars", "Jupiter", "Saturn", "Uranus", "Neptune", "Pluto", "Ceres", "Eris", "Makemake", "Haumea", "Sedna", "Quaoar"],
        "chemical_elements": ["Hydrogen", "Helium", "Lithium", "Carbon", "Nitrogen", "Oxygen", "Fluorine", "Neon", "Sodium", "Magnesium", "Aluminum", "Silicon", "Phosphorus", "Sulfur", "Chlorine"]
    ]
    
    static func generateContextSwitchPuzzle(difficulty: String) -> (String, String) {
        // Select random content category
        let categoryKey = contentDatabase.keys.randomElement()!
        let allItems = contentDatabase[categoryKey]!
        
        // Determine complexity based on difficulty
        let complexity = getComplexity(for: difficulty)
        
        // Generate memory items
        let memoryItems = Array(allItems.shuffled().prefix(complexity.memoryItemCount))
        
        // Generate smart distractors (same category but not in memory list)
        let availableDistractors = allItems.filter { !memoryItems.contains($0) }
        let distractors = Array(availableDistractors.shuffled().prefix(complexity.distractorCount))
        
        // Create recognition list (memory + distractors, shuffled)
        let recognitionItems = (memoryItems + distractors).shuffled()
        
        // ALWAYS generate math subtraction task - removed random selection
        let interferenceTask = createMathSubtractionTask(complexity: complexity.interferenceComplexity)
        
        // Build question JSON
        let questionData: [String: Any] = [
            "puzzleType": "context_switch",
            "category": categoryKey,
            "difficulty": difficulty,
            "memoryItems": memoryItems,
            "recognitionItems": recognitionItems,
            "correctAnswers": memoryItems,
            "interferenceTask": [
                "type": interferenceTask.type,
                "items": interferenceTask.items,
                "instruction": interferenceTask.instruction,
                "complexity": complexity.interferenceComplexity
            ],
            "metadata": [
                "memoryItemCount": complexity.memoryItemCount,
                "distractorCount": complexity.distractorCount,
                "totalChoices": complexity.totalChoices,
                "categoryName": categoryKey.replacingOccurrences(of: "_", with: " ").capitalized
            ]
        ]
        
        // Build answer JSON
        let answerData: [String: Any] = [
            "correctAnswers": memoryItems,
            "puzzleType": "context_switch",
            "category": categoryKey
        ]
        
        // Convert to JSON strings
        let questionJSON = try! JSONSerialization.data(withJSONObject: questionData)
        let answerJSON = try! JSONSerialization.data(withJSONObject: answerData)
        
        let questionString = String(data: questionJSON, encoding: .utf8)!
        let answerString = String(data: answerJSON, encoding: .utf8)!
        
        return (questionString, answerString)
    }
    
    private static func getComplexity(for difficulty: String) -> PuzzleComplexity {
        switch difficulty.lowercased() {
        case "easy":
            return PuzzleComplexity(
                memoryItemCount: 4,
                distractorCount: 2,
                interferenceComplexity: "simple",
                totalChoices: 6
            )
        case "medium":
            return PuzzleComplexity(
                memoryItemCount: 5,
                distractorCount: 3,
                interferenceComplexity: "moderate",
                totalChoices: 8
            )
        case "hard":
            return PuzzleComplexity(
                memoryItemCount: 6,
                distractorCount: 4,
                interferenceComplexity: "complex",
                totalChoices: 10
            )
        default:
            return PuzzleComplexity(
                memoryItemCount: 5,
                distractorCount: 3,
                interferenceComplexity: "moderate",
                totalChoices: 8
            )
        }
    }
    
    // REMOVED: createInterferenceTask method that randomly selected task types
    // ADDED: Only math subtraction task creation
    private static func createMathSubtractionTask(complexity: String) -> InterferenceTaskData {
        let problemCount: Int
        
        switch complexity {
        case "simple": problemCount = 3
        case "moderate": problemCount = 4
        case "complex": problemCount = 5
        default: problemCount = 4
        }
        
        var problems: [String] = []
        
        for _ in 0..<problemCount {
            let a: Int
            let b: Int
            
            switch complexity {
            case "simple":
                a = Int.random(in: 10...20)  // Ensure positive result
                b = Int.random(in: 1...9)    // Smaller number to subtract
            case "moderate":
                a = Int.random(in: 20...50)  // Larger numbers
                b = Int.random(in: 5...25)   // But still ensuring positive results
            case "complex":
                a = Int.random(in: 30...100) // Even larger numbers
                b = Int.random(in: 10...50)  // More challenging subtraction
            default:
                a = Int.random(in: 10...20)
                b = Int.random(in: 1...9)
            }
            
            // Ensure a > b for positive results
            let minuend = max(a, b)
            let subtrahend = min(a, b)
            problems.append("\(minuend) - \(subtrahend)")
        }
        
        return InterferenceTaskData(
            type: "math_subtraction",
            items: problems,
            instruction: "Solve these subtraction problems"
        )
    }
    
    // REMOVED: All other task creation methods (number_sort, word_alphabetize, etc.)
    // since we only want math subtraction
}

// MARK: - Supporting Data Structures
private struct PuzzleComplexity {
    let memoryItemCount: Int
    let distractorCount: Int
    let interferenceComplexity: String
    let totalChoices: Int
}

private struct InterferenceTaskData {
    let type: String
    let items: [String]
    let instruction: String
}
