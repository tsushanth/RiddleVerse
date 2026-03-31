// services/memoryMatrixPathGenerator.js

/**
 * Memory Matrix Path Puzzle Generator
 * Generates binary matrix with obstacles where user must remember obstacle positions
 * and then draw path from top-left to bottom-right avoiding obstacles
 */

export class MemoryMatrixPathGenerator {
    constructor() {
      // Difficulty settings
      this.difficultyConfig = {
        easy: {
          matrixSize: 5,           // 5x5 grid
          obstacleCount: 6,        // Number of obstacles
          memoryTime: 3000,        // 3 seconds to memorize
          minPathLength: 8,        // Minimum valid path length
          maxObstacleClusterSize: 2 // Max connected obstacles
        },
        medium: {
          matrixSize: 6,           // 6x6 grid
          obstacleCount: 10,       // Number of obstacles
          memoryTime: 4000,        // 4 seconds to memorize
          minPathLength: 10,       // Minimum valid path length
          maxObstacleClusterSize: 3 // Max connected obstacles
        },
        hard: {
          matrixSize: 7,           // 7x7 grid
          obstacleCount: 15,       // Number of obstacles
          memoryTime: 5000,        // 5 seconds to memorize
          minPathLength: 12,       // Minimum valid path length
          maxObstacleClusterSize: 4 // Max connected obstacles
        }
      };
    }
  
    /**
     * Generate a memory matrix path puzzle
     */
    async generateMemoryMatrixPathPuzzle(difficulty = 'easy') {
      console.log(`🧠 Generating ${difficulty} memory matrix path puzzle`);
  
      try {
        // Validate inputs
        if (!this.difficultyConfig[difficulty]) {
          throw new Error(`Invalid difficulty: ${difficulty}`);
        }
  
        const config = this.difficultyConfig[difficulty];
        
        // Generate matrix with guaranteed path
        const matrix = this.generateMatrixWithPath(config);
        
        // Validate the matrix meets requirements
        if (!this.validateMatrix(matrix, config)) {
          throw new Error('Generated matrix does not meet requirements');
        }
  
        // Find all valid paths from start to end
        const validPaths = this.findAllPaths(matrix);
        const shortestPath = this.findShortestPath(validPaths);
  
        // Create puzzle data structure
        const puzzleData = {
          difficulty: difficulty,
          matrix: matrix,
          matrixSize: config.matrixSize,
          obstacleCount: this.countObstacles(matrix),
          startPosition: [0, 0], // Top-left
          endPosition: [config.matrixSize - 1, config.matrixSize - 1], // Bottom-right
          memoryTime: config.memoryTime,
          validPaths: validPaths,
          shortestPath: shortestPath,
          instructions: this.generateInstructions(config),
          scoring: {
            pathFoundBonus: 50,
            shortestPathBonus: 30,
            memoryAccuracyBonus: 20,
            maxPoints: 100
          },
          metadata: {
            generatedAt: new Date().toISOString(),
            version: '1.0',
            generationType: 'binary_matrix',
            totalCells: config.matrixSize * config.matrixSize,
            obstaclePercentage: Math.round((this.countObstacles(matrix) / (config.matrixSize * config.matrixSize)) * 100),
            shortestPathLength: shortestPath.length,
            totalValidPaths: validPaths.length
          }
        };
  
        console.log(`✅ Generated ${config.matrixSize}x${config.matrixSize} matrix with ${puzzleData.obstacleCount} obstacles`);
        console.log(`🛤️ Found ${validPaths.length} valid paths, shortest: ${shortestPath.length} steps`);
        
        // Format for storage in existing puzzle system
        const formattedPuzzle = this.formatForStorage(puzzleData, difficulty);
        
        return {
          success: true,
          puzzleData: formattedPuzzle
        };
  
      } catch (error) {
        console.error(`❌ Memory matrix path generation error: ${error.message}`);
        return {
          success: false,
          message: error.message
        };
      }
    }
  
    /**
     * Generate matrix with obstacles ensuring at least one path exists
     */
    generateMatrixWithPath(config) {
      let attempts = 0;
      const maxAttempts = 100;
      
      while (attempts < maxAttempts) {
        const matrix = this.createRandomMatrix(config);
        
        // Check if path exists from start to end
        if (this.hasValidPath(matrix)) {
          console.log(`✅ Valid matrix generated on attempt ${attempts + 1}`);
          return matrix;
        }
        
        attempts++;
      }
      
      // Fallback: create matrix with guaranteed path
      console.log(`⚠️ Fallback: Creating matrix with guaranteed path`);
      return this.createGuaranteedPathMatrix(config);
    }
  
    /**
     * Create random matrix with obstacles
     */
    createRandomMatrix(config) {
      const { matrixSize, obstacleCount } = config;
      
      // Initialize empty matrix (all 0s)
      const matrix = Array(matrixSize).fill().map(() => Array(matrixSize).fill(0));
      
      // Ensure start and end positions are clear
      matrix[0][0] = 0; // Start position
      matrix[matrixSize - 1][matrixSize - 1] = 0; // End position
      
      // Place obstacles randomly
      let obstaclesPlaced = 0;
      const availablePositions = [];
      
      // Create list of available positions (excluding start and end)
      for (let i = 0; i < matrixSize; i++) {
        for (let j = 0; j < matrixSize; j++) {
          if (!(i === 0 && j === 0) && !(i === matrixSize - 1 && j === matrixSize - 1)) {
            availablePositions.push([i, j]);
          }
        }
      }
      
      // Shuffle available positions
      this.shuffleArray(availablePositions);
      
      // Place obstacles
      for (let i = 0; i < Math.min(obstacleCount, availablePositions.length); i++) {
        const [row, col] = availablePositions[i];
        matrix[row][col] = 1;
        obstaclesPlaced++;
      }
      
      return matrix;
    }
  
    /**
     * Create matrix with guaranteed path (fallback method)
     */
    createGuaranteedPathMatrix(config) {
      const { matrixSize, obstacleCount } = config;
      
      // Initialize empty matrix
      const matrix = Array(matrixSize).fill().map(() => Array(matrixSize).fill(0));
      
      // Create a simple path from start to end
      const guaranteedPath = this.createSimplePath(matrixSize);
      
      // Mark path cells as free
      guaranteedPath.forEach(([row, col]) => {
        matrix[row][col] = 0;
      });
      
      // Place obstacles in remaining cells
      let obstaclesPlaced = 0;
      const pathCells = new Set(guaranteedPath.map(([r, c]) => `${r},${c}`));
      
      for (let i = 0; i < matrixSize && obstaclesPlaced < obstacleCount; i++) {
        for (let j = 0; j < matrixSize && obstaclesPlaced < obstacleCount; j++) {
          if (!pathCells.has(`${i},${j}`) && Math.random() < 0.6) {
            matrix[i][j] = 1;
            obstaclesPlaced++;
          }
        }
      }
      
      return matrix;
    }
  
    /**
     * Create simple path from top-left to bottom-right
     */
    createSimplePath(matrixSize) {
      const path = [];
      let row = 0, col = 0;
      
      // Add start position
      path.push([row, col]);
      
      // Move right until end column
      while (col < matrixSize - 1) {
        col++;
        path.push([row, col]);
      }
      
      // Move down until end row
      while (row < matrixSize - 1) {
        row++;
        path.push([row, col]);
      }
      
      return path;
    }
  
    /**
     * Check if valid path exists from start to end using BFS
     */
    hasValidPath(matrix) {
      const matrixSize = matrix.length;
      const start = [0, 0];
      const end = [matrixSize - 1, matrixSize - 1];
      
      const queue = [start];
      const visited = new Set();
      visited.add(`${start[0]},${start[1]}`);
      
      const directions = [[0, 1], [1, 0], [0, -1], [-1, 0]]; // right, down, left, up
      
      while (queue.length > 0) {
        const [row, col] = queue.shift();
        
        // Check if we reached the end
        if (row === end[0] && col === end[1]) {
          return true;
        }
        
        // Explore neighbors
        for (const [dr, dc] of directions) {
          const newRow = row + dr;
          const newCol = col + dc;
          const key = `${newRow},${newCol}`;
          
          if (
            newRow >= 0 && newRow < matrixSize &&
            newCol >= 0 && newCol < matrixSize &&
            matrix[newRow][newCol] === 0 && // Not an obstacle
            !visited.has(key)
          ) {
            visited.add(key);
            queue.push([newRow, newCol]);
          }
        }
      }
      
      return false;
    }
  
    /**
     * Find all valid paths from start to end
     */
    findAllPaths(matrix) {
      const matrixSize = matrix.length;
      const start = [0, 0];
      const end = [matrixSize - 1, matrixSize - 1];
      const allPaths = [];
      
      const directions = [[0, 1], [1, 0], [0, -1], [-1, 0]];
      
      const dfs = (row, col, path, visited) => {
        // If we reached the end, save this path
        if (row === end[0] && col === end[1]) {
          allPaths.push([...path, [row, col]]);
          return;
        }
        
        // Explore neighbors
        for (const [dr, dc] of directions) {
          const newRow = row + dr;
          const newCol = col + dc;
          const key = `${newRow},${newCol}`;
          
          if (
            newRow >= 0 && newRow < matrixSize &&
            newCol >= 0 && newCol < matrixSize &&
            matrix[newRow][newCol] === 0 && // Not an obstacle
            !visited.has(key)
          ) {
            visited.add(key);
            path.push([newRow, newCol]);
            dfs(newRow, newCol, path, visited);
            path.pop();
            visited.delete(key);
          }
        }
      };
      
      const visited = new Set();
      visited.add(`${start[0]},${start[1]}`);
      dfs(start[0], start[1], [start], visited);
      
      return allPaths.slice(0, 20); // Limit to first 20 paths for performance
    }
  
    /**
     * Find shortest path among valid paths
     */
    findShortestPath(validPaths) {
      if (validPaths.length === 0) return [];
      
      return validPaths.reduce((shortest, current) => 
        current.length < shortest.length ? current : shortest
      );
    }
  
    /**
     * Count obstacles in matrix
     */
    countObstacles(matrix) {
      return matrix.flat().filter(cell => cell === 1).length;
    }
  
    /**
     * Validate matrix meets requirements
     */
    validateMatrix(matrix, config) {
      const { matrixSize, obstacleCount } = config;
      
      // Check matrix size
      if (matrix.length !== matrixSize || matrix[0].length !== matrixSize) {
        console.error(`Invalid matrix size: ${matrix.length}x${matrix[0].length}, expected ${matrixSize}x${matrixSize}`);
        return false;
      }
      
      // Check start and end positions are clear
      if (matrix[0][0] !== 0 || matrix[matrixSize - 1][matrixSize - 1] !== 0) {
        console.error('Start or end position is blocked');
        return false;
      }
      
      // Check valid path exists
      if (!this.hasValidPath(matrix)) {
        console.error('No valid path from start to end');
        return false;
      }
      
      // Check obstacle count is reasonable
      const actualObstacles = this.countObstacles(matrix);
      if (actualObstacles === 0) {
        console.error('No obstacles in matrix');
        return false;
      }
      
      console.log(`✅ Matrix validation passed: ${actualObstacles} obstacles, valid path exists`);
      return true;
    }
  
    /**
     * Shuffle array using Fisher-Yates algorithm
     */
    shuffleArray(array) {
      for (let i = array.length - 1; i > 0; i--) {
        const j = Math.floor(Math.random() * (i + 1));
        [array[i], array[j]] = [array[j], array[i]];
      }
      return array;
    }
  
    /**
     * Generate instructions for the puzzle
     */
    generateInstructions(config) {
      return {
        title: "Memory Matrix Path",
        description: "Remember obstacle positions and draw a path from start to end!",
        steps: [
          "1. Study the matrix and memorize obstacle positions",
          "2. Matrix will disappear after the timer",
          "3. Draw a path from top-left (start) to bottom-right (end)",
          "4. Avoid all obstacles you remember",
          "5. Find the shortest path for maximum points!"
        ],
        tip: `You have ${Math.round(config.memoryTime / 1000)} seconds to memorize the ${config.matrixSize}x${config.matrixSize} grid`,
        controls: {
          drawing: "Tap and drag to draw your path",
          restart: "Double-tap to restart your path",
          submit: "Tap 'Submit' when path is complete"
        }
      };
    }
  
    /**
     * Format puzzle for storage in database
     */
    formatForStorage(puzzleData, difficulty) {
      return {
        question: JSON.stringify({
          matrix: puzzleData.matrix,
          matrixSize: puzzleData.matrixSize,
          startPosition: puzzleData.startPosition,
          endPosition: puzzleData.endPosition,
          memoryTime: puzzleData.memoryTime,
          instructions: puzzleData.instructions,
          difficulty: difficulty
        }),
        answer: JSON.stringify({
          validPaths: puzzleData.validPaths,
          shortestPath: puzzleData.shortestPath,
          obstaclePositions: this.getObstaclePositions(puzzleData.matrix),
          scoring: puzzleData.scoring
        }),
        hint: `Memorize obstacle positions in ${Math.round(puzzleData.memoryTime / 1000)} seconds, then draw path from top-left to bottom-right`,
        difficulty: difficulty,
        metadata: {
          ...puzzleData.metadata,
          matrixSize: puzzleData.matrixSize,
          obstacleCount: puzzleData.obstacleCount
        }
      };
    }
  
    /**
     * Get obstacle positions from matrix
     */
    getObstaclePositions(matrix) {
      const obstacles = [];
      for (let i = 0; i < matrix.length; i++) {
        for (let j = 0; j < matrix[i].length; j++) {
          if (matrix[i][j] === 1) {
            obstacles.push([i, j]);
          }
        }
      }
      return obstacles;
    }
  
    /**
     * Get difficulty configurations
     */
    getDifficultyConfigs() {
      return this.difficultyConfig;
    }
  
    /**
     * Get example matrix for testing
     */
    getExampleMatrix() {
      return {
        easy: [
          [0, 1, 0, 1, 0],
          [0, 0, 0, 1, 0],
          [1, 1, 0, 0, 0],
          [0, 0, 0, 1, 0],
          [0, 1, 0, 0, 0]
        ],
        shortestPath: [[0,0], [1,0], [1,1], [1,2], [2,2], [2,3], [2,4], [3,4], [4,4]],
        obstacleCount: 7
      };
    }
  }
  
  // Export convenience function
  export function generateMemoryMatrixPathPuzzle(difficulty = 'easy') {
    const generator = new MemoryMatrixPathGenerator();
    return generator.generateMemoryMatrixPathPuzzle(difficulty);
  }