// services/pinballDeflectorGenerator.js

/**
 * Pinball Deflector Matrix Puzzle Generator
 * Generates matrix with deflectors that change ball direction by 90 degrees
 * User must predict where ball will end up after deflections
 */

export class PinballDeflectorGenerator {
    constructor() {
      // Deflector orientation constants
      this.DEFLECTOR_TYPES = {
        EMPTY: 0,
        SLASH: 1,      // / deflector (↑→ becomes →↓, →↓ becomes ↓←, ↓← becomes ←↑, ←↑ becomes ↑→)
        BACKSLASH: 2   // \ deflector (↑→ becomes ←↑, →↓ becomes ↑→, ↓← becomes →↓, ←↑ becomes ↓←)
      };
  
      // Direction constants
      this.DIRECTIONS = {
        UP: { dr: -1, dc: 0, name: 'UP' },
        DOWN: { dr: 1, dc: 0, name: 'DOWN' },
        LEFT: { dr: 0, dc: -1, name: 'LEFT' },
        RIGHT: { dr: 0, dc: 1, name: 'RIGHT' }
      };
  
      // Difficulty settings
      this.difficultyConfig = {
        easy: {
          matrixSize: 5,
          deflectorCount: 3,        // Number of deflectors
          minActiveDeflectors: 2,   // Minimum deflectors ball must hit
          memoryTime: 4000,         // 4 seconds to memorize deflectors
          maxBounces: 8             // Maximum bounces before stopping
        },
        medium: {
          matrixSize: 6,
          deflectorCount: 4,
          minActiveDeflectors: 3,
          memoryTime: 5000,         // 5 seconds to memorize
          maxBounces: 10
        },
        hard: {
          matrixSize: 7,
          deflectorCount: 5,
          minActiveDeflectors: 3,
          memoryTime: 6000,         // 6 seconds to memorize
          maxBounces: 12
        }
      };
    }
  
    /**
     * Generate a pinball deflector puzzle
     */
    async generatePinballDeflectorPuzzle(difficulty = 'easy') {
      difficulty = difficulty.toLowerCase();
      console.log(`🎯 Generating ${difficulty} pinball deflector puzzle`);
  
      try {
        if (!this.difficultyConfig[difficulty]) {
          throw new Error(`Invalid difficulty: ${difficulty}`);
        }
  
        const config = this.difficultyConfig[difficulty];
        
        // Generate puzzle with valid ball path
        const puzzleResult = this.generatePuzzleWithPath(config);
        
        if (!puzzleResult.success) {
          throw new Error(puzzleResult.error);
        }
  
        const { matrix, startPosition, startDirection, ballPath, endPosition, activeDeflectors } = puzzleResult;
  
        // Create puzzle data structure
        const puzzleData = {
          difficulty: difficulty,
          matrix: matrix,
          matrixSize: config.matrixSize,
          startPosition: startPosition,
          startDirection: startDirection,
          endPosition: endPosition,
          ballPath: ballPath,
          deflectorCount: this.countDeflectors(matrix),
          activeDeflectors: activeDeflectors,
          memoryTime: config.memoryTime,
          instructions: this.generateInstructions(config),
          scoring: {
            correctEndBonus: 70,
            pathAccuracyBonus: 30,
            maxPoints: 100
          },
          metadata: {
            generatedAt: new Date().toISOString(),
            version: '1.0',
            generationType: 'pinball_deflector',
            totalBounces: ballPath.filter(step => step.deflected).length,
            pathLength: ballPath.length,
            deflectorUtilization: Math.round((activeDeflectors.length / this.countDeflectors(matrix)) * 100)
          }
        };
  
        console.log(`✅ Generated ${config.matrixSize}x${config.matrixSize} matrix with ${puzzleData.deflectorCount} deflectors`);
        console.log(`🎯 Ball path: ${ballPath.length} steps, ${puzzleData.metadata.totalBounces} bounces`);
        console.log(`📍 Start: [${startPosition[0]},${startPosition[1]}] ${startDirection.name}, End: [${endPosition[0]},${endPosition[1]}]`);
        
        // Format for storage
        const formattedPuzzle = this.formatForStorage(puzzleData, difficulty);
        
        return {
          success: true,
          puzzleData: formattedPuzzle
        };
  
      } catch (error) {
        console.error(`❌ Pinball deflector generation error: ${error.message}`);
        return {
          success: false,
          message: error.message
        };
      }
    }
  
    /**
     * Generate puzzle ensuring ball hits minimum deflectors
     */
    generatePuzzleWithPath(config) {
      let attempts = 0;
      const maxAttempts = 50;
      
      while (attempts < maxAttempts) {
        attempts++;
        
        // Create matrix with deflectors
        const matrix = this.createEmptyMatrix(config.matrixSize);
        const startResult = this.selectRandomStartPosition(config.matrixSize);
        
        if (!startResult.success) {
          continue;
        }
  
        const { position: startPosition, direction: startDirection } = startResult;
        
        // Place deflectors strategically
        const deflectorResult = this.placeStrategicDeflectors(matrix, startPosition, startDirection, config);
        
        if (!deflectorResult.success) {
          continue;
        }
  
        // Simulate ball path
        const pathResult = this.simulateBallPath(matrix, startPosition, startDirection, config.maxBounces);
        
        if (!pathResult.success) {
          continue;
        }
  
        const { path: ballPath, endPosition, activeDeflectors } = pathResult;
        
        // Check if minimum deflectors were hit
        if (activeDeflectors.length >= config.minActiveDeflectors) {
          console.log(`✅ Valid puzzle generated on attempt ${attempts}`);
          return {
            success: true,
            matrix,
            startPosition,
            startDirection,
            ballPath,
            endPosition,
            activeDeflectors
          };
        }
      }
      
      return {
        success: false,
        error: `Could not generate valid puzzle after ${maxAttempts} attempts`
      };
    }
  
    /**
     * Create empty matrix
     */
    createEmptyMatrix(size) {
      return Array(size).fill().map(() => Array(size).fill(this.DEFLECTOR_TYPES.EMPTY));
    }
  
    /**
     * Select random start position on matrix edge
     */
    selectRandomStartPosition(matrixSize) {
      const edges = [
        // Top edge (going down)
        ...Array(matrixSize).fill().map((_, i) => ({
          position: [0, i],
          direction: this.DIRECTIONS.DOWN
        })),
        // Bottom edge (going up)
        ...Array(matrixSize).fill().map((_, i) => ({
          position: [matrixSize - 1, i],
          direction: this.DIRECTIONS.UP
        })),
        // Left edge (going right)
        ...Array(matrixSize).fill().map((_, i) => ({
          position: [i, 0],
          direction: this.DIRECTIONS.RIGHT
        })),
        // Right edge (going left)
        ...Array(matrixSize).fill().map((_, i) => ({
          position: [i, matrixSize - 1],
          direction: this.DIRECTIONS.LEFT
        }))
      ];
      
      const randomEdge = edges[Math.floor(Math.random() * edges.length)];
      return {
        success: true,
        position: randomEdge.position,
        direction: randomEdge.direction
      };
    }
  
    /**
     * Place deflectors strategically in ball's path
     */
    placeStrategicDeflectors(matrix, startPosition, startDirection, config) {
      const { matrixSize, deflectorCount, minActiveDeflectors } = config;
      let currentPos = [...startPosition];
      let currentDir = { ...startDirection };
      let deflatorsPlaced = 0;
      
      // Simulate initial path to place first deflector
      while (deflatorsPlaced < Math.min(deflectorCount, minActiveDeflectors)) {
        // Move in current direction to find placement spot
        const nextPos = [
          currentPos[0] + currentDir.dr,
          currentPos[1] + currentDir.dc
        ];
        
        // Check bounds
        if (nextPos[0] < 0 || nextPos[0] >= matrixSize || 
            nextPos[1] < 0 || nextPos[1] >= matrixSize) {
          break;
        }
        
        // Skip if too close to start (first 1-2 cells)
        const distanceFromStart = Math.abs(nextPos[0] - startPosition[0]) + Math.abs(nextPos[1] - startPosition[1]);
        if (distanceFromStart <= 1) {
          currentPos = nextPos;
          continue;
        }
        
        // Place deflector with some probability
        if (Math.random() < 0.4 && matrix[nextPos[0]][nextPos[1]] === this.DEFLECTOR_TYPES.EMPTY) {
          // Randomly choose deflector type
          const deflectorType = Math.random() < 0.5 ? this.DEFLECTOR_TYPES.SLASH : this.DEFLECTOR_TYPES.BACKSLASH;
          matrix[nextPos[0]][nextPos[1]] = deflectorType;
          deflatorsPlaced++;
          
          // Update direction based on deflector
          currentDir = this.getNewDirection(currentDir, deflectorType);
          console.log(`📍 Placed deflector ${deflectorType} at [${nextPos[0]},${nextPos[1]}], new direction: ${currentDir.name}`);
        }
        
        currentPos = nextPos;
      }
      
      // Place remaining deflectors randomly
      while (deflatorsPlaced < deflectorCount) {
        const row = Math.floor(Math.random() * matrixSize);
        const col = Math.floor(Math.random() * matrixSize);
        
        if (matrix[row][col] === this.DEFLECTOR_TYPES.EMPTY) {
          const deflectorType = Math.random() < 0.5 ? this.DEFLECTOR_TYPES.SLASH : this.DEFLECTOR_TYPES.BACKSLASH;
          matrix[row][col] = deflectorType;
          deflatorsPlaced++;
        }
      }
      
      return { success: true };
    }
  
    /**
     * Simulate ball path through matrix
     */
    simulateBallPath(matrix, startPosition, startDirection, maxBounces) {
      const matrixSize = matrix.length;
      const path = [];
      const activeDeflectors = [];
      
      let currentPos = [...startPosition];
      let currentDir = { ...startDirection };
      let bounces = 0;
      
      // Add start position
      path.push({
        position: [...currentPos],
        direction: { ...currentDir },
        deflected: false,
        deflectorType: null
      });
      
      while (bounces < maxBounces) {
        // Calculate next position
        const nextPos = [
          currentPos[0] + currentDir.dr,
          currentPos[1] + currentDir.dc
        ];
        
        // Check if out of bounds
        if (nextPos[0] < 0 || nextPos[0] >= matrixSize || 
            nextPos[1] < 0 || nextPos[1] >= matrixSize) {
          break;
        }
        
        currentPos = nextPos;
        const cellValue = matrix[currentPos[0]][currentPos[1]];
        
        // Check for deflector
        if (cellValue === this.DEFLECTOR_TYPES.SLASH || cellValue === this.DEFLECTOR_TYPES.BACKSLASH) {
          // Ball hits deflector
          const newDirection = this.getNewDirection(currentDir, cellValue);
          
          path.push({
            position: [...currentPos],
            direction: { ...currentDir },
            deflected: true,
            deflectorType: cellValue,
            newDirection: { ...newDirection }
          });
          
          activeDeflectors.push({
            position: [...currentPos],
            type: cellValue,
            oldDirection: { ...currentDir },
            newDirection: { ...newDirection }
          });
          
          currentDir = newDirection;
          bounces++;
        } else {
          // Empty cell
          path.push({
            position: [...currentPos],
            direction: { ...currentDir },
            deflected: false,
            deflectorType: null
          });
        }
      }
      
      const endPosition = path[path.length - 1].position;
      
      return {
        success: true,
        path,
        endPosition,
        activeDeflectors
      };
    }
  
    /**
     * Calculate new direction after hitting deflector
     */
    getNewDirection(currentDirection, deflectorType) {
      const { dr, dc } = currentDirection;
      
      if (deflectorType === this.DEFLECTOR_TYPES.SLASH) { // /
        // Slash deflector: (dr, dc) -> (-dc, -dr)
        return this.getDirectionFromComponents(-dc, -dr);
      } else if (deflectorType === this.DEFLECTOR_TYPES.BACKSLASH) { // \
        // Backslash deflector: (dr, dc) -> (dc, dr)
        return this.getDirectionFromComponents(dc, dr);
      }
      
      return currentDirection; // No change
    }
  
    /**
     * Get direction object from components
     */
    getDirectionFromComponents(dr, dc) {
      if (dr === -1 && dc === 0) return this.DIRECTIONS.UP;
      if (dr === 1 && dc === 0) return this.DIRECTIONS.DOWN;
      if (dr === 0 && dc === -1) return this.DIRECTIONS.LEFT;
      if (dr === 0 && dc === 1) return this.DIRECTIONS.RIGHT;
      
      // Fallback
      return { dr, dc, name: `CUSTOM_${dr}_${dc}` };
    }
  
    /**
     * Count deflectors in matrix
     */
    countDeflectors(matrix) {
      return matrix.flat().filter(cell => 
        cell === this.DEFLECTOR_TYPES.SLASH || cell === this.DEFLECTOR_TYPES.BACKSLASH
      ).length;
    }
  
    /**
     * Generate instructions
     */
    generateInstructions(config) {
      return {
        title: "Pinball Deflector Puzzle",
        description: "Predict where the ball will end up after hitting deflectors!",
        steps: [
          "1. Memorize the deflector positions and orientations",
          "2. Deflectors will disappear after the timer",
          "3. A ball will appear at the start position",
          "4. Predict where the ball will end up after all deflections",
          "5. Tap the predicted end position"
        ],
        tip: `You have ${Math.round(config.memoryTime / 1000)} seconds to memorize the ${config.deflectorCount} deflectors`,
        deflectorRules: {
          slash: "/ deflector rotates direction 90° clockwise",
          backslash: "\\ deflector rotates direction 90° counter-clockwise",
          bounce: "Ball continues until it hits edge or max bounces"
        }
      };
    }
  
    /**
     * Format puzzle for storage
     */
    formatForStorage(puzzleData, difficulty) {
      return {
        question: JSON.stringify({
          matrix: puzzleData.matrix,
          matrixSize: puzzleData.matrixSize,
          startPosition: puzzleData.startPosition,
          startDirection: puzzleData.startDirection,
          memoryTime: puzzleData.memoryTime,
          instructions: puzzleData.instructions,
          difficulty: difficulty
        }),
        answer: JSON.stringify({
          endPosition: puzzleData.endPosition,
          ballPath: puzzleData.ballPath,
          activeDeflectors: puzzleData.activeDeflectors,
          scoring: puzzleData.scoring
        }),
        hint: `Memorize deflector positions and predict ball path in ${Math.round(puzzleData.memoryTime / 1000)} seconds`,
        difficulty: difficulty,
        metadata: {
          ...puzzleData.metadata,
          matrixSize: puzzleData.matrixSize,
          deflectorCount: puzzleData.deflectorCount
        }
      };
    }
  
    /**
     * Get difficulty configurations
     */
    getDifficultyConfigs() {
      return this.difficultyConfig;
    }
  
    /**
     * Get example puzzle for testing
     */
    getExamplePuzzle() {
      return {
        matrix: [
          [0, 0, 1, 0, 0],  // / at [0,2]
          [0, 0, 0, 0, 0],
          [0, 2, 0, 0, 0],  // \ at [2,1]
          [0, 0, 0, 1, 0],  // / at [3,3]
          [0, 0, 0, 0, 0]
        ],
        startPosition: [2, 0],
        startDirection: this.DIRECTIONS.RIGHT,
        ballPath: [
          { position: [2, 0], direction: this.DIRECTIONS.RIGHT, deflected: false },
          { position: [2, 1], direction: this.DIRECTIONS.RIGHT, deflected: true, deflectorType: 2 },
          { position: [3, 1], direction: this.DIRECTIONS.DOWN, deflected: false },
          { position: [4, 1], direction: this.DIRECTIONS.DOWN, deflected: false }
        ],
        endPosition: [4, 1]
      };
    }
  }
  
  // Export convenience function
  export function generatePinballDeflectorPuzzle(difficulty = 'easy') {
    const generator = new PinballDeflectorGenerator();
    return generator.generatePinballDeflectorPuzzle(difficulty);
  }