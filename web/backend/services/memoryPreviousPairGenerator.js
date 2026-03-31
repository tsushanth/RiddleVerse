// services/memoryPreviousPairGenerator.js

/**
 * Memory Previous Pair Puzzle Generator
 * Generates number-based sequences that can be mapped to any theme
 */

export class MemoryPreviousPairGenerator {
  constructor() {
    // Difficulty settings - only numbers and sequence logic
    this.difficultyConfig = {
      easy: {
        sequenceLength: 10,
        objectsPerScreen: 2,
        maxObjectPool: 6  // Use numbers 1-6
      },
      medium: {
        sequenceLength: 16,
        objectsPerScreen: 3,
        maxObjectPool: 8  // Use numbers 1-8
      },
      hard: {
        sequenceLength: 20,
        objectsPerScreen: 4,
        maxObjectPool: 10 // Use numbers 1-10
      }
    };
  }

  /**
   * Generate a memory previous pair puzzle with just numbers
   */
  async generateMemoryPreviousPairPuzzle(difficulty = 'easy') {
    difficulty = difficulty.toLowerCase();
    console.log(`🧠 Generating ${difficulty} memory previous pair puzzle with numbers`);

    try {
      // Validate inputs
      if (!this.difficultyConfig[difficulty]) {
        throw new Error(`Invalid difficulty: ${difficulty}`);
      }

      const config = this.difficultyConfig[difficulty];

      // Generate sequence with linking numbers
      const sequence = this.generateLinkedNumberSequence(config);

      // Create puzzle data structure (theme-agnostic)
      const puzzleData = {
        difficulty: difficulty,
        sequence: sequence,
        totalScreens: sequence.length,
        objectsPerScreen: config.objectsPerScreen,
        maxObjectPool: config.maxObjectPool,
        instructions: this.generateInstructions(),
        scoring: {
          pointsPerCorrect: 10,
          timeBonus: true,
          maxPoints: (sequence.length - 1) * 10
        },
        metadata: {
          generatedAt: new Date().toISOString(),
          version: '1.0',
          generationType: 'number_based'
        }
      };

      console.log(`✅ Generated number sequence with ${sequence.length} screens using numbers 1-${config.maxObjectPool}`);
      
      // Format for storage in existing puzzle system
      const formattedPuzzle = this.formatForStorage(puzzleData, difficulty);
      
      return {
        success: true,
        puzzleData: formattedPuzzle
      };

    } catch (error) {
      console.error(`❌ Memory previous pair generation error: ${error.message}`);
      return {
        success: false,
        message: error.message
      };
    }
  }

  /**
   * Generate linked sequence using just numbers
   */
  generateLinkedNumberSequence(config) {
    const { sequenceLength, objectsPerScreen, maxObjectPool } = config;
    const availableNumbers = Array.from({length: maxObjectPool}, (_, i) => i + 1); // [1, 2, 3, ..., maxObjectPool]
    
    const sequence = [];
    
    for (let i = 0; i < sequenceLength; i++) {
      let screenNumbers = [];
      
      if (i === 0) {
        // First screen: random selection of numbers
        screenNumbers = this.selectRandomNumbers(availableNumbers, objectsPerScreen);
      } else {
        // Subsequent screens: must include one number from previous screen
        const previousScreen = sequence[i - 1];
        
        // Pick one random number from previous screen (this is the linking number)
        const linkingNumber = this.getRandomElement(previousScreen.numbers);
        screenNumbers.push(linkingNumber);
        
        // Fill remaining slots with new numbers (not in previous screen)
        const excludeNumbers = previousScreen.numbers;
        const newNumbers = this.selectRandomNumbers(
          availableNumbers.filter(num => !excludeNumbers.includes(num)),
          objectsPerScreen - 1
        );
        
        screenNumbers = [...screenNumbers, ...newNumbers];
        
        // Shuffle to randomize positions
        screenNumbers = this.shuffleArray(screenNumbers);
      }
      
      // Create screen data
      const screenData = {
        screenNumber: i + 1,
        numbers: screenNumbers.sort((a, b) => a - b), // Sort for consistency
        linkingNumber: i > 0 ? this.findLinkingNumber(sequence[i - 1].numbers, screenNumbers) : null,
        isFirstScreen: i === 0
      };
      
      sequence.push(screenData);
    }
    
    return sequence;
  }

  /**
   * Find which number links current screen to previous screen
   */
  findLinkingNumber(previousNumbers, currentNumbers) {
    for (const currentNum of currentNumbers) {
      if (previousNumbers.includes(currentNum)) {
        return currentNum;
      }
    }
    return null;
  }

  /**
   * Select random numbers from available pool
   */
  selectRandomNumbers(availableNumbers, count) {
    if (availableNumbers.length < count) {
      throw new Error(`Not enough numbers available. Need ${count}, have ${availableNumbers.length}`);
    }
    
    const shuffled = this.shuffleArray([...availableNumbers]);
    return shuffled.slice(0, count);
  }

  /**
   * Get random element from array
   */
  getRandomElement(array) {
    return array[Math.floor(Math.random() * array.length)];
  }

  /**
   * Shuffle array using Fisher-Yates algorithm
   */
  shuffleArray(array) {
    const shuffled = [...array];
    for (let i = shuffled.length - 1; i > 0; i--) {
      const j = Math.floor(Math.random() * (i + 1));
      [shuffled[i], shuffled[j]] = [shuffled[j], shuffled[i]];
    }
    return shuffled;
  }

  /**
   * Generate theme-agnostic instructions
   */
  generateInstructions() {
    return {
      title: "Memory Previous Pair",
      description: "Remember objects from the previous screen!",
      steps: [
        "1. Look at the objects on the first screen",
        "2. On each new screen, tap the object that appeared in the previous screen",
        "3. Continue until you complete the sequence",
        "4. Be quick for bonus points!"
      ],
      tip: "Focus on unique features to help remember each object"
    };
  }

  /**
   * Validate generated sequence
   */
  validateSequence(sequence) {
    console.log(`🔍 Validating number sequence with ${sequence.length} screens...`);
    
    for (let i = 1; i < sequence.length; i++) {
      const currentScreen = sequence[i];
      const previousScreen = sequence[i - 1];
      
      // Check if current screen has a linking number
      if (!currentScreen.linkingNumber) {
        console.error(`❌ Screen ${i + 1} missing linking number`);
        return false;
      }
      
      // Verify linking number exists in both screens
      const linkingInCurrent = currentScreen.numbers.includes(currentScreen.linkingNumber);
      const linkingInPrevious = previousScreen.numbers.includes(currentScreen.linkingNumber);
      
      if (!linkingInCurrent || !linkingInPrevious) {
        console.error(`❌ Linking number ${currentScreen.linkingNumber} not found in both screens ${i} and ${i + 1}`);
        return false;
      }
    }
    
    console.log(`✅ Number sequence validation passed`);
    return true;
  }

  /**
   * Format puzzle for storage in database (theme-agnostic)
   */
  formatForStorage(puzzleData, difficulty) {
    return {
      question: JSON.stringify({
        sequence: puzzleData.sequence,
        instructions: puzzleData.instructions,
        totalScreens: puzzleData.totalScreens,
        objectsPerScreen: puzzleData.objectsPerScreen,
        maxObjectPool: puzzleData.maxObjectPool,
        difficulty: difficulty
      }),
      answer: JSON.stringify({
        linkingNumbers: puzzleData.sequence
          .filter(screen => screen.linkingNumber)
          .map(screen => ({
            screenNumber: screen.screenNumber,
            linkingNumber: screen.linkingNumber
          })),
        totalCorrectAnswers: puzzleData.sequence.length - 1,
        maxScore: puzzleData.scoring.maxPoints
      }),
      hint: "Remember objects from the previous screen and tap the one that appeared before",
      difficulty: difficulty,
      metadata: {
        ...puzzleData.metadata,
        totalScreens: puzzleData.totalScreens,
        maxObjectPool: puzzleData.maxObjectPool
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
   * Get example of how theme mapping would work
   */
  getThemeMappingExample() {
    return {
      example: "When puzzle is fetched with theme 'water_creatures':",
      numberMapping: {
        1: { name: "Fish", icon: "🐟" },
        2: { name: "Shark", icon: "🦈" },
        3: { name: "Octopus", icon: "🐙" },
        4: { name: "Turtle", icon: "🐢" },
        5: { name: "Whale", icon: "🐋" },
        6: { name: "Dolphin", icon: "🐬" }
      },
      usage: "Frontend maps [1, 3, 5] to [Fish, Octopus, Whale] for display"
    };
  }
}

// Export convenience function
export function generateMemoryPreviousPairPuzzle(difficulty = 'easy') {
  const generator = new MemoryPreviousPairGenerator();
  return generator.generateMemoryPreviousPairPuzzle(difficulty);
}