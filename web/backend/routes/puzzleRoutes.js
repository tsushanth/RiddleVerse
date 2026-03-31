// routes/puzzleRoutes.js - Updated to use new system

import express from 'express';
import { PuzzleGenerator } from '../services/puzzleGeneration.js';
import { PuzzleValidator } from '../services/puzzleValidation.js';
import { PUZZLE_TYPES, DIFFICULTY_LEVELS } from '../config/puzzleConfig.js';

const router = express.Router();
const puzzleGenerator = new PuzzleGenerator();

/**
 * Generate puzzle with new standardized system
 */
router.post('/generate', async (req, res) => {
  try {
    const { 
      puzzleType, 
      difficulty = DIFFICULTY_LEVELS.MEDIUM, 
      modelName = 'gpt-3.5-turbo',
      count = 1 
    } = req.body;

    if (!puzzleType) {
      return res.status(400).json({
        success: false,
        message: 'puzzleType is required'
      });
    }

    // Validate inputs
    if (!Object.values(PUZZLE_TYPES).includes(puzzleType)) {
      return res.status(400).json({
        success: false,
        message: `Invalid puzzle type. Must be one of: ${Object.values(PUZZLE_TYPES).join(', ')}`
      });
    }

    if (!Object.values(DIFFICULTY_LEVELS).includes(difficulty)) {
      return res.status(400).json({
        success: false,
        message: `Invalid difficulty. Must be one of: ${Object.values(DIFFICULTY_LEVELS).join(', ')}`
      });
    }

    if (count === 1) {
      // Single puzzle generation
      const result = await puzzleGenerator.generatePuzzle(puzzleType, modelName, difficulty);
      
      return res.json({
        success: result.success,
        message: result.message,
        puzzleId: result.puzzleId,
        puzzleType,
        difficulty,
        modelName,
        validationMethod: 'enhanced_programmatic'
      });
    } else {
      // Batch generation
      const result = await puzzleGenerator.generateBatch(puzzleType, difficulty, count, modelName);
      
      return res.json({
        success: result.successCount > 0,
        message: `Generated ${result.successCount}/${count} puzzles successfully`,
        summary: {
          requested: count,
          successful: result.successCount,
          failed: result.failureCount,
          puzzleIds: result.puzzleIds
        },
        errors: result.errors.length > 0 ? result.errors.slice(0, 5) : [], // Show first 5 errors
        puzzleType,
        difficulty,
        modelName
      });
    }

  } catch (error) {
    console.error('❌ Error in puzzle generation route:', error);
    res.status(500).json({
      success: false,
      message: 'Internal server error during puzzle generation',
      error: error.message
    });
  }
});


/**
 * Migrate puzzles to current standards
 */
router.post('/migrate', async (req, res) => {
  try {
    const { 
      puzzleTypes = null, // null means all types
      dryRun = true 
    } = req.body;

    console.log(`🚀 Starting puzzle migration (dryRun: ${dryRun})`);

    let migrationResult;
    
    if (puzzleTypes && Array.isArray(puzzleTypes)) {
      // Migrate specific puzzle types
      migrationResult = await migrationService.migrateSpecificPuzzleTypes(puzzleTypes);
    } else {
      // Migrate all puzzle types
      if (dryRun) {
        migrationResult = await migrationService.analyzeMigrationNeeds();
      } else {
        migrationResult = await migrationService.migrateAllPuzzlesToCurrentStandards();
      }
    }

    const statusCode = migrationResult.fatalError ? 500 : 200;

    res.status(statusCode).json({
      success: !migrationResult.fatalError,
      message: dryRun 
        ? `Migration analysis completed: ${migrationResult.needsDifficultyUpdate + migrationResult.needsValidationFix} puzzles need updates`
        : `Migration completed: ${migrationResult.updated}/${migrationResult.totalPuzzles} puzzles updated`,
      mode: dryRun ? 'analysis' : 'migration',
      result: migrationResult,
      nextSteps: dryRun ? [
        'Review the analysis results',
        'Run with dryRun=false to perform the actual migration',
        'Consider migrating specific puzzle types first for testing'
      ] : [
        'Migration completed',
        'Test puzzle generation and fetching',
        'Monitor system for any issues'
      ]
    });

  } catch (error) {
    console.error('❌ Error in puzzle migration route:', error);
    res.status(500).json({
      success: false,
      message: 'Internal server error during migration',
      error: error.message
    });
  }
});

/**
 * Get migration analysis for specific puzzle type
 */
router.get('/migration-analysis/:puzzleType', async (req, res) => {
  try {
    const { puzzleType } = req.params;

    if (!Object.values(PUZZLE_TYPES).includes(puzzleType)) {
      return res.status(400).json({
        success: false,
        message: `Invalid puzzle type: ${puzzleType}`
      });
    }

    const analysis = await migrationService.analyzeMigrationNeeds(puzzleType);

    res.json({
      success: true,
      analysis,
      recommendations: [
        ...(analysis.needsDifficultyUpdate > 0 ? 
          [`${analysis.needsDifficultyUpdate} puzzles need difficulty updates`] : []),
        ...(analysis.needsValidationFix > 0 ? 
          [`${analysis.needsValidationFix} puzzles have validation issues`] : []),
        ...(analysis.needsDifficultyUpdate === 0 && analysis.needsValidationFix === 0 ? 
          ['All puzzles meet current standards'] : [])
      ]
    });

  } catch (error) {
    console.error('❌ Error in migration analysis route:', error);
    res.status(500).json({
      success: false,
      message: 'Internal server error during analysis',
      error: error.message
    });
  }
});

/**
 * Get current puzzle standards for a type
 */
router.get('/standards/:puzzleType', async (req, res) => {
  try {
    const { puzzleType } = req.params;

    if (!Object.values(PUZZLE_TYPES).includes(puzzleType)) {
      return res.status(400).json({
        success: false,
        message: `Invalid puzzle type: ${puzzleType}`
      });
    }

    const criteria = DIFFICULTY_CRITERIA[puzzleType];
    const metadata = PUZZLE_METADATA[puzzleType];

    res.json({
      success: true,
      puzzleType,
      standards: {
        difficulties: Object.keys(criteria || {}),
        validationMethod: metadata?.skipAIValidation ? 'programmatic' : 'ai_assisted',
        storageFormat: metadata?.storeFullJSONInQuestion ? 'json_in_question' : 'standard',
        category: metadata?.category || 'unknown'
      },
      difficultyCriteria: criteria || {},
      metadata: metadata || {}
    });

  } catch (error) {
    console.error('❌ Error in standards route:', error);
    res.status(500).json({
      success: false,
      message: 'Internal server error',
      error: error.message
    });
  }
});

/**
 * Bulk validate puzzles
 */
router.post('/bulk-validate', async (req, res) => {
  try {
    const { 
      puzzleType, 
      difficulty = null,
      limit = 100 
    } = req.body;

    if (!puzzleType || !Object.values(PUZZLE_TYPES).includes(puzzleType)) {
      return res.status(400).json({
        success: false,
        message: 'Valid puzzleType is required'
      });
    }

    console.log(`🔍 Bulk validating ${puzzleType} puzzles${difficulty ? ` (${difficulty})` : ''}`);

    let query = supabase
      .from('puzzles')
      .select('*')
      .ilike('type', puzzleType)
      .limit(limit);

    if (difficulty) {
      query = query.ilike('difficulty', difficulty);
    }

    const { data: puzzles, error } = await query;

    if (error) {
      throw new Error(`Database query failed: ${error.message}`);
    }

    const results = {
      totalChecked: puzzles?.length || 0,
      valid: 0,
      invalid: 0,
      difficultyMismatches: 0,
      issues: [],
      summary: {}
    };

    if (!puzzles || puzzles.length === 0) {
      return res.json({
        success: true,
        message: 'No puzzles found to validate',
        results
      });
    }

    for (const puzzle of puzzles) {
      try {
        const puzzleData = migrationService.parsePuzzleData(puzzle, puzzleType);
        const validation = await PuzzleValidator.validatePuzzle(puzzleData, puzzleType, puzzle.difficulty);
        const standardDifficulty = migrationService.determineDifficultyByStandards(puzzleData, puzzleType);

        if (validation.isValid) {
          results.valid++;
        } else {
          results.invalid++;
          results.issues.push({
            puzzleId: puzzle.puzzleid,
            type: 'validation',
            issue: validation.reason
          });
        }

        if (standardDifficulty && standardDifficulty !== puzzle.difficulty) {
          results.difficultyMismatches++;
          results.issues.push({
            puzzleId: puzzle.puzzleid,
            type: 'difficulty',
            issue: `Should be ${standardDifficulty}, currently ${puzzle.difficulty}`
          });
        }

      } catch (error) {
        results.invalid++;
        results.issues.push({
          puzzleId: puzzle.puzzleid,
          type: 'error',
          issue: error.message
        });
      }
    }

    // Generate summary by issue type
    results.summary = {
      validationRate: `${Math.round((results.valid / results.totalChecked) * 100)}%`,
      issueBreakdown: {
        validation: results.issues.filter(i => i.type === 'validation').length,
        difficulty: results.issues.filter(i => i.type === 'difficulty').length,
        error: results.issues.filter(i => i.type === 'error').length
      }
    };

    res.json({
      success: true,
      message: `Bulk validation completed: ${results.valid}/${results.totalChecked} puzzles valid`,
      results,
      recommendations: [
        ...(results.invalid > 0 ? ['Fix validation issues using migration endpoint'] : []),
        ...(results.difficultyMismatches > 0 ? ['Update difficulty assignments to match current standards'] : []),
        ...(results.valid === results.totalChecked ? ['All puzzles meet current standards'] : [])
      ]
    });

  } catch (error) {
    console.error('❌ Error in bulk validation route:', error);
    res.status(500).json({
      success: false,
      message: 'Internal server error during bulk validation',
      error: error.message
    });
  }
});

export default router;