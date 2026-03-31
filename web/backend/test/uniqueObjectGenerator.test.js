// uniqueObjectGenerator.test.js

import { UniqueObjectGenerator } from './uniqueObjectGenerator.js';

/**
 * Comprehensive unit test for UniqueObjectGenerator
 * Tests 10 runs across different difficulties and validates results
 */
class UniqueObjectGeneratorTest {
  constructor() {
    this.generator = new UniqueObjectGenerator();
    this.generator.setDebugMode(true);
    this.testResults = [];
  }

  /**
   * Validate a single puzzle's correctness
   */
  validatePuzzle(puzzleData, difficulty, runNumber) {
    const result = {
      runNumber,
      difficulty,
      valid: false,
      errors: [],
      warnings: [],
      stats: {}
    };

    try {
      // Parse the puzzle data
      const questionData = JSON.parse(puzzleData.question);
      const answerData = JSON.parse(puzzleData.answer);
      
      const objects = questionData.objects;
      const uniqueObjectIndex = answerData.uniqueObjectIndex;
      
      result.stats.totalObjects = objects.length;
      result.stats.uniqueObjectIndex = uniqueObjectIndex;
      
      // 1. BASIC STRUCTURE VALIDATION
      if (!Array.isArray(objects) || objects.length === 0) {
        result.errors.push("Invalid or empty objects array");
        return result;
      }
      
      if (typeof uniqueObjectIndex !== 'number' || uniqueObjectIndex < 0 || uniqueObjectIndex >= objects.length) {
        result.errors.push(`Invalid uniqueObjectIndex: ${uniqueObjectIndex}, array length: ${objects.length}`);
        return result;
      }
      
      // 2. COUNT ALL SHAPE-COLOR COMBINATIONS
      const combinations = new Map();
      objects.forEach((obj, idx) => {
        if (typeof obj.shape !== 'number' || typeof obj.color !== 'number') {
          result.errors.push(`Invalid object at index ${idx}: shape=${obj.shape}, color=${obj.color}`);
          return;
        }
        
        const combo = `${obj.shape}-${obj.color}`;
        if (!combinations.has(combo)) {
          combinations.set(combo, []);
        }
        combinations.get(combo).push(idx);
      });
      
      if (result.errors.length > 0) return result;
      
      // 3. FIND UNIQUE COMBINATIONS
      const uniqueCombos = Array.from(combinations.entries()).filter(([combo, indices]) => indices.length === 1);
      const duplicateCombos = Array.from(combinations.entries()).filter(([combo, indices]) => indices.length > 1);
      
      result.stats.combinations = Object.fromEntries(combinations);
      result.stats.uniqueComboCount = uniqueCombos.length;
      result.stats.duplicateComboCount = duplicateCombos.length;
      
      // 4. VALIDATE UNIQUE OBJECT COUNT
      if (uniqueCombos.length === 0) {
        result.errors.push("FATAL: No unique objects found!");
        result.stats.allCombinations = Array.from(combinations.entries()).map(([combo, indices]) => 
          `${combo}: ${indices.length} times at [${indices.join(', ')}]`
        );
        return result;
      }
      
      if (uniqueCombos.length > 1) {
        result.errors.push(`FATAL: Multiple unique objects found! Expected 1, got ${uniqueCombos.length}`);
        result.stats.multipleUniques = uniqueCombos.map(([combo, indices]) => 
          `${combo} at index ${indices[0]}`
        );
        return result;
      }
      
      // 5. VALIDATE UNIQUE OBJECT INDEX
      const [actualUniqueCombo, [actualUniqueIdx]] = uniqueCombos[0];
      const expectedUniqueObj = objects[uniqueObjectIndex];
      const expectedCombo = `${expectedUniqueObj.shape}-${expectedUniqueObj.color}`;
      
      if (actualUniqueIdx !== uniqueObjectIndex) {
        result.errors.push(`INDEX MISMATCH: Answer claims unique at ${uniqueObjectIndex}, but actually at ${actualUniqueIdx}`);
        return result;
      }
      
      if (actualUniqueCombo !== expectedCombo) {
        result.errors.push(`COMBINATION MISMATCH: Expected ${expectedCombo}, but found ${actualUniqueCombo} at index ${uniqueObjectIndex}`);
        return result;
      }
      
      // 6. VALIDATE DUPLICATE GROUPS
      const minDuplicates = 2;
      for (const [combo, indices] of duplicateCombos) {
        if (indices.length < minDuplicates) {
          result.errors.push(`Invalid duplicate group: ${combo} appears ${indices.length} times (minimum ${minDuplicates})`);
        }
      }
      
      // 7. VALIDATE DIFFICULTY CONSTRAINTS
      const difficultySettings = this.generator.difficultySettings[difficulty.toLowerCase()];
      if (difficultySettings) {
        if (objects.length !== difficultySettings.totalObjects) {
          result.warnings.push(`Object count mismatch: got ${objects.length}, expected ${difficultySettings.totalObjects} for ${difficulty}`);
        }
      }
      
      // 8. SUCCESS!
      if (result.errors.length === 0) {
        result.valid = true;
        result.stats.uniqueCombo = actualUniqueCombo;
        result.stats.uniqueIndex = actualUniqueIdx;
        result.stats.duplicateGroups = duplicateCombos.map(([combo, indices]) => 
          `${combo}: ${indices.length} times`
        );
      }
      
    } catch (error) {
      result.errors.push(`Exception during validation: ${error.message}`);
    }
    
    return result;
  }

  /**
   * Run comprehensive tests
   */
  async runTests() {
    console.log('🧪 Starting Unique Object Generator Tests...\n');
    
    const difficulties = ['easy', 'medium', 'hard'];
    const runsPerDifficulty = 4; // Total: 12 runs
    let totalRuns = 0;
    
    for (const difficulty of difficulties) {
      console.log(`\n🎯 Testing ${difficulty.toUpperCase()} difficulty (${runsPerDifficulty} runs):`);
      console.log('=' .repeat(50));
      
      for (let run = 1; run <= runsPerDifficulty; run++) {
        totalRuns++;
        
        try {
          console.log(`\n🔄 Run ${totalRuns}: ${difficulty} #${run}`);
          
          // Generate puzzle
          const result = await this.generator.generateUniqueObjectPuzzle(difficulty);
          
          if (!result.success) {
            console.log(`❌ Generation failed: ${result.message}`);
            this.testResults.push({
              runNumber: totalRuns,
              difficulty,
              valid: false,
              errors: [`Generation failed: ${result.message}`]
            });
            continue;
          }
          
          // Validate puzzle
          const validation = this.validatePuzzle(result.puzzleData, difficulty, totalRuns);
          this.testResults.push(validation);
          
          // Report results
          if (validation.valid) {
            console.log(`✅ PASS: Valid puzzle generated`);
            console.log(`   📊 ${validation.stats.totalObjects} objects, unique: ${validation.stats.uniqueCombo} at index ${validation.stats.uniqueIndex}`);
            console.log(`   🔄 Duplicate groups: ${validation.stats.duplicateGroups.join(', ')}`);
            
            if (validation.warnings.length > 0) {
              console.log(`   ⚠️  Warnings: ${validation.warnings.join(', ')}`);
            }
          } else {
            console.log(`❌ FAIL: Invalid puzzle`);
            validation.errors.forEach(error => console.log(`     💥 ${error}`));
            
            if (validation.stats.combinations) {
              console.log(`     📊 Actual combinations:`);
              Object.entries(validation.stats.combinations).forEach(([combo, indices]) => {
                console.log(`        ${combo}: ${indices.length} times at [${indices.join(', ')}]`);
              });
            }
          }
          
        } catch (error) {
          console.log(`💥 Exception in run ${totalRuns}: ${error.message}`);
          this.testResults.push({
            runNumber: totalRuns,
            difficulty,
            valid: false,
            errors: [`Exception: ${error.message}`]
          });
        }
      }
    }
    
    // Print summary
    this.printSummary();
  }

  /**
   * Print test summary
   */
  printSummary() {
    console.log('\n' + '=' .repeat(60));
    console.log('📋 TEST SUMMARY');
    console.log('=' .repeat(60));
    
    const totalTests = this.testResults.length;
    const passedTests = this.testResults.filter(r => r.valid).length;
    const failedTests = totalTests - passedTests;
    
    console.log(`\n🎯 Overall Results:`);
    console.log(`   ✅ Passed: ${passedTests}/${totalTests} (${((passedTests/totalTests)*100).toFixed(1)}%)`);
    console.log(`   ❌ Failed: ${failedTests}/${totalTests} (${((failedTests/totalTests)*100).toFixed(1)}%)`);
    
    // Group by difficulty
    const byDifficulty = {};
    this.testResults.forEach(result => {
      if (!byDifficulty[result.difficulty]) {
        byDifficulty[result.difficulty] = { passed: 0, failed: 0 };
      }
      if (result.valid) {
        byDifficulty[result.difficulty].passed++;
      } else {
        byDifficulty[result.difficulty].failed++;
      }
    });
    
    console.log(`\n📊 Results by Difficulty:`);
    Object.entries(byDifficulty).forEach(([difficulty, stats]) => {
      const total = stats.passed + stats.failed;
      const passRate = ((stats.passed / total) * 100).toFixed(1);
      console.log(`   ${difficulty.toUpperCase()}: ${stats.passed}/${total} passed (${passRate}%)`);
    });
    
    // Show failed tests
    const failedTests_details = this.testResults.filter(r => !r.valid);
    if (failedTests_details.length > 0) {
      console.log(`\n❌ Failed Test Details:`);
      failedTests_details.forEach(result => {
        console.log(`   Run ${result.runNumber} (${result.difficulty}):`);
        result.errors.forEach(error => console.log(`     💥 ${error}`));
      });
    }
    
    console.log('\n' + '=' .repeat(60));
    console.log(passedTests === totalTests ? '🎉 ALL TESTS PASSED!' : '⚠️  SOME TESTS FAILED - CHECK GENERATOR LOGIC');
    console.log('=' .repeat(60));
  }
}

/**
 * Quick validation function for use in browser console
 */
function quickValidate(puzzleDataString) {
  try {
    const puzzleData = JSON.parse(puzzleDataString);
    const objects = puzzleData.objects;
    
    console.log(`📊 Analyzing ${objects.length} objects:`);
    
    // Count combinations
    const combinations = {};
    objects.forEach((obj, idx) => {
      const combo = `${obj.shape}-${obj.color}`;
      if (!combinations[combo]) combinations[combo] = [];
      combinations[combo].push(idx);
    });
    
    // Report results
    Object.entries(combinations).forEach(([combo, indices]) => {
      const status = indices.length === 1 ? '🎯 UNIQUE' : `🔄 x${indices.length}`;
      console.log(`   ${combo}: ${status} at [${indices.join(', ')}]`);
    });
    
    const uniqueCount = Object.values(combinations).filter(indices => indices.length === 1).length;
    console.log(`\n${uniqueCount === 1 ? '✅ VALID' : '❌ INVALID'}: ${uniqueCount} unique combination(s) found`);
    
    return { valid: uniqueCount === 1, combinations };
    
  } catch (error) {
    console.log(`❌ Error: ${error.message}`);
    return { valid: false, error: error.message };
  }
}

// Export for different environments
if (typeof module !== 'undefined' && module.exports) {
  module.exports = { UniqueObjectGeneratorTest, quickValidate };
} else if (typeof window !== 'undefined') {
  window.UniqueObjectGeneratorTest = UniqueObjectGeneratorTest;
  window.quickValidate = quickValidate;
}

// Auto-run the tests immediately
(async () => {
  console.log('🚀 Auto-running tests...\n');
  const test = new UniqueObjectGeneratorTest();
  await test.runTests();
})().catch(console.error);

console.log('🧪 Unique Object Generator Test Suite Loaded');
console.log('💡 Usage:');
console.log('   const test = new UniqueObjectGeneratorTest();');
console.log('   await test.runTests();');
console.log('');
console.log('🔍 Quick validation:');
console.log('   quickValidate(puzzleDataString);');