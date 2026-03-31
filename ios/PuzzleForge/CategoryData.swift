//
//  CategoryFilter.swift
//  PuzzleForge
//
//  Created by Sushanth Tiruvaipati on 9/17/25.
//


//
//  CategoryData.swift
//  PuzzleForge
//

import SwiftUI

enum CategoryFilter: String, CaseIterable, Identifiable {
    case all = "All"
    case math = "Math"
    case english = "English"
    case memory = "Memory"
    case speed = "Speed"
    case word = "Word"
    case visual = "Visual"
    case logic = "Logic"
    case aToZ = "A-Z"
    
    var id: String { rawValue }
    
    var icon: String {
        switch self {
        case .all: return "square.grid.3x3"
        case .math: return "function"
        case .english: return "textformat.abc"
        case .memory: return "brain.head.profile"
        case .word: return "textformat"
        case .logic: return "puzzlepiece"
        case .visual: return "eyedropper"
        case .speed: return "arrow.triangle.2.circlepath"
        case .aToZ: return "textformat.abc.dottedunderline"
        }
    }
    
    var color: Color {
        switch self {
        case .all: return .gray
        case .math: return .blue
        case .english: return .green
        case .memory: return .purple
        case .word: return .orange
        case .logic: return .red
        case .visual: return .yellow
        case .speed: return .pink
        case .aToZ: return .indigo
        }
    }
}

struct CategoryData {
    let title: String
    let subtitle: String
    let icon: String
    let backgroundColor: Color
    let category: String
    let tags: [CategoryFilter]
    
    static let newCategories: [CategoryData] = [
        CategoryData(
             title: "Math Expression",
             subtitle: "Solve falling equations",
             icon: "function",
             backgroundColor: Color(red: 0.9, green: 0.4, blue: 0.6),
             category: "mathexpression",
             tags: [.math, .logic]
         ),
        
        CategoryData(
            title: "Letter Set",
            subtitle: "Hunt for words in set of letters",
            icon: "textformat.characters",
            backgroundColor: Color(red: 0.3, green: 0.8, blue: 0.5),
            category: "letterset",
            tags: [.word, .english]
        ),

        CategoryData(
            title: "Waldo Puzzle",
            subtitle: "Find hidden objects in images",
            icon: "eye.fill",
            backgroundColor: Color(red: 0.9, green: 0.4, blue: 0.6),
            category: "waldopuzzle",
            tags: [.visual, .logic]
        ),
        
        CategoryData(
            title: "Sentence Transitions",
            subtitle: "Complete sentences with connecting words",
            icon: "link.circle.fill",
            backgroundColor: Color(red: 0.3, green: 0.7, blue: 0.5),
            category: "sentencetransitions",
            tags: [.english, .logic]
        ),
        
        CategoryData(
            title: "Odd One Out",
            subtitle: "Find the Different One",
            icon: "eye.circle.fill",
            backgroundColor: Color(red: 0.91, green: 0.30, blue: 0.24),
            category: "oddoneout",
            tags: [.logic, .visual]
        ),
        
        CategoryData(
            title: "Flow Puzzle",
            subtitle: "Connect matching colors",
            icon: "arrow.triangle.branch",
            backgroundColor: Color(red: 0.29, green: 0.56, blue: 0.89),
            category: "flowpuzzle",
            tags: [.logic, .visual]
        ),
        
        CategoryData(
            
            title: "Symmetry",
            subtitle: "Mirror & Copy Patterns",
            icon: "arrow.left.arrow.right",
            backgroundColor: Color(red: 0.40, green: 0.23, blue: 0.72),
            category: "symmetry",
            tags: [.logic, .memory, .speed]
        ),
        
        CategoryData(
            title: "Image Question",
            subtitle: "Study & answer questions",
            icon: "photo.on.rectangle.angled",
            backgroundColor: Color(red: 0.4, green: 0.7, blue: 0.9),
            category: "imagequestion",
            tags: [.memory, .visual]
        ),
        
        CategoryData(
            title: "Triangle Dot Memory",
            subtitle: "Remember dot positions",
            icon: "triangle.fill",
            backgroundColor: .purple,
            category: "triangledotmemory",
            tags: [.memory]
        ),
        
        CategoryData(
            title: "Progressive Reveal",
            subtitle: "Guess before picture reveals",
            icon: "puzzlepiece.extension.fill",
            backgroundColor: Color(red: 0.8, green: 0.4, blue: 0.9),
            category: "progressiverevelation",
            tags: [.logic]
        ),
        
        CategoryData(
            title: "Music Match",
            subtitle: "Match Songs to Titles",
            icon: "music.note",
            backgroundColor: Color(red: 0.8, green: 0.2, blue: 0.8),
            category: "musicidentification",
            tags: [.logic, .memory]
        ),
        
        CategoryData(
            title: "Find Object",
            subtitle: "AI Vision Discovery",
            icon: "eye.fill",
            backgroundColor: Color(red: 0.9, green: 0.4, blue: 0.6),
            category: "find_object",
            tags: [.logic, .memory]
        ),
        
        CategoryData(
            title: "Which Is Real?",
            subtitle: "Identify AI vs Real Images",
            icon: "photo.fill.on.rectangle.fill",
            backgroundColor: Color(red: 0.42, green: 0.36, blue: 0.90),
            category: "realorai",
            tags: [.logic]
        ),
        
        CategoryData(
            title: "Word Snake",
            subtitle: "Find Connected Words",
            icon: "link",
            backgroundColor: Color(red: 0.3, green: 0.8, blue: 0.5),
            category: "wordsnake",
            tags: [.word, .logic]
        ),
        
        CategoryData(
            title: "Crypto Puzzle",
            subtitle: "Decode Hidden Messages",
            icon: "lock.fill",
            backgroundColor: Color(red: 0.2, green: 0.1, blue: 0.4),
            category: "crypto",
            tags: [.logic, .word]
        ),
        
        CategoryData(
            title: "Color Shape Match",
            subtitle: "Visual Recognition",
            icon: "paintbrush.fill",
            backgroundColor: Color(red: 0.9, green: 0.4, blue: 0.7),
            category: "colorshapematching",
            tags: [.logic, .memory]
        ),
        
        CategoryData(
            title: "Image Vortex",
            subtitle: "Find the New Image",
            icon: "sparkles",
            backgroundColor: Color(red: 0.3, green: 0.6, blue: 0.9),
            category: "imagevortex",
            tags: [.memory, .logic]
        ),
        
        CategoryData(
            title: "Math Crossword",
            subtitle: "Number Equations Grid",
            icon: "grid.circle.fill",
            backgroundColor: Color(red: 0.2, green: 0.6, blue: 0.8),
            category: "mathcrossword",
            tags: [.math, .logic]
        ),
        
        CategoryData(
            title: "Dual Task",
            subtitle: "Switch Between Tasks",
            icon: "brain.head.profile.fill",
            backgroundColor: Color(red: 0.6, green: 0.2, blue: 0.9),
            category: "dualtask",
            tags: [.memory, .logic]
        ),

        CategoryData(
            title: "Color Text Match",
            subtitle: "Match Meaning & Color",
            icon: "paintpalette.fill",
            backgroundColor: Color(red: 0.9, green: 0.3, blue: 0.4),
            category: "colortextmatching",
            tags: [.logic, .memory]
        ),
        
        CategoryData(
            title: "Geography Cities",
            subtitle: "Place Cities on World Map",
            icon: "building.2.fill",
            backgroundColor: Color(red: 0.2, green: 0.6, blue: 0.8),
            category: "geography_cities",
            tags: [.logic, .memory]
        ),
        
        CategoryData(
            title: "Geography Countries",
            subtitle: "Place Countries on World Map",
            icon: "map.fill",
            backgroundColor: Color(red: 0.8, green: 0.4, blue: 0.2),
            category: "geography_countries",
            tags: [.logic, .memory]
        ),
        
        CategoryData(
            title: "Context Switch",
            subtitle: "Memory & Interference",
            icon: "brain.head.profile.fill",
            backgroundColor: Color(red: 0.4, green: 0.2, blue: 0.8),
            category: "contextswitch",
            tags: [.memory, .logic]
        )
    ]
    
    static let allCategories: [CategoryData] = [
        CategoryData(
            title: "Sentence Transitions",
            subtitle: "Complete sentences with connecting words",
            icon: "link.circle.fill",
            backgroundColor: Color(red: 0.3, green: 0.7, blue: 0.5),
            category: "sentencetransitions",
            tags: [.english, .logic]
        ),
        
        CategoryData(
            title: "Letter Set",
            subtitle: "Hunt for words in set of letters",
            icon: "textformat.characters",
            backgroundColor: Color(red: 0.3, green: 0.8, blue: 0.5),
            category: "letterset",
            tags: [.word, .english]
        ),

        CategoryData(
            title: "Waldo Puzzle",
            subtitle: "Find hidden objects in images",
            icon: "eye.fill",
            backgroundColor: Color(red: 0.9, green: 0.4, blue: 0.6),
            category: "waldopuzzle",
            tags: [.visual, .logic]
        ),
        
        CategoryData(
            title: "Music Match",
            subtitle: "Match Songs to Titles",
            icon: "music.note",
            backgroundColor: Color(red: 0.8, green: 0.2, blue: 0.8),
            category: "musicidentification",
            tags: [.logic, .memory]
        ),
        
        CategoryData(
            title: "Flow Puzzle",
            subtitle: "Connect matching colors",
            icon: "arrow.triangle.branch",
            backgroundColor: Color(red: 0.29, green: 0.56, blue: 0.89),
            category: "flowpuzzle",
            tags: [.logic, .visual]
        ),
        
        CategoryData(
            title: "Odd One Out",
            subtitle: "Find the Different One",
            icon: "eye.circle.fill",
            backgroundColor: Color(red: 0.91, green: 0.30, blue: 0.24),
            category: "oddoneout",
            tags: [.logic, .visual]
        ),
        
        CategoryData(
            title: "Image Puzzle",
            subtitle: "Assemble Image Pieces",
            icon: "puzzlepiece.extension.fill",
            backgroundColor: Color(red: 0.8, green: 0.4, blue: 0.9),
            category: "imagepuzzle",
            tags: [.logic, .memory]
        ),
        
        CategoryData(
            
            title: "Symmetry",
            subtitle: "Mirror & Copy Patterns",
            icon: "arrow.left.arrow.right",
            backgroundColor: Color(red: 0.40, green: 0.23, blue: 0.72),
            category: "symmetry",
            tags: [.logic, .memory, .speed]
        ),
        
        CategoryData(
             title: "Math Expression",
             subtitle: "Solve falling equations",
             icon: "function",
             backgroundColor: Color(red: 0.9, green: 0.4, blue: 0.6),
             category: "mathexpression",
             tags: [.math, .logic]
         ),
        
        CategoryData(
            title: "Image Question",
            subtitle: "Study & answer questions",
            icon: "photo.on.rectangle.angled",
            backgroundColor: Color(red: 0.4, green: 0.7, blue: 0.9),
            category: "imagequestion",
            tags: [.memory]
        ),
        
        CategoryData(
            title: "Triangle Dot Memory",
            subtitle: "Remember dot positions",
            icon: "triangle.fill",
            backgroundColor: .purple,
            category: "triangledotmemory",
            tags: [.memory]
        ),
        
        CategoryData(
            title: "Progressive Reveal",
            subtitle: "Guess before picture reveals",
            icon: "puzzlepiece.extension.fill",
            backgroundColor: Color(red: 0.8, green: 0.4, blue: 0.9),
            category: "progressiverevelation",
            tags: [.logic]
        ),
        
        CategoryData(
            title: "Find Object",
            subtitle: "AI Vision Discovery",
            icon: "eye.fill",
            backgroundColor: Color(red: 0.9, green: 0.4, blue: 0.6),
            category: "find_object",
            tags: [.logic, .memory]
        ),
        
        CategoryData(
            title: "Word Snake",
            subtitle: "Find Connected Words",
            icon: "link",
            backgroundColor: Color(red: 0.3, green: 0.8, blue: 0.5),
            category: "wordsnake",
            tags: [.word, .logic]
        ),
        
        CategoryData(
            title: "Crypto Puzzle",
            subtitle: "Decode Hidden Messages",
            icon: "lock.fill",
            backgroundColor: Color(red: 0.2, green: 0.1, blue: 0.4),
            category: "crypto",
            tags: [.logic, .word]
        ),
        
        CategoryData(
            title: "Which Is Real?",
            subtitle: "Identify AI vs Real Images",
            icon: "photo.fill.on.rectangle.fill",
            backgroundColor: Color(red: 0.42, green: 0.36, blue: 0.90),
            category: "realorai",
            tags: [.logic]
        ),

        CategoryData(title: "Math", subtitle: "Mathematical Challenges", icon: "function", backgroundColor: .mathCardColor, category: "math", tags: [.math]),
        
        CategoryData(title: "Memory Story", subtitle: "Audio Memory Challenges", icon: "brain.head.profile", backgroundColor: Color(red: 0.6, green: 0.2, blue: 0.8), category: "memorystory", tags: [.memory, .english]),
        
        CategoryData(title: "Memory Squares", subtitle: "Pattern Recognition", icon: "grid.circle.fill", backgroundColor: Color(red: 0.4, green: 0.6, blue: 0.8), category: "memorysquares", tags: [.memory, .logic]),
        
        CategoryData(title: "Story Puzzle", subtitle: "Narrative Mysteries", icon: "book.fill", backgroundColor: .storyCardColor, category: "storyPuzzle", tags: [.english, .logic]),
        
        CategoryData(title: "Anagram", subtitle: "Word Scrambles", icon: "textformat.abc", backgroundColor: .anagramCardColor, category: "anagram", tags: [.word, .english]),
        
        CategoryData(title: "Synonyms", subtitle: "Group Similar Words", icon: "link.circle.fill", backgroundColor: Color(red: 0.6, green: 0.8, blue: 0.4), category: "synonyms", tags: [.word, .english]),
        
        CategoryData(title: "Antonyms", subtitle: "Match Opposite Words", icon: "arrow.left.arrow.right.circle.fill", backgroundColor: .antonymCardColor, category: "antonyms", tags: [.word, .english]),
        
        CategoryData(title: "Trivia", subtitle: "General Knowledge", icon: "questionmark.circle.fill", backgroundColor: .triviaCardColor, category: "trivia", tags: [.logic, .english]),
        
        CategoryData(title: "Average", subtitle: "Calculate Averages", icon: "chart.bar.fill", backgroundColor: .averageCardColor, category: "average", tags: [.math]),
        
        CategoryData(title: "Division", subtitle: "Master Division", icon: "divide.circle.fill", backgroundColor: .divisionCardColor, category: "division", tags: [.math]),
        
        CategoryData(title: "Estimation", subtitle: "Chart Estimation", icon: "chart.line.uptrend.xyaxis", backgroundColor: .estimationCardColor, category: "mathestimation", tags: [.math, .logic]),
        
        CategoryData(title: "Percentage", subtitle: "Percentage Calculations", icon: "percent", backgroundColor: .percentageCardColor, category: "percentages", tags: [.math]),
        
        CategoryData(title: "Discounts", subtitle: "Price Ordering", icon: "tag.fill", backgroundColor: .discountsCardColor, category: "discounts", tags: [.math, .logic]),
        
        CategoryData(
            title: "Memory Pairs",
            subtitle: "Recall Previous Screen",
            icon: "brain.head.profile",
            backgroundColor: Color(red: 0.4, green: 0.2, blue: 0.8),
            category: "memorypreviouspair",
            tags: [.memory, .logic]
        ),
        
        CategoryData(
            title: "Memory Single",
            subtitle: "Recall Previous Symbol",
            icon: "brain.head.profile",
            backgroundColor: Color(red: 0.7, green: 0.2, blue: 0.8),
            category: "memoryprevioussingle",
            tags: [.memory, .logic]
        ),
        
        CategoryData(
            title: "Dual Task",
            subtitle: "Switch Between Tasks",
            icon: "brain.head.profile.fill",
            backgroundColor: Color(red: 0.6, green: 0.2, blue: 0.9),
            category: "dualtask",
            tags: [.memory, .logic]
        ),
        
        CategoryData(
            title: "Geography Cities",
            subtitle: "Place Cities on World Map",
            icon: "building.2.fill",
            backgroundColor: Color(red: 0.2, green: 0.6, blue: 0.8),
            category: "geography_cities",
            tags: [.logic, .memory]
        ),
        
        CategoryData(
            title: "Geography Countries",
            subtitle: "Place Countries on World Map",
            icon: "map.fill",
            backgroundColor: Color(red: 0.8, green: 0.4, blue: 0.2),
            category: "geography_countries",
            tags: [.logic, .memory]
        ),

        CategoryData(
            title: "Color Text Match",
            subtitle: "Match Meaning & Color",
            icon: "paintpalette.fill",
            backgroundColor: Color(red: 0.9, green: 0.3, blue: 0.4),
            category: "colortextmatching",
            tags: [.logic, .memory]
        ),
        
        CategoryData(
            title: "Math Crossword",
            subtitle: "Number Equations Grid",
            icon: "grid.circle.fill",
            backgroundColor: Color(red: 0.2, green: 0.6, blue: 0.8),
            category: "mathcrossword",
            tags: [.math, .logic]
        ),
        
        CategoryData(
            title: "Context Switch",
            subtitle: "Memory & Interference",
            icon: "brain.head.profile.fill",
            backgroundColor: Color(red: 0.4, green: 0.2, blue: 0.8),
            category: "contextswitch",
            tags: [.memory, .logic]
        ),
        
        CategoryData(
            title: "Pinball Deflector",
            subtitle: "Physics Prediction",
            icon: "target",
            backgroundColor: Color(red: 0.9, green: 0.3, blue: 0.5),
            category: "pinballdeflector",
            tags: [.logic, .math]
        ),
        
        CategoryData(
            title: "Color Shape Match",
            subtitle: "Visual Recognition",
            icon: "paintbrush.fill",
            backgroundColor: Color(red: 0.9, green: 0.4, blue: 0.7),
            category: "colorshapematching",
            tags: [.logic, .memory]
        ),
        
        CategoryData(
            title: "Image Vortex",
            subtitle: "Find the New Image",
            icon: "sparkles",
            backgroundColor: Color(red: 0.3, green: 0.6, blue: 0.9),
            category: "imagevortex",
            tags: [.memory, .logic]
        ),
        
        CategoryData(
            title: "Math Comparison",
            subtitle: "Compare Mathematical Values",
            icon: "equal.circle.fill",
            backgroundColor: Color(red: 0.2, green: 0.6, blue: 0.9),
            category: "mathcomparison",
            tags: [.math, .logic]
        ),
        
        CategoryData(
            title: "Number Sequence",
            subtitle: "Tap Numbers in Order",
            icon: "123.rectangle.fill",
            backgroundColor: Color(red: 0.3, green: 0.7, blue: 0.4),
            category: "numbersequence",
            tags: [.math, .logic]
        ),
        
        CategoryData(
            title: "Number Sum",
            subtitle: "Find Numbers That Add Up",
            icon: "plus.circle.fill",
            backgroundColor: Color(red: 0.9, green: 0.5, blue: 0.3),
            category: "numbersum",
            tags: [.math, .logic]
        ),
        
        CategoryData(
            title: "Symbol Swipe",
            subtitle: "Swipe Based on Symbols",
            icon: "arrow.left.arrow.right.circle.fill",
            backgroundColor: Color(red: 0.6, green: 0.3, blue: 0.8),
            category: "symbolswipe",
            tags: [.memory, .logic]
        ),
        
        CategoryData(
            title: "Unique Object",
            subtitle: "Find the Different One",
            icon: "eye.circle.fill",
            backgroundColor: Color(red: 0.8, green: 0.4, blue: 0.6),
            category: "uniqueobject",
            tags: [.logic, .memory]
        ),
        
        CategoryData(
            title: "Word Search",
            subtitle: "Find Hidden Words",
            icon: "text.magnifyingglass",
            backgroundColor: Color(red: 0.5, green: 0.8, blue: 0.6),
            category: "wordsearch",
            tags: [.word, .english]
        ),
        
        CategoryData(title: "Purchasing", subtitle: "Subscription Calculations", icon: "creditcard.fill", backgroundColor: .purchasingCardColor, category: "purchasing", tags: [.math, .logic]),
        
        CategoryData(title: "Crossword", subtitle: "Word Puzzles", icon: "grid.circle.fill", backgroundColor: .crosswordCardColor, category: "crossword", tags: [.word, .english, .logic]),
        
        CategoryData(title: "Memory Sequencing", subtitle: "Audio & Categorization", icon: "brain.head.profile", backgroundColor: .memoryRetentionCardColor, category: "memorysequencing", tags: [.memory, .logic]),
        
        CategoryData(title: "Word Prefix", subtitle: "Find words with prefix", icon: "textformat.alt", backgroundColor: Color(red: 0.6, green: 0.3, blue: 0.9), category: "wordprefix", tags: [.word, .english]),
        
        CategoryData(title: "Memory Retention", subtitle: "Audio & Categorization", icon: "brain.head.profile", backgroundColor: .memoryRetentionCardColor, category: "memoryretention", tags: [.memory, .english]),
        
        CategoryData(title: "Conversion", subtitle: "Unit Comparisons", icon: "arrow.left.arrow.right", backgroundColor: .conversionCardColor, category: "conversion", tags: [.math, .logic]),
        
        CategoryData(title: "Word Connotations", subtitle: "Sort words by tone", icon: "brain.head.profile", backgroundColor: .connotationCardColor, category: "connotationwords", tags: [.word, .english]),
        
        CategoryData(title: "Subtraction", subtitle: "Find the difference", icon: "minus.circle.fill", backgroundColor: .subtractionCardColor, category: "subtraction", tags: [.math]),
        
        CategoryData(title: "Tip Calculation", subtitle: "Calculate tips", icon: "dollarsign.circle.fill", backgroundColor: .tippingCardColor, category: "mathtipping", tags: [.math])
    ]
}

// MARK: - Color Extensions
extension Color {
    static let mathCardColor = Color(red: 0.3, green: 0.7, blue: 0.9)
    static let storyCardColor = Color(red: 0.8, green: 0.4, blue: 0.9)
    static let anagramCardColor = Color(red: 0.9, green: 0.6, blue: 0.3)
    static let triviaCardColor = Color(red: 0.4, green: 0.8, blue: 0.5)
    static let averageCardColor = Color(red: 0.9, green: 0.5, blue: 0.6)
    static let divisionCardColor = Color(red: 0.6, green: 0.5, blue: 0.9)
    static let estimationCardColor = Color(red: 0.5, green: 0.8, blue: 0.8)
    static let percentageCardColor = Color(red: 0.9, green: 0.7, blue: 0.4)
    static let discountsCardColor = Color(red: 0.7, green: 0.3, blue: 0.5)
    static let purchasingCardColor = Color(red: 0.4, green: 0.6, blue: 0.9)
    static let conversionCardColor = Color(red: 0.6, green: 0.8, blue: 0.3)
    static let connotationCardColor = Color(red: 0.8, green: 0.3, blue: 0.7)
    static let subtractionCardColor = Color(red: 0.3, green: 0.5, blue: 0.8)
    static let tippingCardColor = Color(red: 0.9, green: 0.4, blue: 0.3)
    static let crosswordCardColor = Color(red: 0.0, green: 0.737, blue: 0.831)
    static let memoryRetentionCardColor = Color(red: 0.10, green: 0.10, blue: 0.18)
    static let antonymCardColor = Color(red: 0.8, green: 0.4, blue: 0.6)
    static let pinballDeflectorCardColor = Color(red: 0.9, green: 0.3, blue: 0.5)
}
