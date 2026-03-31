// services/memorySquaresGenerator.js

/**
 * Memory Squares Puzzle Generator
 * Generates n×n matrices with m positions set to 1 and rest to 0
 */

export class MemorySquaresGenerator {
    constructor() {
      this.difficultySettings = {
        'Easy': { n: 4, m: 4, timeLimit: 8 },      // 4×4 grid with 4 ones
        'Medium': { n: 5, m: 8, timeLimit: 10 },   // 5×5 grid with 8 ones  
        'Hard': { n: 6, m: 12, timeLimit: 12 },    // 6×6 grid with 12 ones
        'Expert': { n: 7, m: 18, timeLimit: 15 }   // 7×7 grid with 18 ones
      };
    }
  
    /**
     * Generate a memory squares puzzle for given difficulty
     */
    generateMemorySquaresPuzzle(difficulty = 'Easy') {
      let normalizedDifficulty = difficulty.charAt(0).toUpperCase() + difficulty.slice(1).toLowerCase()
      try {
        const settings = this.difficultySettings[normalizedDifficulty];
        if (!settings) {
          throw new Error(`Invalid difficulty: ${difficulty}`);
        }
  
        const { n, m, timeLimit } = settings;
  
        // Validate that m doesn't exceed total positions
        if (m > n * n) {
          throw new Error(`Cannot place ${m} ones in ${n}×${n} matrix (max: ${n * n})`);
        }
  
        // Generate matrix with m random positions set to 1
        const matrix = this.generateMatrix(n, m);
        
        // Convert matrix to different formats for storage and display
        const matrixData = {
          matrix: matrix,
          size: n,
          onesCount: m,
          difficulty: difficulty,
          timeLimit: timeLimit,
          positions: this.getOnesPositions(matrix),
          compressed: this.compressMatrix(matrix)
        };
  
        const puzzleData = {
          question: JSON.stringify(matrixData),
          answer: JSON.stringify(matrixData.positions), // Store positions of 1s as answer
          hint: `Memorize the positions of ${m} squares in this ${n}×${n} grid. You have ${timeLimit} seconds!`,
          difficulty: difficulty,
          metadata: {
            gridSize: n,
            targetCount: m,
            timeLimit: timeLimit,
            totalPositions: n * n,
            difficultyRatio: (m / (n * n) * 100).toFixed(1) + '%'
          }
        };
  
        return {
          success: true,
          puzzleData: puzzleData,
          generationMethod: 'algorithmic',
          attempts: 1,
          message: `Generated ${difficulty} memory squares puzzle (${n}×${n} with ${m} targets)`
        };
  
      } catch (error) {
        return {
          success: false,
          message: `Memory squares generation failed: ${error.message}`,
          error: error.message
        };
      }
    }
  
    /**
     * Generate n×n matrix with exactly m positions set to 1
     */
    generateMatrix(n, m) {
      // Initialize matrix with all zeros
      const matrix = Array(n).fill().map(() => Array(n).fill(0));
      
      // Generate all possible positions
      const allPositions = [];
      for (let i = 0; i < n; i++) {
        for (let j = 0; j < n; j++) {
          allPositions.push([i, j]);
        }
      }
  
      // Randomly select m positions
      const selectedPositions = this.getRandomPositions(allPositions, m);
      
      // Set selected positions to 1
      selectedPositions.forEach(([row, col]) => {
        matrix[row][col] = 1;
      });
  
      return matrix;
    }
  
    /**
     * Randomly select m positions from available positions
     */
    getRandomPositions(positions, count) {
      const shuffled = [...positions];
      
      // Fisher-Yates shuffle
      for (let i = shuffled.length - 1; i > 0; i--) {
        const j = Math.floor(Math.random() * (i + 1));
        [shuffled[i], shuffled[j]] = [shuffled[j], shuffled[i]];
      }
      
      return shuffled.slice(0, count);
    }
  
    /**
     * Get positions of all 1s in the matrix
     */
    getOnesPositions(matrix) {
      const positions = [];
      for (let i = 0; i < matrix.length; i++) {
        for (let j = 0; j < matrix[i].length; j++) {
          if (matrix[i][j] === 1) {
            positions.push([i, j]);
          }
        }
      }
      return positions;
    }
  
    /**
     * Compress matrix to string for efficient storage
     */
    compressMatrix(matrix) {
      return matrix.map(row => row.join('')).join('');
    }
  
    /**
     * Decompress string back to matrix
     */
    decompressMatrix(compressed, size) {
      const matrix = [];
      for (let i = 0; i < size; i++) {
        const row = [];
        for (let j = 0; j < size; j++) {
          const index = i * size + j;
          row.push(parseInt(compressed[index]) || 0);
        }
        matrix.push(row);
      }
      return matrix;
    }
  
    /**
     * Validate a user's answer against the correct positions
     */
    validateAnswer(userPositions, correctPositions, tolerance = 0) {
      try {
        // Parse if strings
        const userPos = typeof userPositions === 'string' ? 
          JSON.parse(userPositions) : userPositions;
        const correctPos = typeof correctPositions === 'string' ? 
          JSON.parse(correctPositions) : correctPositions;
  
        // Convert to sets of strings for comparison
        const userSet = new Set(userPos.map(pos => `${pos[0]},${pos[1]}`));
        const correctSet = new Set(correctPos.map(pos => `${pos[0]},${pos[1]}`));
  
        // Calculate matches
        const matches = [...userSet].filter(pos => correctSet.has(pos)).length;
        const accuracy = (matches / correctSet.size) * 100;
  
        // Allow for tolerance (e.g., 80% accuracy)
        const threshold = 100 - tolerance;
        const isCorrect = accuracy >= threshold;
  
        return {
          isCorrect,
          accuracy: Math.round(accuracy),
          matches,
          total: correctSet.size,
          feedback: this.generateFeedback(accuracy, matches, correctSet.size)
        };
  
      } catch (error) {
        return {
          isCorrect: false,
          accuracy: 0,
          error: error.message
        };
      }
    }
  
    /**
     * Generate feedback based on performance
     */
    generateFeedback(accuracy, matches, total) {
      if (accuracy >= 100) {
        return "Perfect! You remembered all positions correctly! 🎉";
      } else if (accuracy >= 80) {
        return `Excellent! You got ${matches}/${total} positions correct! 👏`;
      } else if (accuracy >= 60) {
        return `Good job! You remembered ${matches}/${total} positions. Keep practicing! 👍`;
      } else if (accuracy >= 40) {
        return `Not bad! You got ${matches}/${total} correct. Try to focus more on the pattern! 🤔`;
      } else {
        return `Keep trying! You got ${matches}/${total} correct. Practice makes perfect! 💪`;
      }
    }
  
    /**
     * Get puzzle statistics for analysis
     */
    getPuzzleStats(difficulty) {
      const settings = this.difficultySettings[difficulty];
      if (!settings) return null;
  
      const { n, m, timeLimit } = settings;
      
      return {
        difficulty,
        gridSize: `${n}×${n}`,
        totalCells: n * n,
        targetCells: m,
        emptyCells: n * n - m,
        density: `${((m / (n * n)) * 100).toFixed(1)}%`,
        timeLimit: `${timeLimit} seconds`,
        estimatedDifficulty: this.calculateDifficultyScore(n, m, timeLimit)
      };
    }
  
    /**
     * Calculate difficulty score based on grid size, target count, and time
     */
    calculateDifficultyScore(n, m, timeLimit) {
      // Factors: grid complexity, target density, time pressure
      const gridComplexity = n * n;
      const density = m / (n * n);
      const timePressure = 1 / timeLimit;
      
      const score = (gridComplexity * density * timePressure * 1000);
      
      if (score < 5) return 'Very Easy';
      if (score < 15) return 'Easy';
      if (score < 35) return 'Medium';
      if (score < 60) return 'Hard';
      return 'Very Hard';
    }
  
    /**
     * Generate variations for testing
     */
    generateTestVariations() {
      const variations = [];
      
      Object.keys(this.difficultySettings).forEach(difficulty => {
        for (let i = 0; i < 3; i++) {
          const result = this.generateMemorySquaresPuzzle(difficulty);
          if (result.success) {
            variations.push({
              difficulty,
              variation: i + 1,
              puzzleData: result.puzzleData
            });
          }
        }
      });
      
      return variations;
    }
  }
  
  // Export singleton instance
  export const memorySquaresGenerator = new MemorySquaresGenerator();
  
  // Export helper functions
  export const generateMemorySquaresPuzzle = (difficulty) => {
    return memorySquaresGenerator.generateMemorySquaresPuzzle(difficulty);
  };
  
  export const validateMemorySquaresAnswer = (userAnswer, correctAnswer, tolerance = 20) => {
    return memorySquaresGenerator.validateAnswer(userAnswer, correctAnswer, tolerance);
  };