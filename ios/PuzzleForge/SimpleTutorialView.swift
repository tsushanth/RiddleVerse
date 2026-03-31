//
//  SimpleTutorialView.swift
//  PuzzleForge
//
//  Simple single-screen tutorial for puzzles that don't need complex interactive tutorials
//  Matches Android SimpleTutorialScreen pattern
//

import SwiftUI

// MARK: - Tutorial Content Model
struct SimpleTutorialContent {
    let title: String
    let emoji: String
    let instruction: String
    let tip: String?
    let gradientColors: [Color]

    init(
        title: String,
        emoji: String,
        instruction: String,
        tip: String? = nil,
        gradientColors: [Color] = [Color(red: 0.4, green: 0.49, blue: 0.92), Color(red: 0.46, green: 0.29, blue: 0.64)]
    ) {
        self.title = title
        self.emoji = emoji
        self.instruction = instruction
        self.tip = tip
        self.gradientColors = gradientColors
    }
}

// MARK: - Simple Tutorial View
struct SimpleTutorialView: View {
    let content: SimpleTutorialContent
    let onComplete: () -> Void
    let onSkip: () -> Void

    var body: some View {
        ZStack {
            // Background gradient
            LinearGradient(
                colors: content.gradientColors,
                startPoint: .top,
                endPoint: .bottom
            )
            .ignoresSafeArea()

            // Close button
            VStack {
                HStack {
                    Spacer()
                    Button(action: onSkip) {
                        Image(systemName: "xmark")
                            .font(.title2)
                            .foregroundColor(.white.opacity(0.8))
                            .padding(16)
                    }
                }
                Spacer()
            }

            // Main content
            VStack(spacing: 0) {
                Spacer()

                // Emoji
                Text(content.emoji)
                    .font(.system(size: 80))

                Spacer().frame(height: 24)

                // Title
                Text(content.title)
                    .font(.system(size: 28, weight: .bold))
                    .foregroundColor(.white)
                    .multilineTextAlignment(.center)

                Spacer().frame(height: 32)

                // Instruction card
                VStack(spacing: 12) {
                    Text("How to Play")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(Color(red: 0.48, green: 0.12, blue: 0.64))

                    Text(content.instruction)
                        .font(.system(size: 16))
                        .foregroundColor(Color(red: 0.2, green: 0.2, blue: 0.2))
                        .multilineTextAlignment(.center)
                        .lineSpacing(6)

                    // Optional tip
                    if let tip = content.tip {
                        HStack(alignment: .center, spacing: 8) {
                            Text("💡")
                                .font(.system(size: 20))
                            Text(tip)
                                .font(.system(size: 14))
                                .foregroundColor(Color(red: 0.47, green: 0.33, blue: 0.28))
                        }
                        .padding(12)
                        .background(Color(red: 1.0, green: 0.95, blue: 0.88))
                        .cornerRadius(8)
                    }
                }
                .padding(24)
                .background(Color.white.opacity(0.95))
                .cornerRadius(16)
                .padding(.horizontal, 32)

                Spacer().frame(height: 40)

                // Start button
                Button(action: onComplete) {
                    Text("Got it! Let's Play")
                        .font(.system(size: 18, weight: .bold))
                        .foregroundColor(Color(red: 0.48, green: 0.12, blue: 0.64))
                        .frame(maxWidth: .infinity)
                        .frame(height: 56)
                        .background(Color.white)
                        .cornerRadius(28)
                }
                .padding(.horizontal, 32)

                Spacer().frame(height: 12)

                // Skip button
                Button(action: onSkip) {
                    Text("Skip Tutorial")
                        .font(.system(size: 14))
                        .foregroundColor(.white.opacity(0.7))
                }

                Spacer()
            }
        }
    }
}

// MARK: - Tutorial Content Library
struct TutorialContentLibrary {

    // MARK: - Math Tutorials
    static let numberSum = SimpleTutorialContent(
        title: "Number Sum",
        emoji: "🔢",
        instruction: "Tap numbers that add up to the target sum. Select multiple numbers until their total matches the goal.",
        tip: "Start with larger numbers to get close, then use smaller ones to hit exact target.",
        gradientColors: [Color(red: 0.07, green: 0.6, blue: 0.56), Color(red: 0.22, green: 0.94, blue: 0.49)]
    )

    static let averages = SimpleTutorialContent(
        title: "Averages",
        emoji: "📊",
        instruction: "Calculate the average of the given numbers. Add all numbers together and divide by how many there are.",
        tip: "Round to the nearest whole number if needed.",
        gradientColors: [Color(red: 0.4, green: 0.49, blue: 0.92), Color(red: 0.46, green: 0.29, blue: 0.64)]
    )

    static let percentage = SimpleTutorialContent(
        title: "Percentage",
        emoji: "💯",
        instruction: "Calculate the percentage of a given number. Remember: X% of Y = (X × Y) ÷ 100.",
        tip: "For 10%, just move the decimal point one place left.",
        gradientColors: [Color(red: 0.91, green: 0.3, blue: 0.24), Color(red: 0.75, green: 0.22, blue: 0.17)]
    )

    static let mathComparison = SimpleTutorialContent(
        title: "Math Comparison",
        emoji: "⚖️",
        instruction: "Compare two mathematical expressions. Determine which is greater, less, or if they're equal.",
        tip: "Calculate each side separately first.",
        gradientColors: [Color(red: 0.16, green: 0.5, blue: 0.73), Color(red: 0.2, green: 0.6, blue: 0.86)]
    )

    static let mathExpression = SimpleTutorialContent(
        title: "Math Expression",
        emoji: "🧮",
        instruction: "Solve the mathematical expression shown. Follow order of operations: parentheses, exponents, multiplication/division, addition/subtraction.",
        tip: "Remember PEMDAS!",
        gradientColors: [Color(red: 0.56, green: 0.27, blue: 0.68), Color(red: 0.67, green: 0.4, blue: 0.8)]
    )

    static let mathCrossword = SimpleTutorialContent(
        title: "Math Crossword",
        emoji: "➕",
        instruction: "Fill in the grid so each row and column forms a valid math equation. Use numbers and operators.",
        tip: "Start with equations you're most confident about.",
        gradientColors: [Color(red: 0.95, green: 0.61, blue: 0.07), Color(red: 0.88, green: 0.44, blue: 0.07)]
    )

    static let tipBubble = SimpleTutorialContent(
        title: "Tip Calculator",
        emoji: "💵",
        instruction: "Calculate the tip amount for a given bill. Multiply the bill by the tip percentage.",
        tip: "For 20% tip: divide bill by 5. For 15%: divide by 10, then add half.",
        gradientColors: [Color(red: 0.18, green: 0.8, blue: 0.44), Color(red: 0.1, green: 0.74, blue: 0.61)]
    )

    static let division = SimpleTutorialContent(
        title: "Division",
        emoji: "➗",
        instruction: "Solve division problems quickly and accurately. Enter the quotient (result of division).",
        tip: "Use multiplication to check: quotient × divisor = dividend.",
        gradientColors: [Color(red: 0.4, green: 0.49, blue: 0.92), Color(red: 0.46, green: 0.29, blue: 0.64)]
    )

    static let subtraction = SimpleTutorialContent(
        title: "Subtraction",
        emoji: "➖",
        instruction: "Quickly solve subtraction problems. Calculate the difference between two numbers.",
        tip: "Count up from the smaller number for an easier approach.",
        gradientColors: [Color(red: 0.91, green: 0.3, blue: 0.24), Color(red: 0.75, green: 0.22, blue: 0.17)]
    )

    static let discounts = SimpleTutorialContent(
        title: "Discounts",
        emoji: "🏷️",
        instruction: "Calculate the final price after a discount. Subtract the discount percentage from 100%, then multiply by the original price.",
        tip: "30% off = pay 70% of original price.",
        gradientColors: [Color(red: 0.95, green: 0.26, blue: 0.21), Color(red: 0.96, green: 0.49, blue: 0.0)]
    )

    static let conversion = SimpleTutorialContent(
        title: "Unit Conversion",
        emoji: "📐",
        instruction: "Convert between different units of measurement. Pay attention to the conversion factor provided.",
        tip: "Write out the conversion factor to avoid mistakes.",
        gradientColors: [Color(red: 0.0, green: 0.74, blue: 0.83), Color(red: 0.0, green: 0.59, blue: 0.53)]
    )

    static let mathEstimation = SimpleTutorialContent(
        title: "Math Estimation",
        emoji: "🎯",
        instruction: "Estimate the answer without exact calculation. Round numbers to make mental math easier.",
        tip: "Round to nearest 10 or 100 for quick estimates.",
        gradientColors: [Color(red: 0.4, green: 0.73, blue: 0.42), Color(red: 0.22, green: 0.56, blue: 0.24)]
    )

    // MARK: - Memory Tutorials
    static let memoryStory = SimpleTutorialContent(
        title: "Memory Story",
        emoji: "📖",
        instruction: "Read the story carefully, then answer questions about details. Pay attention to names, places, and events.",
        tip: "Create mental images of the story to remember better.",
        gradientColors: [Color(red: 0.56, green: 0.27, blue: 0.68), Color(red: 0.67, green: 0.4, blue: 0.8)]
    )

    static let memorySequencing = SimpleTutorialContent(
        title: "Memory Sequencing",
        emoji: "🔄",
        instruction: "Remember the sequence of items shown, then recreate them in the correct order.",
        tip: "Create a story linking the items together.",
        gradientColors: [Color(red: 0.16, green: 0.71, blue: 0.96), Color(red: 0.13, green: 0.59, blue: 0.95)]
    )

    static let memoryRetention = SimpleTutorialContent(
        title: "Memory Retention",
        emoji: "🧠",
        instruction: "Study the information shown, then recall specific details when asked.",
        tip: "Focus on unusual or distinctive features.",
        gradientColors: [Color(red: 0.93, green: 0.26, blue: 0.35), Color(red: 0.7, green: 0.19, blue: 0.26)]
    )

    static let memoryPreviousSingle = SimpleTutorialContent(
        title: "Memory Previous",
        emoji: "⏮️",
        instruction: "Remember what was shown previously. Compare current item to the one before.",
        tip: "Always keep the previous item fresh in your mind.",
        gradientColors: [Color(red: 0.4, green: 0.49, blue: 0.92), Color(red: 0.46, green: 0.29, blue: 0.64)]
    )

    static let memoryPreviousPair = SimpleTutorialContent(
        title: "Memory Pairs",
        emoji: "👯",
        instruction: "Remember pairs of items shown previously. Match or compare pairs from memory.",
        tip: "Create associations between paired items.",
        gradientColors: [Color(red: 0.95, green: 0.61, blue: 0.07), Color(red: 0.88, green: 0.44, blue: 0.07)]
    )

    static let triangleDotMemory = SimpleTutorialContent(
        title: "Triangle Dot Memory",
        emoji: "🔺",
        instruction: "Remember the pattern of dots within triangles. Recreate the pattern from memory.",
        tip: "Count dots in each section of the triangle.",
        gradientColors: [Color(red: 0.56, green: 0.27, blue: 0.68), Color(red: 0.67, green: 0.4, blue: 0.8)]
    )

    static let memorySquares = SimpleTutorialContent(
        title: "Memory Squares",
        emoji: "⬛",
        instruction: "Watch the sequence of squares that light up, then repeat the pattern in the same order.",
        tip: "Use spatial memory - imagine the grid as a clock or keypad.",
        gradientColors: [Color(red: 0.16, green: 0.5, blue: 0.73), Color(red: 0.2, green: 0.6, blue: 0.86)]
    )

    // MARK: - Word/Language Tutorials
    static let swipeWord = SimpleTutorialContent(
        title: "Swipe Word",
        emoji: "👆",
        instruction: "Swipe in the direction that matches the word's meaning or category shown.",
        tip: "Read quickly but carefully before swiping.",
        gradientColors: [Color(red: 0.07, green: 0.6, blue: 0.56), Color(red: 0.22, green: 0.94, blue: 0.49)]
    )

    static let jumbleInput = SimpleTutorialContent(
        title: "Word Jumble",
        emoji: "🔤",
        instruction: "Unscramble the jumbled letters to form a word. Type your answer when ready.",
        tip: "Look for common letter patterns like 'tion', 'ing', 'ed'.",
        gradientColors: [Color(red: 0.4, green: 0.49, blue: 0.92), Color(red: 0.46, green: 0.29, blue: 0.64)]
    )

    static let wordPrefix = SimpleTutorialContent(
        title: "Word Prefix",
        emoji: "🔡",
        instruction: "Find words that share the same prefix. Identify the common beginning of related words.",
        tip: "Common prefixes: un-, re-, pre-, dis-.",
        gradientColors: [Color(red: 0.56, green: 0.27, blue: 0.68), Color(red: 0.67, green: 0.4, blue: 0.8)]
    )

    static let letterSet = SimpleTutorialContent(
        title: "Letter Set",
        emoji: "🅰️",
        instruction: "Form words using only the letters provided. Each letter can only be used once per word.",
        tip: "Start with vowels - every word needs at least one.",
        gradientColors: [Color(red: 0.95, green: 0.61, blue: 0.07), Color(red: 0.88, green: 0.44, blue: 0.07)]
    )

    static let sentenceTransitions = SimpleTutorialContent(
        title: "Sentence Transitions",
        emoji: "↔️",
        instruction: "Choose the correct transition word that best connects the sentences or ideas.",
        tip: "Think about the relationship: contrast, addition, cause, or time.",
        gradientColors: [Color(red: 0.16, green: 0.71, blue: 0.96), Color(red: 0.13, green: 0.59, blue: 0.95)]
    )

    static let synonyms = SimpleTutorialContent(
        title: "Synonyms",
        emoji: "📝",
        instruction: "Match words with similar meanings. Select the word that means the same as the given word.",
        tip: "Think of how you'd use the word in a sentence.",
        gradientColors: [Color(red: 0.4, green: 0.49, blue: 0.92), Color(red: 0.46, green: 0.29, blue: 0.64)]
    )

    static let antonyms = SimpleTutorialContent(
        title: "Antonyms",
        emoji: "🔄",
        instruction: "Find words with opposite meanings. Select the word that means the opposite of the given word.",
        tip: "Think of the word's exact opposite, not just a different word.",
        gradientColors: [Color(red: 0.91, green: 0.3, blue: 0.24), Color(red: 0.75, green: 0.22, blue: 0.17)]
    )

    static let antonymBalloon = SimpleTutorialContent(
        title: "Antonym Balloon",
        emoji: "🎈",
        instruction: "Pop the balloon containing the antonym (opposite) of the word shown. Be quick!",
        tip: "Focus on the word meaning, not the balloon position.",
        gradientColors: [Color(red: 0.93, green: 0.26, blue: 0.35), Color(red: 0.7, green: 0.19, blue: 0.26)]
    )

    static let wordSearch = SimpleTutorialContent(
        title: "Word Search",
        emoji: "🔍",
        instruction: "Find hidden words in the letter grid. Words can go horizontally, vertically, or diagonally.",
        tip: "Scan for the first letter of each word, then check surrounding letters.",
        gradientColors: [Color(red: 0.07, green: 0.6, blue: 0.56), Color(red: 0.22, green: 0.94, blue: 0.49)]
    )

    static let anagram = SimpleTutorialContent(
        title: "Anagram",
        emoji: "🔀",
        instruction: "Rearrange the scrambled letters to form a valid word. All letters must be used.",
        tip: "Try different letter combinations starting with common patterns.",
        gradientColors: [Color(red: 0.56, green: 0.27, blue: 0.68), Color(red: 0.67, green: 0.4, blue: 0.8)]
    )

    // MARK: - Matching/Pattern Tutorials
    static let symbolSwipe = SimpleTutorialContent(
        title: "Symbol Swipe",
        emoji: "⭐",
        instruction: "Swipe the symbol in the correct direction based on the rule shown. Pay attention to the pattern.",
        tip: "Remember the rule for each symbol type.",
        gradientColors: [Color(red: 0.95, green: 0.77, blue: 0.06), Color(red: 0.95, green: 0.61, blue: 0.07)]
    )

    static let colorShapeMatching = SimpleTutorialContent(
        title: "Color Shape Match",
        emoji: "🔷",
        instruction: "Match items based on color, shape, or both. Follow the specific matching rule given.",
        tip: "Focus on one attribute at a time.",
        gradientColors: [Color(red: 0.0, green: 0.74, blue: 0.83), Color(red: 0.0, green: 0.59, blue: 0.53)]
    )

    static let imageMatch = SimpleTutorialContent(
        title: "Image Match",
        emoji: "🖼️",
        instruction: "Find matching pairs of images. Remember positions to make matches quickly.",
        tip: "Create mental associations for image locations.",
        gradientColors: [Color(red: 0.4, green: 0.49, blue: 0.92), Color(red: 0.46, green: 0.29, blue: 0.64)]
    )

    static let match = SimpleTutorialContent(
        title: "Match",
        emoji: "🎯",
        instruction: "Match items from one column to the corresponding items in another column.",
        tip: "Start with matches you're most confident about.",
        gradientColors: [Color(red: 0.16, green: 0.71, blue: 0.96), Color(red: 0.13, green: 0.59, blue: 0.95)]
    )

    static let symmetry = SimpleTutorialContent(
        title: "Symmetry",
        emoji: "🪞",
        instruction: "Identify or create symmetrical patterns. The pattern should mirror across the line.",
        tip: "Fold the image mentally along the line of symmetry.",
        gradientColors: [Color(red: 0.56, green: 0.27, blue: 0.68), Color(red: 0.67, green: 0.4, blue: 0.8)]
    )

    static let colorTextMatching = SimpleTutorialContent(
        title: "Color Text Match",
        emoji: "🎨",
        instruction: "Match the color of the text, not the word itself. This tests your focus and attention.",
        tip: "Ignore what the word says - focus only on the color you see.",
        gradientColors: [Color(red: 0.95, green: 0.26, blue: 0.21), Color(red: 0.96, green: 0.49, blue: 0.0)]
    )

    static let numberSequence = SimpleTutorialContent(
        title: "Number Sequence",
        emoji: "1️⃣",
        instruction: "Find the pattern in the number sequence and predict the next number.",
        tip: "Look for addition, subtraction, multiplication, or alternating patterns.",
        gradientColors: [Color(red: 0.07, green: 0.6, blue: 0.56), Color(red: 0.22, green: 0.94, blue: 0.49)]
    )

    // MARK: - Visual/Image Tutorials
    static let imageVortex = SimpleTutorialContent(
        title: "Image Vortex",
        emoji: "🌀",
        instruction: "Identify the image as it spirals and distorts. Act quickly before it disappears.",
        tip: "Focus on distinctive features that remain visible.",
        gradientColors: [Color(red: 0.56, green: 0.27, blue: 0.68), Color(red: 0.67, green: 0.4, blue: 0.8)]
    )

    static let imageQuestion = SimpleTutorialContent(
        title: "Image Question",
        emoji: "❓",
        instruction: "Answer questions about the image shown. Study the image carefully before answering.",
        tip: "Note colors, numbers, positions, and relationships.",
        gradientColors: [Color(red: 0.4, green: 0.49, blue: 0.92), Color(red: 0.46, green: 0.29, blue: 0.64)]
    )

    static let findDifferences = SimpleTutorialContent(
        title: "Find Differences",
        emoji: "🔎",
        instruction: "Compare two images and tap on the differences. Look carefully at every detail.",
        tip: "Scan systematically from one corner to the other.",
        gradientColors: [Color(red: 0.95, green: 0.61, blue: 0.07), Color(red: 0.88, green: 0.44, blue: 0.07)]
    )

    static let findObject = SimpleTutorialContent(
        title: "Find Object",
        emoji: "🎯",
        instruction: "Locate the specific object hidden in the scene. Look carefully throughout the image.",
        tip: "Check edges and corners where objects often hide.",
        gradientColors: [Color(red: 0.16, green: 0.71, blue: 0.96), Color(red: 0.13, green: 0.59, blue: 0.95)]
    )

    static let waldoPuzzle = SimpleTutorialContent(
        title: "Waldo Puzzle",
        emoji: "🔴",
        instruction: "Find the target character or object in a crowded scene. Scan carefully!",
        tip: "Look for distinctive features like colors or patterns.",
        gradientColors: [Color(red: 0.91, green: 0.3, blue: 0.24), Color(red: 0.75, green: 0.22, blue: 0.17)]
    )

    static let uniqueObject = SimpleTutorialContent(
        title: "Unique Object",
        emoji: "⭐",
        instruction: "Find the one object that's different from all the others in the group.",
        tip: "Check size, color, orientation, and small details.",
        gradientColors: [Color(red: 0.07, green: 0.6, blue: 0.56), Color(red: 0.22, green: 0.94, blue: 0.49)]
    )

    static let progressiveReveal = SimpleTutorialContent(
        title: "Progressive Reveal",
        emoji: "🎬",
        instruction: "Identify the image as it's gradually revealed. Guess as early as possible for more points!",
        tip: "Look for distinctive shapes or colors as hints.",
        gradientColors: [Color(red: 0.56, green: 0.27, blue: 0.68), Color(red: 0.67, green: 0.4, blue: 0.8)]
    )

    static let realOrAI = SimpleTutorialContent(
        title: "Real or AI",
        emoji: "🤖",
        instruction: "Determine if the image was created by AI or is a real photograph. Look for telltale signs.",
        tip: "Check hands, text, and unusual patterns - AI often struggles with these.",
        gradientColors: [Color(red: 0.4, green: 0.49, blue: 0.92), Color(red: 0.46, green: 0.29, blue: 0.64)]
    )

    static let imagePuzzle = SimpleTutorialContent(
        title: "Image Puzzle",
        emoji: "🧩",
        instruction: "Analyze the image and answer the question about it. Pay attention to all details.",
        tip: "Look at the entire image before focusing on specifics.",
        gradientColors: [Color(red: 0.95, green: 0.61, blue: 0.07), Color(red: 0.88, green: 0.44, blue: 0.07)]
    )

    // MARK: - Logic/Other Tutorials
    static let flowPuzzle = SimpleTutorialContent(
        title: "Flow Puzzle",
        emoji: "🌊",
        instruction: "Connect matching colors with pipes. Fill the entire grid without crossing paths.",
        tip: "Start with colors that have limited paths.",
        gradientColors: [Color(red: 0.0, green: 0.74, blue: 0.83), Color(red: 0.0, green: 0.59, blue: 0.53)]
    )

    static let contextSwitch = SimpleTutorialContent(
        title: "Context Switch",
        emoji: "🔄",
        instruction: "Follow changing rules throughout the game. Adapt quickly when the rule changes.",
        tip: "Pay attention to rule change announcements.",
        gradientColors: [Color(red: 0.4, green: 0.49, blue: 0.92), Color(red: 0.46, green: 0.29, blue: 0.64)]
    )

    static let dualCard = SimpleTutorialContent(
        title: "Dual Card",
        emoji: "🃏",
        instruction: "Make decisions based on two cards shown. Compare attributes between cards.",
        tip: "Read both cards before making your choice.",
        gradientColors: [Color(red: 0.56, green: 0.27, blue: 0.68), Color(red: 0.67, green: 0.4, blue: 0.8)]
    )

    static let multiMatchMusic = SimpleTutorialContent(
        title: "Music Match",
        emoji: "🎵",
        instruction: "Listen to audio clips and match them to the correct answers. Pay attention to melody and rhythm.",
        tip: "Listen to the full clip before answering.",
        gradientColors: [Color(red: 0.93, green: 0.26, blue: 0.35), Color(red: 0.7, green: 0.19, blue: 0.26)]
    )

    static let geographyCity = SimpleTutorialContent(
        title: "Geography Cities",
        emoji: "🏙️",
        instruction: "Place cities correctly on the world map. Tap the location where the city belongs.",
        tip: "Think about which continent and region first.",
        gradientColors: [Color(red: 0.16, green: 0.71, blue: 0.96), Color(red: 0.13, green: 0.59, blue: 0.95)]
    )

    static let geographyCountry = SimpleTutorialContent(
        title: "Geography Countries",
        emoji: "🌍",
        instruction: "Identify and place countries on the world map. Select the correct region.",
        tip: "Remember neighboring countries as reference points.",
        gradientColors: [Color(red: 0.07, green: 0.6, blue: 0.56), Color(red: 0.22, green: 0.94, blue: 0.49)]
    )

    static let pinballDeflector = SimpleTutorialContent(
        title: "Pinball Deflector",
        emoji: "🎱",
        instruction: "Angle the deflectors to guide the ball to the target. Think about physics and angles.",
        tip: "The ball bounces at equal angles.",
        gradientColors: [Color(red: 0.95, green: 0.77, blue: 0.06), Color(red: 0.95, green: 0.61, blue: 0.07)]
    )

    // MARK: - Basic Types
    static let qa = SimpleTutorialContent(
        title: "Q&A",
        emoji: "❓",
        instruction: "Read the question carefully and type your answer. Spelling counts!",
        tip: "Take your time to understand the question fully.",
        gradientColors: [Color(red: 0.4, green: 0.49, blue: 0.92), Color(red: 0.46, green: 0.29, blue: 0.64)]
    )

    static let multipleChoice = SimpleTutorialContent(
        title: "Multiple Choice",
        emoji: "📋",
        instruction: "Select the correct answer from the options provided. Only one answer is correct.",
        tip: "Eliminate obviously wrong answers first.",
        gradientColors: [Color(red: 0.16, green: 0.5, blue: 0.73), Color(red: 0.2, green: 0.6, blue: 0.86)]
    )

    static let trivia = SimpleTutorialContent(
        title: "Trivia",
        emoji: "🧠",
        instruction: "Answer trivia questions on various topics. Test your general knowledge!",
        tip: "Trust your first instinct - it's often right.",
        gradientColors: [Color(red: 0.56, green: 0.27, blue: 0.68), Color(red: 0.67, green: 0.4, blue: 0.8)]
    )

    // MARK: - Get Tutorial for Puzzle Type
    static func getTutorial(for puzzleType: String) -> SimpleTutorialContent? {
        let normalizedType = puzzleType.lowercased()
            .replacingOccurrences(of: " ", with: "")
            .replacingOccurrences(of: "_", with: "")

        switch normalizedType {
        // Math
        case "numbersum": return numberSum
        case "averages", "average": return averages
        case "percentage", "percentages": return percentage
        case "mathcomparison": return mathComparison
        case "mathexpression": return mathExpression
        case "mathcrossword": return mathCrossword
        case "tipbubble", "mathtipping": return tipBubble
        case "division": return division
        case "subtraction": return subtraction
        case "discounts": return discounts
        case "conversion": return conversion
        case "mathestimation": return mathEstimation

        // Memory
        case "memorystory": return memoryStory
        case "memorysequencing": return memorySequencing
        case "memoryretention": return memoryRetention
        case "memoryprevioussingle": return memoryPreviousSingle
        case "memorypreviouspair": return memoryPreviousPair
        case "triangledotmemory": return triangleDotMemory
        case "memorysquares": return memorySquares

        // Word/Language
        case "swipeword": return swipeWord
        case "jumbleinput": return jumbleInput
        case "wordprefix": return wordPrefix
        case "letterset": return letterSet
        case "sentencetransitions": return sentenceTransitions
        case "synonyms": return synonyms
        case "antonyms": return antonyms
        case "antonymballoon": return antonymBalloon
        case "wordsearch": return wordSearch
        case "anagram": return anagram

        // Matching/Pattern
        case "symbolswipe": return symbolSwipe
        case "colorshapematching": return colorShapeMatching
        case "imagematch": return imageMatch
        case "match": return match
        case "symmetry": return symmetry
        case "colortextmatching": return colorTextMatching
        case "numbersequence": return numberSequence

        // Visual/Image
        case "imagevortex": return imageVortex
        case "imagequestion": return imageQuestion
        case "finddifferences": return findDifferences
        case "findobject": return findObject
        case "waldopuzzle": return waldoPuzzle
        case "uniqueobject": return uniqueObject
        case "progressivereveal": return progressiveReveal
        case "realorai": return realOrAI
        case "imagepuzzle": return imagePuzzle

        // Logic/Other
        case "flowpuzzle": return flowPuzzle
        case "contextswitch": return contextSwitch
        case "dualcard": return dualCard
        case "multimatchmusic": return multiMatchMusic
        case "geographycity", "geographycities": return geographyCity
        case "geographycountry", "geographycountries": return geographyCountry
        case "pinballdeflector": return pinballDeflector

        // Basic types
        case "qa": return qa
        case "multiplechoice": return multipleChoice
        case "trivia": return trivia

        default:
            return nil
        }
    }

    // List of all supported tutorial types
    static let supportedTypes: Set<String> = [
        // Math
        "numbersum", "averages", "average", "percentage", "percentages",
        "mathcomparison", "mathexpression", "mathcrossword", "tipbubble", "mathtipping",
        "division", "subtraction", "discounts", "conversion", "mathestimation",
        // Memory
        "memorystory", "memorysequencing", "memoryretention",
        "memoryprevioussingle", "memorypreviouspair", "triangledotmemory", "memorysquares",
        // Word/Language
        "swipeword", "jumbleinput", "wordprefix", "letterset", "sentencetransitions",
        "synonyms", "antonyms", "antonymballoon", "wordsearch", "anagram",
        // Matching/Pattern
        "symbolswipe", "colorshapematching", "imagematch", "match",
        "symmetry", "colortextmatching", "numbersequence",
        // Visual/Image
        "imagevortex", "imagequestion", "finddifferences", "findobject",
        "waldopuzzle", "uniqueobject", "progressivereveal", "realorai", "imagepuzzle",
        // Logic/Other
        "flowpuzzle", "contextswitch", "dualcard", "multimatchmusic",
        "geographycity", "geographycities", "geographycountry", "geographycountries",
        "pinballdeflector",
        // Basic types
        "qa", "multiplechoice", "trivia",
        // Complex tutorials (handled separately)
        "crossword", "crypto", "wordsnake"
    ]

    static func hasSimpleTutorial(for puzzleType: String) -> Bool {
        let normalizedType = puzzleType.lowercased()
            .replacingOccurrences(of: " ", with: "")
            .replacingOccurrences(of: "_", with: "")
        return getTutorial(for: normalizedType) != nil
    }
}

// MARK: - Preview
struct SimpleTutorialView_Previews: PreviewProvider {
    static var previews: some View {
        SimpleTutorialView(
            content: TutorialContentLibrary.numberSum,
            onComplete: { print("Complete") },
            onSkip: { print("Skip") }
        )
    }
}
