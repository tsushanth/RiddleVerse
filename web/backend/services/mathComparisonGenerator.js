// services/mathComparisonGenerator.js

/**
 * Mathematical Comparison Puzzle Generator
 * Generates sequences of comparison puzzles with progressive difficulty
 */

export class MathComparisonGenerator {
    constructor() {
      this.debugMode = false;
    }
  
    setDebugMode(enabled) {
      this.debugMode = enabled;
    }
  
    debugLog(message, type = 'info') {
      if (this.debugMode) {
        const timestamp = new Date().toISOString();
        const emoji = type === 'error' ? '❌' : type === 'success' ? '✅' : type === 'warning' ? '⚠️' : '🔄';
        console.log(`${emoji} [${timestamp}] MATH_COMPARISON: ${message}`);
      }
    }
  
    /**
     * Generate a complete mathematical comparison puzzle
     */
    async generateMathComparisonPuzzle(difficulty = 'easy') {
      this.debugLog(`🎯 Generating ${difficulty} mathematical comparison puzzle...`);
  
      try {
        const difficultyConfig = this.getDifficultyConfig(difficulty);
        const puzzleSequence = this.generatePuzzleSequence(difficultyConfig);
        
        const questionData = {
          sequence: puzzleSequence,
          totalPairs: puzzleSequence.length,
          difficulty: difficulty,
          timeLimit: difficultyConfig.timeLimit,
          scoring: difficultyConfig.scoring,
          instructions: "Compare the values and select which is greater, or select EQUAL if they are the same.",
          metadata: {
            generatedAt: new Date().toISOString(),
            difficulty: difficulty,
            sequenceLength: puzzleSequence.length,
            operationTypes: this.getOperationTypes(puzzleSequence)
          }
        };
  
        const answerData = {
          correctAnswers: puzzleSequence.map(pair => pair.correctAnswer),
          scoring: difficultyConfig.scoring,
          maxScore: puzzleSequence.length * difficultyConfig.scoring.pointsPerCorrect
        };
  
        const result = {
          success: true,
          puzzleData: {
            question: JSON.stringify(questionData),
            answer: JSON.stringify(answerData),
            hint: `Compare ${puzzleSequence.length} mathematical expressions and choose the greater value or EQUAL`,
            difficulty: difficulty,
            metadata: {
              totalPairs: puzzleSequence.length,
              generatedAt: questionData.metadata.generatedAt,
              puzzleType: 'mathematical_comparison'
            }
          }
        };
  
        this.debugLog(`✅ Generated ${puzzleSequence.length} comparison pairs for ${difficulty} difficulty`, 'success');
        return result;
  
      } catch (error) {
        this.debugLog(`❌ Generation failed: ${error.message}`, 'error');
        return {
          success: false,
          message: `Math comparison generation failed: ${error.message}`
        };
      }
    }
  
    /**
     * Get difficulty configuration
     */
    getDifficultyConfig(difficulty) {
      const configs = {
        easy: {
          sequenceLength: 8,
          numberRange: [1, 20],
          operations: ['addition', 'subtraction', 'simple_multiplication'],
          allowDecimals: false,
          allowNegatives: false,
          timeLimit: 60000, // 60 seconds
          scoring: {
            pointsPerCorrect: 100,
            timeBonus: true,
            streakMultiplier: 1.1
          }
        },
        medium: {
          sequenceLength: 12,
          numberRange: [1, 50],
          operations: ['addition', 'subtraction', 'multiplication', 'division', 'mixed'],
          allowDecimals: true,
          allowNegatives: false,
          timeLimit: 90000, // 90 seconds
          scoring: {
            pointsPerCorrect: 150,
            timeBonus: true,
            streakMultiplier: 1.2
          }
        },
        hard: {
          sequenceLength: 15,
          numberRange: [1, 100],
          operations: ['multiplication', 'division', 'mixed', 'powers', 'fractions'],
          allowDecimals: true,
          allowNegatives: true,
          timeLimit: 120000, // 120 seconds
          scoring: {
            pointsPerCorrect: 200,
            timeBonus: true,
            streakMultiplier: 1.3
          }
        }
      };
  
      return configs[difficulty.toLowerCase()] || configs.medium;
    }
  
    /**
     * Generate a sequence of comparison pairs with progressive difficulty
     */
    generatePuzzleSequence(config) {
      const sequence = [];
      const { sequenceLength, operations, numberRange, allowDecimals, allowNegatives } = config;
  
      // Progressive difficulty within the sequence
      for (let i = 0; i < sequenceLength; i++) {
        const progressRatio = i / sequenceLength;
        const currentConfig = this.getProgressiveConfig(config, progressRatio);
        
        const pair = this.generateComparisonPair(currentConfig, i + 1);
        sequence.push(pair);
      }
  
      return sequence;
    }
  
    /**
     * Adjust difficulty progressively through the sequence
     */
    getProgressiveConfig(baseConfig, progressRatio) {
      const { numberRange, operations } = baseConfig;
      
      // Increase number range as we progress
      const rangeDiff = numberRange[1] - numberRange[0];
      const newMax = Math.round(numberRange[0] + rangeDiff * (0.3 + progressRatio * 0.7));
      
      return {
        ...baseConfig,
        numberRange: [numberRange[0], newMax],
        complexity: progressRatio
      };
    }
  
    /**
     * Generate a single comparison pair
     */
    generateComparisonPair(config, pairNumber) {
      const { operations, numberRange, allowDecimals, allowNegatives, complexity } = config;
      
      // Choose operation type based on difficulty progression
      const operationType = this.selectOperation(operations, complexity);
      
      // Generate the two values to compare
      const leftValue = this.generateValue(operationType, config, 'left');
      const rightValue = this.generateValue(operationType, config, 'right');
      
      // Calculate actual numeric values
      const leftNumeric = this.evaluateExpression(leftValue);
      const rightNumeric = this.evaluateExpression(rightValue);
      
      // Determine correct answer
      let correctAnswer;
      const tolerance = 0.001; // For floating point comparison
      
      if (Math.abs(leftNumeric - rightNumeric) < tolerance) {
        correctAnswer = 'equal';
      } else if (leftNumeric > rightNumeric) {
        correctAnswer = 'left';
      } else {
        correctAnswer = 'right';
      }
  
      return {
        pairNumber,
        leftValue,
        rightValue,
        leftNumeric,
        rightNumeric,
        correctAnswer,
        operationType,
        difficulty: this.calculatePairDifficulty(leftValue, rightValue, operationType)
      };
    }
  
    /**
     * Select operation type based on complexity
     */
    selectOperation(availableOps, complexity) {
      // Weight operations by complexity
      const operationWeights = {
        'addition': 0.1,
        'subtraction': 0.2,
        'simple_multiplication': 0.3,
        'multiplication': 0.5,
        'division': 0.7,
        'mixed': 0.8,
        'powers': 0.9,
        'fractions': 0.95
      };
  
      // Filter operations that are appropriate for current complexity
      const suitableOps = availableOps.filter(op => 
        operationWeights[op] <= (complexity + 0.3)
      );
  
      if (suitableOps.length === 0) {
        return availableOps[0]; // Fallback
      }
  
      return suitableOps[Math.floor(Math.random() * suitableOps.length)];
    }
  
    /**
     * Generate a mathematical value/expression
     */
    generateValue(operationType, config, side) {
      const { numberRange, allowDecimals } = config;
      
      switch (operationType) {
        case 'addition':
          return this.generateAddition(numberRange, allowDecimals);
        
        case 'subtraction':
          return this.generateSubtraction(numberRange, allowDecimals);
        
        case 'simple_multiplication':
          return this.generateSimpleMultiplication(numberRange);
        
        case 'multiplication':
          return this.generateMultiplication(numberRange, allowDecimals);
        
        case 'division':
          return this.generateDivision(numberRange, allowDecimals);
        
        case 'mixed':
          return this.generateMixedExpression(numberRange, allowDecimals);
        
        case 'powers':
          return this.generatePowerExpression(numberRange);
        
        case 'fractions':
          return this.generateFractionExpression(numberRange);
        
        default:
          return this.generateSimpleNumber(numberRange, allowDecimals);
      }
    }
  
    /**
     * Generate addition expression
     */
    generateAddition(range, allowDecimals) {
      const a = this.randomNumber(range, allowDecimals);
      const b = this.randomNumber(range, allowDecimals);
      return `${a} + ${b}`;
    }
  
    /**
     * Generate subtraction expression
     */
    generateSubtraction(range, allowDecimals) {
      const a = this.randomNumber(range, allowDecimals);
      const b = this.randomNumber([range[0], Math.min(a, range[1])], allowDecimals);
      return `${a} - ${b}`;
    }
  
    /**
     * Generate simple multiplication (single digits)
     */
    generateSimpleMultiplication(range) {
      const a = this.randomNumber([2, 9], false);
      const b = this.randomNumber([2, 9], false);
      return `${a} × ${b}`;
    }
  
    /**
     * Generate multiplication expression
     */
    generateMultiplication(range, allowDecimals) {
      // Keep numbers smaller for multiplication
      const maxVal = Math.min(range[1], 15);
      const a = this.randomNumber([range[0], maxVal], allowDecimals);
      const b = this.randomNumber([range[0], maxVal], allowDecimals);
      return `${a} × ${b}`;
    }
  
    /**
     * Generate division expression
     */
    generateDivision(range, allowDecimals) {
      const b = this.randomNumber([2, Math.min(range[1], 10)], false);
      const result = this.randomNumber([1, range[1]], allowDecimals);
      const a = b * result;
      return `${a} ÷ ${b}`;
    }
  
    /**
     * Generate mixed expression
     */
    generateMixedExpression(range, allowDecimals) {
      const operations = ['+', '-', '×'];
      const op1 = operations[Math.floor(Math.random() * operations.length)];
      const op2 = operations[Math.floor(Math.random() * operations.length)];
      
      const a = this.randomNumber([range[0], Math.min(range[1], 12)], allowDecimals);
      const b = this.randomNumber([range[0], Math.min(range[1], 12)], allowDecimals);
      const c = this.randomNumber([range[0], Math.min(range[1], 12)], allowDecimals);
      
      return `${a} ${op1} ${b} ${op2} ${c}`;
    }
  
    /**
     * Generate power expression
     */
    generatePowerExpression(range) {
      const base = this.randomNumber([2, Math.min(range[1], 8)], false);
      const exponent = this.randomNumber([2, 4], false);
      return `${base}^${exponent}`;
    }
  
    /**
     * Generate fraction expression
     */
    generateFractionExpression(range) {
      const numerator = this.randomNumber([1, range[1]], false);
      const denominator = this.randomNumber([2, 10], false);
      return `${numerator}/${denominator}`;
    }
  
    /**
     * Generate simple number
     */
    generateSimpleNumber(range, allowDecimals) {
      return this.randomNumber(range, allowDecimals).toString();
    }
  
    /**
     * Generate random number within range
     */
    randomNumber(range, allowDecimals) {
      const min = range[0];
      const max = range[1];
      
      if (allowDecimals && Math.random() < 0.3) { // 30% chance for decimals
        return Math.round((Math.random() * (max - min) + min) * 10) / 10;
      } else {
        return Math.floor(Math.random() * (max - min + 1)) + min;
      }
    }
  
    /**
     * Evaluate mathematical expression to numeric value
     */
    evaluateExpression(expression) {
      try {
        // Convert mathematical symbols to JavaScript operators
        let jsExpression = expression
          .replace(/×/g, '*')
          .replace(/÷/g, '/')
          .replace(/\^/g, '**')
          .replace(/(\d+)\/(\d+)/g, '($1/$2)'); // Handle fractions
        
        // Use Function constructor for safe evaluation (in production, use a proper math parser)
        return Function(`"use strict"; return (${jsExpression})`)();
      } catch (error) {
        this.debugLog(`Error evaluating expression "${expression}": ${error.message}`, 'error');
        return 0;
      }
    }
  
    /**
     * Calculate difficulty rating for a pair
     */
    calculatePairDifficulty(leftValue, rightValue, operationType) {
      let difficulty = 1;
      
      // Base difficulty by operation type
      const operationDifficulty = {
        'addition': 1,
        'subtraction': 1.2,
        'simple_multiplication': 1.5,
        'multiplication': 2,
        'division': 2.5,
        'mixed': 3,
        'powers': 3.5,
        'fractions': 4
      };
      
      difficulty *= operationDifficulty[operationType] || 1;
      
      // Increase difficulty for longer expressions
      const avgLength = (leftValue.length + rightValue.length) / 2;
      difficulty *= (1 + avgLength * 0.1);
      
      return Math.round(difficulty * 10) / 10; // Round to 1 decimal
    }
  
    /**
     * Get summary of operation types used
     */
    getOperationTypes(sequence) {
      const types = {};
      sequence.forEach(pair => {
        types[pair.operationType] = (types[pair.operationType] || 0) + 1;
      });
      return types;
    }
  
    /**
     * Format puzzle for storage (integrates with existing puzzle system)
     */
    formatForStorage(puzzleData, difficulty) {
      return {
        question: puzzleData.question,   // JSON string of comparison sequence
        answer: puzzleData.answer,       // JSON string of correct answers
        hint: puzzleData.hint,           // Instructions
        difficulty: difficulty,
        options: [], // No multiple choice options
        metadata: puzzleData.metadata    // Generation stats
      };
    }
  }