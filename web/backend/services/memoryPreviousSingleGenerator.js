// services/memoryPreviousSingleGenerator.js

/**
 * Memory Previous Single Puzzle Generator
 * Generates binary sequences where user must remember if current state matches previous state
 */

export class MemoryPreviousSingleGenerator {
    constructor() {
      // Difficulty settings
      this.difficultyConfig = {
        easy: {
          sequenceLength: 20,
          minContinuousSequences: 8,
          minSequenceLength: 2,
          sameAsPreviousRatio: 0.6 // 60% of answers should be "same as previous"
        },
        medium: {
          sequenceLength: 18,
          minContinuousSequences: 6,
          minSequenceLength: 2,
          sameAsPreviousRatio: 0.55 // 55% of answers should be "same as previous"
        },
        hard: {
          sequenceLength: 16,
          minContinuousSequences: 4,
          minSequenceLength: 2,
          sameAsPreviousRatio: 0.5 // 50% of answers should be "same as previous"
        }
      };
    }
  
    /**
     * Generate a memory previous single puzzle
     */
    async generateMemoryPreviousSinglePuzzle(difficulty = 'easy') {
      difficulty = difficulty.toLowerCase();
      console.log(`🧠 Generating ${difficulty} memory previous single puzzle`);
  
      try {
        // Validate inputs
        if (!this.difficultyConfig[difficulty]) {
          throw new Error(`Invalid difficulty: ${difficulty}`);
        }
  
        const config = this.difficultyConfig[difficulty];
        
        // Generate binary sequence with required continuous sequences
        const binarySequence = this.generateBinarySequence(config);
        
        // Validate the sequence meets requirements
        if (!this.validateSequence(binarySequence, config)) {
          throw new Error('Generated sequence does not meet requirements');
        }
  
        // Create the question sequence (what user sees)
        const questionSequence = this.createQuestionSequence(binarySequence);
  
        // Create the answer sequence (0 = different, 1 = same as previous)
        const answerSequence = this.createAnswerSequence(binarySequence);
  
        // Create puzzle data structure
        const puzzleData = {
          difficulty: difficulty,
          binarySequence: binarySequence,
          questionSequence: questionSequence,
          answerSequence: answerSequence,
          totalSteps: binarySequence.length,
          totalQuestions: answerSequence.length,
          instructions: this.generateInstructions(),
          scoring: {
            pointsPerCorrect: 5,
            timeBonus: true,
            maxPoints: answerSequence.length * 5
          },
          metadata: {
            generatedAt: new Date().toISOString(),
            version: '1.0',
            generationType: 'binary_sequence',
            continuousSequences: this.countContinuousSequences(binarySequence),
            sameAsPreviousCount: answerSequence.filter(a => a === 1).length,
            differentFromPreviousCount: answerSequence.filter(a => a === 0).length
          }
        };
  
        console.log(`✅ Generated binary sequence: ${binarySequence.join('')}`);
        console.log(`📊 Continuous sequences: ${puzzleData.metadata.continuousSequences}`);
        console.log(`🎯 Same as previous: ${puzzleData.metadata.sameAsPreviousCount}/${puzzleData.totalQuestions}`);
        
        // Format for storage in existing puzzle system
        const formattedPuzzle = this.formatForStorage(puzzleData, difficulty);
        
        return {
          success: true,
          puzzleData: formattedPuzzle
        };
  
      } catch (error) {
        console.error(`❌ Memory previous single generation error: ${error.message}`);
        return {
          success: false,
          message: error.message
        };
      }
    }
  
    /**
     * Generate binary sequence with required continuous sequences
     */
    generateBinarySequence(config) {
      const { sequenceLength, minContinuousSequences, minSequenceLength, sameAsPreviousRatio } = config;
      
      let attempts = 0;
      const maxAttempts = 100;
      
      while (attempts < maxAttempts) {
        const sequence = this.createRandomBinarySequence(sequenceLength, sameAsPreviousRatio);
        const continuousCount = this.countContinuousSequences(sequence);
        
        if (continuousCount >= minContinuousSequences) {
          console.log(`✅ Generated sequence with ${continuousCount} continuous sequences (required: ${minContinuousSequences})`);
          return sequence;
        }
        
        attempts++;
      }
      
      // Fallback: force creation of sequence with required continuous sequences
      console.log(`⚠️ Fallback: Creating sequence with forced continuous sequences`);
      return this.createForcedContinuousSequence(config);
    }
  
    /**
     * Create a random binary sequence biased toward the desired ratio
     */
    createRandomBinarySequence(length, sameAsPreviousRatio) {
      const sequence = [Math.random() < 0.5 ? 0 : 1]; // Random start
      
      for (let i = 1; i < length; i++) {
        const shouldBeSame = Math.random() < sameAsPreviousRatio;
        
        if (shouldBeSame) {
          sequence.push(sequence[i - 1]); // Same as previous
        } else {
          sequence.push(sequence[i - 1] === 0 ? 1 : 0); // Different from previous
        }
      }
      
      return sequence;
    }
  
    /**
     * Create sequence with forced continuous sequences (fallback method)
     */
    createForcedContinuousSequence(config) {
      const { sequenceLength, minContinuousSequences, minSequenceLength } = config;
      
      const sequence = [];
      let remainingLength = sequenceLength;
      let sequencesCreated = 0;
      
      // Start with random value
      let currentValue = Math.random() < 0.5 ? 0 : 1;
      
      while (remainingLength > 0 && sequencesCreated < minContinuousSequences) {
        // Determine length of this continuous sequence
        const maxLength = Math.min(remainingLength, Math.floor(remainingLength / (minContinuousSequences - sequencesCreated)));
        const seqLength = Math.max(minSequenceLength, Math.floor(Math.random() * maxLength) + 1);
        
        // Add continuous sequence
        for (let i = 0; i < seqLength; i++) {
          sequence.push(currentValue);
        }
        
        remainingLength -= seqLength;
        sequencesCreated++;
        
        // Switch to opposite value for next sequence
        currentValue = currentValue === 0 ? 1 : 0;
      }
      
      // Fill remaining with random values
      while (remainingLength > 0) {
        const shouldBeSame = Math.random() < 0.5;
        if (shouldBeSame && sequence.length > 0) {
          sequence.push(sequence[sequence.length - 1]);
        } else {
          sequence.push(Math.random() < 0.5 ? 0 : 1);
        }
        remainingLength--;
      }
      
      return sequence;
    }
  
    /**
     * Count continuous sequences in binary array
     */
    countContinuousSequences(sequence) {
      if (sequence.length < 2) return 0;
      
      let count = 0;
      let currentSequenceLength = 1;
      
      for (let i = 1; i < sequence.length; i++) {
        if (sequence[i] === sequence[i - 1]) {
          currentSequenceLength++;
        } else {
          if (currentSequenceLength >= 2) {
            count++;
          }
          currentSequenceLength = 1;
        }
      }
      
      // Check final sequence
      if (currentSequenceLength >= 2) {
        count++;
      }
      
      return count;
    }
  
    /**
     * Validate sequence meets requirements
     */
    validateSequence(sequence, config) {
      const continuousCount = this.countContinuousSequences(sequence);
      const meetsLengthRequirement = sequence.length >= config.sequenceLength;
      const meetsContinuousRequirement = continuousCount >= config.minContinuousSequences;
      
      console.log(`🔍 Validation: Length ${sequence.length}/${config.sequenceLength}, Continuous ${continuousCount}/${config.minContinuousSequences}`);
      
      return meetsLengthRequirement && meetsContinuousRequirement;
    }
  
    /**
     * Create question sequence (what user sees step by step)
     */
    createQuestionSequence(binarySequence) {
      return binarySequence.map((value, index) => ({
        step: index + 1,
        value: value,
        isFirstStep: index === 0
      }));
    }
  
    /**
     * Create answer sequence (0 = different, 1 = same as previous)
     */
    createAnswerSequence(binarySequence) {
      const answers = [];
      
      for (let i = 1; i < binarySequence.length; i++) {
        const currentValue = binarySequence[i];
        const previousValue = binarySequence[i - 1];
        
        // 1 = same as previous, 0 = different from previous
        answers.push(currentValue === previousValue ? 1 : 0);
      }
      
      return answers;
    }
  
    /**
     * Generate instructions for the puzzle
     */
    generateInstructions() {
      return {
        title: "Memory Previous Single",
        description: "Remember the previous state and compare it with the current one!",
        steps: [
          "1. Look at the first object and remember it",
          "2. For each new object, decide if it's the SAME or DIFFERENT from the previous one",
          "3. Tap 'SAME' if it matches the previous object, 'DIFFERENT' if it doesn't",
          "4. Continue through the entire sequence",
          "5. Be quick and accurate for maximum points!"
        ],
        tip: "Focus on the immediate previous object, not the entire sequence"
      };
    }
  
    /**
     * Format puzzle for storage in database
     */
    formatForStorage(puzzleData, difficulty) {
      return {
        question: JSON.stringify({
          binarySequence: puzzleData.binarySequence,
          questionSequence: puzzleData.questionSequence,
          instructions: puzzleData.instructions,
          totalSteps: puzzleData.totalSteps,
          totalQuestions: puzzleData.totalQuestions,
          difficulty: difficulty
        }),
        answer: JSON.stringify({
          answerSequence: puzzleData.answerSequence,
          correctAnswers: puzzleData.answerSequence,
          totalQuestions: puzzleData.totalQuestions,
          maxScore: puzzleData.scoring.maxPoints
        }),
        hint: "Remember only the previous object and compare it with the current one",
        difficulty: difficulty,
        metadata: {
          ...puzzleData.metadata,
          totalSteps: puzzleData.totalSteps,
          totalQuestions: puzzleData.totalQuestions
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
     * Get example of binary sequence
     */
    getSequenceExample() {
      return {
        binarySequence: [0, 0, 1, 1, 1, 0, 0, 1, 0, 0, 0, 1, 1, 0, 1, 1, 0, 1, 1, 1],
        explanation: "User sees objects mapped to 0s and 1s, must identify when current matches previous",
        answers: [1, 0, 1, 1, 0, 1, 0, 0, 1, 1, 0, 1, 0, 0, 1, 0, 0, 1, 1], // 1=same, 0=different
        continuousSequences: [
          "00 (positions 1-2)",
          "111 (positions 3-5)", 
          "00 (positions 6-7)",
          "000 (positions 9-11)",
          "11 (positions 12-13)",
          "11 (positions 15-16)",
          "111 (positions 18-20)"
        ]
      };
    }
  }
  
  // Export convenience function
  export function generateMemoryPreviousSinglePuzzle(difficulty = 'easy') {
    const generator = new MemoryPreviousSingleGenerator();
    return generator.generateMemoryPreviousSinglePuzzle(difficulty);
  }