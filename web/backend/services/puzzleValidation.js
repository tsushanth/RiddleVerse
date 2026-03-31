// services/puzzleValidation.js

import { PUZZLE_TYPES, DIFFICULTY_CRITERIA, PUZZLE_METADATA } from '../config/puzzleConfig.js';

/**
 * Comprehensive puzzle validation system that uses programmatic validation
 * instead of AI validation for deterministic puzzle types
 */

export class PuzzleValidator {
  /**
   * Main validation entry point
   */
  static async validatePuzzle(puzzle, puzzleType, difficulty) {
    console.log(`🔍 Validating ${puzzleType}/${difficulty} puzzle`);
    
    try {
      // Step 1: Basic structure validation
      const basicValidation = this.validateBasicStructure(puzzle, puzzleType);
      if (!basicValidation.isValid) {
        return basicValidation;
      }

      // Step 2: Difficulty-specific validation
      const difficultyValidation = this.validateDifficulty(puzzle, puzzleType, difficulty);
      if (!difficultyValidation.isValid) {
        return difficultyValidation;
      }

      // Step 3: Mathematical/logical validation
      const logicalValidation = this.validateLogic(puzzle, puzzleType);
      if (!logicalValidation.isValid) {
        return logicalValidation;
      }

      // Step 4: AI validation only if needed (for subjective puzzles)
      if (!PUZZLE_METADATA[puzzleType]?.skipAIValidation) {
        const aiValidation = await this.validateWithAI(puzzle, puzzleType);
        if (!aiValidation.isValid) {
          return aiValidation;
        }
      }

      console.log(`✅ Puzzle validation passed for ${puzzleType}/${difficulty}`);
      return { 
        isValid: true, 
        validationType: 'comprehensive',
        skippedAI: PUZZLE_METADATA[puzzleType]?.skipAIValidation || false
      };

    } catch (error) {
      console.error(`❌ Validation error for ${puzzleType}:`, error);
      return { 
        isValid: false, 
        reason: `Validation error: ${error.message}` 
      };
    }
  }

  /**
   * Validates basic puzzle structure
   */
  static validateBasicStructure(puzzle, puzzleType) {
    if (!puzzle || typeof puzzle !== 'object') {
      return { isValid: false, reason: 'Puzzle must be a valid object' };
    }

    // Check required fields based on puzzle type
    const requiredFields = this.getRequiredFields(puzzleType);
    for (const field of requiredFields) {
      if (!(field in puzzle)) {
        return { isValid: false, reason: `Missing required field: ${field}` };
      }
    }

    return { isValid: true };
  }

  /**
   * Validates difficulty-specific criteria
   */
  static validateDifficulty(puzzle, puzzleType, difficulty) {
    const criteria = DIFFICULTY_CRITERIA[puzzleType]?.[difficulty];
    
    if (!criteria) {
      console.warn(`No difficulty criteria found for ${puzzleType}/${difficulty}`);
      return { isValid: true, reason: 'No criteria defined' };
    }

    if (criteria.validateFn) {
      const isValid = criteria.validateFn(puzzle);
      if (!isValid) {
        return { 
          isValid: false, 
          reason: `Puzzle doesn't meet ${difficulty} difficulty criteria for ${puzzleType}` 
        };
      }
    }

    return { isValid: true };
  }

  /**
   * Validates mathematical/logical correctness
   */
  static validateLogic(puzzle, puzzleType) {
    switch (puzzleType) {
      case PUZZLE_TYPES.MATH_ESTIMATION:
        return this.validateMathEstimation(puzzle);
      
      case PUZZLE_TYPES.MATH_TIPPING:
        return this.validateMathTipping(puzzle);
      
      case PUZZLE_TYPES.PERCENTAGES:
        return this.validatePercentages(puzzle);
      
      case PUZZLE_TYPES.DIVISION:
        return this.validateDivision(puzzle);
      
      case PUZZLE_TYPES.AVERAGE:
        return this.validateAverage(puzzle);
      
      case PUZZLE_TYPES.SUBTRACTION:
        return this.validateSubtraction(puzzle);
      
      case PUZZLE_TYPES.PURCHASING:
        return this.validatePurchasing(puzzle);
      
      case PUZZLE_TYPES.DISCOUNTS:
        return this.validateDiscounts(puzzle);
      
      case PUZZLE_TYPES.ANTONYMS:
        return this.validateAntonyms(puzzle);
      
      case PUZZLE_TYPES.WORD_ASSOCIATION:
        return this.validateWordAssociation(puzzle);
      
      case PUZZLE_TYPES.CONNOTATION_WORDS:
        return this.validateConnotationWords(puzzle);

      case PUZZLE_TYPES.ANAGRAM:
        return this.validateAnagrams(puzzle);
      
      default:
        return { isValid: true, reason: 'No specific validation implemented' };
    }
  }

  /**
   * Math Estimation validation
   */
  static validateMathEstimation(puzzle) {
    if (!Array.isArray(puzzle.numbers) || puzzle.numbers.length === 0) {
      return { isValid: false, reason: 'Numbers array is required and must not be empty' };
    }

    if (typeof puzzle.sum !== 'number') {
      return { isValid: false, reason: 'Sum must be a number' };
    }

    // Verify sum calculation
    const calculatedSum = puzzle.numbers.reduce((a, b) => a + b, 0);
    const tolerance = 0.01;
    
    if (Math.abs(calculatedSum - puzzle.sum) > tolerance) {
      return { 
        isValid: false, 
        reason: `Sum mismatch: calculated ${calculatedSum}, provided ${puzzle.sum}` 
      };
    }

    // Validate number ranges
    if (puzzle.numbers.some(num => typeof num !== 'number' || num < 0 || num > 10000)) {
      return { isValid: false, reason: 'All numbers must be valid positive numbers under 10,000' };
    }

    return { isValid: true };
  }

  static validateAnagrams(puzzle) {
    if (!puzzle.question || !puzzle.answer || !puzzle.hint) {
      return { isValid: false, reason: 'Question, answer, and hint are required' };
    }
  
    if (puzzle.question === puzzle.answer) {
      return { isValid: false, reason: 'Scrambled word cannot be identical to original' };
    }
  
    return { isValid: true };
  }

  /**
   * Math Tipping validation
   */
  static validateMathTipping(puzzle) {
    const requiredFields = ['billAmount', 'tipPercentage', 'tipAmount', 'isCorrect'];
    for (const field of requiredFields) {
      if (!(field in puzzle) || typeof puzzle[field] !== 'number' && typeof puzzle[field] !== 'boolean') {
        if (field === 'isCorrect' && typeof puzzle[field] !== 'boolean') {
          return { isValid: false, reason: `${field} must be a boolean` };
        }
        if (field !== 'isCorrect' && typeof puzzle[field] !== 'number') {
          return { isValid: false, reason: `${field} must be a number` };
        }
      }
    }

    // Verify tip calculation
    const calculatedTip = Math.round(puzzle.billAmount * (puzzle.tipPercentage / 100) * 100) / 100;
    const actuallyCorrect = Math.abs(calculatedTip - puzzle.tipAmount) < 0.01;
    
    if (puzzle.isCorrect !== actuallyCorrect) {
      return { 
        isValid: false, 
        reason: `isCorrect field (${puzzle.isCorrect}) doesn't match calculation (${actuallyCorrect})` 
      };
    }

    return { isValid: true };
  }

  /**
   * Percentages validation
   */
  static validatePercentages(puzzle) {
    if (typeof puzzle.total !== 'number' || typeof puzzle.percentage !== 'number') {
      return { isValid: false, reason: 'Total and percentage must be numbers' };
    }

    if (puzzle.percentage < 0 || puzzle.percentage > 100) {
      return { isValid: false, reason: 'Percentage must be between 0 and 100' };
    }

    const calculatedAnswer = (puzzle.total * puzzle.percentage) / 100;
    if (Math.abs(calculatedAnswer - puzzle.answer) > 0.01) {
      return { 
        isValid: false, 
        reason: `Answer mismatch: calculated ${calculatedAnswer}, provided ${puzzle.answer}` 
      };
    }

    return { isValid: true };
  }

  /**
   * Division validation
   */
  static validateDivision(puzzle) {
    if (!Array.isArray(puzzle.problems) || puzzle.problems.length === 0) {
      return { isValid: false, reason: 'Problems array is required' };
    }

    for (const [dividend, divisor, quotient] of puzzle.problems) {
      if (typeof dividend !== 'number' || typeof divisor !== 'number' || typeof quotient !== 'number') {
        return { isValid: false, reason: 'All problem values must be numbers' };
      }

      if (divisor === 0) {
        return { isValid: false, reason: 'Division by zero detected' };
      }

      const calculatedQuotient = dividend / divisor;
      if (Math.abs(calculatedQuotient - quotient) > 0.01) {
        return { 
          isValid: false, 
          reason: `Division error: ${dividend} ÷ ${divisor} = ${quotient}, should be ${calculatedQuotient}` 
        };
      }

      // Ensure even division (no remainders for this puzzle type)
      if (dividend % divisor !== 0) {
        return { isValid: false, reason: 'All divisions must result in whole numbers (no remainders)' };
      }
    }

    return { isValid: true };
  }

  /**
   * Average validation
   */
  static validateAverage(puzzle) {
    if (!Array.isArray(puzzle.numbers) || puzzle.numbers.length === 0) {
      return { isValid: false, reason: 'Numbers array is required' };
    }

    if (typeof puzzle.average !== 'number') {
      return { isValid: false, reason: 'Average must be a number' };
    }

    const calculatedAverage = puzzle.numbers.reduce((a, b) => a + b, 0) / puzzle.numbers.length;
    const roundedAverage = Math.round(calculatedAverage);
    
    if (Math.abs(roundedAverage - puzzle.average) > 0.01) {
      return { 
        isValid: false, 
        reason: `Average mismatch: calculated ${roundedAverage}, provided ${puzzle.average}` 
      };
    }

    // For this puzzle type, average should be a whole number
    if (!Number.isInteger(puzzle.average)) {
      return { isValid: false, reason: 'Average must be a whole number for this puzzle type' };
    }

    return { isValid: true };
  }

  /**
   * Subtraction validation
   */
  static validateSubtraction(puzzle) {
    if (!Array.isArray(puzzle.problems) || puzzle.problems.length === 0) {
      return { isValid: false, reason: 'Problems array is required' };
    }

    for (const [minuend, subtrahend, difference] of puzzle.problems) {
      if (typeof minuend !== 'number' || typeof subtrahend !== 'number' || typeof difference !== 'number') {
        return { isValid: false, reason: 'All problem values must be numbers' };
      }

      const calculatedDifference = minuend - subtrahend;
      if (Math.abs(calculatedDifference - difference) > 0.01) {
        return { 
          isValid: false, 
          reason: `Subtraction error: ${minuend} - ${subtrahend} = ${difference}, should be ${calculatedDifference}` 
        };
      }

      if (minuend < subtrahend) {
        return { isValid: false, reason: 'Minuend must be greater than subtrahend' };
      }
    }

    return { isValid: true };
  }

  /**
   * Purchasing validation
   */
  static validatePurchasing(puzzle) {
    const requiredFields = ['payment', 'frequency', 'yearlyTotal'];
    for (const field of requiredFields) {
      if (!(field in puzzle)) {
        return { isValid: false, reason: `Missing required field: ${field}` };
      }
    }

    const frequencyMultipliers = {
      'weekly': 52,
      'biweekly': 26,
      'monthly': 12,
      'quarterly': 4,
      'yearly': 1
    };

    const multiplier = frequencyMultipliers[puzzle.frequency.toLowerCase()];
    if (!multiplier) {
      return { isValid: false, reason: `Invalid frequency: ${puzzle.frequency}` };
    }

    const calculatedYearly = Math.round(puzzle.payment * multiplier * 100) / 100;
    if (Math.abs(calculatedYearly - puzzle.yearlyTotal) > 0.01) {
      return { 
        isValid: false, 
        reason: `Yearly total mismatch: calculated ${calculatedYearly}, provided ${puzzle.yearlyTotal}` 
      };
    }

    return { isValid: true };
  }

  /**
   * Discounts validation
   */
  static validateDiscounts(puzzle) {
    if (!Array.isArray(puzzle.items) || puzzle.items.length < 2) {
      return { isValid: false, reason: 'At least 2 items are required' };
    }

    // Validate item structure
    for (const item of puzzle.items) {
      if (!item.name || !item.icon || typeof item.originalPrice !== 'number') {
        return { isValid: false, reason: 'Each item must have name, icon, and originalPrice' };
      }

      if (item.discountPercentage !== null && 
          (typeof item.discountPercentage !== 'number' || 
           item.discountPercentage < 0 || 
           item.discountPercentage > 100)) {
        return { isValid: false, reason: 'Discount percentage must be null or 0-100' };
      }
    }

    // Validate correct order if provided
    if (puzzle.correctOrder) {
      if (!Array.isArray(puzzle.correctOrder) || puzzle.correctOrder.length !== puzzle.items.length) {
        return { isValid: false, reason: 'Correct order array must match items length' };
      }

      // Calculate actual final prices and verify order
      const itemsWithPrices = puzzle.items.map((item, index) => ({
        index: index + 1,
        finalPrice: item.discountPercentage 
          ? item.originalPrice * (1 - item.discountPercentage / 100)
          : item.originalPrice
      }));

      const sortedByPrice = itemsWithPrices.sort((a, b) => a.finalPrice - b.finalPrice);
      const expectedOrder = sortedByPrice.map(item => item.index);

      if (JSON.stringify(puzzle.correctOrder) !== JSON.stringify(expectedOrder)) {
        return { 
          isValid: false, 
          reason: `Incorrect order: expected ${expectedOrder}, got ${puzzle.correctOrder}` 
        };
      }
    }

    return { isValid: true };
  }

  /**
   * Antonyms validation
   */
  static validateAntonyms(puzzle) {
    if (!Array.isArray(puzzle.pairs) || puzzle.pairs.length === 0) {
      return { isValid: false, reason: 'Pairs array is required' };
    }

    for (const pair of puzzle.pairs) {
      if (!Array.isArray(pair) || pair.length !== 2) {
        return { isValid: false, reason: 'Each pair must be an array of exactly 2 words' };
      }

      if (typeof pair[0] !== 'string' || typeof pair[1] !== 'string') {
        return { isValid: false, reason: 'Pair elements must be strings' };
      }

      if (pair[0].trim() === '' || pair[1].trim() === '') {
        return { isValid: false, reason: 'Pair elements cannot be empty' };
      }

      if (pair[0].toLowerCase() === pair[1].toLowerCase()) {
        return { isValid: false, reason: 'Antonym pairs cannot be the same word' };
      }
    }

    return { isValid: true };
  }

  /**
   * Word Association validation
   */
  static validateWordAssociation(puzzle) {
    if (!Array.isArray(puzzle.clueWords) || puzzle.clueWords.length !== 4) {
      return { isValid: false, reason: 'Exactly 4 clue words are required' };
    }

    if (!puzzle.targetWord || typeof puzzle.targetWord !== 'string') {
      return { isValid: false, reason: 'Target word is required and must be a string' };
    }

    // Ensure no clue word matches the target word
    const targetLower = puzzle.targetWord.toLowerCase();
    for (const clue of puzzle.clueWords) {
      if (typeof clue !== 'string') {
        return { isValid: false, reason: 'All clue words must be strings' };
      }

      if (clue.toLowerCase() === targetLower) {
        return { isValid: false, reason: 'Clue words cannot match the target word' };
      }
    }

    // Check for duplicate clue words
    const uniqueClues = new Set(puzzle.clueWords.map(w => w.toLowerCase()));
    if (uniqueClues.size !== puzzle.clueWords.length) {
      return { isValid: false, reason: 'Clue words must be unique' };
    }

    return { isValid: true };
  }

  /**
   * Connotation Words validation
   */
  static validateConnotationWords(puzzle) {
    if (!Array.isArray(puzzle.positiveWords) || !Array.isArray(puzzle.negativeWords)) {
      return { isValid: false, reason: 'Both positiveWords and negativeWords arrays are required' };
    }

    if (puzzle.positiveWords.length !== 4 || puzzle.negativeWords.length !== 4) {
      return { isValid: false, reason: 'Both word arrays must contain exactly 4 words' };
    }

    // Check for valid strings
    const allWords = [...puzzle.positiveWords, ...puzzle.negativeWords];
    for (const word of allWords) {
      if (typeof word !== 'string' || word.trim() === '') {
        return { isValid: false, reason: 'All words must be non-empty strings' };
      }
    }

    // Check for duplicates
    const uniqueWords = new Set(allWords.map(w => w.toLowerCase()));
    if (uniqueWords.size !== allWords.length) {
      return { isValid: false, reason: 'All words must be unique' };
    }

    return { isValid: true };
  }

  /**
   * AI validation fallback for subjective puzzles
   */
  static async validateWithAI(puzzle, puzzleType) {
    // This would be called only for puzzle types that still need AI validation
    // Implementation would depend on your AI calling function
    console.log(`🤖 AI validation needed for ${puzzleType}`);
    return { isValid: true, reason: 'AI validation not implemented yet' };
  }

  /**
   * Get required fields for each puzzle type
   */
  static getRequiredFields(puzzleType) {
    const fieldMap = {
      [PUZZLE_TYPES.MATH_ESTIMATION]: ['numbers', 'sum', 'difficulty'],
      [PUZZLE_TYPES.MATH_TIPPING]: ['billAmount', 'tipPercentage', 'tipAmount', 'isCorrect', 'difficulty'],
      [PUZZLE_TYPES.PERCENTAGES]: ['total', 'percentage', 'answer', 'difficulty'],
      [PUZZLE_TYPES.DIVISION]: ['problems', 'difficulty'],
      [PUZZLE_TYPES.AVERAGE]: ['numbers', 'average', 'difficulty'],
      [PUZZLE_TYPES.SUBTRACTION]: ['problems', 'difficulty'],
      [PUZZLE_TYPES.PURCHASING]: ['payment', 'frequency', 'yearlyTotal', 'difficulty'],
      [PUZZLE_TYPES.DISCOUNTS]: ['items', 'difficulty'],
      [PUZZLE_TYPES.ANTONYMS]: ['pairs', 'difficulty'],
      [PUZZLE_TYPES.WORD_ASSOCIATION]: ['clueWords', 'targetWord', 'difficulty'],
      [PUZZLE_TYPES.CONNOTATION_WORDS]: ['positiveWords', 'negativeWords', 'difficulty'],
    };

    return fieldMap[puzzleType] || ['difficulty'];
  }
}