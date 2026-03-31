// services/puzzleGeneration.js - Enhanced with ImageMatch Integration

import { PUZZLE_TYPES, DIFFICULTY_LEVELS, PUZZLE_METADATA } from '../config/puzzleConfig.js';
import { generatePromptForPuzzle } from '../criteria/puzzlePrompts.js';
import { PuzzleValidator } from './puzzleValidation.js';
import { callAI } from '../utils/aiClient.js';
import { generateRandomizedMathPuzzle } from './mathPuzzleIntegration.js';
import { deduplicationService } from './deduplicationService.js';
import { enhancedAntonymGenerator } from './antonymGeneratorService.js';
import { wordSearchService } from './wordSearchGeneratorService.js';
import { USAGE_CATEGORIES } from '../utils/usageTracker.js';
import { ProgressivePuzzleSystem } from './progressivePuzzleSystem.js';

/**
 * Enhanced puzzle generation service with ImageMatch integration
 */
export class PuzzleGenerator {
  constructor() {
    this.maxRetries = 5;
    this.validationModel = "gpt-3.5-turbo";
    this.storePuzzleInSupabase = null;
    this.debugMode = true;
    this.progressCallbacks = new Map();
    this.generationProgress = new Map();

    // Initialize deduplication service
    this.deduplicationService = deduplicationService;
    this.deduplicationService.setDebugMode(this.debugMode);

    // Track which puzzle types use randomized generation (UPDATED with IMAGE_MATCH)
    this.randomizedTypes = [
      PUZZLE_TYPES.LETTER_SET,
      PUZZLE_TYPES.MATH_ESTIMATION,
      PUZZLE_TYPES.MATH_TIPPING,
      PUZZLE_TYPES.PERCENTAGES,
      PUZZLE_TYPES.DIVISION,
      PUZZLE_TYPES.AVERAGE,
      PUZZLE_TYPES.SUBTRACTION,
      PUZZLE_TYPES.PURCHASING,
      PUZZLE_TYPES.DISCOUNTS,
      PUZZLE_TYPES.CONVERSION,
      PUZZLE_TYPES.MEMORY_SQUARES,
      PUZZLE_TYPES.CROSSWORD,
      PUZZLE_TYPES.DAILY_CROSSWORD,
      PUZZLE_TYPES.WORD_PREFIX,
      PUZZLE_TYPES.MEMORY_PREVIOUS_PAIR,
      PUZZLE_TYPES.MEMORY_PREVIOUS_SINGLE,
      PUZZLE_TYPES.MEMORY_MATRIX_PATH,
      PUZZLE_TYPES.PINBALL_DEFLECTOR,
      PUZZLE_TYPES.ANTONYMS,
      PUZZLE_TYPES.ANAGRAM,
      PUZZLE_TYPES.WORD_SEARCH,
      PUZZLE_TYPES.UNIQUE_OBJECT,
      PUZZLE_TYPES.MATH_COMPARISON,
      PUZZLE_TYPES.CRYPTO,
      PUZZLE_TYPES.WORD_SNAKE,
      PUZZLE_TYPES.FIND_DIFFERENCES,
      PUZZLE_TYPES.FIND_OBJECT,
      PUZZLE_TYPES.IMAGE_QUESTION,
      PUZZLE_TYPES.WALDO_PUZZLE,
      PUZZLE_TYPES.MUSIC_IDENTIFICATION,
      PUZZLE_TYPES.IMAGE_PUZZLE,
      PUZZLE_TYPES.IMAGE_MATCH,
      PUZZLE_TYPES.FLOW_PUZZLE,
      PUZZLE_TYPES.PROGRESSIVE_REVELATION,
      PUZZLE_TYPES.REAL_OR_AI,
      PUZZLE_TYPES.ODD_ONE_OUT
    ];
  }

  /**
   * Debug logging helper for server-side debugging
   */
  debugLog(message, type = 'info') {
    if (this.debugMode) {
      const timestamp = new Date().toISOString();
      const emoji = type === 'error' ? '❌' : type === 'success' ? '✅' : type === 'warning' ? '⚠️' : '🔄';
      const logMessage = `${emoji} [${timestamp}] PUZZLE_GEN: ${message}`;
      
      switch (type) {
        case 'error':
          console.error(logMessage);
          break;
        case 'warning':
          console.warn(logMessage);
          break;
        case 'success':
          console.info(logMessage);
          break;
        default:
          console.log(logMessage);
      }
    }
  }

  /**
   * Set the storage function (called from puzzleService.js after initialization)
   */
  setStorageFunction(storageFunction) {
    this.storePuzzleInSupabase = storageFunction;
    this.debugLog("Storage function set successfully");
  }

  setProgressCallback(generationId, callback) {
    this.progressCallbacks.set(generationId, callback);
  }

  updateProgress(generationId, step, progress, message) {
    const progressData = {
      generationId,
      step,
      progress,
      message,
      timestamp: new Date().toISOString()
    };
    
    this.generationProgress.set(generationId, progressData);
    
    const callback = this.progressCallbacks.get(generationId);
    if (callback) {
      callback(progressData);
    }
  }

  /**
   * Main puzzle generation entry point - ENHANCED with IMAGE_MATCH
   */
  async generatePuzzle(puzzleType, modelName = "gpt-3.5-turbo", difficulty = DIFFICULTY_LEVELS.MEDIUM) {
    this.debugLog(`🚀 STARTING: ${difficulty} ${puzzleType} puzzle generation`);
    
    try {
      // ✅ Input validation first - normalize puzzle type aliases
      const puzzleTypeAliases = {
        'snakewordsearch': 'wordsnake',
        'pairmemory': 'memorypreviouspair',
        'singlemamory': 'memoryprevioussingle',
        'singlememory': 'memoryprevioussingle',
        'matrixpath': 'memorymatrixpath',
        'sequencing': 'memorysequencing',
        'retention': 'memoryretention',
        'squares': 'memorysquares'
      };

      const normalizedType = puzzleType.toLowerCase();
      if (puzzleTypeAliases[normalizedType]) {
        puzzleType = puzzleTypeAliases[normalizedType];
      }
      const inputValidation = this.validateInputs(puzzleType, difficulty);
      if (!inputValidation.isValid) {
        this.debugLog(`Input validation failed: ${inputValidation.reason}`, 'error');
        return { success: false, message: inputValidation.reason };
      }

      // 🎲 Check if this puzzle type supports randomized generation
      if (this.randomizedTypes.includes(puzzleType.toLowerCase())) {
        this.debugLog(`🎲 Using randomized generation for ${puzzleType}...`);
        return await this.generateRandomizedPuzzle(puzzleType, difficulty);
      }

      // ✅ Lazy load storage function if not set
      if (!this.storePuzzleInSupabase) {
        try {
          const { storePuzzleInSupabase } = await import('./puzzleService.js');
          this.storePuzzleInSupabase = storePuzzleInSupabase;
          this.debugLog("Storage function loaded from puzzleService.js");
        } catch (importError) {
          this.debugLog(`Failed to import storage function: ${importError.message}`, 'error');
          return { success: false, message: `Storage function import failed: ${importError.message}` };
        }
      }

      let attempts = 0;
      let puzzle = null;

      while (attempts < this.maxRetries) {
        this.debugLog(`🔄 Generation attempt ${attempts + 1}/${this.maxRetries}`);
        
        try {
          // Step 1: Generate puzzle with AI
          this.debugLog(`Calling AI for ${puzzleType}...`);
          puzzle = await this.generatePuzzleWithAI(puzzleType, difficulty);
          
          if (!puzzle) {
            this.debugLog(`AI generation failed on attempt ${attempts + 1}`, 'warning');
            attempts++;
            continue;
          }
          
          this.debugLog(`AI generation successful on attempt ${attempts + 1}`, 'success');

          // Step 2: Ensure difficulty is correctly assigned
          puzzle.difficulty = difficulty;

          // Step 3: Process puzzle (calculations, formatting, etc.)
          this.debugLog(`Processing ${puzzleType} puzzle...`);
          const processResult = await this.processPuzzle(puzzle, puzzleType);
          
          if (!processResult.success) {
            this.debugLog(`Processing failed: ${processResult.error}`, 'warning');
            attempts++;
            continue;
          }
          
          this.debugLog(`Processing successful`, 'success');

          // FIXED: Handle anagram special case
          // In the anagram handling section - collect all puzzles first, then batch insert
          if ((puzzleType === PUZZLE_TYPES.ANAGRAM || puzzleType === PUZZLE_TYPES.ANTONYMS || puzzleType === PUZZLE_TYPES.SYNONYMS)) {
            this.debugLog(`Processing ${processResult.puzzles.length} individual ${puzzleType} puzzles...`);
            
            const validatedPuzzles = [];
            
            // First pass: validate all puzzles
            for (const individualPuzzle of processResult.puzzles) {
              const validationResult = await PuzzleValidator.validatePuzzle(individualPuzzle, puzzleType, difficulty);
              if (validationResult.isValid) {
                const isDuplicate = await this.deduplicationService.isDuplicate(puzzleType, individualPuzzle.question);
                if (!isDuplicate) {
                  validatedPuzzles.push(individualPuzzle);
                }
              }
            }
            
            // Second pass: batch store all validated puzzles
            const storeResult = await this.storeBatchPuzzles(validatedPuzzles, puzzleType, modelName);
            
            if (storeResult.success) {
              return { 
                success: true, 
                puzzleIds: storeResult.puzzleIds,
                count: storeResult.puzzleIds.length,
                message: `Generated ${storeResult.puzzleIds.length} ${puzzleType} puzzles`
              };
            }
          } else {
            // For non-anagram puzzles
            puzzle = processResult.puzzle;
          }

          // Step 4: Validate puzzle meets standards (skip for anagrams as handled above)
          if (puzzleType !== PUZZLE_TYPES.ANAGRAM) {
            this.debugLog(`Validating ${puzzleType} puzzle...`);
            const validationResult = await PuzzleValidator.validatePuzzle(puzzle, puzzleType, difficulty);
            
            if (!validationResult.isValid) {
              this.debugLog(`Validation failed: ${validationResult.reason}`, 'warning');
              attempts++;
              continue;
            }
            
            this.debugLog(`Validation successful`, 'success');
          }

          // Step 5: UPDATED - Use centralized deduplication service
          this.debugLog(`Checking for duplicates using deduplication service...`);
          const isDuplicate = await this.deduplicationService.isDuplicate(puzzleType, puzzle.question);
          
          if (isDuplicate) {
            this.debugLog(`Duplicate puzzle detected by deduplication service`, 'warning');
            attempts++;
            continue;
          }
          
          this.debugLog(`No duplicates found by deduplication service`, 'success');

          // Step 6: Store puzzle
          this.debugLog(`Storing ${puzzleType} puzzle...`);
          const storeResult = await this.storePuzzle(puzzle, puzzleType, modelName);
          
          if (storeResult.success) {
            this.debugLog(`🎉 COMPLETED: Successfully stored ${difficulty} ${puzzleType} puzzle! ID: ${storeResult.puzzleId}`, 'success');
            return storeResult;
          } else {
            this.debugLog(`Storage failed: ${storeResult.message}`, 'error');
            attempts++;
            continue;
          }

        } catch (error) {
          this.debugLog(`💥 Error in attempt ${attempts + 1}: ${error.message}`, 'error');
          console.error('Full error:', error);
          attempts++;
          continue;
        }
      }

      this.debugLog(`❌ FAILED: Could not generate valid puzzle after ${this.maxRetries} attempts`, 'error');
      return { 
        success: false, 
        message: `Failed to generate valid ${difficulty} ${puzzleType} puzzle after ${this.maxRetries} attempts` 
      };

    } catch (error) {
      this.debugLog(`💥 FATAL ERROR: ${error.message}`, 'error');
      console.error('Fatal error details:', error);
      return { success: false, message: `Generation error: ${error.message}` };
    }
  }

  /**
   * Generate puzzle using randomized generators - ENHANCED with IMAGE_MATCH
   */
  async generateRandomizedPuzzle(puzzleType, difficulty) {
    this.debugLog(`🎲 Generating randomized ${puzzleType} puzzle (${difficulty})`);
    
    try {
      const wordBasedPuzzles = ['anagram', 'antonyms', 'synonyms'];
      const isWordBased = wordBasedPuzzles.includes(puzzleType.toLowerCase());
      const maxRetries = isWordBased ? 5 : 1;
      
      for (let attempt = 1; attempt <= maxRetries; attempt++) {
        this.debugLog(`🔄 Generation attempt ${attempt}/${maxRetries}`);
        
        let result;

        // ✅ NEW: IMAGE_MATCH INTEGRATION
        if (puzzleType.toLowerCase() === 'imagematch') {
          this.debugLog(`🌍 Using enhanced Pixabay image match generation...`);
          const { EnhancedPixabayPuzzleSystem } = await import('./enhancedPixabayImageMatch.js');
          
          try {
            const puzzleSystem = new EnhancedPixabayPuzzleSystem({
              pixabayKey: process.env.PIXABAY_API_KEY,
              debug: this.debugMode
            });

            // Generate complete puzzle with multiple questions
            const puzzleResult = await puzzleSystem.generateCompletePuzzle(null, null, 3);
            
            if (puzzleResult.success) {
              this.debugLog(`✅ Image match puzzle generated successfully`);
              
              // Format for storage
              result = {
                success: true,
                puzzleData: {
                  question: JSON.stringify({
                    puzzleId: puzzleResult.puzzle.puzzleId,
                    theme: puzzleResult.puzzle.theme,
                    themeItem: puzzleResult.puzzle.themeItem,
                    title: puzzleResult.puzzle.title,
                    description: puzzleResult.puzzle.description,
                    primaryImage: puzzleResult.puzzle.primaryImage,
                    questions: puzzleResult.puzzle.questions,
                    totalQuestions: puzzleResult.puzzle.totalQuestions,
                    timeLimit: puzzleResult.puzzle.timeLimit,
                    difficulty: puzzleResult.puzzle.difficulty,
                    instructions: "Look at the image and answer the questions about its origin, context, or cultural significance"
                  }),
                  answer: JSON.stringify({
                    correctAnswers: puzzleResult.puzzle.questions.map(q => q.answer),
                    questions: puzzleResult.puzzle.questions,
                    totalQuestions: puzzleResult.puzzle.totalQuestions,
                    maxScore: puzzleResult.puzzle.totalQuestions * 10,
                    passingScore: Math.ceil(puzzleResult.puzzle.totalQuestions * 0.6) * 10,
                    themeContext: puzzleResult.puzzle.themeItem
                  }),
                  hint: `Test your knowledge about ${puzzleResult.puzzle.themeItem.answer || puzzleResult.puzzle.theme}`,
                  difficulty: difficulty,
                  metadata: {
                    theme: puzzleResult.puzzle.theme,
                    questionCount: puzzleResult.puzzle.totalQuestions,
                    imageSource: 'pixabay_enhanced',
                    generatedAt: puzzleResult.puzzle.generated,
                    puzzleType: 'image_match',
                    hasMultipleChoice: true,
                    imageUrl: puzzleResult.puzzle.primaryImage?.url,
                    photographer: puzzleResult.puzzle.primaryImage?.photographer,
                    apiStats: puzzleResult.puzzle.apiStats,
                    requiresGeographicalKnowledge: true,
                    educationalValue: true
                  }
                },
                imageGenerated: true // Flag that image was successfully sourced
              };
            } else {
              this.debugLog(`❌ Image match generation failed: ${puzzleResult.error}`, 'error');
              result = { 
                success: false, 
                message: `Image match generation failed: ${puzzleResult.error}` 
              };
            }
          } catch (imageMatchError) {
            this.debugLog(`❌ Image match system error: ${imageMatchError.message}`, 'error');
            result = { 
              success: false, 
              message: `Image match system error: ${imageMatchError.message}` 
            };
          }
        }
        // EXISTING PUZZLE TYPE HANDLERS
        else if (puzzleType.toLowerCase() === 'crossword') {
          this.debugLog(`📚 Using 5x5 crossword generation...`);
          const { BeamSearch5x5CrosswordGenerator } = await import('./crosswordGeneratorService.js');
          const generator = new BeamSearch5x5CrosswordGenerator();
          
          const wordSet = await this.generateCrosswordWordsForTopic(difficulty);
          if (!wordSet || wordSet.length < 8) {
            return {
              success: false,
              message: 'Failed to get sufficient words from database for crossword'
            };
          }
          
          const crosswordResult = await generator.generate5x5Crossword(wordSet);
          if (!crosswordResult || crosswordResult.words.length < 5) {
            return {
              success: false,
              message: 'Failed to generate 5x5 crossword with sufficient words'
            };
          }
          
          result = {
            success: true,
            puzzleData: generator.formatForStorage(crosswordResult, difficulty)
          };

        } else if (puzzleType.toLowerCase() === 'dailycrossword') {
          this.debugLog(`📚 Using enhanced daily crossword generation with multi-attempt strategy...`);
          
          try {
            const { DailyCrosswordGenerator } = await import('./dailyCrosswordGenerator.js');
            const { supabase } = await import('../config/database.js');
            
            // Initialize the daily crossword generator WITH SUPABASE
            const dailyGenerator = new DailyCrosswordGenerator(supabase);
            dailyGenerator.setDebugMode(this.debugMode);
            
            // Use the proven multi-attempt strategy
            const crosswordResult = await dailyGenerator.generateDailyCrossword(difficulty, 50);
            
            if (crosswordResult.success) {
              this.debugLog(`✅ Daily crossword generated successfully`);
              this.debugLog(`📊 Stats: ${crosswordResult.stats.wordsPlaced} words, ${crosswordResult.stats.emptySpaces} empty spaces`);
              this.debugLog(`🎯 Efficiency: ${crosswordResult.stats.efficiency}`);
              
              result = {
                success: true,
                puzzleData: crosswordResult.puzzleData
              };
            } else {
              this.debugLog(`❌ Daily crossword generation failed: ${crosswordResult.message}`, 'error');
              result = {
                success: false,
                message: crosswordResult.message || 'Failed to generate crossword with multi-attempt strategy'
              };
            }
            
          } catch (crosswordError) {
            this.debugLog(`❌ Daily crossword system error: ${crosswordError.message}`, 'error');
            
            // Fallback to old method if daily generator fails
            this.debugLog(`⚠️ Falling back to basic crossword generation...`, 'warning');
            
            const { BeamSearch5x5CrosswordGenerator } = await import('./crosswordGeneratorService.js');
            const generator = new BeamSearch5x5CrosswordGenerator();
            
            const wordSet = await this.generateCrosswordWordsForTopic(difficulty);
            if (!wordSet || wordSet.length < 8) {
              result = {
                success: false,
                message: 'Failed to get sufficient words from database for crossword'
              };
            } else {
              const crosswordResult = await generator.generate5x5Crossword(wordSet);
              if (!crosswordResult || crosswordResult.words.length < 5) {
                result = {
                  success: false,
                  message: 'Failed to generate 5x5 crossword with sufficient words'
                };
              } else {
                result = {
                  success: true,
                  puzzleData: generator.formatForStorage(crosswordResult, difficulty)
                };
              }
            }
          }
        } else if (puzzleType.toLowerCase() === 'oddoneout') {
          this.debugLog(`🎯 Using odd one out generation...`);
          const { OddOneOutPuzzleSystem } = await import('./oddOneOutPuzzle.js');
          
          try {
            const puzzleSystem = new OddOneOutPuzzleSystem({
              debug: this.debugMode,
              maxRetries: 3,
              useAIFallback: true
            });
        
            // Generate complete puzzle with images
            const puzzleResult = await puzzleSystem.generateCompletePuzzle(null, difficulty);
            
            if (puzzleResult.success) {
              this.debugLog(`✅ Odd one out puzzle generated successfully`);
              
              // Format for storage - all image URLs are already in Supabase
              result = {
                success: true,
                puzzleData: {
                  question: JSON.stringify({
                    puzzleId: puzzleResult.puzzle.puzzleId,
                    type: puzzleResult.puzzle.type,
                    theme: puzzleResult.puzzle.theme,
                    subTheme: puzzleResult.puzzle.subTheme,
                    title: puzzleResult.puzzle.title,
                    description: puzzleResult.puzzle.description,
                    scenario: puzzleResult.puzzle.scenario,
                    images: puzzleResult.puzzle.images, // Already has Supabase URLs
                    questions: puzzleResult.puzzle.questions,
                    timeLimit: puzzleResult.puzzle.timeLimit,
                    instructions: puzzleResult.puzzle.instructions,
                    difficulty: puzzleResult.puzzle.difficulty
                  }),
                  answer: JSON.stringify({
                    correctAnswer: puzzleResult.puzzle.correctAnswer,
                    oddOneOut: puzzleResult.puzzle.correctAnswer.oddOneOut,
                    theme: puzzleResult.puzzle.correctAnswer.theme,
                    explanation: puzzleResult.puzzle.correctAnswer.explanation,
                    imageDetails: puzzleResult.puzzle.images.map(img => ({
                      id: img.id,
                      itemName: img.item.name,
                      isOddOneOut: img.isOddOneOut,
                      reason: img.item.reason,
                      belongsToGroup: img.item.belongsToGroup
                    }))
                  }),
                  hint: `Find which item doesn't belong with the others. Theme: ${puzzleResult.puzzle.scenario.theme}`,
                  difficulty: difficulty,
                  metadata: {
                    ...puzzleResult.puzzle.apiStats,
                    puzzleType: 'odd_one_out_interactive',
                    hasImages: true,
                    imageCount: puzzleResult.puzzle.totalImages,
                    patternType: puzzleResult.puzzle.scenario.patternType,
                    theme: puzzleResult.puzzle.theme,
                    subTheme: puzzleResult.puzzle.subTheme,
                    generatedAt: puzzleResult.puzzle.generated,
                    storagePattern: 'question_contains_all_data',
                    version: puzzleResult.puzzle.version
                  }
                },
                imageGenerated: true
              };
            } else {
              this.debugLog(`❌ Odd one out generation failed: ${puzzleResult.error}`, 'error');
              result = { 
                success: false, 
                message: `Odd one out generation failed: ${puzzleResult.error}` 
              };
            }
          } catch (oddOneOutError) {
            this.debugLog(`❌ Odd one out system error: ${oddOneOutError.message}`, 'error');
            result = { 
              success: false, 
              message: `Odd one out system error: ${oddOneOutError.message}` 
            };
          }
        } else if (puzzleType.toLowerCase() === 'flowpuzzle' || puzzleType.toLowerCase() === 'flow') {
          this.debugLog(`🧩 Using Flow puzzle generation...`);
          const { FlowPuzzleGenerator } = await import('./flowPuzzleService.js');
          const flowGenerator = new FlowPuzzleGenerator();
          flowGenerator.setDebugMode(this.debugMode);
          
          // Configure grid size based on difficulty
          const gridSizeMap = {
            easy: 6,
            medium: 8,
            hard: 10
          };
          const gridSize = gridSizeMap[difficulty] || 8;
          
          // Generate Flow puzzle
          const flowResult = await flowGenerator.generateFlowPuzzle(gridSize, difficulty, 1);
          
          if (flowResult.success && flowResult.puzzles.length > 0) {
            const flowPuzzle = flowResult.puzzles[0];
            
            this.debugLog(`✅ Flow puzzle generated successfully`);
            this.debugLog(`🎯 Grid: ${flowPuzzle.gridSize}x${flowPuzzle.gridSize}`);
            this.debugLog(`🔗 Pairs: ${flowPuzzle.pairs.length}`);
            this.debugLog(`📊 Coverage: ${((flowPuzzle.pairs.length * 2) / flowPuzzle.cells * 100).toFixed(1)}%`);
            
            result = {
              success: true,
              puzzleData: {
                question: JSON.stringify({
                  gridSize: flowPuzzle.gridSize,
                  pairs: flowPuzzle.pairs,
                  difficulty: flowPuzzle.difficulty,
                  totalCells: flowPuzzle.cells,
                  instructions: "Connect matching colored dots without crossing paths",
                  timeLimit: this.getFlowTimeLimit(difficulty, flowPuzzle.pairs.length),
                  metadata: flowPuzzle.metadata
                }),
                answer: JSON.stringify({
                  solution: flowPuzzle.solution,
                  totalPairs: flowPuzzle.pairs.length,
                  gridSize: flowPuzzle.gridSize,
                  pathLengths: Object.values(flowPuzzle.solution).map(path => path.length),
                  coverage: (Object.values(flowPuzzle.solution).reduce((sum, path) => sum + path.length, 0) / flowPuzzle.cells * 100).toFixed(1)
                }),
                hint: `Connect ${flowPuzzle.pairs.length} pairs of colored dots. Each pair has a unique path.`,
                difficulty: difficulty,
                metadata: {
                  puzzleType: 'flow_interactive',
                  gridSize: flowPuzzle.gridSize,
                  pairCount: flowPuzzle.pairs.length,
                  pathMethod: flowPuzzle.metadata.pathMethod,
                  cutMethod: flowPuzzle.metadata.cutMethod,
                  totalBends: flowPuzzle.metadata.totalBends,
                  generatedAt: flowPuzzle.metadata.generatedAt,
                  storagePattern: 'question_contains_all_data'
                }
              },
              flowGenerated: true
            };
          } else {
            this.debugLog(`❌ Flow puzzle generation failed: ${flowResult.error || 'Unknown error'}`, 'error');
            result = { 
              success: false, 
              message: `Flow puzzle generation failed: ${flowResult.error || 'Unknown error'}` 
            };
          }
        } else if (puzzleType.toLowerCase() === 'progressiverevelation' || puzzleType.toLowerCase() === 'progressive') {
          this.debugLog(`🔮 Using progressive revelation generation...`);
          const progressiveSystem = new ProgressivePuzzleSystem({
            debug: this.debugMode,
            useAIGeneration: true
          });
        
          try {
            // Generate complete progressive puzzle
            const progressiveResult = await progressiveSystem.generateCompletePuzzle(null, difficulty);
            
            if (progressiveResult.success) {
              const puzzle = progressiveResult.puzzle;
              
              this.debugLog(`✅ Progressive puzzle generated successfully`);
              this.debugLog(`🎨 Theme: ${puzzle.category}`);
              this.debugLog(`❓ Answer: ${puzzle.answer}`);
              this.debugLog(`🔗 Clues: ${puzzle.clues.length}`);
              this.debugLog(`🖼️ Image: ${puzzle.image.url}`);
              
              result = {
                success: true,
                puzzleData: {
                  // Store as JSON for complex interactive puzzle
                  question: JSON.stringify({
                    puzzleId: puzzle.puzzleId,
                    type: puzzle.type,
                    category: puzzle.category,
                    clues: puzzle.clues,
                    image: puzzle.image, // Single image with blur configs
                    gameFlow: puzzle.gameFlow,
                    clientConfig: puzzle.clientConfig,
                    instructions: "Guess what's in the image! Each wrong answer reveals a clearer image and an easier clue.",
                    timeLimit: puzzle.gameFlow.maxTime,
                    difficulty: puzzle.metadata.difficulty
                  }),
                  answer: JSON.stringify({
                    correctAnswer: puzzle.answer,
                    clues: puzzle.clues,
                    maxScore: puzzle.gameFlow.scoringSystem.correctAtClue1,
                    scoringBreakdown: puzzle.gameFlow.scoringSystem,
                    imageValidation: puzzle.metadata.imageValidation,
                    searchTerms: puzzle.searchTerms
                  }),
                  hint: `Progressive revelation puzzle: ${puzzle.clues.length} clues from hardest to easiest. Answer: ${puzzle.answer}`,
                  difficulty: difficulty,
                  metadata: {
                    ...puzzle.metadata,
                    puzzleType: 'progressive_revelation_interactive',
                    hasClientSideBlur: true,
                    hasProgressiveClues: true,
                    requiresImageDisplay: true,
                    storagePattern: 'question_contains_all_data'
                  }
                },
                progressiveGenerated: true
              };
            } else {
              this.debugLog(`❌ Progressive puzzle generation failed: ${progressiveResult.error}`, 'error');
              result = { 
                success: false, 
                message: `Progressive puzzle generation failed: ${progressiveResult.error}` 
              };
            }
          } catch (progressiveError) {
            this.debugLog(`❌ Progressive puzzle system error: ${progressiveError.message}`, 'error');
            result = { 
              success: false, 
              message: `Progressive puzzle system error: ${progressiveError.message}` 
            };
          }
        } else if (puzzleType.toLowerCase() === 'imagepuzzle') {
          this.debugLog(`🧩 Using image puzzle generation...`);
          const { imagePuzzleGenerator } = await import('./imagePuzzleGenerator.js');
          imagePuzzleGenerator.setDebugMode(this.debugMode);
          result = await imagePuzzleGenerator.generateImagePuzzle(difficulty);
          
          if (result.success) {
            this.debugLog(`✅ Image puzzle generated successfully`);
            result.imageGenerated = true;
          } else {
            this.debugLog(`❌ Image puzzle generation failed: ${result.message}`, 'error');
          }

        } else if (puzzleType.toLowerCase() === 'musicidentification') {
          this.debugLog(`Using music identification generation...`);
          const { musicPuzzleGenerator } = await import('./musicPuzzleGenerator.js');
          musicPuzzleGenerator.setDebugMode(this.debugMode);
          
          // FIXED: Simple call with just difficulty - let generator handle everything
          result = await musicPuzzleGenerator.generateMusicPuzzle(difficulty);
          
          if (result && result.success && result.data) {
            this.debugLog(`Music puzzle generated successfully`);
            result.musicGenerated = true;
            
            try {
              // FIXED: Use the formatForPuzzleSystem method to get storage-ready data
              result.puzzleData = musicPuzzleGenerator.formatForPuzzleSystem(result.data);
              this.debugLog(`Music puzzle formatted for storage`);
              this.debugLog(`Songs included: ${result.data.songs?.length || 0}`);
              this.debugLog(`Theme: ${result.data.theme || 'Mixed'}`);
            } catch (formatError) {
              this.debugLog(`Failed to format music puzzle data: ${formatError.message}`, 'error');
              
              if (attempt === maxRetries) {
                return {
                  success: false,
                  message: `Music puzzle formatting failed: ${formatError.message}`
                };
              }
              continue; // Try again if not last attempt
            }
          } else {
            const errorMsg = result?.message || result?.error || 'Unknown music generation error';
            this.debugLog(`Music puzzle generation failed: ${errorMsg}`, 'error');
            
            if (attempt === maxRetries) {
              return {
                success: false,
                message: `Music generation failed after ${maxRetries} attempts: ${errorMsg}`
              };
            }
            continue; // Try again if not last attempt
          }
        } else if (puzzleType.toLowerCase() === 'letterset') {
          this.debugLog(`🔤 Using letter set generation...`);
          const { LetterSetGenerator } = await import('./letterSetGenerator.js');
          const generator = new LetterSetGenerator();
          generator.setDebugMode(this.debugMode);
          result = await generator.generateLetterSetPuzzle(difficulty);
          
        } else if (puzzleType.toLowerCase() === 'wordsnake') {
          this.debugLog(`🐍 Using snake word search generation...`);
          const { enhancedWordSnakeGenerator } = await import('./snakeWordSearch.js');
          
          const gridSizes = { easy: 6, medium: 8, hard: 10 };
          const gridSize = gridSizes[difficulty] || 8;
          
          result = await enhancedWordSnakeGenerator.generateWordSnakePuzzle(difficulty, gridSize);

        } else if (puzzleType.toLowerCase() === 'waldopuzzle') {
          const { waldoPuzzleGenerator } = await import('./waldoPuzzleGeneratorService.js');
          this.debugLog(`🎯 Using Waldo puzzle generation...`);
          waldoPuzzleGenerator.setDebugMode(this.debugMode);
          result = await waldoPuzzleGenerator.generateWaldoPuzzle(difficulty);
          
          if (result.success) {
            this.debugLog(`✅ Waldo puzzle generated successfully`);
            result.imageGenerated = true;
          } else {
            this.debugLog(`❌ Waldo puzzle generation failed: ${result.message}`, 'error');
          }

        } else if (puzzleType.toLowerCase() === 'anagram') {
          const { AnagramGenerator } = await import('./anagramGeneratorService.js');
          const anagramGenerator = new AnagramGenerator();
          anagramGenerator.setDebugMode(this.debugMode);
          
          result = await anagramGenerator.generateAnagramPuzzle(difficulty, null, 1);
          
          if (result.success && result.anagrams.length > 0) {
            this.debugLog(`Anagram puzzle generated successfully`);
            
            // Transform to match your puzzle data structure
            const anagram = result.anagrams[0];
            result = {
              success: true,
              puzzleData: {
                puzzleId: `anagram_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
                puzzleType: 'anagram',
                question: anagram.scrambledWord,
                answer: anagram.originalWord,
                hint: anagram.hint,
                difficulty: difficulty,
                options: []
              },
              generationMethod: 'local_anagram'
            };
          } else {
            this.debugLog(`Anagram generation failed: ${result.error}`, 'error');
          }
        } else if (puzzleType.toLowerCase() === 'imagequestion') {
          const { imageQuestionGenerator } = await import('./imageQuestionGenerator.js');
          imageQuestionGenerator.setDebugMode(this.debugMode);
          result = await imageQuestionGenerator.generateImageQuestionPuzzle(difficulty);
          
          if (result.success) {
            this.debugLog(`✅ Image question puzzle generated successfully`);
            result.imageGenerated = true;
          } else {
            this.debugLog(`❌ Image question generation failed: ${result.message}`, 'error');
          }

        } else if (puzzleType.toLowerCase() === 'find_differences') {
          this.debugLog(`🖼️ Using find differences generation...`);
          const { findDifferencesGenerator } = await import('./findDifferencesGeneratorService.js');
          
          findDifferencesGenerator.setDebugMode(this.debugMode);
          result = await findDifferencesGenerator.generateFindDifferencesPuzzle(difficulty);
          
          if (result && result.success) {
              result.imageGenerated = true;
              this.debugLog(`✅ Find differences puzzle generated successfully`);
              
              if (!result.puzzleData) {
                  this.debugLog(`⚠️ Missing puzzleData in result, creating from available data`, 'warning');
                  result.puzzleData = {
                      question: JSON.stringify({
                          imageUrl: result.imageUrl || '',
                          instructions: `Find all the differences between the left and right images.`,
                          differences: result.differences || [],
                          imageStatus: 'generated',
                          generatedAt: new Date().toISOString()
                      }),
                      answer: JSON.stringify(result.differences || []),
                      hint: `Find ${(result.differences || []).length} differences between the images`,
                      difficulty: difficulty,
                      options: [],
                      metadata: {
                          puzzleType: 'find_differences',
                          imageUrl: result.imageUrl,
                          generatedAt: new Date().toISOString()
                      }
                  };
              }
          } else {
              this.debugLog(`❌ Find differences generation failed: ${result?.error || 'Unknown error'}`, 'error');
              if (!result) {
                  result = { success: false, message: 'Find differences generator returned undefined result' };
              } else if (!result.success && !result.message) {
                  result.message = result.error || 'Find differences generation failed';
              }
          }

        } else if (puzzleType.toLowerCase() === 'find_object') {
          this.debugLog(`🔍 Using find object generation...`);
          const { FindObjectGenerator } = await import('./findObjectGenerator.js');
          const generator = new FindObjectGenerator();
          generator.setDebugMode(this.debugMode);
          result = await generator.generateFindObjectPuzzle(difficulty);
          
          if (result.success) {
              this.debugLog(`✅ Find object puzzle generated with image status: ${result.imageGenerated ? 'generated' : 'pending'}`);
              
              if (!result.imageGenerated && result.imageError) {
                  this.debugLog(`⚠️ Image generation failed: ${result.imageError}`, 'warning');
              }
          }

        } else if (puzzleType.toLowerCase() === 'realorai') {
            this.debugLog(`🔍 Using "Which Is Real" puzzle generation...`);
            const { whichIsRealGenerator } = await import('./whichIsRealGenerator.js');
            
            whichIsRealGenerator.setDebugMode(this.debugMode);
            const whichIsRealResult = await whichIsRealGenerator.generateWhichIsRealPuzzle(difficulty);
            
            if (whichIsRealResult.success) {
                this.debugLog(`✅ Which Is Real puzzle generated, now uploading images...`);
                
                // 🔥 Upload images to Supabase BEFORE storing puzzle
                const uploadResult = await whichIsRealGenerator.uploadImagesToSupabase(
                    whichIsRealResult.puzzleData
                );
                
                if (!uploadResult.success) {
                    this.debugLog(`❌ Image upload failed: ${uploadResult.error}`, 'error');
                    result = { 
                        success: false, 
                        message: `Image upload failed: ${uploadResult.error}` 
                    };
                } else {
                    // Update puzzle data with image URLs and REMOVE buffers
                    const updatedPuzzleData = whichIsRealGenerator.updatePuzzleWithUrls(
                        whichIsRealResult.puzzleData,
                        uploadResult.urls
                    );
                    
                    this.debugLog(`✅ Images uploaded successfully`);
                    this.debugLog(`🖼️ Image A: ${uploadResult.urls.image_a}`);
                    this.debugLog(`🖼️ Image B: ${uploadResult.urls.image_b}`);
                    
                    result = {
                        success: true,
                        puzzleData: updatedPuzzleData,  // ✅ Now has URLs, NO buffers
                        imagesGenerated: true
                    };
                }
            } else {
                this.debugLog(`❌ Which Is Real generation failed: ${whichIsRealResult.message}`, 'error');
                result = { 
                    success: false, 
                    message: `Which Is Real generation failed: ${whichIsRealResult.message}` 
                };
            }
        } else if (puzzleType.toLowerCase() === 'crypto') {
          this.debugLog(`🔒 Using simplified crypto quote generation...`);
          
          const { simplifiedEnhancedCryptoQuoteGenerator } = await import('./cryptoQuoteGenerator.js');
          result = await simplifiedEnhancedCryptoQuoteGenerator.generateCryptoQuoteData(difficulty);
          
          if (result.success) {
            const cryptoForStorage = {
              question: result.puzzleData.quote,
              answer: result.puzzleData.author,
              hint: `Quote by ${result.puzzleData.author}`,
              difficulty: difficulty,
              options: [],
              metadata: {
                author: result.puzzleData.author,
                source: result.puzzleData.source,
                wordCount: result.puzzleData.wordCount,
                characterCount: result.puzzleData.characterCount,
                generatedAt: result.puzzleData.timestamp,
                puzzleType: 'crypto',
                clientSideGeneration: true
              }
            };
            
            result.puzzleData = cryptoForStorage;
          }

        } else if (puzzleType.toLowerCase() === 'uniqueobject') {
          this.debugLog(`🎯 Using unique object generation...`);
          const { UniqueObjectGenerator } = await import('./uniqueObjectGenerator.js');
          const generator = new UniqueObjectGenerator();
          generator.setDebugMode(this.debugMode);
          result = await generator.generateUniqueObjectPuzzle(difficulty);

        } else if (puzzleType.toLowerCase() === 'mathcomparison') {
          this.debugLog(`🔢 Using mathematical comparison generation...`);
          const { MathComparisonGenerator } = await import('./mathComparisonGenerator.js');
          const generator = new MathComparisonGenerator();
          generator.setDebugMode(this.debugMode);
          result = await generator.generateMathComparisonPuzzle(difficulty);
          
        } else if (puzzleType.toLowerCase() === 'wordsearch') {
          this.debugLog(`🔍 Using word search generation...`);
          const result = await wordSearchService.generateStandardWordSearch(difficulty);
          
          if (!result.success) {
              return {
                  success: false,
                  message: `Word search generation failed: ${result.message}`
              };
          }
          
          const puzzleForStorage = this.formatRandomizedPuzzleForStorage(result.puzzleData, puzzleType, difficulty);
          const storeResult = await this.storePuzzle(puzzleForStorage, puzzleType, 'enhanced_word_search_generator');
          
          if (storeResult.success) {
              this.debugLog(`🎉 Successfully generated and stored word search puzzle! ID: ${storeResult.puzzleId}`, 'success');
              return {
                  success: true,
                  puzzleId: storeResult.puzzleId,
                  pathStatus: storeResult.pathStatus,
                  generationMethod: 'enhanced_randomized_with_deduplication',
                  attempts: 1,
                  message: `Generated word search puzzle with deduplication`,
                  costSavings: '$0.02',
                  duplicateRisk: '0%'
              };
          } else {
              return {
                  success: false,
                  message: `Word search generation succeeded but storage failed: ${storeResult.message}`
              };
          }

        } else if (puzzleType.toLowerCase() === 'antonyms') {
          this.debugLog(`🔤 Using enhanced antonym generation...`);
          enhancedAntonymGenerator.setDebugMode(this.debugMode);
          result = await enhancedAntonymGenerator.generateAntonymPuzzle(difficulty);
          
        } else if (puzzleType.toLowerCase() === 'wordprefix') {
          this.debugLog(`🔤 Using word prefix generation...`);
          const { WordPrefixGenerator } = await import('./wordPrefixGenerator.js');
          const generator = new WordPrefixGenerator();
          result = await generator.generatePrefixPuzzle(difficulty);

        } else if (puzzleType.toLowerCase() === 'memorysquares') {
          this.debugLog(`🧠 Using memory squares generation...`);
          const { generateMemorySquaresPuzzle } = await import('./memorySquaresGenerator.js');
          result = await generateMemorySquaresPuzzle(difficulty);

        } else if (puzzleType.toLowerCase() === 'memorypreviouspair') {
          this.debugLog(`🧠 Using memory previous pair generation...`);
          const { MemoryPreviousPairGenerator } = await import('./memoryPreviousPairGenerator.js');
          const generator = new MemoryPreviousPairGenerator();
          result = await generator.generateMemoryPreviousPairPuzzle(difficulty);

        } else if (puzzleType.toLowerCase() === 'memoryprevioussingle') {
          this.debugLog(`🧠 Using memory previous single generation...`);
          const { MemoryPreviousSingleGenerator } = await import('./memoryPreviousSingleGenerator.js');
          const generator = new MemoryPreviousSingleGenerator();
          result = await generator.generateMemoryPreviousSinglePuzzle(difficulty);

        } else if (puzzleType.toLowerCase() === 'memorymatrixpath') {
          this.debugLog(`🧠 Using memory matrix path generation...`);
          const { MemoryMatrixPathGenerator } = await import('./memoryMatrixPathGenerator.js');
          const generator = new MemoryMatrixPathGenerator();
          result = await generator.generateMemoryMatrixPathPuzzle(difficulty);

        } else if (puzzleType.toLowerCase() === 'pinballdeflector') {
          this.debugLog(`🎯 Using pinball deflector generation...`);
          const { PinballDeflectorGenerator } = await import('./pinballDeflectorGenerator.js');
          const generator = new PinballDeflectorGenerator();
          result = await generator.generatePinballDeflectorPuzzle(difficulty);

        } else {
          // For math puzzles, use existing math generators
          result = await generateRandomizedMathPuzzle(puzzleType, difficulty);
        }
        
        if (!result.success) {
          this.debugLog(`Generation failed on attempt ${attempt}: ${result.message}`, 'warning');
          if (attempt === maxRetries) {
            return result;
          }
          continue;
        }
        
        // ✅ Format puzzle for storage
        const puzzleForStorage = this.formatRandomizedPuzzleForStorage(result.puzzleData, puzzleType, difficulty);
        
        // ✅ UPDATED - Use centralized deduplication service for word-based puzzles
        if (isWordBased) {
          this.debugLog(`🔍 Checking for duplicates using deduplication service (attempt ${attempt})...`);
          const isDuplicate = await this.deduplicationService.isDuplicate(puzzleType, puzzleForStorage.question);
          
          if (isDuplicate) {
            this.debugLog(`❌ Duplicate detected by deduplication service on attempt ${attempt}, retrying...`, 'warning');
            if (attempt === maxRetries) {
              return {
                success: false,
                message: `Failed to generate unique ${puzzleType} puzzle after ${maxRetries} attempts`,
                reason: 'max_duplicate_retries_exceeded'
              };
            }
            continue;
          }
          this.debugLog(`✅ No duplicates found by deduplication service on attempt ${attempt}`, 'success');
        }
        
        // ✅ Store the puzzle
        const storeResult = await this.storePuzzle(puzzleForStorage, puzzleType, 'randomized_generator');
        
        if (storeResult.success) {
          this.debugLog(`🎉 Successfully generated and stored randomized ${puzzleType} puzzle on attempt ${attempt}! ID: ${storeResult.puzzleId}`, 'success');
          return {
            success: true,
            puzzleId: storeResult.puzzleId,
            pathStatus: storeResult.pathStatus,
            generationMethod: 'randomized',
            attempts: attempt,
            message: `Generated randomized ${puzzleType} puzzle in ${attempt} attempts`,
            costSavings: '$0.02',
            duplicateRisk: '0%',
            ...(result.imageGenerated && { imageGenerated: true }),
            ...(result.musicGenerated && { musicGenerated: true })
          };
        } else {
          this.debugLog(`Storage failed on attempt ${attempt}: ${storeResult.message}`, 'error');
          if (attempt === maxRetries) {
            return {
              success: false,
              message: `Randomized generation succeeded but storage failed after ${maxRetries} attempts: ${storeResult.message}`
            };
          }
          continue;
        }
      }
      
    } catch (error) {
      this.debugLog(`Randomized ${puzzleType} generation error: ${error.message}`, 'error');
      return {
        success: false,
        message: `Randomized generation error: ${error.message}`,
        fallbackToAI: true
      };
    }
  }

  getFlowTimeLimit(difficulty, pairCount) {
    const baseTime = {
      easy: 120,   // 2 minutes
      medium: 180, // 3 minutes  
      hard: 300    // 5 minutes
    };
    
    const base = baseTime[difficulty] || baseTime.medium;
    const complexityMultiplier = Math.max(1, pairCount / 6); // Scale with pair count
    
    return Math.round(base * complexityMultiplier) * 1000; // Convert to milliseconds
  }

  async uploadWhichIsRealImages(puzzleId, imageBuffers) {
    try {
      this.debugLog(`📤 Uploading Which Is Real images for puzzle ${puzzleId}...`);
      
      const { whichIsRealGenerator } = await import('./whichIsRealGenerator.js');
      
      // Create temporary puzzle data for upload
      const puzzleData = {
        puzzleId: puzzleId,
        metadata: {
          imageBuffers: imageBuffers
        }
      };
      
      // Upload images
      const uploadResult = await whichIsRealGenerator.uploadImagesToSupabase(puzzleData);
      
      if (!uploadResult.success) {
        throw new Error(`Image upload failed: ${uploadResult.error}`);
      }
      
      this.debugLog(`✅ Images uploaded successfully`);
      this.debugLog(`🖼️ Image A: ${uploadResult.urls.image_a}`);
      this.debugLog(`🖼️ Image B: ${uploadResult.urls.image_b}`);
      
      // Update puzzle record with image URLs
      const { supabase } = await import('../config/database.js');
      
      // Get current puzzle data
      const { data: currentPuzzle, error: fetchError } = await supabase
        .from('puzzles')
        .select('question')
        .eq('id', puzzleId)
        .single();
      
      if (fetchError) {
        throw new Error(`Failed to fetch puzzle: ${fetchError.message}`);
      }
      
      // Update question with image URLs
      const questionData = JSON.parse(currentPuzzle.question);
      questionData.imageA.url = uploadResult.urls.image_a;
      questionData.imageB.url = uploadResult.urls.image_b;
      
      // Update puzzle record
      const { error: updateError } = await supabase
        .from('puzzles')
        .update({ 
          question: JSON.stringify(questionData),
          updated_at: new Date().toISOString()
        })
        .eq('id', puzzleId);
      
      if (updateError) {
        throw new Error(`Failed to update puzzle with URLs: ${updateError.message}`);
      }
      
      this.debugLog(`✅ Puzzle ${puzzleId} updated with image URLs`, 'success');
      
      return {
        success: true,
        urls: uploadResult.urls,
        puzzleId: puzzleId
      };
      
    } catch (error) {
      this.debugLog(`❌ Image upload error: ${error.message}`, 'error');
      return {
        success: false,
        error: error.message
      };
    }
  }

  /**
   * Format randomized puzzle for storage - ENHANCED with IMAGE_MATCH
   */
  formatRandomizedPuzzleForStorage(puzzleData, puzzleType, difficulty) {
    this.debugLog(`🔧 Formatting ${puzzleType} puzzle for storage...`);
    this.debugLog(`📊 Input puzzle data: ${JSON.stringify(puzzleData, null, 2)}`);
    
    switch (puzzleType.toLowerCase()) {
      // ✅ NEW: IMAGE_MATCH FORMATTING
      case 'imagematch':
        this.debugLog(`🌍 Formatting image match puzzle for storage...`);
        
        // Validate required fields
        if (!puzzleData.question || !puzzleData.answer) {
          throw new Error('Image match puzzle missing question or answer data');
        }
        
        try {
          // Parse to validate JSON structure
          const questionData = JSON.parse(puzzleData.question);
          const answerData = JSON.parse(puzzleData.answer);
          
          // Validate essential fields
          if (!questionData.questions || !Array.isArray(questionData.questions)) {
            throw new Error('Image match puzzle missing questions array in question data');
          }
          
          if (!answerData.correctAnswers || !Array.isArray(answerData.correctAnswers)) {
            throw new Error('Image match puzzle missing correctAnswers array in answer data');
          }
          
          if (questionData.questions.length !== answerData.correctAnswers.length) {
            throw new Error('Image match puzzle question/answer count mismatch');
          }
          
          this.debugLog(`✅ Image match formatting validated successfully`);
          this.debugLog(`🌍 Theme: ${questionData.theme}`);
          this.debugLog(`❓ Questions: ${questionData.questions.length}`);
          this.debugLog(`🖼️ Image: ${questionData.primaryImage?.url ? 'YES' : 'NO'}`);
          
          return {
            question: puzzleData.question,   // JSON string with all puzzle data
            answer: puzzleData.answer,       // JSON string with correct answers
            hint: puzzleData.hint || `Test your knowledge about world locations and culture`,
            difficulty: difficulty,
            options: [], // Options stored within question JSON
            metadata: {
              ...puzzleData.metadata,
              puzzleType: 'image_match_interactive',
              hasMultipleChoice: true,
              requiresGeographicalKnowledge: true,
              imageSource: 'pixabay_enhanced',
              storagePattern: 'question_contains_all_data'
            }
          };
          
        } catch (parseError) {
          throw new Error(`Image match puzzle validation failed: ${parseError.message}`);
        }

        case 'realorai':
          this.debugLog(`🔍 Formatting Which Is Real puzzle for storage...`);
          
          // Validate required fields
          if (!puzzleData.question || !puzzleData.answer) {
              throw new Error('Which Is Real puzzle missing question or answer data');
          }
          
          try {
              const questionData = JSON.parse(puzzleData.question);
              const answerData = JSON.parse(puzzleData.answer);
              
              // Validate image URLs are present (not null)
              if (!questionData.imageA?.url || !questionData.imageB?.url) {
                  throw new Error('Which Is Real puzzle missing image URLs - images must be uploaded first');
              }
              
              this.debugLog(`✅ Which Is Real formatting validated`);
              this.debugLog(`🖼️ Image A URL: ${questionData.imageA.url}`);
              this.debugLog(`🖼️ Image B URL: ${questionData.imageB.url}`);
              
              return {
                  question: puzzleData.question,   // JSON with image URLs
                  answer: puzzleData.answer,
                  hint: puzzleData.hint || 'Examine both images carefully to identify which is real',
                  difficulty: difficulty,
                  options: [],
                  metadata: {
                      // 🔥 NEVER include imageBuffers here
                      category: puzzleData.metadata.category,
                      displayCategory: puzzleData.metadata.displayCategory,
                      subjectName: puzzleData.metadata.subjectName,
                      puzzleType: 'which_is_real_interactive',
                      educationalPuzzle: true,
                      aiLiteracy: true,
                      hasReasoning: true,
                      communityLearning: true,
                      storagePattern: 'question_contains_all_data',
                      // ✅ Only include metadata, NOT buffers
                      generatedAt: puzzleData.metadata.generatedAt,
                      imageGenerated: true
                  }
              };
              
          } catch (parseError) {
              throw new Error(`Which Is Real puzzle validation failed: ${parseError.message}`);
          }

        case 'oddoneout':
          this.debugLog(`🎯 Formatting odd one out puzzle for storage...`);
          
          // Validate required fields
          if (!puzzleData.question || !puzzleData.answer) {
            throw new Error('Odd one out puzzle missing question or answer data');
          }
          
          try {
            // Parse to validate JSON structure
            const questionData = JSON.parse(puzzleData.question);
            const answerData = JSON.parse(puzzleData.answer);
            
            // Validate essential fields
            if (!questionData.images || !Array.isArray(questionData.images)) {
              throw new Error('Odd one out puzzle missing images array in question data');
            }
            
            if (questionData.images.length !== 4) {
              throw new Error('Odd one out puzzle must have exactly 4 images');
            }
            
            // Validate all images have Supabase URLs
            for (let i = 0; i < questionData.images.length; i++) {
              const img = questionData.images[i];
              if (!img.url || !img.url.includes('supabase')) {
                throw new Error(`Image ${i + 1} missing valid Supabase URL`);
              }
              if (!img.item || !img.item.name) {
                throw new Error(`Image ${i + 1} missing item data`);
              }
            }
            
            if (!answerData.oddOneOut) {
              throw new Error('Odd one out puzzle missing oddOneOut in answer data');
            }
            
            if (!questionData.questions || !Array.isArray(questionData.questions)) {
              throw new Error('Odd one out puzzle missing questions array');
            }
            
            // Count odd ones out - should be exactly 1
            const oddOnesOut = questionData.images.filter(img => img.isOddOneOut);
            if (oddOnesOut.length !== 1) {
              throw new Error(`Expected exactly 1 odd one out, found ${oddOnesOut.length}`);
            }
            
            this.debugLog(`✅ Odd one out formatting validated successfully`);
            this.debugLog(`🎨 Theme: ${questionData.theme}`);
            this.debugLog(`🖼️ Images: ${questionData.images.length}`);
            this.debugLog(`❓ Questions: ${questionData.questions.length}`);
            this.debugLog(`⭐ Odd one out: ${answerData.oddOneOut}`);
            
            return {
              question: puzzleData.question,   // JSON string with all puzzle data
              answer: puzzleData.answer,       // JSON string with correct answers
              hint: puzzleData.hint || `Find the odd one out among these ${questionData.theme} items`,
              difficulty: difficulty,
              options: [], // Options stored within question JSON
              metadata: {
                ...puzzleData.metadata,
                puzzleType: 'odd_one_out_interactive',
                hasImages: true,
                imageCount: questionData.images.length,
                requiresVisualAnalysis: true,
                storagePattern: 'question_contains_all_data'
              }
            };
            
          } catch (parseError) {
            throw new Error(`Odd one out puzzle validation failed: ${parseError.message}`);
          }
        
        case 'progressiverevelation':
        case 'progressive':
            this.debugLog(`🔮 Formatting progressive revelation puzzle for storage...`);
            
            // Validate required fields
            if (!puzzleData.question || !puzzleData.answer) {
                throw new Error('Progressive puzzle missing question or answer data');
            }
            
            try {
                // Parse to validate JSON structure
                const questionData = JSON.parse(puzzleData.question);
                const answerData = JSON.parse(puzzleData.answer);
                
                // Validate essential fields
                if (!questionData.clues || !Array.isArray(questionData.clues)) {
                    throw new Error('Progressive puzzle missing clues array in question data');
                }
                
                if (!answerData.correctAnswer) {
                    throw new Error('Progressive puzzle missing correctAnswer in answer data');
                }
                
                if (!questionData.image || !questionData.image.url) {
                    throw new Error('Progressive puzzle missing image data');
                }
                
                this.debugLog(`✅ Progressive puzzle formatting validated successfully`);
                this.debugLog(`🎨 Category: ${questionData.category}`);
                this.debugLog(`❓ Clues: ${questionData.clues.length}`);
                this.debugLog(`🖼️ Image: ${questionData.image.url ? 'YES' : 'NO'}`);
                
                return {
                    question: puzzleData.question,   // JSON string with all puzzle data
                    answer: puzzleData.answer,       // JSON string with answers and scoring
                    hint: puzzleData.hint || `Progressive revelation puzzle: ${answerData.correctAnswer}`,
                    difficulty: difficulty,
                    options: [], // No options for progressive puzzles
                    metadata: {
                        ...puzzleData.metadata,
                        puzzleType: 'progressive_revelation_interactive',
                        hasProgressiveClues: true,
                        hasClientSideBlur: true,
                        requiresImageDisplay: true,
                        storagePattern: 'question_contains_all_data'
                    }
                };
                
            } catch (parseError) {
                throw new Error(`Progressive puzzle validation failed: ${parseError.message}`);
            }

        case 'flowpuzzle':
        case 'flow':
          this.debugLog(`🧩 Formatting Flow puzzle for storage...`);
          
          // Validate required fields
          if (!puzzleData.question || !puzzleData.answer) {
            throw new Error('Flow puzzle missing question or answer data');
          }
          
          try {
            // Parse to validate JSON structure
            const questionData = JSON.parse(puzzleData.question);
            const answerData = JSON.parse(puzzleData.answer);
            
            // Validate essential fields
            if (!questionData.pairs || !Array.isArray(questionData.pairs)) {
              throw new Error('Flow puzzle missing pairs array in question data');
            }
            
            if (!answerData.solution || typeof answerData.solution !== 'object') {
              throw new Error('Flow puzzle missing solution object in answer data');
            }
            
            if (!questionData.gridSize || typeof questionData.gridSize !== 'number') {
              throw new Error('Flow puzzle missing or invalid gridSize');
            }
            
            // Validate each pair
            for (let i = 0; i < questionData.pairs.length; i++) {
              const pair = questionData.pairs[i];
              if (!pair.id || !pair.start || !pair.end || !pair.color || !pair.label) {
                throw new Error(`Flow puzzle pair ${i} missing required fields`);
              }
              
              if (typeof pair.start.x !== 'number' || typeof pair.start.y !== 'number' ||
                  typeof pair.end.x !== 'number' || typeof pair.end.y !== 'number') {
                throw new Error(`Flow puzzle pair ${i} has invalid coordinate data`);
              }
            }
            
            // Validate solution paths
            for (const pairId of Object.keys(answerData.solution)) {
              const path = answerData.solution[pairId];
              if (!Array.isArray(path) || path.length < 2) {
                throw new Error(`Flow puzzle solution for ${pairId} is invalid`);
              }
              
              // Validate path coordinates
              for (let i = 0; i < path.length; i++) {
                const point = path[i];
                if (typeof point.x !== 'number' || typeof point.y !== 'number') {
                  throw new Error(`Flow puzzle solution path for ${pairId} has invalid coordinates at index ${i}`);
                }
              }
            }
            
            this.debugLog(`✅ Flow puzzle formatting validated successfully`);
            this.debugLog(`🎯 Grid: ${questionData.gridSize}x${questionData.gridSize}`);
            this.debugLog(`🔗 Pairs: ${questionData.pairs.length}`);
            this.debugLog(`⏱️ Time limit: ${questionData.timeLimit}ms`);
            
            return {
              question: puzzleData.question,   // JSON string with all Flow puzzle data
              answer: puzzleData.answer,       // JSON string with solution paths
              hint: puzzleData.hint || `Connect ${questionData.pairs.length} pairs of colored dots without crossing paths`,
              difficulty: difficulty,
              options: [], // Options not needed for Flow puzzles
              metadata: {
                ...puzzleData.metadata,
                puzzleType: 'flow_interactive',
                hasPathfinding: true,
                requiresLogicalThinking: true,
                gridBased: true,
                storagePattern: 'question_contains_all_data'
              }
            };
            
          } catch (parseError) {
            throw new Error(`Flow puzzle validation failed: ${parseError.message}`);
          }

      // EXISTING CASES CONTINUE...
      case 'letterset':
        this.debugLog(`🔧 Formatting letter set puzzle for storage...`);
        this.debugLog(`📋 puzzleData structure: ${JSON.stringify(Object.keys(puzzleData), null, 2)}`);
        
        let gameData, answerData;
        
        if (puzzleData.question && puzzleData.answer) {
          this.debugLog(`📦 Using pre-formatted letter set data`);
          gameData = puzzleData.question;
          answerData = puzzleData.answer;
        } else if (puzzleData.letterSet && puzzleData.allWords) {
          this.debugLog(`🔧 Formatting raw letter set data`);
          
          const questionData = {
            letterSet: puzzleData.letterSet,
            letters: puzzleData.letters || puzzleData.letterSet.split(''),
            allWords: puzzleData.allWords || [],
            keyWord: puzzleData.keyWord || null,
            difficulty: difficulty,
            timeLimit: puzzleData.timeLimit || 180000,
            scoring: puzzleData.scoring || {
              basePointsPerWord: difficulty === 'Easy' ? 10 : difficulty === 'Medium' ? 15 : 20,
              lengthMultiplier: {
                3: 1.0, 4: 1.2, 5: 1.5, 6: 2.0, 7: 2.5, 8: 3.0, 9: 4.0, 10: 5.0
              },
              rarityBonus: { common: 0, uncommon: 5, rare: 15 },
              allLettersBonus: 25
            },
            targets: puzzleData.targets || {
              bronze: Math.max(1, Math.ceil((puzzleData.allWords?.length || 0) * 0.15)),
              silver: Math.max(2, Math.ceil((puzzleData.allWords?.length || 0) * 0.35)),
              gold: Math.max(3, Math.ceil((puzzleData.allWords?.length || 0) * 0.60))
            },
            metadata: puzzleData.metadata || {
              generatedAt: new Date().toISOString(),
              totalWords: puzzleData.allWords?.length || 0,
              source: 'randomized_generator'
            }
          };
          
          const answerDataObj = {
            allWords: (puzzleData.allWords || []).map(w => 
              typeof w === 'string' ? w : (w.word || w)
            ),
            keyWord: puzzleData.keyWord || null,
            totalWords: puzzleData.allWords?.length || 0,
            targets: questionData.targets
          };
          
          gameData = JSON.stringify(questionData);
          answerData = JSON.stringify(answerDataObj);
          
        } else {
          this.debugLog(`❌ Invalid letter set data structure`, 'error');
          this.debugLog(`📋 Available keys: ${Object.keys(puzzleData).join(', ')}`);
          throw new Error(`Invalid letter set puzzle data structure. Expected either {question, answer} or {letterSet, allWords}`);
        }
        
        if (!gameData || gameData === 'undefined' || gameData === 'null') {
          throw new Error('Letter set question data is invalid after formatting');
        }
        
        if (!answerData || answerData === 'undefined' || answerData === 'null') {
          throw new Error('Letter set answer data is invalid after formatting');
        }
        
        try {
          const parsedQuestion = JSON.parse(gameData);
          const parsedAnswer = JSON.parse(answerData);
          
          if (!parsedQuestion.letterSet) {
            throw new Error('Letter set question missing letterSet field');
          }
          
          if (!parsedAnswer.allWords || !Array.isArray(parsedAnswer.allWords)) {
            throw new Error('Letter set answer missing valid allWords array');
          }
          
        } catch (parseError) {
          throw new Error(`Letter set data validation failed: ${parseError.message}`);
        }
        
        this.debugLog(`✅ Letter set formatting completed successfully`);
        this.debugLog(`📝 Question length: ${gameData.length} chars`);
        this.debugLog(`📝 Answer length: ${answerData.length} chars`);
        
        return {
          question: gameData,
          answer: answerData,
          hint: puzzleData.hint || `Form words using the letters ${puzzleData.letterSet || 'provided'} - find as many as you can!`,
          difficulty: difficulty,
          options: [],
          metadata: {
            letterSet: puzzleData.letterSet,
            totalWords: puzzleData.allWords?.length || 0,
            keyWord: puzzleData.keyWord,
            puzzleType: 'interactive_letter_game',
            generationMethod: 'database_first',
            ...(puzzleData.metadata || {})
          }
        };

      case 'wordprefix':
        return {
          question: puzzleData.prefix,
          answer: JSON.stringify({
            timeLimit: puzzleData.timeLimit,
            allWords: puzzleData.allWords,
            scoring: puzzleData.scoring,
            targets: puzzleData.targets,
            metadata: puzzleData.metadata
          }),
          hint: puzzleData.hint,
          difficulty: difficulty,
          options: [],
          metadata: {
            totalWords: puzzleData.allWords.length,
            generatedAt: puzzleData.metadata.generatedAt,
            puzzleType: 'interactive_word_game'
          }
        };

      case 'imagepuzzle':
        return {
          question: puzzleData.question,
          answer: puzzleData.answer,
          hint: puzzleData.hint,
          difficulty: difficulty,
          options: [],
          metadata: {
            ...puzzleData.metadata,
            puzzleType: 'visual_interactive',
            requiresImageGeneration: true,
            dragAndDrop: true,
            gridAssembly: true,
            storagePattern: 'question_contains_all_data'
          }
        };

      case 'musicidentification':
        return {
          question: puzzleData.question,
          answer: puzzleData.answer,
          hint: puzzleData.hint,
          difficulty: difficulty,
          options: [],
          metadata: {
            ...puzzleData.metadata,
            puzzleType: 'music_identification_interactive',
            requiresAudioGeneration: true,
            hasMultipleChoice: true,
            timeLimit: puzzleData.metadata.timeLimit,
            trackCount: puzzleData.metadata.trackCount,
            storagePattern: 'question_contains_all_data'
          }
        };

      case 'find_object':
        return {
          question: puzzleData.question,
          answer: puzzleData.answer,
          hint: puzzleData.hint,
          difficulty: difficulty,
          options: [],
          metadata: {
            ...puzzleData.metadata,
            puzzleType: 'visual_interactive',
            requiresImageGeneration: true,
            clickValidation: true,
            coordinateSystem: 'percentage',
            storagePattern: 'question_contains_all_data'
          }
        };

      case 'imagequestion':
        return {
          question: puzzleData.question,
          answer: puzzleData.answer,
          hint: puzzleData.hint,
          difficulty: difficulty,
          options: [],
          metadata: {
            ...puzzleData.metadata,
            puzzleType: 'image_question_interactive',
            requiresImageGeneration: true,
            hasMultipleChoice: true,
            timeLimit: puzzleData.metadata.timeLimit,
            storagePattern: 'question_contains_all_data'
          }
        };

      case 'waldopuzzle':
        return {
          question: puzzleData.question,
          answer: puzzleData.answer,
          hint: puzzleData.hint,
          difficulty: difficulty,
          options: [],
          metadata: {
            ...puzzleData.metadata,
            puzzleType: 'waldo_interactive',
            requiresImageGeneration: true,
            gridInteraction: true,
            objectCount: puzzleData.metadata.objectCount,
            timeLimit: puzzleData.metadata.timeLimit,
            storagePattern: 'question_contains_all_data'
          }
        };

      case 'find_differences':
        return {
          question: puzzleData.question,
          answer: puzzleData.answer,
          hint: puzzleData.hint,
          difficulty: difficulty,
          options: [],
          metadata: {
            ...puzzleData.metadata,
            puzzleType: 'visual_interactive',
            requiresImageGeneration: true,
            clickValidation: true,
            coordinateSystem: 'percentage',
            storagePattern: 'question_contains_all_data'
          }
        };

      case 'uniqueobject':
        return {
          question: puzzleData.question,
          answer: puzzleData.answer,
          hint: puzzleData.hint,
          difficulty: difficulty,
          options: [],
          metadata: puzzleData.metadata
        };

      case 'wordsnake':
        return {
          question: puzzleData.question,
          answer: puzzleData.answer,
          hint: puzzleData.hint,
          difficulty: difficulty,
          options: [],
          metadata: {
            ...puzzleData.metadata,
            gridSize: puzzleData.gridSize,
            wordCount: puzzleData.words ? puzzleData.words.length : 0,
            puzzleType: 'interactive_snake_word_search',
            sessionStats: puzzleData.sessionStats
          }
        };

      case 'mathcomparison':
        return {
          question: puzzleData.question,
          answer: puzzleData.answer,
          hint: puzzleData.hint,
          difficulty: difficulty,
          options: [],
          metadata: puzzleData.metadata
        };

      case 'wordsearch':
        return {
          question: puzzleData.question,
          answer: puzzleData.answer,
          hint: puzzleData.hint,
          difficulty: difficulty,
          options: [],
          metadata: {
              ...puzzleData.metadata,
              wordSource: 'ai_generated_deduped',
              generationMethod: 'enhanced_with_deduplication'
          }
        };

      case 'anagram':
        return {
          question: puzzleData.question,
          answer: puzzleData.answer,
          hint: puzzleData.hint,
          difficulty: difficulty,
          options: [],
          source: 'dictionary'
        };

      case 'memorypreviouspair':
        return {
          question: puzzleData.question,
          answer: puzzleData.answer,
          hint: puzzleData.hint,
          difficulty: difficulty,
          options: [],
          metadata: puzzleData.metadata
        };

      case 'crossword':
      case 'dailycrossword':
        return {
          question: puzzleData.question,
          answer: puzzleData.answer,
          hint: puzzleData.hint,
          difficulty: difficulty,
          options: [],
          metadata: {
            ...puzzleData.metadata,
            generationType: '5x5_constrained'
          }
        };

      case 'pinballdeflector':
        return {
          question: puzzleData.question,
          answer: puzzleData.answer,
          hint: puzzleData.hint,
          difficulty: difficulty,
          options: [],
          metadata: puzzleData.metadata
        };

      case 'memorymatrixpath':
        return {
          question: puzzleData.question,
          answer: puzzleData.answer,
          hint: puzzleData.hint,
          difficulty: difficulty,
          options: [],
          metadata: puzzleData.metadata
        };
      
      case 'crypto':
        return {
          question: puzzleData.question,
          answer: puzzleData.answer,
          hint: puzzleData.hint,
          difficulty: difficulty,
          options: [],
          metadata: {
            ...puzzleData.metadata,
            clientSideGeneration: true
          }
        };

      case 'memoryprevioussingle':
        return {
          question: puzzleData.question,
          answer: puzzleData.answer,
          hint: puzzleData.hint,
          difficulty: difficulty,
          options: [],
          metadata: puzzleData.metadata
        };

        case 'antonyms':
          this.debugLog(`🔧 Formatting antonym puzzleData keys: ${Object.keys(puzzleData).join(', ')}`);
          
          return {
            question: JSON.stringify(puzzleData.pairs.map(pair => {
              if (Array.isArray(pair)) {
                return pair; // Already in array format
              } else {
                return [pair.word1, pair.word2]; // Convert object to array
              }
            })),
            answer: "antonym",
            hint: puzzleData.hint || `Match ${puzzleData.pairCount || puzzleData.pairs.length} pairs of opposite words`,
            difficulty: difficulty,
            options: [],
            metadata: {
              pairCount: puzzleData.pairCount || puzzleData.pairs.length,
              instruction: puzzleData.instruction || "Drag each word to its opposite meaning",
              source: puzzleData.source || 'enhanced_generator',
              averageConfidence: puzzleData.metadata?.averageConfidence || 0.8,
              categories: puzzleData.metadata?.categories || {},
              generatedAt: puzzleData.metadata?.generatedAt || new Date().toISOString()
            }
          };

      case 'percentages':
        return {
          question: JSON.stringify({
            total: puzzleData.total,
            percentage: puzzleData.percentage,
            answer: puzzleData.answer,
            difficulty: puzzleData.difficulty || difficulty,
            hint: puzzleData.hint || `What is ${puzzleData.percentage}% of ${puzzleData.total}?`
          }),
          answer: (puzzleData.answer || '').toString(),
          hint: puzzleData.hint || `What is ${puzzleData.percentage}% of ${puzzleData.total}?`,
          difficulty: difficulty,
          options: []
        };

      case 'memorysquares':
        return {
          question: puzzleData.question,
          answer: puzzleData.answer,
          hint: puzzleData.hint,
          difficulty: difficulty,
          options: [],
          metadata: puzzleData.metadata
        };
    
      case 'mathestimation':
        return {
          question: JSON.stringify(puzzleData.numbers || []),
          answer: (puzzleData.sum || puzzleData.answer || '').toString(),
          hint: puzzleData.hint || "Round each number to make estimation easier",
          difficulty: difficulty,
          options: []
        };
    
      case 'mathtipping':
        return {
          question: JSON.stringify({
            billAmount: puzzleData.billAmount,
            tipPercentage: puzzleData.tipPercentage,
            tipAmount: puzzleData.tipAmount,
            isCorrect: puzzleData.isCorrect,
            difficulty: puzzleData.difficulty || difficulty,
            hint: puzzleData.hint || `Calculate ${puzzleData.tipPercentage}% tip on ${puzzleData.billAmount}`
          }),
          answer: "correct",
          hint: puzzleData.hint || `Calculate ${puzzleData.tipPercentage}% tip on ${puzzleData.billAmount}`,
          difficulty: difficulty,
          options: []
        };
    
      case 'division':
        return {
          question: JSON.stringify({
            problems: puzzleData.problems || []
          }),
          answer: puzzleData.problems ? puzzleData.problems.map(p => p[2]).join(', ') : '',
          hint: puzzleData.hint || "Think about multiplication tables to help with division",
          difficulty: difficulty,
          options: []
        };
    
      case 'average':
        return {
          question: JSON.stringify({
            numbers: puzzleData.numbers || [],
            average: puzzleData.average,
            difficulty: puzzleData.difficulty || difficulty,
            hint: puzzleData.hint || "Add all numbers together and divide by how many numbers there are"
          }),
          answer: (puzzleData.average || '').toString(),
          hint: puzzleData.hint || "Add all numbers together and divide by how many numbers there are",
          difficulty: difficulty,
          options: []
        };
    
      case 'subtraction':
        return {
          question: JSON.stringify({
            problems: puzzleData.problems || []
          }),
          answer: puzzleData.problems ? puzzleData.problems.map(p => p[2]).join(', ') : '',
          hint: puzzleData.hint || "Work from right to left, borrowing when necessary",
          difficulty: difficulty,
          options: []
        };
    
      case 'purchasing':
        return {
          question: JSON.stringify({
            payment: puzzleData.payment,
            frequency: puzzleData.frequency,
            purpose: "Subscription service",
            yearlyTotal: puzzleData.yearlyTotal,
            difficulty: puzzleData.difficulty || difficulty,
            hint: puzzleData.hint || `Multiply the ${puzzleData.frequency} payment by the number of periods in a year`
          }),
          answer: (puzzleData.yearlyTotal || '').toString(),
          hint: puzzleData.hint || `Calculate yearly total for ${puzzleData.frequency} payments of $${puzzleData.payment}`,
          difficulty: difficulty,
          options: []
        };
    
      case 'discounts':
        return {
          question: JSON.stringify(puzzleData.items || []),
          answer: JSON.stringify(puzzleData.correctOrder || []),
          hint: puzzleData.hint || "Calculate final prices after discounts and order from least to most expensive",
          difficulty: difficulty,
          options: []
        };
    
      case 'conversion':
        return {
          question: JSON.stringify({
            value1: puzzleData.value1,
            unit1: puzzleData.unit1,
            value2: puzzleData.value2,
            unit2: puzzleData.unit2,
            comparison: puzzleData.comparison,
            difficulty: puzzleData.difficulty || difficulty,
            hint: puzzleData.hint || `Check if ${puzzleData.value1} ${puzzleData.unit1} ${puzzleData.comparison} ${puzzleData.value2} ${puzzleData.unit2}`
          }),
          answer: "placeholder",
          hint: puzzleData.hint || `Check if ${puzzleData.value1} ${puzzleData.unit1} ${puzzleData.comparison} ${puzzleData.value2} ${puzzleData.unit2}`,
          difficulty: difficulty,
          options: []
        };

      default:
        this.debugLog(`⚠️ Unknown puzzle type ${puzzleType}, using fallback formatting`, 'warning');
        
        let question = '';
        let answer = '';
        
        if (puzzleData.numbers && Array.isArray(puzzleData.numbers)) {
          question = JSON.stringify(puzzleData.numbers);
          answer = (puzzleData.sum || puzzleData.average || '').toString();
        } else if (puzzleData.problems && Array.isArray(puzzleData.problems)) {
          question = JSON.stringify({ problems: puzzleData.problems });
          answer = puzzleData.problems.map(p => p[2]).join(', ');
        } else if (puzzleData.total && puzzleData.percentage) {
          question = JSON.stringify({ total: puzzleData.total, percentage: puzzleData.percentage });
          answer = (puzzleData.answer || '').toString();
        } else {
          question = JSON.stringify(puzzleData);
          answer = (puzzleData.answer || puzzleData.result || '').toString();
        }
        
        return {
          question: question,
          answer: answer,
          hint: puzzleData.hint || `Solve this ${puzzleType} puzzle`,
          difficulty: difficulty,
          options: []
        };
    }
  }

  /**
   * Process puzzle - ADD IMAGE_MATCH PROCESSING
   */
  async processPuzzle(puzzle, puzzleType) {
    try {
      switch (puzzleType) {
        case PUZZLE_TYPES.PROGRESSIVE_REVELATION:
          return this.processProgressiveRevelation(puzzle);
        case PUZZLE_TYPES.IMAGE_MATCH:
          return this.processImageMatch(puzzle);

        case PUZZLE_TYPES.REAL_OR_AI:
        case 'realorai':
          return this.processWhichIsReal(puzzle);
        case PUZZLE_TYPES.ODD_ONE_OUT:
          return this.processOddOneOut(puzzle);

        case PUZZLE_TYPES.LETTER_SET:
          return this.processLetterSet(puzzle);
        case PUZZLE_TYPES.FLOW_PUZZLE:
        case 'flow':
          return this.processFlowPuzzle(puzzle);
        case PUZZLE_TYPES.MATH_ESTIMATION:
          return this.processMathEstimation(puzzle);
        case PUZZLE_TYPES.MATH_TIPPING:
          return this.processMathTipping(puzzle);
        case PUZZLE_TYPES.PERCENTAGES:
          return this.processPercentages(puzzle);
        case PUZZLE_TYPES.DIVISION:
          return this.processDivision(puzzle);
        case PUZZLE_TYPES.AVERAGE:
          return this.processAverage(puzzle);
        case PUZZLE_TYPES.IMAGE_PUZZLE:
          return this.processImagePuzzle(puzzle);
        case PUZZLE_TYPES.SENTENCE_TRANSITIONS:    
          return this.processSentenceTransitions(puzzle);
        case PUZZLE_TYPES.SUBTRACTION:
          return this.processSubtraction(puzzle);
        case PUZZLE_TYPES.PURCHASING:
          return this.processPurchasing(puzzle);
        case PUZZLE_TYPES.MUSIC_IDENTIFICATION:
          return this.processMusicIdentification(puzzle);
        case PUZZLE_TYPES.IMAGE_QUESTION:
          return this.processImageQuestion(puzzle);
        case PUZZLE_TYPES.WALDO_PUZZLE:
          return this.processWaldoPuzzle(puzzle);
        case PUZZLE_TYPES.MATH_COMPARISON:
          return this.processMathComparison(puzzle);
        case PUZZLE_TYPES.DISCOUNTS:
          return this.processDiscounts(puzzle);
        case PUZZLE_TYPES.CONVERSION:
          return this.processConversion(puzzle);
        case PUZZLE_TYPES.ANAGRAM:
          return this.processAnagrams(puzzle);
        case PUZZLE_TYPES.ANTONYMS:
          return this.processAntonyms(puzzle);
        case PUZZLE_TYPES.SYNONYMS:
          return this.processSynonyms(puzzle);
        case PUZZLE_TYPES.MEMORY_SEQUENCING:
          return this.processMemorySequencing(puzzle);
        case PUZZLE_TYPES.MEMORY_RETENTION:
          return this.processMemoryRetention(puzzle);
        case PUZZLE_TYPES.CRYPTO:
          return { success: true, puzzle };
        case PUZZLE_TYPES.UNIQUE_OBJECT:
          return this.processUniqueObject(puzzle);
        case PUZZLE_TYPES.MEMORY_SQUARES:
          return this.processMemorySquares(puzzle);
        case PUZZLE_TYPES.MEMORY_PREVIOUS_PAIR:
          return this.processMemoryPreviousPair(puzzle);
        case PUZZLE_TYPES.MEMORY_PREVIOUS_SINGLE:
          return this.processMemoryPreviousSingle(puzzle);
        case PUZZLE_TYPES.MEMORY_MATRIX_PATH:
          return this.processMemoryMatrixPath(puzzle);
        case PUZZLE_TYPES.SNAKE_WORD_SEARCH:
          return this.processSnakeWordSearch(puzzle);
        case PUZZLE_TYPES.PINBALL_DEFLECTOR:
          return this.processPinballDeflector(puzzle);
        case PUZZLE_TYPES.WORD_SEARCH:
          return this.processWordSearch(puzzle);
        case PUZZLE_TYPES.FIND_DIFFERENCES:
          return this.processFindDifferences(puzzle);
        case PUZZLE_TYPES.FIND_OBJECT:
          return this.processFindObject(puzzle);
        
        default:
          return { success: true, puzzle };
      }
    } catch (error) {
      return { success: false, error: error.message };
    }
  }

  processProgressiveRevelation(puzzle) {
      try {
          this.debugLog(`🔄 Processing progressive revelation puzzle...`);
          
          // Parse question data (contains everything)
          const questionData = JSON.parse(puzzle.question);
          const answerData = JSON.parse(puzzle.answer);
          
          // Validate required fields in question
          if (!questionData.clues || !Array.isArray(questionData.clues)) {
              return { success: false, error: "Missing or invalid clues array" };
          }

          if (!questionData.puzzleId || !questionData.category) {
              return { success: false, error: "Missing required question fields: puzzleId or category" };
          }

          if (!questionData.image || !questionData.image.url) {
              return { success: false, error: "Missing image data" };
          }

          // Validate clues structure
          if (questionData.clues.length !== 4) {
              return { success: false, error: "Must have exactly 4 progressive clues" };
          }

          for (let i = 0; i < questionData.clues.length; i++) {
              const clue = questionData.clues[i];
              
              if (!clue.text || !clue.difficulty || typeof clue.level !== 'number') {
                  return { 
                      success: false, 
                      error: `Clue ${i + 1} missing required fields (text, difficulty, level)` 
                  };
              }
          }

          // Validate answer data
          if (!answerData.correctAnswer) {
              return { success: false, error: "Missing correct answer in answer data" };
          }

          // Validate game flow
          if (!questionData.gameFlow || !questionData.gameFlow.scoringSystem) {
              return { success: false, error: "Missing game flow or scoring system" };
          }

          // Validate client config for blur levels
          if (!questionData.image.blurLevels || !Array.isArray(questionData.image.blurLevels)) {
              return { success: false, error: "Missing blur levels configuration" };
          }

          // Ensure hint exists
          if (!puzzle.hint) {
              puzzle.hint = `Progressive revelation puzzle: guess what's in the image using ${questionData.clues.length} clues`;
          }

          this.debugLog(`✅ Successfully processed progressive revelation puzzle:`);
          this.debugLog(`🎨 Category: ${questionData.category}`);
          this.debugLog(`🎯 Answer: ${answerData.correctAnswer}`);
          this.debugLog(`❓ Clues: ${questionData.clues.length}`);
          this.debugLog(`🖼️ Image: ${questionData.image.url}`);
          this.debugLog(`⭐ Max Score: ${questionData.gameFlow.scoringSystem.correctAtClue1}`);
          
          return { success: true, puzzle };

      } catch (error) {
          this.debugLog(`❌ Progressive revelation processing error: ${error.message}`, 'error');
          return { success: false, error: `Progressive revelation processing error: ${error.message}` };
      }
  }

  processOddOneOut(puzzle) {
    try {
      this.debugLog(`📄 Processing odd one out puzzle...`);
      
      // Parse question data (contains everything)
      const questionData = JSON.parse(puzzle.question);
      const answerData = JSON.parse(puzzle.answer);
      
      // Validate required fields in question
      if (!questionData.images || !Array.isArray(questionData.images)) {
        return { success: false, error: "Missing or invalid images array" };
      }
  
      if (questionData.images.length !== 4) {
        return { 
          success: false, 
          error: `Must have exactly 4 images, got ${questionData.images.length}` 
        };
      }
  
      if (!questionData.puzzleId || !questionData.type || !questionData.theme) {
        return { success: false, error: "Missing required question fields: puzzleId, type, or theme" };
      }
  
      // Validate each image
      for (let i = 0; i < questionData.images.length; i++) {
        const img = questionData.images[i];
        
        if (!img.id || !img.url || !img.item) {
          return { 
            success: false, 
            error: `Image ${i + 1} missing required fields (id, url, item)` 
          };
        }
  
        // Ensure URL is from Supabase
        if (!img.url.includes('supabase') && !img.url.includes('storage')) {
          return { 
            success: false, 
            error: `Image ${i + 1} URL is not from Supabase: ${img.url}` 
          };
        }
  
        if (!img.item.name || typeof img.item.belongsToGroup !== 'boolean') {
          return { 
            success: false, 
            error: `Image ${i + 1} has invalid item data` 
          };
        }
      }
  
      // Validate exactly one odd one out
      const oddOnes = questionData.images.filter(img => img.isOddOneOut);
      if (oddOnes.length !== 1) {
        return { 
          success: false, 
          error: `Must have exactly 1 odd one out, found ${oddOnes.length}` 
        };
      }
  
      // Validate questions array
      if (!questionData.questions || !Array.isArray(questionData.questions)) {
        return { success: false, error: "Missing or invalid questions array" };
      }
  
      if (questionData.questions.length === 0) {
        return { success: false, error: "Must have at least 1 question" };
      }
  
      // Validate each question
      for (let i = 0; i < questionData.questions.length; i++) {
        const q = questionData.questions[i];
        
        if (!q.question || !q.type) {
          return { 
            success: false, 
            error: `Question ${i + 1} missing required fields (question, type)` 
          };
        }
  
        if (q.type === 'single_choice') {
          if (!q.options || !Array.isArray(q.options) || q.options.length < 2) {
            return { 
              success: false, 
              error: `Question ${i + 1} has invalid options array` 
            };
          }
  
          if (!q.correctAnswer) {
            return { 
              success: false, 
              error: `Question ${i + 1} missing correctAnswer` 
            };
          }
  
          // Ensure correct answer is in options
          if (!q.options.includes(q.correctAnswer)) {
            return { 
              success: false, 
              error: `Question ${i + 1} correct answer not found in options` 
            };
          }
        }
      }
  
      // Validate answer data
      if (!answerData.oddOneOut || !answerData.theme) {
        return { success: false, error: "Missing oddOneOut or theme in answer data" };
      }
  
      // Validate scenario
      if (!questionData.scenario || !questionData.scenario.name) {
        return { success: false, error: "Missing scenario data" };
      }
  
      // Validate time limit
      if (!questionData.timeLimit || questionData.timeLimit < 15000) {
        return { success: false, error: "Invalid or missing time limit (minimum 15 seconds)" };
      }
  
      // Ensure hint exists
      if (!puzzle.hint) {
        puzzle.hint = `Find the odd one out among these ${questionData.theme} items`;
      }
  
      this.debugLog(`✅ Successfully processed odd one out puzzle:`);
      this.debugLog(`🎯 Puzzle ID: ${questionData.puzzleId}`);
      this.debugLog(`🎨 Theme: ${questionData.theme}${questionData.subTheme ? ` / ${questionData.subTheme}` : ''}`);
      this.debugLog(`🖼️ Images: ${questionData.images.length}`);
      this.debugLog(`❓ Questions: ${questionData.questions.length}`);
      this.debugLog(`⏱️ Time limit: ${Math.round(questionData.timeLimit / 1000)}s`);
      this.debugLog(`⭐ Odd one out: ${answerData.oddOneOut}`);
      this.debugLog(`🔍 Pattern: ${questionData.scenario.patternType}`);
      
      return { success: true, puzzle };
  
    } catch (error) {
      this.debugLog(`❌ Odd one out processing error: ${error.message}`, 'error');
      return { success: false, error: `Odd one out processing error: ${error.message}` };
    }
  }

  /**
   * Process Which Is Real puzzle - validates and prepares for storage
   */
  processWhichIsReal(puzzle) {
    try {
      this.debugLog(`🔄 Processing Which Is Real puzzle...`);
      
      // Parse question data
      const questionData = JSON.parse(puzzle.question);
      const answerData = JSON.parse(puzzle.answer);
      
      // Validate required fields in question
      if (!questionData.puzzleId || !questionData.type || !questionData.category) {
        return { 
          success: false, 
          error: "Missing required question fields: puzzleId, type, or category" 
        };
      }

      // Validate image placeholders exist (URLs will be filled after upload)
      if (!questionData.imageA || !questionData.imageB) {
        return { 
          success: false, 
          error: "Missing image placeholders in question data" 
        };
      }

      // Validate answer data
      if (!answerData.correctAnswer || !answerData.imageDetails) {
        return { 
          success: false, 
          error: "Missing correctAnswer or imageDetails in answer data" 
        };
      }

      // Validate correct answer is either 'image_a' or 'image_b'
      if (answerData.correctAnswer !== 'image_a' && answerData.correctAnswer !== 'image_b') {
        return { 
          success: false, 
          error: `Invalid correctAnswer: ${answerData.correctAnswer}. Must be 'image_a' or 'image_b'` 
        };
      }

      // Validate image details structure
      if (!answerData.imageDetails.image_a || !answerData.imageDetails.image_b) {
        return { 
          success: false, 
          error: "Missing image details for image_a or image_b" 
        };
      }

      // Validate each image has required metadata
      const imageA = answerData.imageDetails.image_a;
      const imageB = answerData.imageDetails.image_b;

      if (!imageA.type || !imageA.source) {
        return { 
          success: false, 
          error: "Image A missing type or source information" 
        };
      }

      if (!imageB.type || !imageB.source) {
        return { 
          success: false, 
          error: "Image B missing type or source information" 
        };
      }

      // Ensure one is real and one is AI
      const types = [imageA.type, imageB.type].sort();
      if (types[0] !== 'ai_generated' || types[1] !== 'real') {
        return { 
          success: false, 
          error: "Puzzle must have one 'real' and one 'ai_generated' image" 
        };
      }

      // Validate instructions exist
      if (!questionData.instructions || !questionData.instructions.task) {
        return { 
          success: false, 
          error: "Missing instructions in question data" 
        };
      }

      // Validate expert clues
      if (!questionData.expertClues || !questionData.expertClues.real || !questionData.expertClues.ai) {
        return { 
          success: false, 
          error: "Missing expert clues for learning" 
        };
      }

      // Validate time limit
      if (!questionData.timeLimit || questionData.timeLimit < 30000) {
        return { 
          success: false, 
          error: "Invalid or missing time limit (minimum 30 seconds)" 
        };
      }

      // Ensure hint exists
      if (!puzzle.hint) {
        puzzle.hint = questionData.instructions.task;
      }

      this.debugLog(`✅ Successfully processed Which Is Real puzzle:`);
      this.debugLog(`🎯 Puzzle ID: ${questionData.puzzleId}`);
      this.debugLog(`📂 Category: ${questionData.category}`);
      this.debugLog(`🎨 Subject: ${questionData.subjectName}`);
      this.debugLog(`✔️ Correct Answer: ${answerData.correctAnswer}`);
      this.debugLog(`⏱️ Time limit: ${Math.round(questionData.timeLimit / 1000)}s`);
      this.debugLog(`🧠 Expert clues: ${questionData.expertClues.real.length + questionData.expertClues.ai.length} total`);
      this.debugLog(`📚 Reasoning enabled: ${questionData.reasoningEnabled ? 'YES' : 'NO'}`);
      
      return { success: true, puzzle };

    } catch (error) {
      this.debugLog(`❌ Which Is Real processing error: ${error.message}`, 'error');
      return { 
        success: false, 
        error: `Which Is Real processing error: ${error.message}` 
      };
    }
  }

  processImageMatch(puzzle) {
    try {
      this.debugLog(`🔄 Processing image match puzzle...`);
      
      // Parse question data (contains everything)
      const questionData = JSON.parse(puzzle.question);
      const answerData = JSON.parse(puzzle.answer);
      
      // Validate required fields in question
      if (!questionData.questions || !Array.isArray(questionData.questions)) {
        return { success: false, error: "Missing or invalid questions array" };
      }

      if (!questionData.puzzleId || !questionData.theme) {
        return { success: false, error: "Missing required question fields: puzzleId or theme" };
      }

      if (!questionData.primaryImage || !questionData.primaryImage.url) {
        return { success: false, error: "Missing primary image data" };
      }

      // Validate each question
      for (let i = 0; i < questionData.questions.length; i++) {
        const q = questionData.questions[i];
        
        if (!q.question || !q.answer || !q.type) {
          return { 
            success: false, 
            error: `Question ${i + 1} missing required fields (question, answer, type)` 
          };
        }

        if (!q.options || !Array.isArray(q.options) || q.options.length < 2) {
          return { 
            success: false, 
            error: `Question ${i + 1} has invalid options array` 
          };
        }

        // Ensure correct answer is in options
        if (!q.options.includes(q.answer)) {
          return { 
            success: false, 
            error: `Question ${i + 1} correct answer not found in options` 
          };
        }
      }

      // Validate answer data
      if (!answerData.correctAnswers || !Array.isArray(answerData.correctAnswers)) {
        return { success: false, error: "Missing or invalid correctAnswers array in answer" };
      }

      if (answerData.correctAnswers.length !== questionData.questions.length) {
        return { 
          success: false, 
          error: `Answer count mismatch: ${answerData.correctAnswers.length} vs ${questionData.questions.length}` 
        };
      }

      // Validate theme context
      if (!questionData.themeItem || !answerData.themeContext) {
        return { success: false, error: "Missing theme context data" };
      }

      // Validate time limit
      if (!questionData.timeLimit || questionData.timeLimit < 30000) {
        return { success: false, error: "Invalid or missing time limit (minimum 30 seconds)" };
      }

      // Ensure hint exists
      if (!puzzle.hint) {
        puzzle.hint = `Test your knowledge about ${questionData.themeItem.answer || questionData.theme}`;
      }

      this.debugLog(`✅ Successfully processed image match puzzle:`);
      this.debugLog(`🌍 Puzzle ID: ${questionData.puzzleId}`);
      this.debugLog(`🎨 Theme: ${questionData.theme}`);
      this.debugLog(`❓ Questions: ${questionData.questions.length}`);
      this.debugLog(`⏱️ Time limit: ${Math.round(questionData.timeLimit / 1000)}s`);
      this.debugLog(`🖼️ Image: ${questionData.primaryImage.url}`);
      this.debugLog(`📷 By: ${questionData.primaryImage.photographer}`);
      
      return { success: true, puzzle };

    } catch (error) {
      this.debugLog(`❌ Image match processing error: ${error.message}`, 'error');
      return { success: false, error: `Image match processing error: ${error.message}` };
    }
  }

  // EXISTING PROCESSING METHODS CONTINUE...
  processUniqueObject(puzzle) {
    try {
      this.debugLog(`🔄 Processing unique object puzzle...`);
      
      const questionData = JSON.parse(puzzle.question);
      const answerData = JSON.parse(puzzle.answer);
      
      if (!questionData.objects || !Array.isArray(questionData.objects)) {
        return { success: false, error: "Missing or invalid objects array" };
      }

      if (questionData.objects.length < 3) {
        return { success: false, error: "Must have at least 3 objects" };
      }

      if (typeof answerData.uniqueObjectIndex !== 'number' || 
          answerData.uniqueObjectIndex < 0 || 
          answerData.uniqueObjectIndex >= questionData.objects.length) {
        return { 
          success: false, 
          error: `Invalid unique object index: ${answerData.uniqueObjectIndex}` 
        };
      }

      for (let i = 0; i < questionData.objects.length; i++) {
        const obj = questionData.objects[i];
        if (typeof obj.shape !== 'number' || typeof obj.color !== 'number') {
          return { 
            success: false, 
            error: `Object ${i} missing or invalid shape/color properties` 
          };
        }
      }

      const uniqueObj = questionData.objects[answerData.uniqueObjectIndex];
      const uniqueCombo = `${uniqueObj.shape}-${uniqueObj.color}`;
      
      let uniqueCount = 0;
      for (const obj of questionData.objects) {
        if (`${obj.shape}-${obj.color}` === uniqueCombo) {
          uniqueCount++;
        }
      }

      if (uniqueCount !== 1) {
        return { 
          success: false, 
          error: `Unique object appears ${uniqueCount} times, should appear exactly once` 
        };
      }

      if (!questionData.shapeMappings || !questionData.colorMappings) {
        return { success: false, error: "Missing shape or color mappings" };
      }

      if (!puzzle.hint) {
        puzzle.hint = "Find the object that appears only once - it has a unique combination of shape and color";
      }

      this.debugLog(`✅ Successfully processed unique object puzzle:`);
      this.debugLog(`📊 Objects: ${questionData.objects.length} total`);
      this.debugLog(`🎯 Unique object at index: ${answerData.uniqueObjectIndex}`);
      this.debugLog(`🎨 Shapes: ${Object.keys(questionData.shapeMappings).length}, Colors: ${Object.keys(questionData.colorMappings).length}`);
      
      return { success: true, puzzle };

    } catch (error) {
      this.debugLog(`❌ Unique object processing error: ${error.message}`, 'error');
      return { success: false, error: `Unique object processing error: ${error.message}` };
    }
  }

  processMemoryRetention(puzzle) {
    try {
      this.debugLog(`🔄 Processing memory retention puzzle...`);
      
      if (!puzzle.topic || !puzzle.essay) {
        return { success: false, error: "Missing required fields: topic or essay" };
      }

      if (!puzzle.subjects || !Array.isArray(puzzle.subjects) || puzzle.subjects.length !== 3) {
        return { success: false, error: "Must have exactly 3 subjects" };
      }

      if (!puzzle.facts || !Array.isArray(puzzle.facts)) {
        return { success: false, error: "Missing or invalid facts array" };
      }

      for (let i = 0; i < puzzle.subjects.length; i++) {
        const subject = puzzle.subjects[i];
        if (!subject.id || !subject.name || !subject.description) {
          return { 
            success: false, 
            error: `Subject ${i + 1} missing required fields (id, name, description)` 
          };
        }

        const duplicateSubject = puzzle.subjects.find((s, index) => 
          index !== i && s.id === subject.id
        );
        if (duplicateSubject) {
          return { 
            success: false, 
            error: `Duplicate subject ID: ${subject.id}` 
          };
        }
      }

      const subjectIds = puzzle.subjects.map(s => s.id);
      const factTimings = [];
      
      for (let i = 0; i < puzzle.facts.length; i++) {
        const fact = puzzle.facts[i];
        
        if (!fact.id || !fact.text || !fact.correctSubject || typeof fact.showTiming !== 'number') {
          return { 
            success: false, 
            error: `Fact ${i + 1} missing required fields (id, text, correctSubject, showTiming)` 
          };
        }

        if (!subjectIds.includes(fact.correctSubject)) {
          return { 
            success: false, 
            error: `Fact ${i + 1} has invalid correctSubject: ${fact.correctSubject}` 
          };
        }

        if (fact.showTiming < 0) {
          return { 
            success: false, 
            error: `Fact ${i + 1} has negative showTiming: ${fact.showTiming}` 
          };
        }

        const duplicateFact = puzzle.facts.find((f, index) => 
          index !== i && f.id === fact.id
        );
        if (duplicateFact) {
          return { 
            success: false, 
            error: `Duplicate fact ID: ${fact.id}` 
          };
        }

        factTimings.push(fact.showTiming);
      }

      const subjectFactCounts = {};
      puzzle.subjects.forEach(subject => {
        subjectFactCounts[subject.id] = 0;
      });

      puzzle.facts.forEach(fact => {
        subjectFactCounts[fact.correctSubject]++;
      });

      const expectedFactsPerSubject = puzzle.facts.length / 3;
      const isEvenDistribution = Object.values(subjectFactCounts).every(count => 
        count === expectedFactsPerSubject
      );

      if (!isEvenDistribution) {
        const distribution = Object.entries(subjectFactCounts)
          .map(([subjectId, count]) => `${subjectId}: ${count}`)
          .join(', ');
        
        return { 
          success: false, 
          error: `Uneven fact distribution - Expected ${expectedFactsPerSubject} per subject. Got: ${distribution}` 
        };
      }

      factTimings.sort((a, b) => a - b);
      const maxTiming = Math.max(...factTimings);
      const minTiming = Math.min(...factTimings);

      const timeSpan = maxTiming - minTiming;
      const minimumSpan = maxTiming * 0.6;

      if (timeSpan < minimumSpan) {
        return { 
          success: false, 
          error: `Facts are too clustered in time. Span: ${timeSpan}ms, minimum required: ${minimumSpan}ms` 
        };
      }

      const wordCount = puzzle.essay.split(/\s+/).length;
      const estimatedDurationMs = (wordCount / 150) * 60 * 1000;

      if (maxTiming > estimatedDurationMs * 1.1) {
        return { 
          success: false, 
          error: `Latest fact timing (${maxTiming}ms) exceeds estimated essay duration (${estimatedDurationMs}ms)` 
        };
      }

      const questionData = {
        topic: puzzle.topic,
        essay: puzzle.essay,
        subjects: puzzle.subjects,
        facts: puzzle.facts,
        totalFacts: puzzle.facts.length,
        factsPerSubject: expectedFactsPerSubject,
        estimatedDuration: Math.round(estimatedDurationMs),
        wordCount: wordCount,
        metadata: {
          generatedAt: new Date().toISOString(),
          factDistribution: subjectFactCounts,
          timingRange: {
            earliest: minTiming,
            latest: maxTiming,
            span: timeSpan
          }
        }
      };

      const answerData = {
        subjects: puzzle.subjects,
        factMappings: puzzle.facts.map(fact => ({
          factId: fact.id,
          factText: fact.text,
          correctSubject: fact.correctSubject,
          showTiming: fact.showTiming
        })),
        answerKey: puzzle.facts.reduce((acc, fact) => {
          acc[fact.id] = fact.correctSubject;
          return acc;
        }, {})
      };

      puzzle.question = JSON.stringify(questionData);
      puzzle.answer = JSON.stringify(answerData);
      
      if (!puzzle.hint) {
        puzzle.hint = "Listen carefully to the essay and remember which facts belong to which subject";
      }

      this.debugLog(`✅ Successfully processed memory retention puzzle:`);
      this.debugLog(`📝 Topic: ${puzzle.topic}`);
      this.debugLog(`📊 Facts: ${puzzle.facts.length} total (${expectedFactsPerSubject} per subject)`);
      this.debugLog(`⏱️ Timing: ${minTiming}ms - ${maxTiming}ms (span: ${timeSpan}ms)`);
      this.debugLog(`📖 Essay: ${wordCount} words (~${Math.round(estimatedDurationMs/1000)}s duration)`);
      
      return { success: true, puzzle };

    } catch (error) {
      this.debugLog(`❌ Memory retention processing error: ${error.message}`, 'error');
      return { success: false, error: `Memory retention processing error: ${error.message}` };
    }
  }

  /**
   * Store puzzle using appropriate method - ENHANCED with IMAGE_MATCH
   */
  async storePuzzle(puzzle, puzzleType, modelName) {
    try {
      const metadata = PUZZLE_METADATA[puzzleType];
      
      if (metadata?.storeFullJSONInQuestion) {
        return await this.storeJSONPuzzle(puzzle, puzzleType, modelName);
      } else {
        return await this.storeRegularPuzzle(puzzle, puzzleType, modelName);
      }
    } catch (error) {
      this.debugLog(`Store puzzle error: ${error.message}`, 'error');
      return { success: false, message: `Storage error: ${error.message}` };
    }
  }

  /**
 * Store multiple puzzles as a batch with atomic path management
 */
  async storeBatchPuzzles(puzzles, puzzleType, modelName) {
    this.debugLog(`📦 Starting atomic batch storage of ${puzzles.length} ${puzzleType} puzzles...`);
    
    if (!puzzles || puzzles.length === 0) {
        return { success: false, message: 'No puzzles to store' };
    }

    const results = {
        success: true,
        puzzleIds: [],
        errors: [],
        successCount: 0,
        failureCount: 0
    };

    // Store puzzles sequentially to maintain proper chain order
    for (let i = 0; i < puzzles.length; i++) {
        const puzzle = puzzles[i];
        
        try {
            this.debugLog(`🔄 Storing batch puzzle ${i + 1}/${puzzles.length} using stored procedure...`);
            
            const storeResult = await storePuzzleInSupabase({
                puzzleType,
                question: puzzle.question,
                answer: puzzle.answer,
                hint: puzzle.hint || '',
                difficulty: puzzle.difficulty.toLowerCase(),
                options: puzzle.options || [],
                modelName,
                validationModel: this.validationModel
            });
            
            if (storeResult.success) {
                results.puzzleIds.push(storeResult.puzzleId);
                results.successCount++;
                this.debugLog(`✅ Batch puzzle ${i + 1}/${puzzles.length} stored atomically: ${storeResult.puzzleId}`, 'success');
            } else {
                results.errors.push(`Puzzle ${i + 1}: ${storeResult.message}`);
                results.failureCount++;
                this.debugLog(`❌ Batch puzzle ${i + 1}/${puzzles.length} failed: ${storeResult.message}`, 'error');
            }
            
        } catch (error) {
            results.errors.push(`Puzzle ${i + 1}: ${error.message}`);
            results.failureCount++;
            this.debugLog(`💥 Batch puzzle ${i + 1}/${puzzles.length} exception: ${error.message}`, 'error');
        }

        // Small delay between puzzles to prevent overwhelming the database
        if (i < puzzles.length - 1) {
            await new Promise(resolve => setTimeout(resolve, 100));
        }
    }

    results.success = results.successCount > 0;
    
    if (results.success) {
        this.debugLog(`🎉 Atomic batch storage completed: ${results.successCount}/${puzzles.length} puzzles stored successfully`, 'success');
    } else {
        this.debugLog(`❌ Batch storage failed: 0/${puzzles.length} puzzles stored`, 'error');
    }

    return results;
}

  /**
   * Store JSON-based puzzles - ENHANCED with IMAGE_MATCH
   */
  async storeJSONPuzzle(puzzle, puzzleType, modelName) {
    if (!this.storePuzzleInSupabase) {
      const { storePuzzleInSupabase } = await import('./puzzleService.js');
      this.storePuzzleInSupabase = storePuzzleInSupabase;
    }
    
    switch (puzzleType) {
      case PUZZLE_TYPES.PROGRESSIVE_REVELATION:
        return await this.storePuzzleInSupabase({
            puzzleType,
            question: puzzle.question,      // JSON string with all progressive puzzle data
            answer: puzzle.answer,          // JSON string with answer and scoring
            hint: puzzle.hint || "Progressive revelation puzzle with image and clues",
            difficulty: puzzle.difficulty,
            options: [], // No options for progressive puzzles
            modelName,
            validationModel: this.validationModel
        });

      case PUZZLE_TYPES.ODD_ONE_OUT:
        return await this.storePuzzleInSupabase({
          puzzleType,
          question: puzzle.question,      // JSON string with all odd one out data
          answer: puzzle.answer,          // JSON string with correct answers
          hint: puzzle.hint || "Find which item doesn't belong with the others",
          difficulty: puzzle.difficulty,
          options: [], // Options are stored within question JSON
          modelName,
          validationModel: this.validationModel
        });
      
      case PUZZLE_TYPES.REAL_OR_AI:
        case 'realorai':
          return await this.storePuzzleInSupabase({
            puzzleType,
            question: puzzle.question,      // JSON string with all puzzle data
            answer: puzzle.answer,          // JSON string with correct answer
            hint: puzzle.hint || "Examine both images carefully to identify which is real",
            difficulty: puzzle.difficulty,
            options: [], // No options for this puzzle type
            modelName,
            validationModel: this.validationModel
          });

      case PUZZLE_TYPES.IMAGE_MATCH:
        return await this.storePuzzleInSupabase({
          puzzleType,
          question: puzzle.question,      // JSON string with all image match data
          answer: puzzle.answer,          // JSON string with correct answers
          hint: puzzle.hint || "Test your knowledge about world locations and culture",
          difficulty: puzzle.difficulty,
          options: [], // Options are stored within question JSON
          modelName,
          validationModel: this.validationModel
        });

      case PUZZLE_TYPES.FLOW_PUZZLE:  
      case 'flow':
        return await this.storePuzzleInSupabase({
          puzzleType,
          question: puzzle.question,      // JSON string with all Flow puzzle data
          answer: puzzle.answer,          // JSON string with solution paths
          hint: puzzle.hint || "Connect matching colored dots without crossing paths",
          difficulty: puzzle.difficulty,
          options: [], // Options not used for Flow puzzles
          modelName,
          validationModel: this.validationModel
        });

      case PUZZLE_TYPES.CROSSWORD:
        case PUZZLE_TYPES.DAILY_CROSSWORD:
        return await this.storePuzzleInSupabase({
          puzzleType,
          question: puzzle.question,
          answer: puzzle.answer,
          hint: puzzle.hint || "Complete the crossword using the given clues",
          difficulty: puzzle.difficulty,
          options: [],
          modelName,
          validationModel: this.validationModel
        });

      case PUZZLE_TYPES.LETTER_SET:
        return await this.storePuzzleInSupabase({
          puzzleType,
          question: puzzle.question,
          answer: puzzle.answer,
          hint: puzzle.hint || "Form words using the provided letters",
          difficulty: puzzle.difficulty,
          options: [],
          modelName,
          validationModel: this.validationModel
        });

      case PUZZLE_TYPES.WORD_SNAKE:
        return await this.storePuzzleInSupabase({
          puzzleType,
          question: puzzle.question,
          answer: puzzle.answer,
          hint: puzzle.hint || "Find hidden words by tracing snake-like paths",
          difficulty: puzzle.difficulty,
          options: [],
          modelName,
          validationModel: this.validationModel
        });

      case PUZZLE_TYPES.MUSIC_IDENTIFICATION:
        return await this.storePuzzleInSupabase({
          puzzleType,
          question: puzzle.question,
          answer: puzzle.answer,
          hint: puzzle.hint || "Listen to song previews and identify each track",
          difficulty: puzzle.difficulty,
          options: [],
          modelName,
          validationModel: this.validationModel
        });

      case PUZZLE_TYPES.IMAGE_QUESTION:
        return await this.storePuzzleInSupabase({
          puzzleType,
          question: puzzle.question,
          answer: puzzle.answer,
          hint: puzzle.hint || "Answer questions about the image by observing carefully",
          difficulty: puzzle.difficulty,
          options: [],
          modelName,
          validationModel: this.validationModel
        });

      case PUZZLE_TYPES.WALDO_PUZZLE:
        return await this.storePuzzleInSupabase({
          puzzleType,
          question: puzzle.question,
          answer: puzzle.answer,
          hint: puzzle.hint || "Find all hidden objects by tapping on them in the image",
          difficulty: puzzle.difficulty,
          options: [],
          modelName,
          validationModel: this.validationModel
        });

      case PUZZLE_TYPES.MATH_COMPARISON:
        return await this.storePuzzleInSupabase({
          puzzleType,
          question: puzzle.question,
          answer: puzzle.answer,
          hint: puzzle.hint || "Compare the mathematical expressions and choose which is greater or EQUAL",
          difficulty: puzzle.difficulty,
          options: [],
          modelName,
          validationModel: this.validationModel
        });

      case PUZZLE_TYPES.UNIQUE_OBJECT:
        return await this.storePuzzleInSupabase({
          puzzleType,
          question: puzzle.question,
          answer: puzzle.answer,
          hint: puzzle.hint || "Find the object that appears only once",
          difficulty: puzzle.difficulty,
          options: [],
          modelName,
          validationModel: this.validationModel
        });

      case PUZZLE_TYPES.FIND_OBJECT:
        return await this.storePuzzleInSupabase({
          puzzleType,
          question: puzzle.question,
          answer: puzzle.answer,
          hint: puzzle.hint || "Find and tap the specified object in the image",
          difficulty: puzzle.difficulty,
          options: [],
          modelName,
          validationModel: this.validationModel
        });

      case PUZZLE_TYPES.FIND_DIFFERENCES:
        return await this.storePuzzleInSupabase({
          puzzleType,
          question: puzzle.question,
          answer: puzzle.answer,
          hint: puzzle.hint || "Find all differences between the images by tapping on them",
          difficulty: puzzle.difficulty,
          options: [],
          modelName,
          validationModel: this.validationModel
        });

      case PUZZLE_TYPES.WORD_SEARCH:
        return await this.storePuzzleInSupabase({
          puzzleType,
          question: puzzle.question,
          answer: puzzle.answer,
          hint: puzzle.hint || "Find all the hidden words in the grid",
          difficulty: puzzle.difficulty,
          options: [],
          modelName,
          validationModel: this.validationModel
        });

      default:
        return await this.storePuzzleInSupabase({
          puzzleType,
          question: JSON.stringify(puzzle),
          answer: "",
          hint: puzzle.hint || "",
          difficulty: puzzle.difficulty,
          options: [],
          modelName,
          validationModel: this.validationModel
        });
    }
  }

  async storeRegularPuzzle(puzzle, puzzleType, modelName) {
    try {
      if (!this.storePuzzleInSupabase) {
        const { storePuzzleInSupabase } = await import('./puzzleService.js');
        this.storePuzzleInSupabase = storePuzzleInSupabase;
      }

      this.debugLog(`🔍 Validating puzzle fields before storage...`);
      this.debugLog(`📝 Question: ${puzzle.question ? 'EXISTS' : 'MISSING'} (${typeof puzzle.question})`);
      this.debugLog(`📝 Answer: ${puzzle.answer ? 'EXISTS' : 'MISSING'} (${typeof puzzle.answer})`);

      if (!puzzle.question || puzzle.question === '' || puzzle.question === null || puzzle.question === undefined) {
        this.debugLog(`❌ Question field validation failed: "${puzzle.question}"`, 'error');
        return { success: false, message: 'Missing or invalid question field' };
      }

      if (!puzzle.answer || puzzle.answer === '' || puzzle.answer === null || puzzle.answer === undefined) {
        this.debugLog(`❌ Answer field validation failed: "${puzzle.answer}"`, 'error');
        return { success: false, message: 'Missing or invalid answer field' };
      }

      this.debugLog(`✅ Puzzle fields validated successfully`);

      const result = await this.storePuzzleInSupabase({
        puzzleType,
        question: puzzle.question,
        answer: puzzle.answer,
        hint: puzzle.hint || '',
        difficulty: puzzle.difficulty.toLowerCase(),
        options: puzzle.options || [],
        modelName,
        validationModel: this.validationModel
      });

      return result;
    } catch (error) {
      this.debugLog(`Store regular puzzle error: ${error.message}`, 'error');
      return { success: false, message: `Storage error: ${error.message}` };
    }
  }

  processMusicIdentification(puzzle) {
    try {
      this.debugLog(`Processing music identification puzzle...`);
      
      // Parse question and answer data
      const questionData = JSON.parse(puzzle.question);
      const answerData = JSON.parse(puzzle.answer);
      
      // Validate required fields in question
      if (!questionData.songs || !Array.isArray(questionData.songs)) {
        return { success: false, error: "Missing or invalid songs array" };
      }
  
      if (!questionData.puzzleId || !questionData.title) {
        return { success: false, error: "Missing required question fields: puzzleId or title" };
      }
  
      if (questionData.songs.length < 3) {
        return { success: false, error: "Must have at least 3 songs for music puzzle" };
      }
  
      // Validate each song
      for (let i = 0; i < questionData.songs.length; i++) {
        const song = questionData.songs[i];
        
        if (!song.title || !song.artist || !song.id) {
          return { 
            success: false, 
            error: `Song ${i + 1} missing required fields (title, artist, id)` 
          };
        }
  
        if (!song.options || !Array.isArray(song.options) || song.options.length < 2) {
          return { 
            success: false, 
            error: `Song ${i + 1} has invalid options array` 
          };
        }
  
        // Ensure correct answer (title) is in options
        if (!song.options.includes(song.title)) {
          return { 
            success: false, 
            error: `Song ${i + 1} correct answer not found in options` 
          };
        }
  
        // Validate preview URL if provided (optional but preferred)
        if (song.previewUrl && !this.isValidUrl(song.previewUrl)) {
          this.debugLog(`Song ${i + 1} has potentially invalid preview URL: ${song.previewUrl}`, 'warning');
        }
      }
  
      // Validate answer data
      if (!answerData.correctAnswers || !Array.isArray(answerData.correctAnswers)) {
        return { success: false, error: "Missing or invalid correctAnswers array in answer" };
      }
  
      if (answerData.correctAnswers.length !== questionData.songs.length) {
        return { 
          success: false, 
          error: `Answer count mismatch: ${answerData.correctAnswers.length} vs ${questionData.songs.length}` 
        };
      }
  
      // Validate time limit
      if (!questionData.timeLimit || questionData.timeLimit < 60000) {
        return { success: false, error: "Invalid or missing time limit (minimum 60 seconds)" };
      }
  
      // Ensure hint exists
      if (!puzzle.hint) {
        puzzle.hint = `Identify ${questionData.songs.length} songs from ${questionData.theme || 'various artists'}`;
      }
  
      this.debugLog(`Successfully processed music identification puzzle:`);
      this.debugLog(`Puzzle ID: ${questionData.puzzleId}`);
      this.debugLog(`Theme: ${questionData.theme}`);
      this.debugLog(`Songs: ${questionData.songs.length}`);
      this.debugLog(`Time limit: ${Math.round(questionData.timeLimit / 1000)}s`);
      this.debugLog(`Previews available: ${questionData.songs.filter(s => s.previewUrl).length}`);
      
      return { success: true, puzzle };
  
    } catch (error) {
      this.debugLog(`Music identification processing error: ${error.message}`, 'error');
      return { success: false, error: `Music identification processing error: ${error.message}` };
    }
  }
  
  // 3. FIXED: Add URL validation helper method (if not already present)
  isValidUrl(url) {
    try {
      new URL(url);
      return url.startsWith('http://') || url.startsWith('https://');
    } catch {
      return false;
    }
  }

  processFlowPuzzle(puzzle) {
    try {
      this.debugLog(`🔄 Processing Flow puzzle...`);
      
      // Parse question data
      const questionData = JSON.parse(puzzle.question);
      const answerData = JSON.parse(puzzle.answer);
      
      // Validate grid size
      if (!questionData.gridSize || questionData.gridSize < 4 || questionData.gridSize > 20) {
        return { success: false, error: "Invalid grid size (must be 4-20)" };
      }
  
      // Validate pairs array
      if (!questionData.pairs || !Array.isArray(questionData.pairs) || questionData.pairs.length === 0) {
        return { success: false, error: "Missing or invalid pairs array" };
      }
  
      // Validate each pair
      for (let i = 0; i < questionData.pairs.length; i++) {
        const pair = questionData.pairs[i];
        
        if (!pair.id || !pair.start || !pair.end || !pair.color || !pair.label) {
          return { 
            success: false, 
            error: `Pair ${i + 1} missing required fields (id, start, end, color, label)` 
          };
        }
  
        // Validate coordinates are within grid bounds
        const { start, end } = pair;
        if (start.x < 0 || start.x >= questionData.gridSize || 
            start.y < 0 || start.y >= questionData.gridSize ||
            end.x < 0 || end.x >= questionData.gridSize || 
            end.y < 0 || end.y >= questionData.gridSize) {
          return { 
            success: false, 
            error: `Pair ${i + 1} coordinates out of bounds` 
          };
        }
  
        // Ensure start and end are different
        if (start.x === end.x && start.y === end.y) {
          return { 
            success: false, 
            error: `Pair ${i + 1} start and end positions are identical` 
          };
        }
      }
  
      // Validate solution paths
      if (!answerData.solution || typeof answerData.solution !== 'object') {
        return { success: false, error: "Missing or invalid solution object" };
      }
  
      // Check that each pair has a solution path
      for (const pair of questionData.pairs) {
        if (!answerData.solution[pair.id]) {
          return { 
            success: false, 
            error: `Missing solution path for pair ${pair.id}` 
          };
        }
  
        const path = answerData.solution[pair.id];
        if (!Array.isArray(path) || path.length < 2) {
          return { 
            success: false, 
            error: `Invalid solution path for pair ${pair.id}` 
          };
        }
  
        // Validate path starts and ends at correct positions
        const firstPoint = path[0];
        const lastPoint = path[path.length - 1];
        
        if ((firstPoint.x !== pair.start.x || firstPoint.y !== pair.start.y) &&
            (firstPoint.x !== pair.end.x || firstPoint.y !== pair.end.y)) {
          return { 
            success: false, 
            error: `Solution path for pair ${pair.id} doesn't start at correct position` 
          };
        }
  
        if ((lastPoint.x !== pair.start.x || lastPoint.y !== pair.start.y) &&
            (lastPoint.x !== pair.end.x || lastPoint.y !== pair.end.y)) {
          return { 
            success: false, 
            error: `Solution path for pair ${pair.id} doesn't end at correct position` 
          };
        }
  
        // Validate path connectivity (each step must be adjacent)
        for (let i = 1; i < path.length; i++) {
          const prev = path[i - 1];
          const curr = path[i];
          
          const dx = Math.abs(curr.x - prev.x);
          const dy = Math.abs(curr.y - prev.y);
          
          if ((dx === 1 && dy === 0) || (dx === 0 && dy === 1)) {
            // Valid adjacent move
            continue;
          } else {
            return { 
              success: false, 
              error: `Solution path for pair ${pair.id} has non-adjacent step at index ${i}` 
            };
          }
        }
  
        // Validate all path points are within grid bounds
        for (let i = 0; i < path.length; i++) {
          const point = path[i];
          if (point.x < 0 || point.x >= questionData.gridSize || 
              point.y < 0 || point.y >= questionData.gridSize) {
            return { 
              success: false, 
              error: `Solution path for pair ${pair.id} goes out of bounds at index ${i}` 
            };
          }
        }
      }
  
      // Check for path overlaps (paths should not cross except at endpoints)
      const occupiedCells = new Map();
      
      for (const pair of questionData.pairs) {
        const path = answerData.solution[pair.id];
        
        for (let i = 0; i < path.length; i++) {
          const point = path[i];
          const cellKey = `${point.x},${point.y}`;
          
          if (occupiedCells.has(cellKey)) {
            const existingPair = occupiedCells.get(cellKey);
            // Allow overlap only at endpoints
            const isEndpoint = (i === 0 || i === path.length - 1);
            const isExistingEndpoint = existingPair.isEndpoint;
            
            if (!isEndpoint || !isExistingEndpoint) {
              return { 
                success: false, 
                error: `Path overlap detected at (${point.x}, ${point.y}) between pairs ${pair.id} and ${existingPair.pairId}` 
              };
            }
          } else {
            occupiedCells.set(cellKey, {
              pairId: pair.id,
              isEndpoint: (i === 0 || i === path.length - 1)
            });
          }
        }
      }
  
      // Validate time limit
      if (!questionData.timeLimit || questionData.timeLimit < 60000) {
        return { success: false, error: "Invalid or missing time limit (minimum 60 seconds)" };
      }
  
      // Ensure hint exists
      if (!puzzle.hint) {
        puzzle.hint = `Connect ${questionData.pairs.length} pairs of colored dots without crossing paths`;
      }
  
      this.debugLog(`✅ Successfully processed Flow puzzle:`);
      this.debugLog(`🎯 Grid: ${questionData.gridSize}x${questionData.gridSize}`);
      this.debugLog(`🔗 Pairs: ${questionData.pairs.length}`);
      this.debugLog(`⏱️ Time limit: ${Math.round(questionData.timeLimit / 1000)}s`);
      this.debugLog(`📊 Total path cells: ${Object.values(answerData.solution).reduce((sum, path) => sum + path.length, 0)}`);
      
      return { success: true, puzzle };
  
    } catch (error) {
      this.debugLog(`❌ Flow puzzle processing error: ${error.message}`, 'error');
      return { success: false, error: `Flow puzzle processing error: ${error.message}` };
    }
  }

  // EXISTING PROCESSING METHODS CONTINUE...
  processLetterSet(puzzle) {
    try {
      this.debugLog(`🔄 Processing letter set puzzle...`);
      
      let questionData;
      if (typeof puzzle.question === 'string') {
        questionData = JSON.parse(puzzle.question);
      } else {
        questionData = puzzle.question;
      }
      
      if (!questionData.letterSet || !questionData.letters || !questionData.allWords) {
        return { success: false, error: "Missing required fields: letterSet, letters, or allWords" };
      }

      if (!Array.isArray(questionData.letters) || !Array.isArray(questionData.allWords)) {
        return { success: false, error: "Invalid letters or allWords format" };
      }

      const letterSet = questionData.letterSet;
      if (typeof letterSet !== 'string' || letterSet.length < 4 || letterSet.length > 8) {
        return { success: false, error: "Invalid letter set format" };
      }

      const expectedLetters = letterSet.split('');
      if (questionData.letters.length !== expectedLetters.length) {
        return { 
          success: false, 
          error: `Letters array length ${questionData.letters.length} doesn't match letter set length ${expectedLetters.length}` 
        };
      }

      for (let i = 0; i < questionData.allWords.length; i++) {
        const wordData = questionData.allWords[i];
        
        if (!wordData.word || !wordData.length || !wordData.points) {
          return { 
            success: false, 
            error: `Word ${i + 1} missing required fields (word, length, points)` 
          };
        }

        if (!this.canFormWordFromLetterSet(wordData.word, letterSet)) {
          return { 
            success: false, 
            error: `Word "${wordData.word}" cannot be formed from letter set "${letterSet}"` 
          };
        }
      }

      if (!questionData.scoring || !questionData.targets) {
        return { success: false, error: "Missing scoring system or targets" };
      }

      if (!questionData.timeLimit || questionData.timeLimit < 60000) {
        return { success: false, error: "Invalid or missing time limit (minimum 60 seconds)" };
      }

      puzzle.question = JSON.stringify(questionData);
      
      const answerData = {
        allWords: questionData.allWords.map(w => w.word),
        keyWord: questionData.keyWord,
        totalWords: questionData.allWords.length,
        targets: questionData.targets
      };
      puzzle.answer = JSON.stringify(answerData);
      
      if (!puzzle.hint) {
        puzzle.hint = `Form words using the letters ${letterSet.toUpperCase()} - ${questionData.allWords.length} possible words!`;
      }

      this.debugLog(`✅ Successfully processed letter set puzzle:`);
      this.debugLog(`🔤 Letter set: ${letterSet}`);
      this.debugLog(`📝 Words: ${questionData.allWords.length}`);
      this.debugLog(`🎯 Key word: ${questionData.keyWord || 'none'}`);
      this.debugLog(`⏱️ Time limit: ${Math.round(questionData.timeLimit / 1000)}s`);
      
      return { success: true, puzzle };

    } catch (error) {
      this.debugLog(`❌ Letter set processing error: ${error.message}`, 'error');
      return { success: false, error: `Letter set processing error: ${error.message}` };
    }
  }

  /**
 * Process synonyms puzzle - validates synonym word sets and formats for storage
 */
processSynonyms(puzzle) {
  try {
    this.debugLog(`Processing synonyms puzzle...`);
    
    // Handle different possible input formats
    let wordSets;
    let categories;
    let setCount;
    
    if (puzzle.wordSets) {
      wordSets = puzzle.wordSets;
      categories = puzzle.categories || [];
      setCount = puzzle.setCount || wordSets.length;
    } else if (puzzle.synonymSets) {
      wordSets = puzzle.synonymSets;
      categories = puzzle.categories || [];
      setCount = puzzle.setCount || wordSets.length;
    } else if (puzzle.sets) {
      wordSets = puzzle.sets;
      categories = puzzle.categories || [];
      setCount = puzzle.setCount || wordSets.length;
    } else {
      this.debugLog(`Missing synonym word sets in puzzle data`, 'error');
      return { success: false, error: "Missing synonym word sets in puzzle data" };
    }

    // Validate word sets structure
    if (!Array.isArray(wordSets) || wordSets.length === 0) {
      return { success: false, error: "Invalid or empty synonym word sets array" };
    }

    // Validate each word set
    for (let i = 0; i < wordSets.length; i++) {
      const wordSet = wordSets[i];
      
      if (!Array.isArray(wordSet)) {
        return { 
          success: false, 
          error: `Word set ${i + 1} must be an array` 
        };
      }

      // Each set should have exactly 4 words for synonyms puzzle
      if (wordSet.length !== 4) {
        return { 
          success: false, 
          error: `Word set ${i + 1} must contain exactly 4 synonyms, got ${wordSet.length}` 
        };
      }

      // Validate each word in the set
      for (let j = 0; j < wordSet.length; j++) {
        const word = wordSet[j];
        
        if (!word || typeof word !== 'string') {
          return { 
            success: false, 
            error: `Word set ${i + 1}, word ${j + 1} is invalid: ${word}` 
          };
        }

        // Words should be reasonable length (2-15 characters)
        if (word.length < 2 || word.length > 15) {
          return { 
            success: false, 
            error: `Word set ${i + 1}, word "${word}" has invalid length: ${word.length}` 
          };
        }

        // Words should not contain numbers or special characters (basic validation)
        const validWordRegex = /^[a-zA-Z\s'-]+$/;
        if (!validWordRegex.test(word)) {
          return { 
            success: false, 
            error: `Word set ${i + 1}, word "${word}" contains invalid characters` 
          };
        }
      }

      // Check for duplicate words within the same set
      const setWords = wordSet.map(w => w.toLowerCase().trim());
      const uniqueWords = new Set(setWords);
      if (uniqueWords.size !== wordSet.length) {
        return { 
          success: false, 
          error: `Word set ${i + 1} contains duplicate words` 
        };
      }
    }

    // Check for word reuse across different sets
    const allWords = new Set();
    for (const wordSet of wordSets) {
      for (const word of wordSet) {
        const normalizedWord = word.toLowerCase().trim();
        if (allWords.has(normalizedWord)) {
          return { 
            success: false, 
            error: `Word "${word}" appears in multiple sets` 
          };
        }
        allWords.add(normalizedWord);
      }
    }

    // Validate categories if provided
    if (categories.length > 0 && categories.length !== wordSets.length) {
      return { 
        success: false, 
        error: `Categories count (${categories.length}) doesn't match word sets count (${wordSets.length})` 
      };
    }

    // Validate setCount
    if (setCount !== wordSets.length) {
      return { 
        success: false, 
        error: `Set count (${setCount}) doesn't match actual word sets length (${wordSets.length})` 
      };
    }

    // Format the question data
    const questionData = {
      wordSets: wordSets,
      setCount: setCount,
      categories: categories.length > 0 ? categories : wordSets.map((_, i) => `Group ${i + 1}`),
      difficulty: puzzle.difficulty,
      hint: puzzle.hint || `Group ${setCount} sets of synonyms. Categories: ${categories.length > 0 ? categories.join(', ') : 'Various'}`,
      instruction: `Find ${setCount} groups of 4 synonyms each`,
      source: puzzle.source || 'ai_generator'
    };

    // Format the answer data
    const answerData = {
      wordSets: wordSets,
      categories: questionData.categories,
      setCount: setCount,
      instruction: "Group words with similar meanings",
      type: "synonym_grouping",
      totalWords: wordSets.length * 4
    };

    puzzle.question = JSON.stringify(questionData);
    puzzle.answer = JSON.stringify(answerData);

    // Generate hint if not provided
    if (!puzzle.hint) {
      const categoryText = categories.length > 0 ? categories.join(', ') : 'Various';
      puzzle.hint = `Group ${setCount} sets of synonyms. Categories: ${categoryText}`;
    }

    // Add metadata
    if (!puzzle.metadata) {
      puzzle.metadata = {};
    }

    puzzle.metadata = {
      ...puzzle.metadata,
      setCount: setCount,
      totalWords: wordSets.length * 4,
      categories: questionData.categories,
      instruction: "Find groups of words with similar meanings",
      source: puzzle.metadata?.source || 'ai_generator',
      generatedAt: puzzle.metadata?.generatedAt || new Date().toISOString(),
      puzzleType: 'synonym_grouping',
      validationChecks: {
        correctSetSize: true,
        noDuplicateWords: true,
        noWordReuse: true,
        validWordFormat: true,
        reasonableLength: true,
        categoryCount: categories.length > 0
      }
    };

    this.debugLog(`Successfully processed synonyms puzzle:`);
    this.debugLog(`Word sets: ${wordSets.length}`);
    this.debugLog(`Total words: ${wordSets.length * 4}`);
    this.debugLog(`Categories: ${questionData.categories.join(', ')}`);
    this.debugLog(`Hint: ${puzzle.hint}`);
    
    return { success: true, puzzle };

  } catch (error) {
    this.debugLog(`Synonyms processing error: ${error.message}`, 'error');
    return { success: false, error: `Synonyms processing error: ${error.message}` };
  }
}

  canFormWordFromLetterSet(word, letterSet) {
    const availableLetters = {};
    const normalizedLetterSet = letterSet.toLowerCase();
    
    for (const letter of normalizedLetterSet) {
      availableLetters[letter] = (availableLetters[letter] || 0) + 1;
    }
    
    const normalizedWord = word.toLowerCase();
    const neededLetters = {};
    
    for (const letter of normalizedWord) {
      neededLetters[letter] = (neededLetters[letter] || 0) + 1;
    }
    
    for (const [letter, count] of Object.entries(neededLetters)) {
      if (!availableLetters[letter] || availableLetters[letter] < count) {
        return false;
      }
    }
    
    return true;
  }

  processMathEstimation(puzzle) {
    if (!Array.isArray(puzzle.numbers) || puzzle.numbers.length === 0) {
      return { success: false, error: "Invalid numbers array" };
    }

    puzzle.numbers = puzzle.numbers.map(n => parseFloat(n)).filter(n => !isNaN(n));
    const calculatedSum = puzzle.numbers.reduce((a, b) => a + b, 0);
    puzzle.sum = Math.round(calculatedSum * 100) / 100;

    if (!puzzle.hint) {
      puzzle.hint = "Round each number to make estimation easier";
    }

    return { success: true, puzzle };
  }

  /**
   * Get words from database for crossword generation
   */
  async generateCrosswordWordsForTopic(topic, wordCount = 20, difficulty = 'easy') {
    console.log(`🎲 Getting ${wordCount} '${difficulty}' crossword words from Supabase`);

    try {
      const { supabase } = await import('../config/database.js');

      const { data: words, error } = await supabase.rpc('get_random_crossword_words', {
        difficulty,
        count: wordCount
      });

      if (error) {
        console.error(`❌ Supabase RPC error: ${error.message}`);
        return null;
      }

      if (!words || words.length < 8) {
        console.warn(`⚠️ Only ${words?.length || 0} words returned — too few to generate crossword`);
        return null;
      }

      const validWords = words
        .filter(w => w.word && w.word.length >= 3 && w.word.length <= 5)
        .map(w => ({
          word: w.word.toUpperCase(),
          hint: w.definition || `${w.word.length}-letter word starting with ${w.word.charAt(0).toUpperCase()}`
        }));

      console.log(`✅ Selected ${validWords.length} words for crossword`);
      return validWords;

    } catch (error) {
      console.error(`💥 Unexpected error fetching crossword words:`, error.message);
      return null;
    }
  }

  /**
   * Validate generation inputs - ENHANCED with IMAGE_MATCH
   */
  validateInputs(puzzleType, difficulty) {
    if (!puzzleType) {
      return { isValid: false, reason: 'Puzzle type is required' };
    }

    if (!difficulty) {
      return { isValid: false, reason: 'Difficulty is required' };
    }

    let normalizedPuzzleType = puzzleType.toLowerCase();
    const normalizedDifficulty = difficulty.toLowerCase();
    
    // Handle legacy/alternative puzzle type names
    const puzzleTypeAliases = {
      'snakewordsearch': 'wordsnake',
      'pairmemory': 'memorypreviouspair',
      'singlemamory': 'memoryprevioussingle',
      'singlememory': 'memoryprevioussingle',
      'matrixpath': 'memorymatrixpath',
      'sequencing': 'memorysequencing',
      'retention': 'memoryretention',
      'squares': 'memorysquares'
    };

    if (puzzleTypeAliases[normalizedPuzzleType]) {
      normalizedPuzzleType = puzzleTypeAliases[normalizedPuzzleType];
    }
    
    const validPuzzleTypes = [...new Set(Object.values(PUZZLE_TYPES).map(type => type.toLowerCase()))]; // Remove duplicates
    
    // Add any missing types that should be valid
    if (!validPuzzleTypes.includes('imagematch')) {
      validPuzzleTypes.push('imagematch');
    }
    
    const validDifficulties = Object.values(DIFFICULTY_LEVELS).map(level => level.toLowerCase());

    if (!validPuzzleTypes.includes(normalizedPuzzleType)) {
      return { 
        isValid: false, 
        reason: `Invalid puzzle type: ${puzzleType}. Valid types: ${validPuzzleTypes.join(', ')}` 
      };
    }

    if (!validDifficulties.includes(normalizedDifficulty)) {
      return { 
        isValid: false, 
        reason: `Invalid difficulty: ${difficulty}. Valid difficulties: ${Object.values(DIFFICULTY_LEVELS).join(', ')}` 
      };
    }

    return { isValid: true, normalizedPuzzleType, normalizedDifficulty };
  }

  /**
   * Generate puzzle using AI with difficulty-specific prompts
   */
  async generatePuzzleWithAI(puzzleType, difficulty) {
    try {
      this.debugLog(`Getting prompt for ${puzzleType}/${difficulty}...`);
      const prompt = generatePromptForPuzzle(puzzleType, difficulty);
      
      if (!prompt) {
        this.debugLog(`No prompt generated for ${puzzleType}/${difficulty}`, 'error');
        return null;
      }
      
      this.debugLog(`Calling AI model:...`);
      const aiResponse = await callAI(prompt, null, 2, {
        category: USAGE_CATEGORIES.PUZZLE_GENERATION,
        puzzleType: puzzleType,
        difficulty: difficulty
      });
      
      if (!aiResponse) {
        this.debugLog(`AI returned empty response`, 'error');
        return null;
      }
      
      if (aiResponse.startsWith("Error")) {
        this.debugLog(`AI Error: ${aiResponse}`, 'error');
        return null;
      }

      this.debugLog(`Parsing AI response...`);
      try {
        const puzzle = JSON.parse(aiResponse);
        this.debugLog(`✅ Successfully parsed AI response for ${puzzleType}/${difficulty}`, 'success');
        return puzzle;
      } catch (parseError) {
        this.debugLog(`JSON parsing failed: ${parseError.message}`, 'error');
        this.debugLog(`Raw AI response: ${aiResponse.substring(0, 200)}...`);
        return null;
      }

    } catch (error) {
      this.debugLog(`AI generation error: ${error.message}`, 'error');
      return null;
    }
  }

  setDebugMode(enabled) {
    this.debugMode = enabled;
    this.deduplicationService.setDebugMode(enabled);
    this.debugLog(`Debug mode ${enabled ? 'enabled' : 'disabled'}`);
  }

  /**
   * Batch generation for multiple puzzles - ENHANCED
   */
  async generateBatch(puzzleType, difficulty, count = 5, modelName = "gpt-3.5-turbo") {
    console.log(`🚀 Starting batch generation: ${count} ${difficulty} ${puzzleType} puzzles`);
    
    const results = {
      successCount: 0,
      failureCount: 0,
      duplicateCount: 0,
      errors: [],
      puzzleIds: []
    };

    let existingQuestions = null;
    try {
      existingQuestions = await this.deduplicationService.getExistingQuestions(puzzleType);
      this.debugLog(`Pre-loaded ${existingQuestions.length} existing questions for batch deduplication`);
    } catch (error) {
      this.debugLog(`Failed to pre-load existing questions: ${error.message}`, 'warning');
    }

    for (let i = 0; i < count; i++) {
      try {
        console.log(`🧩 Generating puzzle ${i + 1}/${count}...`);
        
        const result = await this.generatePuzzle(puzzleType, modelName, difficulty);
        
        if (result.success) {
          results.successCount++;
          results.puzzleIds.push(result.puzzleId);
          
          if (existingQuestions && result.puzzle?.question) {
            existingQuestions.push(result.puzzle.question);
          }
          
          console.log(`✅ Generated puzzle ${i + 1}/${count} successfully`);
        } else {
          results.failureCount++;
          
          if (result.message && result.message.toLowerCase().includes('duplicate')) {
            results.duplicateCount++;
          }
          
          results.errors.push(`Puzzle ${i + 1}: ${result.message}`);
          console.error(`❌ Failed to generate puzzle ${i + 1}/${count}: ${result.message}`);
        }

      } catch (error) {
        results.failureCount++;
        results.errors.push(`Puzzle ${i + 1}: ${error.message}`);
        console.error(`💥 Exception generating puzzle ${i + 1}/${count}:`, error);
      }

      if (i < count - 1) {
        await new Promise(resolve => setTimeout(resolve, 200));
      }
    }

    console.log(`🎯 Batch generation completed: ${results.successCount}/${count} successful, ${results.duplicateCount} duplicates`);
    return results;
  }

  processWordSearch(puzzle) {
    try {
      this.debugLog(`🔄 Processing word search puzzle...`);

      let questionData;
      if (typeof puzzle.question === 'string') {
        questionData = JSON.parse(puzzle.question);
      } else {
        questionData = puzzle.question;
      }

      // Handle double-nested format from legacy storage
      if (questionData.question && !questionData.matrix) {
        const innerData = typeof questionData.question === 'string'
          ? JSON.parse(questionData.question)
          : questionData.question;
        questionData = innerData;
      }

      if (!questionData.matrix || !questionData.words) {
        return { success: false, error: "Missing required fields: matrix or words" };
      }

      if (!Array.isArray(questionData.matrix) || !Array.isArray(questionData.words)) {
        return { success: false, error: "Invalid matrix or words format" };
      }

      const height = questionData.height || questionData.matrix.length;
      const width = questionData.width || (questionData.matrix[0]?.length || 0);

      if (questionData.matrix.length !== height ||
          !questionData.matrix.every(row => Array.isArray(row) && row.length === width)) {
        return {
          success: false,
          error: `Invalid grid dimensions: expected ${width}x${height}`
        };
      }

      if (questionData.words.length === 0) {
        return { success: false, error: "Word search has no words" };
      }

      for (const wordData of questionData.words) {
        if (!wordData.word || !wordData.hint) {
          return { success: false, error: `Word entry missing required fields (word, hint)` };
        }
      }

      return { success: true, puzzle };
    } catch (error) {
      return { success: false, error: `Word search processing failed: ${error.message}` };
    }
  }

  processSnakeWordSearch(puzzle) {
    try {
      this.debugLog(`🔄 Processing word snake puzzle...`);

      // Parse question data if it's a string
      let questionData;
      if (typeof puzzle.question === 'string') {
        questionData = JSON.parse(puzzle.question);
      } else {
        questionData = puzzle.question;
      }

      // Validate required fields
      if (!questionData.grid || !questionData.words || !questionData.gridSize) {
        return { success: false, error: "Missing required fields: grid, words, or gridSize" };
      }
  
      if (!Array.isArray(questionData.grid) || !Array.isArray(questionData.words)) {
        return { success: false, error: "Invalid grid or words format" };
      }
  
      // Validate grid dimensions
      const gridSize = questionData.gridSize;
      if (questionData.grid.length !== gridSize || 
          !questionData.grid.every(row => Array.isArray(row) && row.length === gridSize)) {
        return { 
          success: false, 
          error: `Invalid grid dimensions: expected ${gridSize}x${gridSize}` 
        };
      }
  
      // Validate each word and its path
      for (let i = 0; i < questionData.words.length; i++) {
        const wordData = questionData.words[i];
        
        if (!wordData.word || !wordData.path || !wordData.clue) {
          return { 
            success: false, 
            error: `Word ${i + 1} missing required fields (word, path, clue)` 
          };
        }
  
        if (!Array.isArray(wordData.path)) {
          return { 
            success: false, 
            error: `Word ${i + 1} path must be an array` 
          };
        }
  
        if (wordData.path.length !== wordData.word.length) {
          return { 
            success: false, 
            error: `Word ${i + 1} path length (${wordData.path.length}) doesn't match word length (${wordData.word.length})` 
          };
        }
  
        // Validate each path position
        for (let j = 0; j < wordData.path.length; j++) {
          const pos = wordData.path[j];
          
          if (!pos || typeof pos.row !== 'number' || typeof pos.col !== 'number') {
            return { 
              success: false, 
              error: `Word ${i + 1} invalid path position at index ${j}: ${JSON.stringify(pos)}` 
            };
          }
  
          if (pos.row < 0 || pos.row >= gridSize || pos.col < 0 || pos.col >= gridSize) {
            return { 
              success: false, 
              error: `Word ${i + 1} path position out of bounds: row ${pos.row}, col ${pos.col}` 
            };
          }
  
          // Verify the letter at this position matches the word
          const expectedLetter = wordData.word[j].toUpperCase();
          const gridLetter = questionData.grid[pos.row][pos.col].toUpperCase();
          
          if (gridLetter !== expectedLetter) {
            return { 
              success: false, 
              error: `Word ${i + 1} letter mismatch at position ${j}: expected "${expectedLetter}", found "${gridLetter}" at grid[${pos.row}][${pos.col}]` 
            };
          }
        }
  
        // Validate path connectivity (snake-like movement)
        for (let j = 1; j < wordData.path.length; j++) {
          const prev = wordData.path[j - 1];
          const curr = wordData.path[j];
          
          const rowDiff = Math.abs(curr.row - prev.row);
          const colDiff = Math.abs(curr.col - prev.col);
          
          // Allow only adjacent moves (horizontal, vertical, or diagonal)
          if (rowDiff > 1 || colDiff > 1 || (rowDiff === 0 && colDiff === 0)) {
            return { 
              success: false, 
              error: `Word ${i + 1} invalid path movement from position ${j-1} to ${j}: not adjacent` 
            };
          }
        }
  
        // Ensure word has a valid color
        if (!wordData.color || typeof wordData.color !== 'string') {
          wordData.color = this.getDefaultColor(i);
        }
  
        // Ensure found status is boolean
        wordData.found = Boolean(wordData.found);
      }
  
      // Validate instruction and hint
      if (!questionData.instruction) {
        questionData.instruction = `Find ${questionData.words.length} hidden words by tracing snake-like paths`;
      }
  
      // Update puzzle data
      puzzle.question = JSON.stringify(questionData);
      
      // Create answer data with words and their paths
      const answerData = {
        words: questionData.words.map(w => w.word.toUpperCase()),
        paths: questionData.words.map(w => w.path),
        totalWords: questionData.words.length,
        gridSize: gridSize
      };
      puzzle.answer = JSON.stringify(answerData);
      
      if (!puzzle.hint) {
        puzzle.hint = `Find ${questionData.words.length} hidden words: ${questionData.words.map(w => w.word).join(', ')}`;
      }
  
      this.debugLog(`✅ Successfully processed word snake puzzle:`);
      this.debugLog(`🔤 Words: ${questionData.words.length}`);
      this.debugLog(`📐 Grid: ${gridSize}x${gridSize}`);
      this.debugLog(`🎯 All paths validated`);
      
      return { success: true, puzzle };
  
    } catch (error) {
      this.debugLog(`❌ Word snake processing error: ${error.message}`, 'error');
      return { success: false, error: `Word snake processing error: ${error.message}` };
    }
  }
  
  // Helper method for default colors
  getDefaultColor(index) {
    const colors = [
      '#E74C3C', '#3498DB', '#2ECC71', '#F39C12', 
      '#9B59B6', '#1ABC9C', '#E67E22', '#34495E',
      '#F1C40F', '#E91E63', '#00BCD4', '#4CAF50'
    ];
    return colors[index % colors.length];
  }
}

