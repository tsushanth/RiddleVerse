//
//  HomeViewDestinations.swift
//  PuzzleForge
//
//  Created by Sushanth Tiruvaipati on 5/23/25.
//

import SwiftUI

extension HomeView {
    
    @ViewBuilder
    var qaPuzzleDestination: some View {
        if let puzzle = selectedPuzzle {
            QAPuzzleView(
                viewModel: QAPuzzleViewModel(
                    puzzle: puzzle,
                    questionIndex: 0,
                    totalQuestions: selectedPuzzleSet?.puzzleData.puzzles.count ?? 1,
                    puzzleSet: selectedPuzzleSet
                ),
                puzzleId: puzzle.id,
                leaderboardData: []
            )
            .id(puzzle.id)
        } else {
            EmptyView()
        }
    }
    
    @ViewBuilder
    var mcPuzzleDestination: some View {
        if let puzzle = selectedPuzzle {
            MultipleChoicePuzzleView(
                puzzleId: puzzle.puzzleId,
                question: puzzle.question,
                options: puzzle.options,
                correctAnswer: puzzle.answer,
                hint: puzzle.hint,
                timerSeconds: 30,
                questionNumber: currentQuestionIndex + 1,
                totalQuestions: selectedPuzzleSet?.puzzleData.puzzles.count ?? 1,
                leaderboardData: selectedPuzzleSet?.puzzleData.leaderboard,
                puzzleDifficulty: puzzle.difficulty,
                onOptionSelected: { selected in
                    print("🔵 MC: Option selected: \(selected)")
                    handlePuzzleCompletion(for: puzzle)
                },
                onExit: {
                    AnalyticsManager.shared.trackSafely(AnalyticsEvent("puzzle_exited", parameters: [
                            "puzzle_type": puzzle.puzzleType ?? "unknown",
                            "exit_reason": "back_button",
                            "question_index": currentQuestionIndex
                        ]))
                    print("🔵 MC: Exit button tapped")
                    selectedPuzzle = nil
                    navigationState = .none
                    selectedPuzzleSet = nil
                    currentQuestionIndex = 0
                    isPuzzleLoading = false
                }
            )
            .id(puzzle.id)
            .onAppear {
                print("🔵 MC: MultipleChoicePuzzleView appeared")
                print("🔵 MC: Question: \(puzzle.question)")
                print("🔵 MC: Correct answer: \(puzzle.answer)")
                print("🔵 MC: Hint: \(puzzle.hint)")
                print("🔵 MC: Difficulty: \(puzzle.difficulty)")
            }
        } else {
            EmptyView()
                .onAppear {
                    print("🔴 MC: selectedPuzzle is nil in mcPuzzleDestination")
                }
        }
    }
    
    
    @ViewBuilder
    var symmetryPuzzleDestination: some View {
        SymmetryPuzzleView(
            timer: currentTimer,
            hearts: 3,
            level: "1",
            onSubmitAnswer: { isCorrect in
                if isCorrect { currentScore += 100 }
            },
            fetchNextPuzzle: { score in
                if let puzzle = selectedPuzzle {
                    handlePuzzleCompletion(for: puzzle)
                }
            },
            onBack: {
                selectedPuzzle = nil
                selectedPuzzleSet = nil
                navigationState = .none
            }
        )
    }
    
    @ViewBuilder
    var synonymPuzzleDestination: some View {
        if let puzzle = selectedPuzzle {
            SynonymPuzzleView(
                puzzle: puzzle,
                questionIndex: currentQuestionIndex,
                totalQuestions: selectedPuzzleSet?.puzzleData.puzzles.count ?? 1,
                onAnswerSubmitted: { answer, isCorrect in
                    // Handle answer submission
                    if isCorrect {
                        currentScore += 12 // Fixed score for synonym puzzles
                    }
                    print("Synonym answer submitted: \(answer), Correct: \(isCorrect)")
                },
                onNextPuzzle: {
                    handlePuzzleCompletion(for: puzzle)
                },
                onExit: {
                    AnalyticsManager.shared.trackSafely(AnalyticsEvent("puzzle_exited", parameters: [
                            "puzzle_type": puzzle.puzzleType ?? "unknown",
                            "exit_reason": "back_button",
                            "question_index": currentQuestionIndex
                        ]))
                    selectedPuzzle = nil
                    navigationState = .none
                    selectedPuzzleSet = nil
                    currentQuestionIndex = 0
                    isPuzzleLoading = false
                }
            )
            .id(puzzle.id)
            .onAppear {
                print("🔵 SynonymPuzzleView appeared")
                print("🔵 Puzzle question: \(puzzle.question)")
                
                // Validate synonym data
                if let synonymData = puzzle.synonymPuzzleData {
                    print("✅ SYNONYM: Valid synonym data found")
                    print("📊 SYNONYM: Sets count: \(synonymData.synonymSets.count)")
                    synonymData.synonymSets.enumerated().forEach { index, set in
                        print("📊 SYNONYM: Set \(index): \(set)")
                    }
                } else {
                    print("❌ SYNONYM: Invalid or missing synonym data")
                }
            }
        } else {
            EmptyView()
                .onAppear {
                    print("🔴 synonymPuzzleDestination: selectedPuzzle is nil")
                }
        }
    }
    
    @ViewBuilder
    var antonymPuzzleDestination: some View {
        if let puzzle = selectedPuzzle {
            AntonymPuzzleView(
                puzzle: puzzle,
                questionIndex: currentQuestionIndex,
                totalQuestions: selectedPuzzleSet?.puzzleData.puzzles.count ?? 1,
                onAnswerSubmitted: { answer, isCorrect in
                    // Handle answer submission
                    if isCorrect {
                        currentScore += 20 // Fixed score for antonym puzzles
                    }
                    print("Antonym answer submitted: \(answer), Correct: \(isCorrect)")
                },
                onNextPuzzle: {
                    handlePuzzleCompletion(for: puzzle)
                },
                onExit: {
                    AnalyticsManager.shared.trackSafely(AnalyticsEvent("puzzle_exited", parameters: [
                            "puzzle_type": puzzle.puzzleType ?? "unknown",
                            "exit_reason": "back_button",
                            "question_index": currentQuestionIndex
                        ]))
                    selectedPuzzle = nil
                    navigationState = .none
                    selectedPuzzleSet = nil
                    currentQuestionIndex = 0
                    isPuzzleLoading = false
                }
            )
            .id(puzzle.id)
            .onAppear {
                print("🔵 AntonymPuzzleView appeared")
                print("🔵 Puzzle question: \(puzzle.question)")
                
                // Validate antonym data
                if let antonymData = puzzle.antonymPuzzleData {
                    print("✅ ANTONYM: Valid antonym data found")
                    print("📊 ANTONYM: Pairs count: \(antonymData.count)")
                    antonymData.forEach { pair in
                        print("📊 ANTONYM: \(pair.word1) ↔ \(pair.word2)")
                    }
                } else {
                    print("❌ ANTONYM: Invalid or missing antonym data")
                }
            }
        } else {
            EmptyView()
                .onAppear {
                    print("🔴 antonymPuzzleDestination: selectedPuzzle is nil")
                }
        }
    }

    
    @ViewBuilder
    var wordPrefixPuzzleDestination: some View {
        if let puzzle = selectedPuzzle {
            WordPrefixPuzzleView(
                puzzle: puzzle,
                questionIndex: currentQuestionIndex,
                totalQuestions: selectedPuzzleSet?.puzzleData.puzzles.count ?? 1,
                onAnswerSubmitted: { answer, isCorrect in
                    print("📝 Word prefix answer submitted: \(answer), Correct: \(isCorrect)")
                    if isCorrect {
                        currentScore += 25 // Fixed score for word prefix puzzles
                    }
                },
                onNextPuzzle: {
                    handlePuzzleCompletion(for: puzzle)
                },
                onExit: {
                    AnalyticsManager.shared.trackSafely(AnalyticsEvent("puzzle_exited", parameters: [
                            "puzzle_type": puzzle.puzzleType ?? "unknown",
                            "exit_reason": "back_button",
                            "question_index": currentQuestionIndex
                        ]))
                    selectedPuzzle = nil
                    navigationState = .none
                    selectedPuzzleSet = nil
                    currentQuestionIndex = 0
                    isPuzzleLoading = false
                }
            )
            .id(puzzle.id)
            .onAppear {
                print("🔵 WordPrefixPuzzleView appeared")
                print("🔵 Puzzle question: \(puzzle.question)")
                
                // Validate word prefix data
                if let wordPrefixData = puzzle.wordPrefixPuzzleData {
                    print("✅ WORDPREFIX: Valid word prefix data found")
                    print("📊 WORDPREFIX: Prefix: \(wordPrefixData.prefix)")
                    print("📊 WORDPREFIX: Total words: \(wordPrefixData.totalWords)")
                    print("📊 WORDPREFIX: Time limit: \(wordPrefixData.timeLimit)s")
                    print("📊 WORDPREFIX: Bronze target: \(wordPrefixData.targets["bronze"] ?? 0)")
                    print("📊 WORDPREFIX: Silver target: \(wordPrefixData.targets["silver"] ?? 0)")
                    print("📊 WORDPREFIX: Gold target: \(wordPrefixData.targets["gold"] ?? 0)")
                } else {
                    print("❌ WORDPREFIX: Invalid or missing word prefix data")
                }
            }
        } else {
            EmptyView()
                .onAppear {
                    print("🔴 wordPrefixPuzzleDestination: selectedPuzzle is nil")
                }
        }
    }
    
    @ViewBuilder
    var memorySequencingPuzzleDestination: some View {
        if let puzzle = selectedPuzzle {
            MemorySequencingPuzzleView(
                puzzleData: puzzle.question, // Pass the JSON puzzle data
                correctAnswer: puzzle.answer, // Pass the correct answer
                onSubmitAnswer: { isCorrect in
                    print("📝 Memory sequencing answer submitted: \(isCorrect)")
                    if isCorrect {
                        currentScore += 35 // Fixed score for memory sequencing puzzles
                    }
                },
                fetchNextPuzzle: {
                    handlePuzzleCompletion(for: puzzle)
                },
                onBack: {
                    selectedPuzzle = nil
                    navigationState = .none
                    selectedPuzzleSet = nil
                    currentQuestionIndex = 0
                    isPuzzleLoading = false
                }
            )
            .id(puzzle.id)
            .onAppear {
                print("🧠 MemorySequencingPuzzleView appeared")
                print("🧠 Puzzle question: \(puzzle.question)")
            }
        } else {
            EmptyView()
                .onAppear {
                    print("🔴 memorySequencingPuzzleDestination: selectedPuzzle is nil")
                }
        }
    }
    
    
    @ViewBuilder
    var oddOneOutPuzzleDestination: some View {
        if let puzzle = selectedPuzzle,
           let puzzleData = OddOneOutPuzzle.parse(from: puzzle.question) {
            OddOneOutView(
                puzzle: puzzleData,
                onComplete: { isCorrect, score in
                    currentScore = score
                    handlePuzzleCompletion(for: puzzle)
                },
                onBack: {
                    selectedPuzzle = nil
                    navigationState = .none
                }
            )
        } else {
            Text("Failed to load Odd One Out puzzle")
                .foregroundColor(.white)
        }
    }
    
    @ViewBuilder
    var letterSetPuzzleDestination: some View {
        if let puzzle = selectedPuzzle {
            LetterSetPuzzleView(
                puzzle: puzzle,
                onBack: {
                        print("🔙 HOME: Letter set back called, isInActiveSession: \(isInActiveSession)")
                        selectedPuzzle = nil
                        navigationState = .none
                        if isInActiveSession {
                            showCompletionScreen = true
                        }
                    },
                onComplete: { success, timeBonus in
                    handlePuzzleCompletion(for: puzzle)
                }
            )
            .navigationBarHidden(true)
        }
    }
    
    @ViewBuilder
    var waldoPuzzleDestination: some View {
        if let puzzle = selectedPuzzle {
            WaldoPuzzleView(
                puzzle: puzzle,
                onBack: {
                        print("🔙 HOME: Waldo back called, isInActiveSession: \(isInActiveSession)")
                        selectedPuzzle = nil
                        navigationState = .none
                        if isInActiveSession {
                            showCompletionScreen = true
                        }
                    },
                onComplete: { success, timeBonus, score in
                    currentScore = score
                    handlePuzzleCompletion(for: puzzle)
                }
            )
            .navigationBarHidden(true)
        }
    }
    
    @ViewBuilder
    var flowPuzzleDestination: some View {
        if let puzzle = selectedPuzzle {
            FlowPuzzleView(
                puzzle: puzzle,
                onComplete: { isCorrect, finalScore in
                    print("✅ Flow puzzle completed: \(isCorrect), score: \(finalScore)")
                    currentScore = finalScore
                    handlePuzzleCompletion(for: puzzle)
                },
                onBack: {
                    selectedPuzzle = nil
                    navigationState = .none
                }
            )
            .onAppear {
                print("🎮 Flow puzzle view appeared")
            }
        }
    }
    
    @ViewBuilder
    var averagePuzzleDestination: some View {
        if let puzzle = selectedPuzzle {
            AveragePuzzleView(
                puzzle: puzzle,
                questionIndex: currentQuestionIndex,
                totalQuestions: selectedPuzzleSet?.puzzleData.puzzles.count ?? 1,
                onAnswerSubmitted: { answer, isCorrect in
                    // Handle answer submission
                    if isCorrect {
                        currentScore += 15 // Fixed score for average puzzles
                    }
                    print("Answer submitted: \(answer), Correct: \(isCorrect)")
                },
                onNextPuzzle: {
                    handlePuzzleCompletion(for: puzzle)
                },
                onExit: {
                    AnalyticsManager.shared.trackSafely(AnalyticsEvent("puzzle_exited", parameters: [
                            "puzzle_type": puzzle.puzzleType ?? "unknown",
                            "exit_reason": "back_button",
                            "question_index": currentQuestionIndex
                        ]))
                    selectedPuzzle = nil
                    navigationState = .none
                },
                onBack: {
                    selectedPuzzle = nil
                    navigationState = .none
                }
            )
            .id(puzzle.id)
        } else {
            EmptyView()
        }
    }
    
    @ViewBuilder
    var divisionPuzzleDestination: some View {
        if let puzzle = selectedPuzzle {
            // Use the division puzzle data from the extension
            if let divisionData = puzzle.divisionPuzzleData {
                DivisionPuzzleView(
                    score: currentScore,
                    hearts: 3,
                    level: "\(currentQuestionIndex + 1)/\(selectedPuzzleSet?.puzzleData.puzzles.count ?? 1)",
                    dividend: divisionData.dividend,
                    divisor: divisionData.divisor,
                    correctAnswer: divisionData.correctAnswer,
                    onSubmitAnswer: { answer in
                        let isCorrect = answer == divisionData.correctAnswer
                        if isCorrect {
                            currentScore += 20
                        }
                        print("Division answer submitted: \(answer), Correct: \(isCorrect)")
                    },
                    fetchNextPuzzle: {
                        handlePuzzleCompletion(for: puzzle)
                    }
                )
                .id(puzzle.id)
            } else {
                Text("Error loading division puzzle")
                    .foregroundColor(.red)
                    .onAppear {
                        print("🔴 Failed to parse division puzzle data")
                        print("🔴 Puzzle question: \(puzzle.question)")
                        print("🔴 Puzzle answer: \(puzzle.answer)")
                    }
            }
        } else {
            EmptyView()
        }
    }
    
    @ViewBuilder
        var geographyCitiesPuzzleDestination: some View {
            if let puzzle = selectedPuzzle {
                GeographyCitiesPuzzleView(
                    difficulty: puzzle.difficulty,
                    timer: getTimerForDifficulty(puzzle.difficulty),
                    hearts: 3,
                    level: "\(currentQuestionIndex + 1)/\(selectedPuzzleSet?.puzzleData.puzzles.count ?? 1)",
                    puzzleData: puzzle.question,
                    correctAnswer: puzzle.answer,
                    onSubmitAnswer: { isCorrect in
                        print("📍 Geography Cities answer submitted: \(isCorrect)")
                        if isCorrect {
                            currentScore += getGeographyCitiesScore(for: puzzle.difficulty)
                        }
                    },
                    fetchNextPuzzle: { earnedScore in
                        currentScore += earnedScore
                        handlePuzzleCompletion(for: puzzle)
                    },
                    onBack: {
                        AnalyticsManager.shared.trackSafely(AnalyticsEvent("puzzle_exited", parameters: [
                            "puzzle_type": puzzle.puzzleType ?? "unknown",
                            "exit_reason": "back_button",
                            "question_index": currentQuestionIndex
                        ]))
                        selectedPuzzle = nil
                        navigationState = .none
                        selectedPuzzleSet = nil
                        currentQuestionIndex = 0
                        isPuzzleLoading = false
                    }
                )
                .id(puzzle.id)
                .onAppear {
                    print("🌍 GeographyCitiesPuzzleView appeared")
                    sessionStartTime = Date()
                }
            } else {
                EmptyView()
                    .onAppear {
                        print("🔴 geographyCitiesPuzzleDestination: selectedPuzzle is nil")
                    }
            }
        }
    
    @ViewBuilder
    var mathExpressionPuzzleDestination: some View {
         if let puzzle = selectedPuzzle {
             MathExpressionPuzzleView(
                 initialDifficulty: puzzle.difficulty,
                 onGameComplete: { isSuccess, score in
                     print("🎮 Math Expression Complete - Success: \(isSuccess), Score: \(score)")
                     
                     // Update score
                     currentScore = score
                     
                     // Create session statistics
                     let sessionTime = Date().timeIntervalSince(sessionStartTime ?? Date())
                     let sessionStats = SessionStatistics(
                         correctAnswers: isSuccess ? 1 : 0,
                         totalAnswers: 1,
                         totalTimeSeconds: Int(sessionTime),
                         bestStreak: isSuccess ? 1 : 0,
                         currentStreak: isSuccess ? 1 : 0,
                         totalScore: score,
                         individualTimes: [Int(sessionTime)],
                         puzzleType: "mathExpression"
                     )
                     
                     // Handle puzzle completion
                     handlePuzzleCompletion(for: puzzle)
                 },
                 onBack: {
                     print("🔙 Math Expression Back pressed")
                     selectedPuzzle = nil
                     navigationState = .none
                 }
             )
             .onAppear {
                 print("📱 Math Expression View Appeared")
                 sessionStartTime = Date()
             }
         }
     }
    
    @ViewBuilder
    var imageQuestionPuzzleDestination: some View {
        if let puzzle = selectedPuzzle {
            ImageQuestionPuzzleView(
                puzzle: puzzle,
                difficulty: puzzle.difficulty,
                round: "Round",
                onComplete: { score, correctAnswers in
                    print("🎮 Image Question Complete - Score: \(score), Correct: \(correctAnswers)")
                    
                    // Update score
                    currentScore = score
                    
                    // Create session statistics
                    let sessionTime = Date().timeIntervalSince(sessionStartTime ?? Date())
                    let sessionStats = SessionStatistics(
                        correctAnswers: correctAnswers,
                        totalAnswers: puzzle.question.contains("totalQuestions") ? extractTotalQuestions(from: puzzle.question) : correctAnswers,
                        totalTimeSeconds: Int(sessionTime),
                        bestStreak: correctAnswers,
                        currentStreak: correctAnswers,
                        totalScore: score,
                        individualTimes: [Int(sessionTime)],
                        puzzleType: "imageQuestion"
                    )
                    
                    // Handle puzzle completion
                    handlePuzzleCompletion(for: puzzle)
                },
                onBack: {
                    print("🔙 Image Question Back pressed")
                    selectedPuzzle = nil
                    navigationState = .none
                }
            )
            .onAppear {
                print("📱 Image Question View Appeared")
                sessionStartTime = Date()
            }
        }
    }

    // Helper function to extract total questions from puzzle data
    private func extractTotalQuestions(from jsonString: String) -> Int {
        guard let data = jsonString.data(using: .utf8),
              let json = try? JSONDecoder().decode(ImageQuestionPuzzleData.self, from: data) else {
            return 0
        }
        return json.totalQuestions
    }
    
    @ViewBuilder
    var progressiveRevealPuzzleDestination: some View {
        if let puzzle = selectedPuzzle {
            Group {
                if let puzzleData = parseProgressiveRevealData(puzzle) {
                    ProgressiveRevealPuzzleView(
                        puzzleData: puzzleData,
                        round: "ROUND",
                        onPuzzleComplete: { score, isCorrect in
                            currentScore = score
                            handlePuzzleCompletion(for: puzzle)
                        },
                        onBack: {
                            selectedPuzzle = nil
                            navigationState = .none
                        }
                    )
                } else {
                    ZStack {
                        Color.black.ignoresSafeArea()
                        
                        VStack(spacing: 20) {
                            Image(systemName: "exclamationmark.triangle")
                                .font(.system(size: 60))
                                .foregroundColor(.orange)
                            
                            Text("Failed to Load Puzzle")
                                .font(.system(size: 20, weight: .bold))
                                .foregroundColor(.white)
                            
                            Text("Unable to parse progressive reveal puzzle data")
                                .font(.system(size: 14))
                                .foregroundColor(.gray)
                                .multilineTextAlignment(.center)
                                .padding(.horizontal, 40)
                            
                            Button(action: {
                                selectedPuzzle = nil
                                navigationState = .none
                            }) {
                                Text("Go Back")
                                    .font(.system(size: 16, weight: .bold))
                                    .foregroundColor(.white)
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 12)
                                    .background(Color.blue)
                                    .cornerRadius(8)
                            }
                            .padding(.horizontal, 40)
                        }
                    }
                }
            }
        } else {
            ZStack {
                Color.black.ignoresSafeArea()
                
                VStack(spacing: 20) {
                    ProgressView()
                        .scaleEffect(1.5)
                        .progressViewStyle(CircularProgressViewStyle(tint: .white))
                    
                    Text("Loading progressive reveal puzzle...")
                        .foregroundColor(.white)
                        .font(.system(size: 16))
                }
            }
        }
    }

    private func parseProgressiveRevealData(_ puzzle: Puzzle) -> ProgressiveRevealPuzzleData? {
        do {
            let data = try ProgressiveRevealPuzzleData.parse(from: puzzle)
            print("✅ PROGRESSIVE: Successfully parsed puzzle")
            print("📋 Answer: \(data.correctAnswer)")
            print("📋 Clues: \(data.clues.count)")
            print("📋 Blur levels: \(data.image.blurLevels.count)")
            return data
        } catch {
            print("❌ PROGRESSIVE: Failed to parse puzzle: \(error.localizedDescription)")
            return nil
        }
    }
    
    
    @ViewBuilder
    var whichIsRealPuzzleDestination: some View {
        if let puzzle = selectedPuzzle {
            WhichIsRealView(
                puzzle: puzzle,
                onBack: {
                    print("📱 Which Is Real: Back tapped")
                    selectedPuzzle = nil
                    selectedPuzzleSet = nil
                    navigationState = .none
                },
                onComplete: { wasCorrect, score in
                    print("📱 Which Is Real: Completed - Correct: \(wasCorrect), Score: \(score)")
                    currentScore = score
                    handlePuzzleCompletion(for: puzzle)
                }
            )
            .navigationBarHidden(true)
        }
    }
    
    @ViewBuilder
    var musicMatchPuzzleDestination: some View {
        if let puzzle = selectedPuzzle {
            SimpleMusicPuzzleView(
                puzzle: puzzle,
                questionIndex: currentQuestionIndex,
                totalQuestions: selectedPuzzleSet?.puzzleData.puzzles.count ?? 1,
                onAnswerSubmitted: { isCorrect in
                    print("🎵 Music match answer submitted: \(isCorrect)")
                    if isCorrect {
                        currentScore += getMusicMatchScore(for: puzzle.difficulty)
                    }
                },
                onNextPuzzle: {
                    handlePuzzleCompletion(for: puzzle)
                },
                onExit: {
                    AnalyticsManager.shared.trackSafely(AnalyticsEvent("puzzle_exited", parameters: [
                        "puzzle_type": puzzle.puzzleType ?? "unknown",
                        "exit_reason": "back_button",
                        "question_index": currentQuestionIndex
                    ]))
                    selectedPuzzle = nil
                    navigationState = .none
                    selectedPuzzleSet = nil
                    currentQuestionIndex = 0
                    isPuzzleLoading = false
                }
            )
            .id(puzzle.id)
            .onAppear {
                print("🎵 SimpleMusicPuzzleView appeared")
                print("🎵 Puzzle question: \(puzzle.question)")
                sessionStartTime = Date()
                
                // Validate music puzzle data
                if let musicData = puzzle.simpleMusicPuzzleData {
                    print("✅ MUSIC: Valid music puzzle data found")
                    print("📊 MUSIC: Theme: \(musicData.theme)")
                    print("📊 MUSIC: Total questions: \(musicData.totalQuestions)")
                    print("📊 MUSIC: Time limit: \(musicData.timeLimit)s")
                    print("📊 MUSIC: Questions count: \(musicData.questions.count)")
                    
                    musicData.questions.enumerated().forEach { index, question in
                        print("📊 MUSIC: Question \(question.id): \(question.answer) - \(question.audioUrl)")
                    }
                } else {
                    print("❌ MUSIC: Invalid or missing music puzzle data")
                }
            }
        } else {
            EmptyView()
                .onAppear {
                    print("🔴 musicMatchPuzzleDestination: selectedPuzzle is nil")
                }
        }
    }
    
    @ViewBuilder
    var cryptoPuzzleDestination: some View {
        if let puzzle = selectedPuzzle {
            CryptoPuzzleView(
                puzzle: puzzle,
                questionIndex: currentQuestionIndex,
                totalQuestions: selectedPuzzleSet?.puzzleData.puzzles.count ?? 1,
                onAnswerSubmitted: { isCorrect in
                    print("📝 Crypto puzzle answer submitted: \(isCorrect)")
                    if isCorrect {
                        currentScore += getCryptoPuzzleScore(for: puzzle.difficulty)
                    }
                },
                onNextPuzzle: {
                    handlePuzzleCompletion(for: puzzle)
                },
                onExit: {
                    AnalyticsManager.shared.trackSafely(AnalyticsEvent("puzzle_exited", parameters: [
                        "puzzle_type": puzzle.puzzleType ?? "unknown",
                        "exit_reason": "back_button",
                        "question_index": currentQuestionIndex
                    ]))
                    selectedPuzzle = nil
                    navigationState = .none
                    selectedPuzzleSet = nil
                    currentQuestionIndex = 0
                    isPuzzleLoading = false
                }
            )
            .id(puzzle.id)
            .onAppear {
                print("🔐 CryptoPuzzleView appeared")
                print("🔐 Puzzle question: \(puzzle.question)")
                sessionStartTime = Date()
                
                // Validate crypto puzzle data
                if let cryptoData = CryptoPuzzleData(from: puzzle.question) {
                    print("✅ CRYPTO: Valid crypto puzzle data found")
                    print("📊 CRYPTO: Original text: \(cryptoData.originalText)")
                    print("📊 CRYPTO: Number mappings: \(cryptoData.numberMapping.count)")
                    print("📊 CRYPTO: Revealed letters: \(cryptoData.revealedLetters)")
                    print("📊 CRYPTO: Time limit: \(cryptoData.timeLimit)s")
                    print("📊 CRYPTO: Difficulty: \(cryptoData.difficulty)")
                } else {
                    print("❌ CRYPTO: Invalid or missing crypto puzzle data")
                }
            }
        } else {
            EmptyView()
                .onAppear {
                    print("🔴 cryptoPuzzleDestination: selectedPuzzle is nil")
                }
        }
    }
        
        @ViewBuilder
        var geographyCountriesPuzzleDestination: some View {
            if let puzzle = selectedPuzzle {
                GeographyCountriesPuzzleView(
                    difficulty: puzzle.difficulty,
                    timer: getTimerForDifficulty(puzzle.difficulty),
                    hearts: 3,
                    level: "\(currentQuestionIndex + 1)/\(selectedPuzzleSet?.puzzleData.puzzles.count ?? 1)",
                    puzzleData: puzzle.question,
                    correctAnswer: puzzle.answer,
                    onSubmitAnswer: { isCorrect in
                        print("📍 Geography Countries answer submitted: \(isCorrect)")
                        if isCorrect {
                            currentScore += getGeographyCountriesScore(for: puzzle.difficulty)
                        }
                    },
                    fetchNextPuzzle: { earnedScore in
                        currentScore += earnedScore
                        handlePuzzleCompletion(for: puzzle)
                    },
                    onBack: {
                        AnalyticsManager.shared.trackSafely(AnalyticsEvent("puzzle_exited", parameters: [
                            "puzzle_type": puzzle.puzzleType ?? "unknown",
                            "exit_reason": "back_button",
                            "question_index": currentQuestionIndex
                        ]))
                        selectedPuzzle = nil
                        navigationState = .none
                        selectedPuzzleSet = nil
                        currentQuestionIndex = 0
                        isPuzzleLoading = false
                    }
                )
                .id(puzzle.id)
                .onAppear {
                    print("🗺️ GeographyCountriesPuzzleView appeared")
                    sessionStartTime = Date()
                }
            } else {
                EmptyView()
                    .onAppear {
                        print("🔴 geographyCountriesPuzzleDestination: selectedPuzzle is nil")
                    }
            }
        }
    
    private func getGeographyCitiesScore(for difficulty: String) -> Int {
        switch difficulty.lowercased() {
        case "easy": return 40
        case "medium": return 60
        case "hard": return 80
        default: return 60
        }
    }
        
    private func getGeographyCountriesScore(for difficulty: String) -> Int {
        switch difficulty.lowercased() {
        case "easy": return 50
        case "medium": return 75
        case "hard": return 100
        default: return 75
        }
    }
    
    private func getWordSnakeScore(for difficulty: String) -> Int {
        switch difficulty.lowercased() {
        case "easy": return 25
        case "medium": return 35
        case "hard": return 45
        case "expert": return 55
        default: return 35
        }
    }
    
    private func getFindObjectScore(for difficulty: String) -> Int {
        switch difficulty.lowercased() {
        case "easy": return 30
        case "medium": return 50
        case "hard": return 75
        case "expert": return 100
        default: return 50
        }
    }
    
    private func getImagePuzzleScore(for difficulty: String) -> Int {
        switch difficulty.lowercased() {
        case "easy": return 40
        case "medium": return 60
        case "hard": return 80
        case "expert": return 100
        default: return 60
        }
    }
    
    @ViewBuilder
    var estimationPuzzleDestination: some View {
        if let puzzle = selectedPuzzle {
            if let estimationData = puzzle.estimationPuzzleData {
                EstimationPuzzleView(
                    puzzle: puzzle,
                    questionIndex: currentQuestionIndex,
                    totalQuestions: selectedPuzzleSet?.puzzleData.puzzles.count ?? 1,
                    onAnswerSubmitted: { estimate, isCorrect in
                        if isCorrect {
                            currentScore += 18 // Fixed score for estimation puzzles
                        }
                        print("🔵 ESTIMATION: Estimate submitted: \(estimate), Correct: \(isCorrect)")
                        print("🔵 ESTIMATION: Expected sum: \(estimationData.correctSum)")
                        print("🔵 ESTIMATION: Tolerance: ±\(estimationData.tolerance)")
                    },
                    onNextPuzzle: {
                        handlePuzzleCompletion(for: puzzle)
                    },
                    onExit: {
                        AnalyticsManager.shared.trackSafely(AnalyticsEvent("puzzle_exited", parameters: [
                                "puzzle_type": puzzle.puzzleType ?? "unknown",
                                "exit_reason": "back_button",
                                "question_index": currentQuestionIndex
                            ]))
                        selectedPuzzle = nil
                        navigationState = .none
                    }
                )
                .id(puzzle.id)
                .onAppear {
                    print("🟢 ESTIMATION: EstimationPuzzleView appeared")
                    print("🟢 ESTIMATION: Correct sum: \(estimationData.correctSum)")
                }
            } else {
                VStack(spacing: 20) {
                    Text("Estimation puzzle format error")
                        .foregroundColor(.red)
                        .font(.title2)
                    
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Expected format:")
                            .font(.headline)
                        Text("Question: [18.3, 30.75, 28]")
                            .font(.caption)
                            .padding()
                            .background(Color.gray.opacity(0.2))
                            .cornerRadius(8)
                        Text("Answer: 77.05")
                            .font(.caption)
                            .padding()
                            .background(Color.gray.opacity(0.2))
                            .cornerRadius(8)
                        
                        Text("Received:")
                            .font(.headline)
                            .padding(.top)
                        Text("Question: \(puzzle.question)")
                            .font(.caption)
                            .padding()
                            .background(Color.red.opacity(0.1))
                            .cornerRadius(8)
                        Text("Answer: \(puzzle.answer)")
                            .font(.caption)
                            .padding()
                            .background(Color.red.opacity(0.1))
                            .cornerRadius(8)
                    }
                    
                    Button("Back to Home") {
                        selectedPuzzle = nil
                        navigationState = .none
                    }
                    .padding()
                    .background(Color.blue)
                    .foregroundColor(.white)
                    .cornerRadius(8)
                }
                .padding()
                .onAppear {
                    print("🔴 ESTIMATION: Failed to parse estimation data")
                    print("🔴 ESTIMATION: Question: \(puzzle.question)")
                    print("🔴 ESTIMATION: Answer: \(puzzle.answer)")
                }
            }
        } else {
            Text("Estimation puzzle not available")
                .foregroundColor(.red)
                .onAppear {
                    print("🔴 ESTIMATION: selectedPuzzle is nil")
                }
        }
    }
    
    @ViewBuilder
    var percentagePuzzleDestination: some View {
        if let puzzle = selectedPuzzle {
            PercentagePuzzleView(
                puzzle: puzzle,
                questionIndex: currentQuestionIndex,
                totalQuestions: selectedPuzzleSet?.puzzleData.puzzles.count ?? 1,
                onAnswerSubmitted: { answer, isCorrect in
                    // Handle answer submission
                    if isCorrect {
                        currentScore += 16 // Fixed score for percentage puzzles
                    }
                    print("Percentage answer submitted: \(answer), Correct: \(isCorrect)")
                },
                onNextPuzzle: {
                    handlePuzzleCompletion(for: puzzle)
                },
                onExit: {
                    AnalyticsManager.shared.trackSafely(AnalyticsEvent("puzzle_exited", parameters: [
                            "puzzle_type": puzzle.puzzleType ?? "unknown",
                            "exit_reason": "back_button",
                            "question_index": currentQuestionIndex
                        ]))
                    selectedPuzzle = nil
                    navigationState = .none
                }
            )
            .id(puzzle.id)
        } else {
            EmptyView()
        }
    }
    
    @ViewBuilder
    var discountsPuzzleDestination: some View {
        if let puzzle = selectedPuzzle {
            PriceOrderingPuzzleView(
                puzzle: puzzle,
                questionIndex: currentQuestionIndex,
                totalQuestions: selectedPuzzleSet?.puzzleData.puzzles.count ?? 1,
                onAnswerSubmitted: { order, isCorrect in
                    // Handle answer submission
                    if isCorrect {
                        currentScore += 22 // Fixed score for discount puzzles
                    }
                    print("Discount order submitted: \(order), Correct: \(isCorrect)")
                },
                onNextPuzzle: {
                    handlePuzzleCompletion(for: puzzle)
                },
                onExit: {
                    AnalyticsManager.shared.trackSafely(AnalyticsEvent("puzzle_exited", parameters: [
                            "puzzle_type": puzzle.puzzleType ?? "unknown",
                            "exit_reason": "back_button",
                            "question_index": currentQuestionIndex
                        ]))
                    selectedPuzzle = nil
                    navigationState = .none
                }
            )
            .id(puzzle.id)
        } else {
            EmptyView()
        }
    }
    
    @ViewBuilder
    var dualTaskPuzzleDestination: some View {
        if let puzzle = selectedPuzzle {
            DualCardPuzzleView(
                difficulty: puzzle.difficulty,
                timer: getTimerForDifficulty(puzzle.difficulty),
                hearts: 3,
                level: "\(currentQuestionIndex + 1)/\(selectedPuzzleSet?.puzzleData.puzzles.count ?? 1)",
                onSubmitAnswer: { isCorrect in
                    print("📝 Dual task answer submitted: \(isCorrect)")
                    if isCorrect {
                        currentScore += getDualTaskScore(for: puzzle.difficulty)
                    }
                },
                fetchNextPuzzle: { earnedScore in
                    currentScore += earnedScore
                    handlePuzzleCompletion(for: puzzle)
                },
                onBack: {
                    AnalyticsManager.shared.trackSafely(AnalyticsEvent("puzzle_exited", parameters: [
                        "puzzle_type": puzzle.puzzleType ?? "unknown",
                        "exit_reason": "back_button",
                        "question_index": currentQuestionIndex
                    ]))
                    selectedPuzzle = nil
                    navigationState = .none
                    selectedPuzzleSet = nil
                    currentQuestionIndex = 0
                    isPuzzleLoading = false
                }
            )
            .id(puzzle.id)
            .onAppear {
                print("🧠 DualCardPuzzleView appeared")
                print("🧠 Puzzle difficulty: \(puzzle.difficulty)")
                sessionStartTime = Date()
            }
        } else {
            EmptyView()
                .onAppear {
                    print("🔴 dualTaskPuzzleDestination: selectedPuzzle is nil")
                }
        }
    }

    @ViewBuilder
    var colorTextMatchingPuzzleDestination: some View {
        if let puzzle = selectedPuzzle {
            ColorTextMatchingPuzzleView(
                difficulty: puzzle.difficulty,
                timer: getTimerForDifficulty(puzzle.difficulty),
                hearts: 3,
                level: "\(currentQuestionIndex + 1)/\(selectedPuzzleSet?.puzzleData.puzzles.count ?? 1)",
                puzzleData: puzzle.question,
                correctAnswer: puzzle.answer,
                onSubmitAnswer: { isCorrect in
                    print("📝 Color text matching answer submitted: \(isCorrect)")
                    if isCorrect {
                        currentScore += getColorTextMatchingScore(for: puzzle.difficulty)
                    }
                },
                fetchNextPuzzle: { earnedScore in
                    currentScore += earnedScore
                    handlePuzzleCompletion(for: puzzle)
                },
                onBack: {
                    AnalyticsManager.shared.trackSafely(AnalyticsEvent("puzzle_exited", parameters: [
                        "puzzle_type": puzzle.puzzleType ?? "unknown",
                        "exit_reason": "back_button",
                        "question_index": currentQuestionIndex
                    ]))
                    selectedPuzzle = nil
                    navigationState = .none
                    selectedPuzzleSet = nil
                    currentQuestionIndex = 0
                    isPuzzleLoading = false
                }
            )
            .id(puzzle.id)
            .onAppear {
                print("🎨 ColorTextMatchingPuzzleView appeared")
                print("🎨 Puzzle difficulty: \(puzzle.difficulty)")
                sessionStartTime = Date()
            }
        } else {
            EmptyView()
                .onAppear {
                    print("🔴 colorTextMatchingPuzzleDestination: selectedPuzzle is nil")
                }
        }
    }

    // MARK: - 7. Add scoring helper functions
    private func getDualTaskScore(for difficulty: String) -> Int {
        switch difficulty.lowercased() {
        case "easy": return 40
        case "medium": return 60
        case "hard": return 80
        default: return 60
        }
    }

    private func getColorTextMatchingScore(for difficulty: String) -> Int {
        switch difficulty.lowercased() {
        case "easy": return 30
        case "medium": return 45
        case "hard": return 60
        default: return 45
        }
    }
    
    @ViewBuilder
    var purchasingPuzzleDestination: some View {
        if let puzzle = selectedPuzzle {
            SubscriptionPuzzleView(
                puzzle: puzzle,
                questionIndex: currentQuestionIndex,
                totalQuestions: selectedPuzzleSet?.puzzleData.puzzles.count ?? 1,
                onAnswerSubmitted: { answer, isCorrect in
                    print("🔵 Purchasing answer submitted: \(answer), Correct: \(isCorrect)")
                    if isCorrect {
                        currentScore += 19
                    }
                },
                onNextPuzzle: {
                    handlePuzzleCompletion(for: puzzle)
                },
                onExit: {
                    AnalyticsManager.shared.trackSafely(AnalyticsEvent("puzzle_exited", parameters: [
                            "puzzle_type": puzzle.puzzleType ?? "unknown",
                            "exit_reason": "back_button",
                            "question_index": currentQuestionIndex
                        ]))
                    selectedPuzzle = nil
                    navigationState = .none
                }
            )
            .id(puzzle.id)
            .onAppear {
                print("🔵 purchasingPuzzleDestination appeared")
                print("🔵 Creating SubscriptionPuzzleView for puzzle: \(puzzle.puzzleType)")
                if let purchasingData = puzzle.purchasingPuzzleData {
                    print("🟢 Purchasing data found:")
                    print("  - Payment: \(purchasingData.payment)")
                    print("  - Frequency: \(purchasingData.frequency)")
                    print("  - Purpose: \(purchasingData.purpose)")
                    print("  - Yearly Total: \(purchasingData.yearlyTotal)")
                } else {
                    print("🔴 No purchasing data found in puzzle question: \(puzzle.question)")
                }
            }
        } else {
            EmptyView()
                .onAppear {
                    print("🔴 purchasingPuzzleDestination: selectedPuzzle is nil")
                }
        }
    }
    
    @ViewBuilder
    var conversionPuzzleDestination: some View {
        if let puzzle = selectedPuzzle {
            SwipeableBlocksPuzzleView(
                puzzle: puzzle,
                questionIndex: currentQuestionIndex,
                totalQuestions: selectedPuzzleSet?.puzzleData.puzzles.count ?? 1,
                onAnswerSubmitted: { answer, isCorrect in
                    // Handle answer submission
                    if isCorrect {
                        currentScore += 17 // Fixed score for conversion puzzles
                    }
                    print("Conversion answer submitted: \(answer), Correct: \(isCorrect)")
                },
                onNextPuzzle: {
                    handlePuzzleCompletion(for: puzzle)
                },
                onExit: {
                    AnalyticsManager.shared.trackSafely(AnalyticsEvent("puzzle_exited", parameters: [
                            "puzzle_type": puzzle.puzzleType ?? "unknown",
                            "exit_reason": "back_button",
                            "question_index": currentQuestionIndex
                        ]))
                    selectedPuzzle = nil
                    navigationState = .none
                }
            )
            .id(puzzle.id)
        } else {
            EmptyView()
        }
    }
    
    @ViewBuilder
    var imagePuzzleDestination: some View {
        if let puzzle = selectedPuzzle {
            ImagePuzzleView(
                puzzle: puzzle,
                questionIndex: currentQuestionIndex,
                totalQuestions: selectedPuzzleSet?.puzzleData.puzzles.count ?? 1,
                onAnswerSubmitted: { isCorrect in
                    print("🧩 Image puzzle answer submitted: \(isCorrect)")
                    if isCorrect {
                        currentScore += getImagePuzzleScore(for: puzzle.difficulty)
                    }
                },
                onNextPuzzle: {
                    handlePuzzleCompletion(for: puzzle)
                },
                onExit: {
                    AnalyticsManager.shared.trackSafely(AnalyticsEvent("puzzle_exited", parameters: [
                        "puzzle_type": puzzle.puzzleType ?? "unknown",
                        "exit_reason": "back_button",
                        "question_index": currentQuestionIndex
                    ]))
                    selectedPuzzle = nil
                    navigationState = .none
                    selectedPuzzleSet = nil
                    currentQuestionIndex = 0
                    isPuzzleLoading = false
                }
            )
            .id(puzzle.id)
            .onAppear {
                print("🧩 ImagePuzzleView appeared")
                print("🧩 Puzzle question: \(puzzle.question.prefix(200))...") // Limit output
                sessionStartTime = Date()
                
                // Validate image puzzle data using the extension
                if let imagePuzzleData = puzzle.imagePuzzleData {
                    print("✅ IMAGE_PUZZLE: Valid image puzzle data found")
                    print("📊 IMAGE_PUZZLE: Puzzle ID: \(imagePuzzleData.puzzleId)")
                    print("📊 IMAGE_PUZZLE: Theme: \(imagePuzzleData.theme)")
                    print("📊 IMAGE_PUZZLE: Image URL: \(imagePuzzleData.imageUrl)")
                    print("📊 IMAGE_PUZZLE: Grid size: \(imagePuzzleData.gridSize)×\(imagePuzzleData.gridSize)")
                    print("📊 IMAGE_PUZZLE: Total pieces: \(imagePuzzleData.totalPieces)")
                    print("📊 IMAGE_PUZZLE: Time limit: \(imagePuzzleData.timeLimit)s")
                    print("📊 IMAGE_PUZZLE: Difficulty: \(imagePuzzleData.difficulty)")
                    print("📊 IMAGE_PUZZLE: Allow rotation: \(imagePuzzleData.allowRotation)")
                    print("📊 IMAGE_PUZZLE: Show preview: \(imagePuzzleData.showPreview)")
                } else {
                    print("❌ IMAGE_PUZZLE: Invalid or missing image puzzle data")
                    // Debug the puzzle data
                    puzzle.debugImagePuzzleData()
                }
            }
        } else {
            EmptyView()
                .onAppear {
                    print("🔴 imagePuzzleDestination: selectedPuzzle is nil")
                }
        }
    }
    
    @ViewBuilder
    var findObjectPuzzleDestination: some View {
        if let puzzle = selectedPuzzle {
            FindObjectPuzzleView(
                puzzle: puzzle,
                questionIndex: currentQuestionIndex,
                totalQuestions: selectedPuzzleSet?.puzzleData.puzzles.count ?? 1,
                onAnswerSubmitted: { isCorrect in
                    print("📝 Find Object answer submitted: \(isCorrect)")
                    if isCorrect {
                        currentScore += getFindObjectScore(for: puzzle.difficulty)
                    }
                },
                onNextPuzzle: {
                    handlePuzzleCompletion(for: puzzle)
                },
                onExit: {
                    AnalyticsManager.shared.trackSafely(AnalyticsEvent("puzzle_exited", parameters: [
                        "puzzle_type": puzzle.puzzleType ?? "unknown",
                        "exit_reason": "back_button",
                        "question_index": currentQuestionIndex
                    ]))
                    selectedPuzzle = nil
                    navigationState = .none
                    selectedPuzzleSet = nil
                    currentQuestionIndex = 0
                    isPuzzleLoading = false
                }
            )
            .id(puzzle.id)
            .onAppear {
                print("🔍 FindObjectPuzzleView appeared")
                print("🔍 Puzzle question: \(puzzle.question)")
                sessionStartTime = Date()
                
                // Validate find object data
                if let findObjectData = puzzle.findObjectPuzzleData {
                    print("✅ FIND_OBJECT: Valid find object data found")
                    print("📊 FIND_OBJECT: Image URL: \(findObjectData.imageUrl)")
                    print("📊 FIND_OBJECT: Total objects: \(findObjectData.totalObjects)")
                    print("📊 FIND_OBJECT: Discovery method: \(findObjectData.discoveryMethod)")
                    print("📊 FIND_OBJECT: Time limit: \(findObjectData.timeLimit)s")
                    print("📊 FIND_OBJECT: Grid: \(findObjectData.gridConfig.cols)×\(findObjectData.gridConfig.rows)")
                    
                    findObjectData.objectsToFind.enumerated().forEach { index, obj in
                        let instanceInfo = obj.totalInstancesOfType > 1 ? " [\(obj.totalInstancesOfType) instances]" : ""
                        print("📊 FIND_OBJECT: Object \(index + 1): \(obj.name) - \(obj.confidence) confidence\(instanceInfo)")
                    }
                } else {
                    print("❌ FIND_OBJECT: Invalid or missing find object data")
                }
            }
        } else {
            EmptyView()
                .onAppear {
                    print("🔴 findObjectPuzzleDestination: selectedPuzzle is nil")
                }
        }
    }
    
    @ViewBuilder
    var wordSnakePuzzleDestination: some View {
        if let puzzle = selectedPuzzle {
            WordSnakePuzzleView(
                puzzle: puzzle,
                questionIndex: currentQuestionIndex,
                totalQuestions: selectedPuzzleSet?.puzzleData.puzzles.count ?? 1,
                onAnswerSubmitted: { isCorrect in
                    print("📝 Word Snake answer submitted: \(isCorrect)")
                    if isCorrect {
                        currentScore += getWordSnakeScore(for: puzzle.difficulty)
                    }
                },
                onNextPuzzle: {
                    handlePuzzleCompletion(for: puzzle)
                },
                onExit: {
                    AnalyticsManager.shared.trackSafely(AnalyticsEvent("puzzle_exited", parameters: [
                        "puzzle_type": puzzle.puzzleType ?? "unknown",
                        "exit_reason": "back_button",
                        "question_index": currentQuestionIndex
                    ]))
                    selectedPuzzle = nil
                    navigationState = .none
                    selectedPuzzleSet = nil
                    currentQuestionIndex = 0
                    isPuzzleLoading = false
                }
            )
            .id(puzzle.id)
            .onAppear {
                print("🐍 WordSnakePuzzleView appeared")
                print("🐍 Puzzle question: \(puzzle.question)")
                sessionStartTime = Date()
                
                // Validate Word Snake data
                if let data = WordSnakePuzzleData(from: puzzle.question) {
                    print("✅ WORD_SNAKE: Valid word snake data found")
                    print("📊 WORD_SNAKE: Grid size: \(data.gridSize)×\(data.gridSize)")
                    print("📊 WORD_SNAKE: Words count: \(data.words.count)")
                    print("📊 WORD_SNAKE: Time limit: \(data.timeLimit)s")
                    print("📊 WORD_SNAKE: Difficulty: \(data.difficulty)")
                    data.words.forEach { word in
                        print("📊 WORD_SNAKE: Word '\(word.word)' - \(word.path.count) positions")
                    }
                } else {
                    print("❌ WORD_SNAKE: Invalid or missing word snake data")
                }
            }
        } else {
            EmptyView()
                .onAppear {
                    print("🔴 wordSnakePuzzleDestination: selectedPuzzle is nil")
                }
        }
    }
    
    @ViewBuilder
    var anagramPuzzleDestination: some View {
        if let puzzle = selectedPuzzle {
            AnagramPuzzleView(
                puzzle: puzzle,
                questionIndex: currentQuestionIndex,
                totalQuestions: selectedPuzzleSet?.puzzleData.puzzles.count ?? 1,
                onAnswerSubmitted: { answer, isCorrect in
                    // Handle answer submission
                    if isCorrect {
                        currentScore += 15 // Fixed score for anagram puzzles
                    }
                    print("Anagram answer submitted: \(answer), Correct: \(isCorrect)")
                },
                onNextPuzzle: {
                    handlePuzzleCompletion(for: puzzle)
                },
                onExit: {
                    AnalyticsManager.shared.trackSafely(AnalyticsEvent("puzzle_exited", parameters: [
                            "puzzle_type": puzzle.puzzleType ?? "unknown",
                            "exit_reason": "back_button",
                            "question_index": currentQuestionIndex
                        ]))
                    selectedPuzzle = nil
                    navigationState = .none
                }
            )
            .id(puzzle.id)
        } else {
            EmptyView()
        }
    }
    
    @ViewBuilder
    var swipeWordPuzzleDestination: some View {
        if let puzzle = selectedPuzzle {
            SwipeWordView(
                puzzle: puzzle,
                questionIndex: currentQuestionIndex,
                totalQuestions: selectedPuzzleSet?.puzzleData.puzzles.count ?? 1,
                onAnswerSubmitted: { word, isCorrect in
                    if isCorrect {
                        currentScore += 10 // Score per correct word
                    }
                    print("Word swiped: \(word), Correct: \(isCorrect)")
                },
                onNextPuzzle: {
                    handlePuzzleCompletion(for: puzzle)
                },
                onExit: {
                    AnalyticsManager.shared.trackSafely(AnalyticsEvent("puzzle_exited", parameters: [
                            "puzzle_type": puzzle.puzzleType ?? "unknown",
                            "exit_reason": "back_button",
                            "question_index": currentQuestionIndex
                        ]))
                    selectedPuzzle = nil
                    navigationState = .none
                }
            )
            .id(puzzle.id)
        } else {
            EmptyView()
        }
    }
    
    @ViewBuilder
    var tipBubblePuzzleDestination: some View {
        if let puzzle = selectedPuzzle {
            TipBubblePuzzleView(
                puzzle: puzzle,
                questionIndex: currentQuestionIndex,
                totalQuestions: selectedPuzzleSet?.puzzleData.puzzles.count ?? 1,
                onAnswerSubmitted: { tipAmount, isCorrect in
                    print("Tip bubble tapped: $\(tipAmount), Correct: \(isCorrect)")
                },
                onNextPuzzle: {
                    handlePuzzleCompletion(for: puzzle)
                },
                onExit: {
                    AnalyticsManager.shared.trackSafely(AnalyticsEvent("puzzle_exited", parameters: [
                            "puzzle_type": puzzle.puzzleType ?? "unknown",
                            "exit_reason": "back_button",
                            "question_index": currentQuestionIndex
                        ]))
                    selectedPuzzle = nil
                    navigationState = .none
                }
            )
            .id(puzzle.id)
            //BubbleTapTestView()
        } else {
            EmptyView()
        }
    }
    
    @ViewBuilder
        var mathComparisonPuzzleDestination: some View {
            if let puzzle = selectedPuzzle {
                MathComparisonPuzzleView(
                    puzzle: puzzle,
                    questionIndex: currentQuestionIndex,
                    totalQuestions: selectedPuzzleSet?.puzzleData.puzzles.count ?? 1,
                    onAnswerSubmitted: { answer, isCorrect in
                        print("📝 Math comparison answer: \(answer), Correct: \(isCorrect)")
                        if isCorrect {
                            currentScore += 100
                        }
                    },
                    onNextPuzzle: {
                        handlePuzzleCompletion(for: puzzle)
                    },
                    onExit: {
                        AnalyticsManager.shared.trackSafely(AnalyticsEvent("puzzle_exited", parameters: [
                            "puzzle_type": puzzle.puzzleType ?? "unknown",
                            "exit_reason": "back_button",
                            "question_index": currentQuestionIndex
                        ]))
                        selectedPuzzle = nil
                        navigationState = .none
                        selectedPuzzleSet = nil
                        currentQuestionIndex = 0
                        isPuzzleLoading = false
                    }
                )
                .id(puzzle.id)
                .onAppear {
                    print("🔢 MathComparisonPuzzleView appeared")
                    sessionStartTime = Date()
                }
            } else {
                EmptyView()
                    .onAppear {
                        print("🔴 mathComparisonPuzzleDestination: selectedPuzzle is nil")
                    }
            }
        }
        
        @ViewBuilder
        var numberSequencePuzzleDestination: some View {
            if let puzzle = selectedPuzzle {
                NumberSequencePuzzleView(
                    puzzle: puzzle,
                    questionIndex: currentQuestionIndex,
                    totalQuestions: selectedPuzzleSet?.puzzleData.puzzles.count ?? 1,
                    onAnswerSubmitted: { isCorrect in
                        print("📝 Number sequence answer: \(isCorrect)")
                        if isCorrect {
                            currentScore += 50
                        }
                    },
                    onNextPuzzle: {
                        handlePuzzleCompletion(for: puzzle)
                    },
                    onExit: {
                        AnalyticsManager.shared.trackSafely(AnalyticsEvent("puzzle_exited", parameters: [
                            "puzzle_type": puzzle.puzzleType ?? "unknown",
                            "exit_reason": "back_button",
                            "question_index": currentQuestionIndex
                        ]))
                        selectedPuzzle = nil
                        navigationState = .none
                        selectedPuzzleSet = nil
                        currentQuestionIndex = 0
                        isPuzzleLoading = false
                    }
                )
                .id(puzzle.id)
                .onAppear {
                    print("🔢 NumberSequencePuzzleView appeared")
                    sessionStartTime = Date()
                }
            } else {
                EmptyView()
                    .onAppear {
                        print("🔴 numberSequencePuzzleDestination: selectedPuzzle is nil")
                    }
            }
        }
        
        @ViewBuilder
        var numberSumPuzzleDestination: some View {
            if let puzzle = selectedPuzzle {
                NumberSumPuzzleView(
                    puzzle: puzzle,
                    questionIndex: currentQuestionIndex,
                    totalQuestions: selectedPuzzleSet?.puzzleData.puzzles.count ?? 1,
                    onAnswerSubmitted: { isCorrect in
                        print("📝 Number sum answer: \(isCorrect)")
                        if isCorrect {
                            currentScore += 25
                        }
                    },
                    onNextPuzzle: {
                        handlePuzzleCompletion(for: puzzle)
                    },
                    onExit: {
                        AnalyticsManager.shared.trackSafely(AnalyticsEvent("puzzle_exited", parameters: [
                            "puzzle_type": puzzle.puzzleType ?? "unknown",
                            "exit_reason": "back_button",
                            "question_index": currentQuestionIndex
                        ]))
                        selectedPuzzle = nil
                        navigationState = .none
                        selectedPuzzleSet = nil
                        currentQuestionIndex = 0
                        isPuzzleLoading = false
                    }
                )
                .id(puzzle.id)
                .onAppear {
                    print("🧮 NumberSumPuzzleView appeared")
                    sessionStartTime = Date()
                }
            } else {
                EmptyView()
                    .onAppear {
                        print("🔴 numberSumPuzzleDestination: selectedPuzzle is nil")
                    }
            }
        }
        
        @ViewBuilder
        var symbolSwipePuzzleDestination: some View {
            if let puzzle = selectedPuzzle {
                SymbolSwipePuzzleView(
                    puzzle: puzzle,
                    questionIndex: currentQuestionIndex,
                    totalQuestions: selectedPuzzleSet?.puzzleData.puzzles.count ?? 1,
                    onAnswerSubmitted: { isCorrect in
                        print("📝 Symbol swipe answer: \(isCorrect)")
                        if isCorrect {
                            currentScore += 10
                        }
                    },
                    onNextPuzzle: {
                        handlePuzzleCompletion(for: puzzle)
                    },
                    onExit: {
                        AnalyticsManager.shared.trackSafely(AnalyticsEvent("puzzle_exited", parameters: [
                            "puzzle_type": puzzle.puzzleType ?? "unknown",
                            "exit_reason": "back_button",
                            "question_index": currentQuestionIndex
                        ]))
                        selectedPuzzle = nil
                        navigationState = .none
                        selectedPuzzleSet = nil
                        currentQuestionIndex = 0
                        isPuzzleLoading = false
                    }
                )
                .id(puzzle.id)
                .onAppear {
                    print("🔄 SymbolSwipePuzzleView appeared")
                    sessionStartTime = Date()
                }
            } else {
                EmptyView()
                    .onAppear {
                        print("🔴 symbolSwipePuzzleDestination: selectedPuzzle is nil")
                    }
            }
        }
        
        @ViewBuilder
        var uniqueObjectPuzzleDestination: some View {
            if let puzzle = selectedPuzzle {
                UniqueObjectPuzzleView(
                    puzzle: puzzle,
                    questionIndex: currentQuestionIndex,
                    totalQuestions: selectedPuzzleSet?.puzzleData.puzzles.count ?? 1,
                    onAnswerSubmitted: { isCorrect in
                        print("📝 Unique object answer: \(isCorrect)")
                        if isCorrect {
                            currentScore += 30
                        }
                    },
                    onNextPuzzle: {
                        handlePuzzleCompletion(for: puzzle)
                    },
                    onExit: {
                        AnalyticsManager.shared.trackSafely(AnalyticsEvent("puzzle_exited", parameters: [
                            "puzzle_type": puzzle.puzzleType ?? "unknown",
                            "exit_reason": "back_button",
                            "question_index": currentQuestionIndex
                        ]))
                        selectedPuzzle = nil
                        navigationState = .none
                        selectedPuzzleSet = nil
                        currentQuestionIndex = 0
                        isPuzzleLoading = false
                    }
                )
                .id(puzzle.id)
                .onAppear {
                    print("🔍 UniqueObjectPuzzleView appeared")
                    sessionStartTime = Date()
                }
            } else {
                EmptyView()
                    .onAppear {
                        print("🔴 uniqueObjectPuzzleDestination: selectedPuzzle is nil")
                    }
            }
        }
        
        @ViewBuilder
        var wordSearchPuzzleDestination: some View {
            if let puzzle = selectedPuzzle {
                WordSearchPuzzleView(
                    puzzle: puzzle,
                    questionIndex: currentQuestionIndex,
                    totalQuestions: selectedPuzzleSet?.puzzleData.puzzles.count ?? 1,
                    onAnswerSubmitted: { isCorrect in
                        print("📝 Word search answer: \(isCorrect)")
                        if isCorrect {
                            currentScore += 20
                        }
                    },
                    onNextPuzzle: {
                        handlePuzzleCompletion(for: puzzle)
                    },
                    onExit: {
                        AnalyticsManager.shared.trackSafely(AnalyticsEvent("puzzle_exited", parameters: [
                            "puzzle_type": puzzle.puzzleType ?? "unknown",
                            "exit_reason": "back_button",
                            "question_index": currentQuestionIndex
                        ]))
                        selectedPuzzle = nil
                        navigationState = .none
                        selectedPuzzleSet = nil
                        currentQuestionIndex = 0
                        isPuzzleLoading = false
                    }
                )
                .id(puzzle.id)
                .onAppear {
                    print("🔍 WordSearchPuzzleView appeared")
                    sessionStartTime = Date()
                }
            } else {
                EmptyView()
                    .onAppear {
                        print("🔴 wordSearchPuzzleDestination: selectedPuzzle is nil")
                    }
            }
        }
    
    @ViewBuilder
    var contextSwitchPuzzleDestination: some View {
        if let puzzle = selectedPuzzle {
            ContextSwitchPuzzleView(
                puzzle: puzzle,
                questionIndex: currentQuestionIndex,
                totalQuestions: selectedPuzzleSet?.puzzleData.puzzles.count ?? 1,
                onAnswerSubmitted: { selectedAnswers, isCorrect in
                    print("📝 Context switch answer submitted: \(selectedAnswers), Correct: \(isCorrect)")
                    if isCorrect {
                        currentScore += 40 // Fixed score for context switch puzzles
                    }
                },
                onNextPuzzle: {
                    handlePuzzleCompletion(for: puzzle)
                },
                onExit: {
                    AnalyticsManager.shared.trackSafely(AnalyticsEvent("puzzle_exited", parameters: [
                        "puzzle_type": puzzle.puzzleType ?? "unknown",
                        "exit_reason": "back_button",
                        "question_index": currentQuestionIndex
                    ]))
                    selectedPuzzle = nil
                    navigationState = .none
                    selectedPuzzleSet = nil
                    currentQuestionIndex = 0
                    isPuzzleLoading = false
                }
            )
            .id(puzzle.id)
            .onAppear {
                print("🧠 ContextSwitchPuzzleView appeared")
                print("🧠 Puzzle question: \(puzzle.question)")
                
                // Validate context switch data
                if let contextData = puzzle.contextSwitchPuzzleData {
                    print("✅ CONTEXT_SWITCH: Valid context switch data found")
                    print("📊 CONTEXT_SWITCH: Category: \(contextData.category)")
                    print("📊 CONTEXT_SWITCH: Memory items: \(contextData.memoryItems.count)")
                    print("📊 CONTEXT_SWITCH: Recognition items: \(contextData.recognitionItems.count)")
                    print("📊 CONTEXT_SWITCH: Interference task: \(contextData.interferenceTask.type)")
                    print("📊 CONTEXT_SWITCH: Difficulty: \(contextData.difficulty)")
                } else {
                    print("❌ CONTEXT_SWITCH: Invalid or missing context switch data")
                }
            }
        } else {
            EmptyView()
                .onAppear {
                    print("🔴 contextSwitchPuzzleDestination: selectedPuzzle is nil")
                }
        }
    }
    
    @ViewBuilder
        var colorShapeMatchingPuzzleDestination: some View {
            if let puzzle = selectedPuzzle {
                ColorShapeMatchingPuzzleView(
                    puzzle: puzzle,
                    questionIndex: currentQuestionIndex,
                    totalQuestions: selectedPuzzleSet?.puzzleData.puzzles.count ?? 1,
                    onAnswerSubmitted: { answer, isCorrect in
                        print("📝 Color Shape Matching answer: \(answer), Correct: \(isCorrect)")
                        if isCorrect {
                            currentScore += 10
                        }
                    },
                    onNextPuzzle: {
                        handlePuzzleCompletion(for: puzzle)
                    },
                    onExit: {
                        AnalyticsManager.shared.trackSafely(AnalyticsEvent("puzzle_exited", parameters: [
                            "puzzle_type": puzzle.puzzleType ?? "unknown",
                            "exit_reason": "back_button",
                            "question_index": currentQuestionIndex
                        ]))
                        selectedPuzzle = nil
                        navigationState = .none
                        selectedPuzzleSet = nil
                        currentQuestionIndex = 0
                        isPuzzleLoading = false
                    }
                )
                .id(puzzle.id)
                .onAppear {
                    print("🎨 ColorShapeMatchingPuzzleView appeared")
                    print("🎨 Puzzle question: \(puzzle.question)")
                }
            } else {
                EmptyView()
                    .onAppear {
                        print("🔴 colorShapeMatchingPuzzleDestination: selectedPuzzle is nil")
                    }
            }
        }
        
        @ViewBuilder
        var imageVortexPuzzleDestination: some View {
            if let puzzle = selectedPuzzle {
                ImageVortexPuzzleView(
                    puzzle: puzzle,
                    questionIndex: currentQuestionIndex,
                    totalQuestions: selectedPuzzleSet?.puzzleData.puzzles.count ?? 1,
                    onAnswerSubmitted: { answer, isCorrect in
                        print("📝 Image Vortex answer: \(answer), Correct: \(isCorrect)")
                        if isCorrect {
                            currentScore += 10
                        }
                    },
                    onNextPuzzle: {
                        handlePuzzleCompletion(for: puzzle)
                    },
                    onExit: {
                        AnalyticsManager.shared.trackSafely(AnalyticsEvent("puzzle_exited", parameters: [
                            "puzzle_type": puzzle.puzzleType ?? "unknown",
                            "exit_reason": "back_button",
                            "question_index": currentQuestionIndex
                        ]))
                        selectedPuzzle = nil
                        navigationState = .none
                        selectedPuzzleSet = nil
                        currentQuestionIndex = 0
                        isPuzzleLoading = false
                    }
                )
                .id(puzzle.id)
                .onAppear {
                    print("🌪️ ImageVortexPuzzleView appeared")
                    print("🌪️ Puzzle question: \(puzzle.question)")
                }
            } else {
                EmptyView()
                    .onAppear {
                        print("🔴 imageVortexPuzzleDestination: selectedPuzzle is nil")
                    }
            }
        }
    
    @ViewBuilder
    var mathCrosswordPuzzleDestination: some View {
        if let puzzle = selectedPuzzle {
            MathCrosswordPuzzleView(
                difficulty: puzzle.difficulty,
                timer: getTimerForDifficulty(puzzle.difficulty), // Dynamic timer based on difficulty
                hearts: 3,
                level: "\(currentQuestionIndex + 1)/\(selectedPuzzleSet?.puzzleData.puzzles.count ?? 1)",
                puzzleData: puzzle.question,
                onSubmitAnswer: { isCorrect in
                    print("📝 Math crossword answer submitted: \(isCorrect)")
                    if isCorrect {
                        currentScore += getMathCrosswordScore(for: puzzle.difficulty)
                    }
                },
                fetchNextPuzzle: { earnedScore in
                    currentScore += earnedScore
                    handlePuzzleCompletion(for: puzzle)
                },
                onBack: {
                    AnalyticsManager.shared.trackSafely(AnalyticsEvent("puzzle_exited", parameters: [
                        "puzzle_type": puzzle.puzzleType ?? "unknown",
                        "exit_reason": "back_button",
                        "question_index": currentQuestionIndex
                    ]))
                    selectedPuzzle = nil
                    navigationState = .none
                    selectedPuzzleSet = nil
                    currentQuestionIndex = 0
                    isPuzzleLoading = false
                }
            )
            .id(puzzle.id)
            .onAppear {
                print("🔢 MathCrosswordPuzzleView appeared")
                print("🔢 Puzzle question: \(puzzle.question)")
                sessionStartTime = Date()
                
                // Validate math crossword data from generator
                if let data = puzzle.question.data(using: .utf8),
                   let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
                    
                    if let gridSize = json["gridSize"] as? Int,
                       let grid = json["grid"] as? [[Any]],
                       let numberCounts = json["numberCounts"] as? [String: Int],
                       let equations = json["equations"] as? [[String: Any]] {
                        
                        print("✅ MATH_CROSSWORD: Valid local math crossword data found")
                        print("📊 MATH_CROSSWORD: Grid size: \(gridSize)x\(gridSize)")
                        print("📊 MATH_CROSSWORD: Available numbers: \(numberCounts)")
                        print("📊 MATH_CROSSWORD: Equations: \(equations.count)")
                        print("📊 MATH_CROSSWORD: Difficulty: \(puzzle.difficulty)")
                    } else {
                        print("❌ MATH_CROSSWORD: Invalid local math crossword data structure")
                    }
                } else {
                    print("❌ MATH_CROSSWORD: Failed to parse local math crossword data")
                }
            }
        } else {
            EmptyView()
                .onAppear {
                    print("🔴 mathCrosswordPuzzleDestination: selectedPuzzle is nil")
                }
        }
    }
    
    private func getTimerForDifficulty(_ difficulty: String) -> String {
        switch difficulty.lowercased() {
        case "easy": return "08:00"      // 8 minutes for easy
        case "medium": return "06:00"    // 6 minutes for medium
        case "hard": return "05:00"      // 5 minutes for hard
        default: return "06:00"          // Default 6 minutes
        }
    }

    private func getMathCrosswordScore(for difficulty: String) -> Int {
        switch difficulty.lowercased() {
        case "easy": return 30
        case "medium": return 50
        case "hard": return 80
        default: return 50
        }
    }
    
    @ViewBuilder
    var pinballDeflectorPuzzleDestination: some View {
        if let puzzle = selectedPuzzle {
            PinballDeflectorPuzzleView(
                puzzle: puzzle,
                questionIndex: currentQuestionIndex,
                totalQuestions: selectedPuzzleSet?.puzzleData.puzzles.count ?? 1,
                onAnswerSubmitted: { selectedPosition, isCorrect in
                    print("📝 Pinball deflector answer submitted: \(selectedPosition?.row ?? -1), \(selectedPosition?.col ?? -1), Correct: \(isCorrect)")
                    if isCorrect {
                        currentScore += 30 // Fixed score for pinball deflector puzzles
                    }
                },
                onNextPuzzle: {
                    handlePuzzleCompletion(for: puzzle)
                },
                onExit: {
                    AnalyticsManager.shared.trackSafely(AnalyticsEvent("puzzle_exited", parameters: [
                            "puzzle_type": puzzle.puzzleType ?? "unknown",
                            "exit_reason": "back_button",
                            "question_index": currentQuestionIndex
                        ]))
                    selectedPuzzle = nil
                    navigationState = .none
                    selectedPuzzleSet = nil
                    currentQuestionIndex = 0
                    isPuzzleLoading = false
                }
            )
            .id(puzzle.id)
            .onAppear {
                print("🎯 PinballDeflectorPuzzleView appeared")
                print("🎯 Puzzle question: \(puzzle.question)")
                
                // Validate pinball deflector data
                if let pinballData = puzzle.pinballDeflectorPuzzleData {
                    print("✅ PINBALL: Valid pinball deflector data found")
                    print("📊 PINBALL: Matrix size: \(pinballData.matrixSize)×\(pinballData.matrixSize)")
                    print("📊 PINBALL: Start position: [\(pinballData.startPosition.row),\(pinballData.startPosition.col)]")
                    print("📊 PINBALL: Start direction: \(pinballData.startDirection.name)")
                    print("📊 PINBALL: Deflectors: \(pinballData.deflectors.count)")
                    print("📊 PINBALL: Memory time: \(pinballData.memoryTime)ms")
                    print("📊 PINBALL: Difficulty: \(pinballData.difficulty)")
                } else {
                    print("❌ PINBALL: Invalid or missing pinball deflector data")
                }
            }
        } else {
            EmptyView()
                .onAppear {
                    print("🔴 pinballDeflectorPuzzleDestination: selectedPuzzle is nil")
                }
        }
    }
    
    @ViewBuilder
    var memoryPreviousSinglePuzzleDestination: some View {
        if let puzzle = selectedPuzzle {
            MemoryPreviousSinglePuzzleView(
                puzzleData: puzzle.question,
                correctAnswer: puzzle.answer,
                onSubmitAnswer: { isCorrect in
                    print("📝 Memory previous single answer submitted: \(isCorrect)")
                    if isCorrect {
                        currentScore += 25 // Fixed score for memory single puzzles
                    }
                },
                fetchNextPuzzle: {
                    handlePuzzleCompletion(for: puzzle)
                },
                onBack: {
                    selectedPuzzle = nil
                    navigationState = .none
                    selectedPuzzleSet = nil
                    currentQuestionIndex = 0
                    isPuzzleLoading = false
                }
            )
            .id(puzzle.id)
            .onAppear {
                print("🧠 MemoryPreviousSinglePuzzleView appeared")
                print("🧠 Puzzle question: \(puzzle.question)")
                
                // Validate memory single data
                if let memoryData = puzzle.memoryPreviousSinglePuzzleData {
                    print("✅ MEMORY_SINGLE: Valid memory previous single data found")
                    print("📊 MEMORY_SINGLE: Binary sequence: \(memoryData.binarySequence)")
                    print("📊 MEMORY_SINGLE: Question sequence count: \(memoryData.questionSequence.count)")
                    print("📊 MEMORY_SINGLE: Total steps: \(memoryData.totalSteps)")
                    print("📊 MEMORY_SINGLE: Total questions: \(memoryData.totalQuestions)")
                    print("📊 MEMORY_SINGLE: Difficulty: \(memoryData.difficulty)")
                } else {
                    print("❌ MEMORY_SINGLE: Invalid or missing memory previous single data")
                }
            }
        } else {
            EmptyView()
                .onAppear {
                    print("🔴 memoryPreviousSinglePuzzleDestination: selectedPuzzle is nil")
                }
        }
    }
    
    @ViewBuilder
    var memoryPreviousPairPuzzleDestination: some View {
        if let puzzle = selectedPuzzle {
            MemoryPreviousPairPuzzleView(
                puzzleData: puzzle.question,
                correctAnswer: puzzle.answer,
                onSubmitAnswer: { isCorrect in
                    print("📝 Memory previous pair answer submitted: \(isCorrect)")
                    if isCorrect {
                        currentScore += 30 // Fixed score for memory pair puzzles
                    }
                },
                fetchNextPuzzle: {
                    handlePuzzleCompletion(for: puzzle)
                },
                onBack: {
                    selectedPuzzle = nil
                    navigationState = .none
                    selectedPuzzleSet = nil
                    currentQuestionIndex = 0
                    isPuzzleLoading = false
                }
            )
            .id(puzzle.id)
            .onAppear {
                print("🧠 MemoryPreviousPairPuzzleView appeared")
                print("🧠 Puzzle question: \(puzzle.question)")
                
                // Validate memory pair data
                if let memoryData = puzzle.memoryPreviousPairPuzzleData {
                    print("✅ MEMORY_PAIR: Valid memory previous pair data found")
                    print("📊 MEMORY_PAIR: Sequence count: \(memoryData.sequence.count)")
                    print("📊 MEMORY_PAIR: Total screens: \(memoryData.totalScreens)")
                    print("📊 MEMORY_PAIR: Difficulty: \(memoryData.difficulty)")
                } else {
                    print("❌ MEMORY_PAIR: Invalid or missing memory previous pair data")
                }
            }
        } else {
            EmptyView()
                .onAppear {
                    print("🔴 memoryPreviousPairPuzzleDestination: selectedPuzzle is nil")
                }
        }
    }
    
    @ViewBuilder
       var memorySquaresPuzzleDestination: some View {
           if let puzzle = selectedPuzzle {
               MemorySquaresPuzzleView(
                   puzzle: puzzle,
                   questionIndex: currentQuestionIndex,
                   totalQuestions: selectedPuzzleSet?.puzzleData.puzzles.count ?? 1,
                   onAnswerSubmitted: { isCorrect in
                       if isCorrect {
                           currentScore += 30 // Fixed score for memory squares puzzles
                       }
                       print("🧠 Memory squares answer submitted: \(isCorrect)")
                   },
                   onNextPuzzle: {
                       handlePuzzleCompletion(for: puzzle)
                   },
                   onExit: {
                       AnalyticsManager.shared.trackSafely(AnalyticsEvent("puzzle_exited", parameters: [
                               "puzzle_type": puzzle.puzzleType ?? "unknown",
                               "exit_reason": "back_button",
                               "question_index": currentQuestionIndex
                           ]))
                       selectedPuzzle = nil
                       navigationState = .none
                       selectedPuzzleSet = nil
                       currentQuestionIndex = 0
                       isPuzzleLoading = false
                   }
               )
               .id(puzzle.id)
               .onAppear {
                   print("🧠 MemorySquaresPuzzleView appeared")
                   print("🧠 Puzzle question: \(puzzle.question)")
                   
               }
           } else {
               EmptyView()
                   .onAppear {
                       print("🔴 memorySquaresPuzzleDestination: selectedPuzzle is nil")
                   }
           }
       }
    
    @ViewBuilder
    var memoryStoryPuzzleDestination: some View {
        if let puzzle = selectedPuzzle {
            MemoryStoryPuzzleView(
                puzzle: puzzle,
                questionIndex: currentQuestionIndex,
                totalQuestions: selectedPuzzleSet?.puzzleData.puzzles.count ?? 1,
                onAnswerSubmitted: { selectedItems, isCorrect in
                    // Handle answer submission
                    if isCorrect {
                        currentScore += 25 // Fixed score for memory story puzzles
                    }
                    let userAnswer = selectedItems.sorted().joined(separator: ", ")
                    print("🧠 Memory story answer submitted: \(userAnswer), Correct: \(isCorrect)")
                },
                onNextPuzzle: {
                    handlePuzzleCompletion(for: puzzle)
                },
                onExit: {
                    AnalyticsManager.shared.trackSafely(AnalyticsEvent("puzzle_exited", parameters: [
                            "puzzle_type": puzzle.puzzleType ?? "unknown",
                            "exit_reason": "back_button",
                            "question_index": currentQuestionIndex
                        ]))
                    selectedPuzzle = nil
                    navigationState = .none
                    selectedPuzzleSet = nil
                    currentQuestionIndex = 0
                    isPuzzleLoading = false
                }
            )
            .id(puzzle.id)
            .onAppear {
                print("🧠 MemoryStoryPuzzleView appeared")
                print("🧠 Puzzle question: \(puzzle.question)")
                
                // Validate memory story data
                if let memoryData = puzzle.memoryStoryPuzzleData {
                    print("✅ MEMORY_STORY: Valid memory story data found")
                    print("📊 MEMORY_STORY: Story: \(memoryData.storyCard)")
                    print("📊 MEMORY_STORY: Question: \(memoryData.question)")
                    print("📊 MEMORY_STORY: Options: \(memoryData.options.count)")
                    print("📊 MEMORY_STORY: Correct items: \(memoryData.correctItems)")
                    if let audioUrl = memoryData.audioUrl {
                        print("🎵 MEMORY_STORY: Audio URL: \(audioUrl)")
                    }
                } else {
                    print("❌ MEMORY_STORY: Invalid or missing memory story data")
                }
            }
        } else {
            EmptyView()
                .onAppear {
                    print("🔴 memoryStoryPuzzleDestination: selectedPuzzle is nil")
                }
        }
    }
    
    @ViewBuilder
    var crosswordPuzzleDestination: some View {
        if let puzzle = selectedPuzzle {
            CrosswordPuzzleView(
                puzzle: puzzle,
                questionIndex: currentQuestionIndex,
                totalQuestions: selectedPuzzleSet?.puzzleData.puzzles.count ?? 1,
                onAnswerSubmitted: { answer, isCorrect in
                    // Handle answer submission
                    if isCorrect {
                        currentScore += 25 // Fixed score for crossword puzzles
                    }
                    print("Crossword answer submitted: \(answer), Correct: \(isCorrect)")
                },
                onNextPuzzle: {
                    handlePuzzleCompletion(for: puzzle)
                },
                onExit: {
                    AnalyticsManager.shared.trackSafely(AnalyticsEvent("puzzle_exited", parameters: [
                            "puzzle_type": puzzle.puzzleType ?? "unknown",
                            "exit_reason": "back_button",
                            "question_index": currentQuestionIndex
                        ]))
                    selectedPuzzle = nil
                    navigationState = .none
                    selectedPuzzleSet = nil
                    currentQuestionIndex = 0
                    isPuzzleLoading = false
                }
            )
            .id(puzzle.id)
            .onAppear {
                print("🔵 CrosswordPuzzleView appeared")
                print("🔵 Puzzle question: \(puzzle.question)")
            }
        } else {
            EmptyView()
                .onAppear {
                    print("🔴 crosswordPuzzleDestination: selectedPuzzle is nil")
                }
        }
    }
    
    @ViewBuilder
    var memoryRetentionPuzzleDestination: some View {
        if let puzzle = selectedPuzzle {
            MemoryRetentionPuzzleView(
                puzzle: puzzle,
                questionIndex: currentQuestionIndex,
                totalQuestions: selectedPuzzleSet?.puzzleData.puzzles.count ?? 1,
                onAnswerSubmitted: { answer, isCorrect in
                    // Handle answer submission
                    if isCorrect {
                        currentScore += 30 // Fixed score for memory retention puzzles
                    }
                    print("🧠 Memory retention answer submitted: \(answer), Correct: \(isCorrect)")
                },
                onNextPuzzle: {
                    handlePuzzleCompletion(for: puzzle)
                },
                onExit: {
                    AnalyticsManager.shared.trackSafely(AnalyticsEvent("puzzle_exited", parameters: [
                            "puzzle_type": puzzle.puzzleType ?? "unknown",
                            "exit_reason": "back_button",
                            "question_index": currentQuestionIndex
                        ]))
                    selectedPuzzle = nil
                    navigationState = .none
                    selectedPuzzleSet = nil
                    currentQuestionIndex = 0
                    isPuzzleLoading = false
                }
            )
            .id(puzzle.id)
            .onAppear {
                print("🧠 MemoryRetentionPuzzleView appeared")
                print("🧠 Puzzle question: \(puzzle.question)")
                
                // Validate memory retention data
                if let memoryData = puzzle.memoryRetentionPuzzleData {
                    print("✅ MEMORY: Valid memory retention data found")
                    print("📊 MEMORY: Topic: \(memoryData.topic)")
                    print("📊 MEMORY: Subjects: \(memoryData.subjects.count)")
                    print("📊 MEMORY: Facts: \(memoryData.facts.count)")
                    print("📊 MEMORY: Duration: \(memoryData.estimatedDuration)s")
                    if let audioUrl = memoryData.audioUrl {
                        print("🎵 MEMORY: Audio URL: \(audioUrl)")
                    }
                } else {
                    print("❌ MEMORY: Invalid or missing memory retention data")
                }
            }
        } else {
            EmptyView()
                .onAppear {
                    print("🔴 memoryRetentionPuzzleDestination: selectedPuzzle is nil")
                }
        }
    }
    
    @ViewBuilder
    var subtractionPuzzleDestination: some View {
        if let puzzle = selectedPuzzle {
            // Try to parse subtraction data from the puzzle
            if let baseSubtractionData = puzzle.subtractionPuzzleData {
                // Create current problem data with the current index
                let currentSubtractionData = SubtractionPuzzleData(
                    allProblems: baseSubtractionData.allProblems,
                    difficulty: baseSubtractionData.difficulty,
                    hint: baseSubtractionData.hint,
                    currentProblemIndex: currentSubtractionProblemIndex
                )
                
                MathDifferenceGameView(
                    score: currentScore,
                    hearts: 3,
                    level: "\(currentQuestionIndex + 1)/\(selectedPuzzleSet?.puzzleData.puzzles.count ?? 1)",
                    number1: currentSubtractionData.number1,
                    number2: currentSubtractionData.number2,
                    onSubmitAnswer: { answer in
                        let isCorrect = answer == currentSubtractionData.correctAnswer
                        if isCorrect {
                            currentScore += 18 // Score for subtraction puzzles
                        }
                        print("🔵 SUBTRACTION: Answer submitted: \(answer), Correct: \(isCorrect)")
                        print("🔵 SUBTRACTION: Expected: \(currentSubtractionData.correctAnswer)")
                    },
                    fetchNextPuzzle: {
                        // Check if there are more problems in the current puzzle
                        if currentSubtractionData.hasNextProblem {
                            print("🔵 SUBTRACTION: Moving to next problem in set (\(currentSubtractionProblemIndex + 1) -> \(currentSubtractionProblemIndex + 2))")
                            currentSubtractionProblemIndex += 1
                        } else {
                            print("🔵 SUBTRACTION: All problems completed, moving to next puzzle")
                            currentSubtractionProblemIndex = 0 // Reset for next puzzle
                            handlePuzzleCompletion(for: puzzle)
                        }
                    }
                )
                .id("\(puzzle.id)-\(currentSubtractionProblemIndex)")
                .onAppear {
                    print("🔵 SUBTRACTION: Building subtraction puzzle view")
                    print("🔵 SUBTRACTION: Puzzle type: \(puzzle.puzzleType ?? "nil")")
                    print("🟢 SUBTRACTION: Successfully parsed subtraction data")
                    print("🟢 SUBTRACTION: Problem \(currentSubtractionData.currentProblemIndex + 1)/\(currentSubtractionData.allProblems.count)")
                    print("🟢 SUBTRACTION: \(currentSubtractionData.number1) - \(currentSubtractionData.number2) = \(currentSubtractionData.correctAnswer)")
                }
            } else {
                // Fallback: Show error and allow navigation back
                VStack(spacing: 20) {
                    Text("Subtraction puzzle format error")
                        .foregroundColor(.red)
                        .font(.title2)
                    
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Expected format:")
                            .font(.headline)
                        Text("""
                        {
                          "problems": [[75,24,51],[91,54,37]],
                          "difficulty": "Easy",
                          "hint": "Work from right to left"
                        }
                        """)
                        .font(.caption)
                        .padding()
                        .background(Color.gray.opacity(0.2))
                        .cornerRadius(8)
                        
                        Text("Received:")
                            .font(.headline)
                            .padding(.top)
                        Text(puzzle.question)
                            .font(.caption)
                            .padding()
                            .background(Color.red.opacity(0.1))
                            .cornerRadius(8)
                    }
                    
                    Button("Back to Home") {
                        selectedPuzzle = nil
                        navigationState = .none
                        currentSubtractionProblemIndex = 0
                    }
                    .padding()
                    .background(Color.blue)
                    .foregroundColor(.white)
                    .cornerRadius(8)
                }
                .padding()
                .onAppear {
                    print("🔴 SUBTRACTION: Failed to parse subtraction data")
                    print("🔴 SUBTRACTION: Question: \(puzzle.question)")
                }
            }
        } else {
            Text("Subtraction puzzle not available")
                .foregroundColor(.red)
                .onAppear {
                    print("🔴 SUBTRACTION: selectedPuzzle is nil in subtractionPuzzleDestination")
                }
        }
    }
    
    var completionOverlay: some View {
        VStack(spacing: 20) {
            Text("🎉 All Puzzles Completed!")
                .font(.largeTitle)
                .fontWeight(.bold)
                .foregroundColor(.primary)
                .multilineTextAlignment(.center)
            
            Text("Great job! You've finished the entire puzzle set.")
                .font(.title3)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
            
            // Show some stats if available
            if let puzzleSet = selectedPuzzleSet {
                VStack(spacing: 8) {
                    Text("Puzzles Completed: \(puzzleSet.puzzleData.puzzles.count)")
                        .font(.headline)
                    Text("Topic: \(puzzleSet.topic)")
                        .font(.subheadline)
                        .foregroundColor(.blue)
                }
                .padding()
                .background(Color.blue.opacity(0.1))
                .cornerRadius(12)
            }

            Button("Continue") {
                print("🔵 Completion Continue button tapped")
                
                // FIXED: Clear all state properly and ensure we're back on home
                withAnimation(.easeOut(duration: 0.3)) {
                    // Clear completion first
                    showCompletionAnimation = false
                    
                    // Then clear all puzzle-related state
                    selectedPuzzle = nil
                    //navigationState = .none
                    selectedPuzzleSet = nil
                    currentQuestionIndex = 0
                    currentSubtractionProblemIndex = 0
                    isPuzzleLoading = false
                    
                    // Ensure we're on the custom puzzles tab since that's where custom puzzles are
                    selectedTab = .customPuzzles
                }
            }
            .font(.title2)
            .fontWeight(.semibold)
            .foregroundColor(.white)
            .padding(.horizontal, 40)
            .padding(.vertical, 12)
            .background(
                LinearGradient(
                    colors: [.orange, .red],
                    startPoint: .leading,
                    endPoint: .trailing
                )
            )
            .cornerRadius(25)
            .shadow(color: .orange.opacity(0.3), radius: 10, x: 0, y: 5)
        }
        .padding(30)
        .background(
            RoundedRectangle(cornerRadius: 20)
                .fill(Color(.systemBackground))
                .shadow(color: .black.opacity(0.2), radius: 20, x: 0, y: 10)
        )
        .padding(.horizontal, 40)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(
            Color.black.opacity(0.4)
                .ignoresSafeArea()
                .onTapGesture {
                    // Prevent dismissing by tapping background
                }
        )
        .onAppear {
            print("🎉 Completion overlay appeared - showCompletionAnimation: \(showCompletionAnimation)")
        }
    }
    
    
    private func getCryptoPuzzleScore(for difficulty: String) -> Int {
        switch difficulty.lowercased() {
        case "easy": return 50
        case "medium": return 75
        case "hard": return 100
        case "expert": return 150
        default: return 75
        }
    }

    private func getMusicMatchScore(for difficulty: String) -> Int {
        switch difficulty.lowercased() {
        case "easy": return 30
        case "medium": return 45
        case "hard": return 60
        case "expert": return 80
        default: return 45
        }
    }
}
