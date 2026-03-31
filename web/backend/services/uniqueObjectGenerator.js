// services/uniqueObjectGenerator.js

/**
 * Unique Object Puzzle Generator
 * Generates puzzles where one object has a unique shape+color combination
 * while others appear in pairs/groups
 */

export class UniqueObjectGenerator {
    constructor() {
      this.debugMode = false;
      
      // Define universe of shapes and colors
      this.shapes = {
        0: 'circle',
        1: 'square', 
        2: 'triangle',
        3: 'diamond',
        4: 'hexagon',
        5: 'star'
      };
      
      this.colors = {
        0: 'red',
        1: 'blue',
        2: 'yellow',
        3: 'green',
        4: 'purple',
        5: 'orange',
        6: 'pink',     // Similar to red - adds confusion in hard mode
        7: 'cyan'      // Similar to blue - adds confusion in hard mode
      };
  
      // Define similar color pairs for confusion in hard mode
      this.similarColorPairs = [
        [0, 6], // red, pink
        [1, 7], // blue, cyan
        [2, 5]  // yellow, orange
      ];
  
      // Define similar shape pairs for confusion in hard mode  
      this.similarShapePairs = [
        [0, 4], // circle, hexagon (both rounded)
        [1, 3], // square, diamond (both angular)
        [2, 5]  // triangle, star (both pointed)
      ];
  
      // Enhanced difficulty settings with multiple cognitive challenges
      this.difficultySettings = {
        easy: {
          totalObjects: 6,
          minDuplicates: 2,
          maxDuplicates: 2,
          shapeRange: 3, // Use first 3 shapes: circle, square, triangle
          colorRange: 3, // Use first 3 colors: red, blue, yellow
          distractorGroups: 1, // Only 1 other group besides unique
          guaranteedStrategy: 'shared_shape_different_color', // Force easier strategy
          visualComplexity: 'low',
          description: 'Few objects, distinct shapes/colors, clear pattern'
        },
        medium: {
          totalObjects: 9,
          minDuplicates: 2,
          maxDuplicates: 3,
          shapeRange: 4, // Add diamond
          colorRange: 4, // Add green
          distractorGroups: 2, // 2 other groups besides unique
          guaranteedStrategy: null, // Allow both strategies
          visualComplexity: 'medium',
          allowSimilarColors: false, // No red/pink confusion yet
          description: 'More objects, additional shapes/colors, multiple groups'
        },
        hard: {
          totalObjects: 12,
          minDuplicates: 2,
          maxDuplicates: 4,
          shapeRange: 5, // Add hexagon
          colorRange: 6, // Add purple, orange
          distractorGroups: 3, // 3+ other groups besides unique
          guaranteedStrategy: null,
          visualComplexity: 'high',
          allowSimilarColors: true, // Enable red/pink, blue/cyan confusion
          allowSimilarShapes: true, // Enable circle/hexagon, square/diamond confusion
          requireCrossCategoryDistractors: true, // Force both shape AND color distractors
          description: 'Many objects, similar shapes/colors, complex groupings'
        },
        expert: {
          totalObjects: 15,
          minDuplicates: 3,
          maxDuplicates: 5,
          shapeRange: 6, // All shapes
          colorRange: 8, // All colors
          distractorGroups: 4,
          guaranteedStrategy: null,
          visualComplexity: 'extreme',
          allowSimilarColors: true,
          allowSimilarShapes: true,
          requireCrossCategoryDistractors: true,
          enableRedHerrings: true, // Add objects that ALMOST break the pattern
          description: 'Maximum complexity with visual and cognitive challenges'
        }
      };
    }
  
    setDebugMode(enabled) {
      this.debugMode = enabled;
    }
  
    debugLog(message, type = 'info') {
      if (this.debugMode) {
        const timestamp = new Date().toISOString();
        const emoji = type === 'error' ? '❌' : type === 'success' ? '✅' : '🔄';
        console.log(`${emoji} [${timestamp}] UNIQUE_OBJ: ${message}`);
      }
    }
  
    /**
     * Generate a unique object puzzle
     */
    async generateUniqueObjectPuzzle(difficulty = 'medium') {
      try {
        this.debugLog(`🎲 Generating unique object puzzle (${difficulty})`);
        
        const settings = this.difficultySettings[difficulty.toLowerCase()];
        if (!settings) {
          throw new Error(`Invalid difficulty: ${difficulty}`);
        }
  
        // Generate the puzzle layout
        const puzzleData = this.generatePuzzleLayout(settings);
        
        // Create question and answer data
        const questionData = this.createQuestionData(puzzleData, settings);
        const answerData = this.createAnswerData(puzzleData);
  
        const puzzle = {
          question: JSON.stringify(questionData),
          answer: JSON.stringify(answerData),
          hint: `Find the object that appears only once - it has a unique combination of shape and color`,
          difficulty: difficulty,
          metadata: {
            totalObjects: puzzleData.objects.length,
            uniqueObjectIndex: puzzleData.uniqueObjectIndex,
            duplicateGroups: puzzleData.duplicateGroups,
            generatedAt: new Date().toISOString(),
            shapeColorMappings: {
              shapes: this.getShapeMapping(settings.shapeRange),
              colors: this.getColorMapping(settings.colorRange)
            }
          }
        };
  
        this.debugLog(`✅ Generated unique object puzzle with ${puzzleData.objects.length} objects`, 'success');
        
        return {
          success: true,
          puzzleData: puzzle
        };
  
      } catch (error) {
        this.debugLog(`❌ Generation failed: ${error.message}`, 'error');
        return {
          success: false,
          message: `Unique object generation failed: ${error.message}`
        };
      }
    }
  
    /**
     * Generate the core puzzle layout with difficulty-appropriate challenges
     */
    // CORRECTED VERSION: Ensure exactly one unique object

generatePuzzleLayout(settings) {
    const { totalObjects, minDuplicates, maxDuplicates, shapeRange, colorRange } = settings;
    
    const objects = [];
    const usedCombinations = new Set();
    const duplicateGroups = [];
    
    // STEP 1: Generate the unique object FIRST
    const uniqueObject = this.createStrategicUniqueObject(
      [], [], usedCombinations, shapeRange, colorRange
    );
    usedCombinations.add(`${uniqueObject.shape}-${uniqueObject.color}`);
    
    // STEP 2: Fill remaining slots with ONLY duplicate groups
    let remainingSlots = totalObjects - 1; // Reserve 1 slot for unique
    
    while (remainingSlots >= minDuplicates) {
      // Determine group size
      const maxGroupSize = Math.min(maxDuplicates, remainingSlots);
      const groupSize = Math.max(minDuplicates, 
        Math.min(maxGroupSize, minDuplicates + Math.floor(Math.random() * 2)));
      
      // Generate a NEW combination that doesn't conflict with unique
      let groupObject;
      let attempts = 0;
      do {
        groupObject = {
          shape: Math.floor(Math.random() * shapeRange),
          color: Math.floor(Math.random() * colorRange)
        };
        attempts++;
        if (attempts > 50) {
          throw new Error('Could not generate non-conflicting duplicate group');
        }
      } while (usedCombinations.has(`${groupObject.shape}-${groupObject.color}`));
      
      // Mark this combination as used
      usedCombinations.add(`${groupObject.shape}-${groupObject.color}`);
      
      // Create the duplicate group
      const groupIndices = [];
      for (let i = 0; i < groupSize; i++) {
        objects.push({ ...groupObject });
        groupIndices.push(objects.length - 1);
      }
      
      duplicateGroups.push({
        combination: `${groupObject.shape}-${groupObject.color}`,
        indices: groupIndices,
        count: groupSize,
        groupType: 'duplicate'
      });
      
      remainingSlots -= groupSize;
    }
    
    // STEP 3: Add the unique object
    objects.push(uniqueObject);
    const uniqueObjectIndex = objects.length - 1;
    
    // STEP 4: Shuffle while tracking unique object
    const shuffledObjects = [...objects];
    let newUniqueIndex = uniqueObjectIndex;
    
    // Fisher-Yates shuffle with proper tracking
    for (let i = shuffledObjects.length - 1; i > 0; i--) {
      const j = Math.floor(Math.random() * (i + 1));
      
      // Track the unique object's position
      if (i === newUniqueIndex) {
        newUniqueIndex = j;
      } else if (j === newUniqueIndex) {
        newUniqueIndex = i;
      }
      
      // Perform swap
      [shuffledObjects[i], shuffledObjects[j]] = [shuffledObjects[j], shuffledObjects[i]];
    }
    
    // STEP 5: VALIDATE the result
    const combinations = new Map();
    shuffledObjects.forEach((obj, idx) => {
      const combo = `${obj.shape}-${obj.color}`;
      if (!combinations.has(combo)) {
        combinations.set(combo, []);
      }
      combinations.get(combo).push(idx);
    });
    
    const uniqueCombos = Array.from(combinations.entries()).filter(([combo, indices]) => indices.length === 1);
    
    if (uniqueCombos.length !== 1) {
      throw new Error(`VALIDATION FAILED: Generated ${uniqueCombos.length} unique objects, expected 1. Combinations: ${JSON.stringify(Object.fromEntries(combinations))}`);
    }
    
    const [actualCombo, [actualIndex]] = uniqueCombos[0];
    if (actualIndex !== newUniqueIndex) {
      this.debugLog(`Index tracking error: expected ${newUniqueIndex}, found ${actualIndex}`, 'error');
      newUniqueIndex = actualIndex; // Correct the tracking
    }
    
    this.debugLog(`✅ Valid puzzle: ${actualCombo} unique at index ${newUniqueIndex}`, 'success');
    
    return {
      objects: shuffledObjects,
      uniqueObjectIndex: newUniqueIndex,
      duplicateGroups,
      usedCombinations: Array.from(usedCombinations)
    };
  }
  
    /**
     * Generate a group object with difficulty-appropriate constraints
     */
    generateGroupObject(usedCombinations, shapeRange, colorRange, allowSimilarColors = false, allowSimilarShapes = false, groupIndex = 0) {
        let attempts = 0;
        let newObject;
        
        do {
          newObject = {
            shape: Math.floor(Math.random() * shapeRange),
            color: Math.floor(Math.random() * colorRange)
          };
          
          // VALIDATION: Ensure we generated valid numbers
          if (typeof newObject.shape !== 'number' || typeof newObject.color !== 'number' ||
              newObject.shape < 0 || newObject.shape >= shapeRange ||
              newObject.color < 0 || newObject.color >= colorRange) {
            this.debugLog(`⚠️ Invalid object generated, retrying: shape=${newObject.shape}, color=${newObject.color}`, 'warning');
            continue;
          }
          
          // In hard mode, sometimes intentionally use similar colors/shapes
          if ((allowSimilarColors || allowSimilarShapes) && Math.random() < 0.3) {
            if (allowSimilarColors && Math.random() < 0.5 && this.similarColorPairs.length > 0) {
              const similarPair = this.similarColorPairs[Math.floor(Math.random() * this.similarColorPairs.length)];
              const newColor = similarPair[groupIndex % 2];
              if (newColor < colorRange) {
                newObject.color = newColor;
              }
            }
            if (allowSimilarShapes && Math.random() < 0.5 && this.similarShapePairs.length > 0) {
              const similarPair = this.similarShapePairs[Math.floor(Math.random() * this.similarShapePairs.length)];
              const newShape = similarPair[groupIndex % 2];
              if (newShape < shapeRange) {
                newObject.shape = newShape;
              }
            }
          }
          
          attempts++;
          if (attempts > 50) {
            this.debugLog(`❌ Could not generate valid group object after 50 attempts`, 'error');
            throw new Error('Could not generate valid group object');
          }
        } while (usedCombinations.has(`${newObject.shape}-${newObject.color}`));
        
        this.debugLog(`🔄 Generated group object: ${newObject.shape}-${newObject.color}`);
        return newObject;
      }      
  
    /**
     * Generate red herring objects that share attributes but aren't the unique object
     */
    generateRedHerring(baseShapes, baseColors, usedCombinations, shapeRange, colorRange) {
      // Red herring: shares shape with one group and color with another group
      const redHerringShape = baseShapes[Math.floor(Math.random() * baseShapes.length)];
      const redHerringColor = baseColors[Math.floor(Math.random() * baseColors.length)];
      
      // Make sure this combination isn't already used
      if (!usedCombinations.has(`${redHerringShape}-${redHerringColor}`)) {
        return { shape: redHerringShape, color: redHerringColor };
      }
      
      return null;
    }
  
    /**
     * Calculate difficulty metrics for generated puzzle
     */
    calculateDifficultyMetrics(objects, uniqueIndex, duplicateGroups, settings) {
      const uniqueObj = objects[uniqueIndex];
      
      // Count visual similarity distractors
      let shapeSimilarityCount = 0;
      let colorSimilarityCount = 0;
      
      for (let i = 0; i < objects.length; i++) {
        if (i === uniqueIndex) continue;
        
        const obj = objects[i];
        if (obj.shape === uniqueObj.shape) shapeSimilarityCount++;
        if (obj.color === uniqueObj.color) colorSimilarityCount++;
      }
      
      // Calculate complexity scores
      const visualComplexity = objects.length + (duplicateGroups.length * 2);
      const cognitiveLoad = shapeSimilarityCount + colorSimilarityCount;
      const patternComplexity = duplicateGroups.filter(g => g.count > 2).length;
      
      return {
        totalObjects: objects.length,
        distractorGroups: duplicateGroups.length,
        shapeSimilarityCount,
        colorSimilarityCount,
        visualComplexity,
        cognitiveLoad,
        patternComplexity,
        estimatedDifficulty: this.estimateDifficulty(visualComplexity, cognitiveLoad, patternComplexity),
        targetDifficulty: settings.description
      };
    }
  
    /**
     * Estimate actual difficulty based on puzzle characteristics
     */
    estimateDifficulty(visualComplexity, cognitiveLoad, patternComplexity) {
      const score = visualComplexity + (cognitiveLoad * 2) + (patternComplexity * 3);
      
      if (score <= 10) return 'easy';
      if (score <= 20) return 'medium';
      if (score <= 35) return 'hard';
      return 'expert';
    }
  
    /**
     * Create a strategically placed unique object with difficulty-appropriate constraints
     */
    createStrategicUniqueObject(baseShapes, baseColors, usedCombinations, shapeRange, colorRange, guaranteedStrategy = null, requireCrossCategoryDistractors = false) {
        // PROBLEM: When baseShapes and baseColors are empty (first call), 
        // the strategy selection fails and returns undefined values
        
        // FIX: Handle empty base arrays properly
        if (baseShapes.length === 0 || baseColors.length === 0) {
          // No existing objects yet, just create a random unique object
          let attempts = 0;
          let uniqueObject;
          
          do {
            uniqueObject = {
              shape: Math.floor(Math.random() * shapeRange),
              color: Math.floor(Math.random() * colorRange)
            };
            attempts++;
            
            if (attempts > 50) {
              throw new Error('Could not generate unique object after 50 attempts');
            }
          } while (usedCombinations.has(`${uniqueObject.shape}-${uniqueObject.color}`));
          
          this.debugLog(`🎯 Generated initial unique object: ${uniqueObject.shape}-${uniqueObject.color}`);
          return uniqueObject;
        }
        
        const strategies = guaranteedStrategy ? [guaranteedStrategy] : [
          'shared_shape_different_color',
          'shared_color_different_shape'
        ];
        
        const strategy = strategies[Math.floor(Math.random() * strategies.length)];
        
        if (strategy === 'shared_shape_different_color') {
          // Pick a shape that already exists, but use a different color
          const existingShape = baseShapes[Math.floor(Math.random() * baseShapes.length)];
          
          // Find a color not used with this shape
          let uniqueColor;
          let attempts = 0;
          do {
            uniqueColor = Math.floor(Math.random() * colorRange);
            attempts++;
            
            // In hard mode with cross-category distractors, prefer colors used elsewhere
            if (requireCrossCategoryDistractors && attempts < 10) {
              if (baseColors.includes(uniqueColor)) {
                break; // Use a color that appears in other groups
              }
            }
          } while (usedCombinations.has(`${existingShape}-${uniqueColor}`) && attempts < 20);
          
          // VALIDATION: Ensure we have valid values
          if (typeof existingShape !== 'number' || typeof uniqueColor !== 'number') {
            throw new Error(`Invalid unique object generated: shape=${existingShape}, color=${uniqueColor}`);
          }
          
          this.debugLog(`🎯 Generated strategic unique object (shared shape): ${existingShape}-${uniqueColor}`);
          return { shape: existingShape, color: uniqueColor };
          
        } else {
          // Pick a color that already exists, but use a different shape
          const existingColor = baseColors[Math.floor(Math.random() * baseColors.length)];
          
          // Find a shape not used with this color
          let uniqueShape;
          let attempts = 0;
          do {
            uniqueShape = Math.floor(Math.random() * shapeRange);
            attempts++;
            
            // In hard mode with cross-category distractors, prefer shapes used elsewhere
            if (requireCrossCategoryDistractors && attempts < 10) {
              if (baseShapes.includes(uniqueShape)) {
                break; // Use a shape that appears in other groups
              }
            }
          } while (usedCombinations.has(`${uniqueShape}-${existingColor}`) && attempts < 20);
          
          // VALIDATION: Ensure we have valid values
          if (typeof uniqueShape !== 'number' || typeof existingColor !== 'number') {
            throw new Error(`Invalid unique object generated: shape=${uniqueShape}, color=${existingColor}`);
          }
          
          this.debugLog(`🎯 Generated strategic unique object (shared color): ${uniqueShape}-${existingColor}`);
          return { shape: uniqueShape, color: existingColor };
        }
      }
  
    /**
     * Analyze the strategy used for the unique object
     */
    analyzeStrategy(uniqueObject, baseShapes, baseColors) {
      const sharesShape = baseShapes.includes(uniqueObject.shape);
      const sharesColor = baseColors.includes(uniqueObject.color);
      
      if (sharesShape && !sharesColor) {
        return {
          type: 'shared_shape_different_color',
          description: `Same shape (${this.shapes[uniqueObject.shape]}) as others, but different color (${this.colors[uniqueObject.color]})`
        };
      } else if (sharesColor && !sharesShape) {
        return {
          type: 'shared_color_different_shape', 
          description: `Same color (${this.colors[uniqueObject.color]}) as others, but different shape (${this.shapes[uniqueObject.shape]})`
        };
      } else if (sharesShape && sharesColor) {
        return {
          type: 'warning_shares_both',
          description: 'WARNING: Unique object shares both shape and color with others'
        };
      } else {
        return {
          type: 'warning_shares_neither',
          description: 'WARNING: Unique object shares neither shape nor color with others'
        };
      }
    }
  
    /**
     * Create question data for client
     */
    createQuestionData(puzzleData, settings) {
      return {
        objects: puzzleData.objects,
        totalObjects: puzzleData.objects.length,
        instruction: "Find the odd one out, and tap on it.",
        shapeMappings: this.getShapeMapping(settings.shapeRange),
        colorMappings: this.getColorMapping(settings.colorRange),
        layout: {
          grid: this.calculateGridLayout(puzzleData.objects.length),
          spacing: "auto"
        }
      };
    }
  
    /**
     * Create answer data
     */
    createAnswerData(puzzleData) {
      return {
        uniqueObjectIndex: puzzleData.uniqueObjectIndex,
        uniqueObject: puzzleData.objects[puzzleData.uniqueObjectIndex],
        duplicateGroups: puzzleData.duplicateGroups,
        explanation: `Object at position ${puzzleData.uniqueObjectIndex + 1} is unique`,
        scoring: {
          correctAnswerPoints: 100,
          timeBonus: true,
          maxTimeBonus: 50
        }
      };
    }
  
    /**
     * Get shape mapping for given range
     */
    getShapeMapping(range) {
      const mapping = {};
      for (let i = 0; i < range; i++) {
        mapping[i] = this.shapes[i];
      }
      return mapping;
    }
  
    /**
     * Get color mapping for given range  
     */
    getColorMapping(range) {
      const mapping = {};
      for (let i = 0; i < range; i++) {
        mapping[i] = this.colors[i];
      }
      return mapping;
    }
  
    /**
     * Calculate optimal grid layout for number of objects
     */
    calculateGridLayout(objectCount) {
      // Try to make a roughly square grid
      const sqrt = Math.sqrt(objectCount);
      const rows = Math.ceil(sqrt);
      const cols = Math.ceil(objectCount / rows);
      
      return {
        rows: rows,
        cols: cols,
        totalCells: rows * cols
      };
    }
  
    /**
     * Validate generated puzzle for quality
     */
    validatePuzzle(puzzleData) {
      const { objects, uniqueObjectIndex, duplicateGroups, strategy } = puzzleData;
      
      // Check unique object exists and is actually unique
      const uniqueObj = objects[uniqueObjectIndex];
      const uniqueCombo = `${uniqueObj.shape}-${uniqueObj.color}`;
      
      let uniqueCount = 0;
      for (const obj of objects) {
        if (`${obj.shape}-${obj.color}` === uniqueCombo) {
          uniqueCount++;
        }
      }
      
      if (uniqueCount !== 1) {
        throw new Error(`Unique object appears ${uniqueCount} times, should be 1`);
      }
  
      // Validate all duplicate groups have at least 2 members
      for (const group of duplicateGroups) {
        if (group.count < 2) {
          throw new Error(`Duplicate group has ${group.count} members, minimum is 2`);
        }
      }
  
      // Validate total object count
      const expectedTotal = 1 + duplicateGroups.reduce((sum, group) => sum + group.count, 0);
      if (objects.length !== expectedTotal) {
        throw new Error(`Object count mismatch: ${objects.length} vs expected ${expectedTotal}`);
      }
  
      // QUALITY CHECK: Validate strategic placement
      if (strategy.type.includes('warning')) {
        throw new Error(`Poor puzzle quality: ${strategy.description}`);
      }
  
      // Additional quality check: ensure unique object shares exactly one attribute
      const uniqueShape = uniqueObj.shape;
      const uniqueColor = uniqueObj.color;
      
      let sharesShapeCount = 0;
      let sharesColorCount = 0;
      
      for (const obj of objects) {
        if (obj === uniqueObj) continue; // Skip the unique object itself
        
        if (obj.shape === uniqueShape) sharesShapeCount++;
        if (obj.color === uniqueColor) sharesColorCount++;
      }
      
      const sharesShape = sharesShapeCount > 0;
      const sharesColor = sharesColorCount > 0;
      
      if (!sharesShape && !sharesColor) {
        throw new Error('Quality issue: Unique object shares no attributes with other objects');
      }
      
      if (sharesShape && sharesColor) {
        throw new Error('Quality issue: Unique object shares both shape and color with other objects');
      }
  
      this.debugLog(`✅ Quality validation passed: ${strategy.description}`, 'success');
      return true;
    }
  
    /**
     * Generate multiple puzzle variations
     */
    async generateBatch(difficulty, count = 5) {
      const results = [];
      
      for (let i = 0; i < count; i++) {
        try {
          const result = await this.generateUniqueObjectPuzzle(difficulty);
          if (result.success) {
            results.push(result.puzzleData);
            this.debugLog(`Generated puzzle ${i + 1}/${count}`, 'success');
          }
        } catch (error) {
          this.debugLog(`Failed to generate puzzle ${i + 1}: ${error.message}`, 'error');
        }
      }
  
      return {
        success: results.length > 0,
        puzzles: results,
        count: results.length,
        successRate: `${results.length}/${count}`
      };
    }
  }
  
  /**
   * Standalone generation function for integration
   */
  export async function generateUniqueObjectPuzzle(difficulty = 'medium') {
    const generator = new UniqueObjectGenerator();
    return await generator.generateUniqueObjectPuzzle(difficulty);
  }