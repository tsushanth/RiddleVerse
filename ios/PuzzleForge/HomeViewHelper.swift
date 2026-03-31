//
//  HomeViewHelper.swift
//  PuzzleForge
//
//  Created by Sushanth Tiruvaipati on 9/17/25.
//


//
//  HomeViewHelpers.swift
//  PuzzleForge
//

import Foundation
import SwiftUI

extension HomeView {
    
    // MARK: - Puzzle Completion Handling
    func handlePuzzleCompletion(for puzzle: Puzzle) {
        print("🔵 handlePuzzleCompletion called for puzzle type: \(puzzle.puzzleType ?? "unknown")")
        print("🔵 selectedPuzzleSet is nil: \(selectedPuzzleSet == nil)")
        print("🔵 currentQuestionIndex: \(currentQuestionIndex)")
        print("🔵 completedPuzzleCount: \(completedPuzzleCount), targetPuzzleCount: \(targetPuzzleCount)")
        
        let completionTime = Date()
        let timeSpent = sessionStartTime != nil ? completionTime.timeIntervalSince(sessionStartTime!) : 0
        
        userStatsManager.recordPuzzleCompletion(
            puzzleType: puzzle.puzzleType ?? "unknown",
            score: currentScore,
            timeSpentSeconds: Int(timeSpent),
            isCorrect: true,
            difficulty: puzzle.difficulty ?? selectedDifficulty
        )
        
        let puzzleSessionStats = SessionStatistics(
            correctAnswers: 1,
            totalAnswers: 1,
            totalTimeSeconds: Int(timeSpent),
            bestStreak: 1,
            currentStreak: 1,
            totalScore: currentScore,
            individualTimes: [Int(timeSpent)],
            puzzleType: puzzle.puzzleType ?? "unknown"
        )
        sessionStats.append(puzzleSessionStats)
        
        completedPuzzleCount += 1
        
        AnalyticsManager.shared.trackFirstPuzzleConversion(
            puzzleType: puzzle.puzzleType ?? "unknown",
            score: currentScore
        )
        
        if completedPuzzleCount >= 2 && timeSpent > 300 {
            AnalyticsManager.shared.trackEngagementConversion(
                sessionDuration: timeSpent,
                puzzlesSolved: completedPuzzleCount
            )
        }
        
        AnalyticsManager.shared.trackPuzzleCompleted(
            puzzleType: puzzle.puzzleType ?? "unknown",
            difficulty: puzzle.difficulty,
            score: currentScore
        )
        
        if let set = selectedPuzzleSet {
            handleCustomPuzzleSetCompletion(set: set)
        } else {
            handleSinglePuzzleSessionCompletion(puzzle: puzzle)
        }
        
        AnalyticsManager.shared.puzzleCompleted(score: currentScore)
        trackRetentionSignals(puzzle: puzzle)
    }
    
    private func handleCustomPuzzleSetCompletion(set: OuterPuzzleData) {
        print("🔵 Custom puzzle set mode")
        let nextIndex = currentQuestionIndex + 1
        let totalPuzzles = set.puzzleData.puzzles.count
        
        if nextIndex < totalPuzzles {
            print("🔵 Loading next puzzle in set (\(nextIndex + 1)/\(totalPuzzles))")
            currentQuestionIndex = nextIndex
            
            let apiPuzzle = set.puzzleData.puzzles[nextIndex]
            let nextPuzzle = Puzzle(apiPuzzle: apiPuzzle, outerPuzzleData: set)
            let newNavigationState = determineCustomPuzzleNavigationTypeFromFormat(set.format, topic: set.topic)
            
            selectedPuzzle = nextPuzzle
            navigationState = newNavigationState
            print("🔵 Successfully updated to next puzzle in set")
            
        } else {
            print("🎉 CUSTOM PUZZLE SET COMPLETED!")
            completionEarnedPoints = totalPuzzles * 20
            isInActiveSession = false
            showCompletionScreen = true
        }
    }
    
    private func handleSinglePuzzleSessionCompletion(puzzle: Puzzle) {
        print("🔵 Single puzzle session mode")
        print("🔵 Completed: \(completedPuzzleCount), Target: \(targetPuzzleCount)")
        
        if completedPuzzleCount >= targetPuzzleCount {
            print("🎉 SESSION COMPLETE! Completed \(completedPuzzleCount)/\(targetPuzzleCount) puzzles")
            isInActiveSession = false
            showCompletionScreen = true
            
        } else {
            print("🔵 Session continuing - generating next puzzle (\(completedPuzzleCount)/\(targetPuzzleCount))")
            
            if let puzzleType = puzzle.puzzleType {
                keepCurrentViewWhileFetchingNext = true
                isPuzzleLoading = true
                isInActiveSession = true
                print("🔄 Set isPuzzleLoading = true and isInActiveSession = true")
                
                print("🔵 Fetching next puzzle in session immediately: \(puzzleType)")
                self.fetchPuzzle(for: puzzleType, for: puzzle.difficulty)
                
            } else {
                print("🔵 No puzzle type available, ending session")
                isInActiveSession = false
                showCompletionScreen = true
            }
        }
    }
    
    // MARK: - Navigation Type Determination
    func determineNavigationType(for category: String, options: [String]) -> PuzzleNavigationType {
        switch category {
        case "average":
            return .average
        case "division":
            return .division
        case "mathestimation":
            return .estimation
        case "percentages":
            return .percentage
        case "discounts":
            return .discounts
        case "purchasing":
            return .purchasing
        case "conversion":
            return .conversion
        case "connotationwords":
            return .swipeWord
        case "mathtipping":
            return .tipBubble
        case "anagram":
            return .anagram
        case "subtraction":
            return .subtraction
        case "crossword":
            return .crossword
        case "memorystory", "memory_story":
            return .memoryStory
        case "memory_retention", "memoryretention":
            return .memoryRetention
        case "memory_sequencing", "memorysequencing":
            return .memorySequencing
        case "wordprefix", "word_prefix":
            return .wordPrefix
        case "antonyms", "antonym":
            return .antonym
        case "synonyms", "synonym":
            return .synonym
        case "memorysquares", "memory_squares":
            return .memorySquares
        case "memorypreviouspair", "memory_previous_pair":
            return .memoryPreviousPair
        case "memoryprevioussingle", "memory_previous_single":
            return .memoryPreviousSingle
        case "pinballdeflector", "pinball_deflector":
            return .pinballDeflector
        case "colorshapematching", "color_shape_matching":
            return .colorshapematching
        case "imagevortex", "image_vortex":
            return .imagevortex
        case "mathcomparison", "math_comparison":
            return .mathComparison
        case "numbersequence", "number_sequence":
            return .numberSequence
        case "numbersum", "number_sum":
            return .numbersum
        case "symbolswipe", "symbol_swipe":
            return .symbolSwipe
        case "uniqueobject", "unique_object":
            return .uniqueObject
        case "wordsearch", "word_search":
            return .wordSearch
        case "geography_cities", "geographycities":
            return .geographyCities
        case "geography_countries", "geographycountries":
            return .geographyCountries
        case "wordsnake", "word_snake":
            return .wordSnake
        case "find_object":
            return .findObject
        case "crypto", "crypto_puzzle":
            return .crypto
        case "imagepuzzle":
            return .imagePuzzle
        case "musicidentification":
            return .musicTrack
        case "sentencetransitions":
            return .sentencetransitions
        default:
            return options.isEmpty ? .qa : .multipleChoice
        }
    }
    
    func determineCustomPuzzleNavigationTypeFromFormat(_ format: String, topic: String) -> PuzzleNavigationType {
        let lowercaseFormat = format.lowercased()
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: ".", with: "")
            .replacingOccurrences(of: "!", with: "")
            .replacingOccurrences(of: "(", with: "")
            .replacingOccurrences(of: ")",
                                  let lowercaseTopic = topic.lowercased()
                                              .trimmingCharacters(in: .whitespacesAndNewlines)
                                              .replacingOccurrences(of: ".", with: "")
                                              .replacingOccurrences(of: "!", with: "")
                                              .replacingOccurrences(of: "(", with: "")
                                              .replacingOccurrences(of: ")", with: "")
                                          
                                          print("🔍 Determining navigation from top-level - Format: '\(lowercaseFormat)', Topic: '\(lowercaseTopic)'")
                                          print("🔍 Original format: '\(format)', Original topic: '\(topic)'")
                                          
                                          switch true {
                                          case lowercaseFormat.contains("word snake") || lowercaseFormat.contains("wordsnake"):
                                              print("✅ Routing to word snake view (format contains 'word snake')")
                                              return .wordSnake
                                          case lowercaseFormat.contains("find_object"):
                                              return .findObject
                                          case lowercaseFormat.contains("word search") || lowercaseFormat.contains("wordsearch"):
                                              print("✅ Routing to word search view (format contains 'word search')")
                                              return .wordSearch
                                          case lowercaseFormat.contains("music"):
                                              return .musicTrack
                                          case lowercaseFormat.contains("image"):
                                              return .imagePuzzle
                                          case lowercaseFormat.contains("crossword"):
                                              print("✅ Routing to crossword view (format contains 'crossword')")
                                              return .crossword
                                          case lowercaseFormat.contains("anagram"):
                                              print("✅ Routing to anagram view (format contains 'anagram')")
                                              return .anagram
                                          case lowercaseFormat.contains("crypto") || lowercaseFormat.contains("cryptogram"):
                                              print("✅ Routing to crypto view (format contains 'crypto')")
                                              return .crypto
                                          case lowercaseFormat.contains("memory story") || lowercaseFormat.contains("memorystory"):
                                              print("✅ Routing to memory story view (format contains 'memory story')")
                                              return .memoryStory
                                          case lowercaseFormat.contains("memory retention") || lowercaseFormat.contains("memoryretention"):
                                              print("✅ Routing to memory retention view (format contains 'memory retention')")
                                              return .memoryRetention
                                          case lowercaseFormat.contains("synonyms") || lowercaseFormat.contains("synonym"):
                                              print("✅ Routing to synonym view (format contains 'synonym')")
                                              return .synonym
                                          case lowercaseFormat.contains("antonyms") || lowercaseFormat.contains("antonym"):
                                              print("✅ Routing to antonym view (format contains 'antonym')")
                                              return .antonym
                                          case lowercaseFormat.contains("word prefix") || lowercaseFormat.contains("wordprefix"):
                                              print("✅ Routing to word prefix view (format contains 'word prefix')")
                                              return .wordPrefix
                                          case lowercaseFormat.contains("memory sequencing") || lowercaseFormat.contains("memorysequencing"):
                                              print("✅ Routing to memory sequencing view (format contains 'memory sequencing')")
                                              return .memorySequencing
                                          case lowercaseFormat.contains("memory squares") || lowercaseFormat.contains("memorysquares"):
                                              print("✅ Routing to memory squares view (format contains 'memory squares')")
                                              return .memorySquares
                                          case lowercaseFormat.contains("average"):
                                              print("✅ Routing to average view (format contains 'average')")
                                              return .average
                                          case lowercaseFormat.contains("division"):
                                              print("✅ Routing to division view (format contains 'division')")
                                              return .division
                                          case lowercaseFormat.contains("percentage"):
                                              print("✅ Routing to percentage view (format contains 'percentage')")
                                              return .percentage
                                          case lowercaseFormat.contains("discount"):
                                              print("✅ Routing to discounts view (format contains 'discount')")
                                              return .discounts
                                          case lowercaseFormat.contains("purchasing") || lowercaseFormat.contains("subscription"):
                                              print("✅ Routing to purchasing view (format contains 'purchasing')")
                                              return .purchasing
                                          case lowercaseFormat.contains("conversion"):
                                              print("✅ Routing to conversion view (format contains 'conversion')")
                                              return .conversion
                                          case lowercaseFormat.contains("subtraction"):
                                              print("✅ Routing to subtraction view (format contains 'subtraction')")
                                              return .subtraction
                                          case lowercaseFormat.contains("estimation"):
                                              print("✅ Routing to estimation view (format contains 'estimation')")
                                              return .estimation
                                          default:
                                              print("🔍 No format match found, checking topic...")
                                              switch true {
                                              case lowercaseTopic.contains("word search") || lowercaseTopic.contains("wordsearch"):
                                                  print("✅ Routing to word search view (topic contains 'word search')")
                                                  return .wordSearch
                                              case lowercaseTopic.contains("music"):
                                                  return .musicTrack
                                              case lowercaseTopic.contains("image"):
                                                  return .imagePuzzle
                                              case lowercaseTopic.contains("crossword"):
                                                  print("✅ Routing to crossword view (topic contains 'crossword')")
                                                  return .crossword
                                              case lowercaseTopic.contains("anagram"):
                                                  print("✅ Routing to anagram view (topic contains 'anagram')")
                                                  return .anagram
                                              case lowercaseFormat.contains("crypto") || lowercaseFormat.contains("cryptogram"):
                                                  print("✅ Routing to crypto view (format contains 'crypto')")
                                                  return .crypto
                                              case lowercaseTopic.contains("memory"):
                                                  if lowercaseTopic.contains("story") {
                                                      print("✅ Routing to memory story view (topic contains 'memory story')")
                                                      return .memoryStory
                                                  } else if lowercaseTopic.contains("retention") {
                                                      print("✅ Routing to memory retention view (topic contains 'memory retention')")
                                                      return .memoryRetention
                                                  } else if lowercaseTopic.contains("sequencing") {
                                                      print("✅ Routing to memory sequencing view (topic contains 'memory sequencing')")
                                                      return .memorySequencing
                                                  } else if lowercaseTopic.contains("squares") {
                                                      print("✅ Routing to memory squares view (topic contains 'memory squares')")
                                                      return .memorySquares
                                                  }
                                                  fallthrough
                                              default:
                                                  print("✅ Routing to multiple choice view (default for custom puzzles)")
                                                  return .multipleChoice
                                              }
                                          }
                                      }
                                      
                                      // MARK: - Helper Functions
                                      func getCategoryDisplayInfo(for category: String) -> (title: String, subtitle: String) {
                                          switch category {
                                          case "math":
                                              return ("Math", "Mathematical Challenges")
                                          case "storyPuzzle":
                                              return ("Story Puzzle", "Narrative Mysteries")
                                          case "anagram":
                                              return ("Anagram", "Word Scrambles")
                                          case "antonyms", "antonym":
                                              return ("Antonyms", "Match Opposite Words")
                                          case "synonyms", "synonym":
                                              return ("Synonyms", "Group Similar Words")
                                          case "trivia":
                                              return ("Trivia", "General Knowledge")
                                          case "average":
                                              return ("Average", "Calculate Averages")
                                          case "wordsnake", "word_snake":
                                              return ("Word Snake", "Find Connected Words")
                                          case "find_object":
                                              return ("Find Object", "Find Objects to Images")
                                          case "division":
                                              return ("Division", "Master Division")
                                          case "mathestimation":
                                              return ("Estimation", "Chart Estimation")
                                          case "percentages":
                                              return ("Percentage", "Percentage Calculations")
                                          case "discounts":
                                              return ("Discounts", "Calculate Discounts")
                                          case "purchasing":
                                              return ("Purchasing", "Subscription Calculations")
                                          case "connotationwords":
                                              return ("Word Connotations", "Sort words by positive/negative connotation")
                                          case "conversion":
                                              return ("Conversion", "Unit Comparisons")
                                          case "mathtipping":
                                              return ("Tip Calculation", "Calculate correct tip amounts")
                                          case "memory_retention", "memoryretention":
                                              return ("Memory Retention", "Audio & Categorization")
                                          case "memory_sequencing", "memorysequencing":
                                              return ("Memory Sequencing", "Audio & Sequence Events")
                                          case "memorystory", "memory_story":
                                              return ("Memory Story", "Audio Memory Challenges")
                                          case "wordprefix", "word_prefix":
                                              return ("Word Prefix", "Find words with prefix")
                                          case "crossword":
                                              return ("Crossword", "Word Puzzles")
                                          case "memorysquares", "memory_squares":
                                              return ("Memory Squares", "Pattern Recognition")
                                          case "contextswitch", "context_switch":
                                              return ("Context Switch", "Memory & Interference")
                                          case "mathcrossword":
                                              return ("Math Crossword", "Math Puzzles")
                                          case "dualtask", "dual_task":
                                              return ("Dual Task", "Switch Between Tasks")
                                          case "colortextmatching", "color_text_matching":
                                              return ("Color Text Match", "Match Meaning & Color")
                                          case "geography_cities", "geographycities":
                                              return ("Geography Cities", "Place Cities on World Map")
                                          case "geography_countries", "geographycountries":
                                              return ("Geography Countries", "Place Countries on World Map")
                                          default:
                                              return (category.capitalized, "Test your \(category.capitalized) skills")
                                          }
                                      }
                                      
                                      func createQuizCardAction(for category: String) -> () -> Void {
                                          return {
                                              selectedCategory = category
                                              selectedDifficulty = difficultyManager.selectedDifficulty
                                              
                                              isPuzzleLoading = true
                                              navigationState = .qa
                                              selectedPuzzle = nil
                                              
                                              DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
                                                  self.fetchPuzzle(for: category, for: self.selectedDifficulty)
                                              }
                                              
                                              UserDefaults.standard.addRecentPuzzleType(category)
                                              
                                              AnalyticsManager.shared.trackSafely(AnalyticsEvent("category_selected", parameters: [
                                                  "puzzle_type": category,
                                                  "source": "categories_grid",
                                                  "filter": selectedFilter.rawValue,
                                                  "difficulty": difficultyManager.selectedDifficulty
                                              ]))
                                          }
                                      }
                                      
                                      private func trackRetentionSignals(puzzle: Puzzle) {
                                          let currentDate = Date()
                                          let lastActiveKey = "last_active_date"
                                          
                                          if let lastActive = UserDefaults.standard.object(forKey: lastActiveKey) as? Date {
                                              let daysSinceLastActive = Calendar.current.dateComponents([.day], from: lastActive, to: currentDate).day ?? 0
                                              
                                              if daysSinceLastActive >= 1 {
                                                  AnalyticsManager.shared.track(AnalyticsEvent("user_returned", parameters: [
                                                      "days_since_last_active": daysSinceLastActive,
                                                      "return_activity": "puzzle_completion",
                                                      "puzzle_type": puzzle.puzzleType ?? "unknown"
                                                  ]))
                                                  
                                                  if daysSinceLastActive >= 7 {
                                                      AnalyticsManager.shared.trackConversion(
                                                          AnalyticsEvent("weekly_retention", parameters: [
                                                              "days_since_last_active": daysSinceLastActive
                                                          ]),
                                                          value: 15.0
                                                      )
                                                  }
                                              }
                                          }
                                          
                                          UserDefaults.standard.set(currentDate, forKey: lastActiveKey)
                                          
                                          let todayKey = "active_date_\(Calendar.current.dateComponents([.year, .month, .day], from: currentDate))"
                                          if !UserDefaults.standard.bool(forKey: todayKey) {
                                              UserDefaults.standard.set(true, forKey: todayKey)
                                              
                                              AnalyticsManager.shared.track(AnalyticsEvent("daily_active_user", parameters: [
                                                  "date": currentDate.timeIntervalSince1970,
                                                  "activity_type": "puzzle_completion"
                                              ]))
                                          }
                                      }
                                  }
