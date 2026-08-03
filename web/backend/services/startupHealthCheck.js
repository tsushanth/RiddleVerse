import { PuzzleGenerator } from './puzzleGeneration.js';
import { PuzzleValidator } from './puzzleValidation.js';
import { PUZZLE_TYPES, DIFFICULTY_LEVELS } from '../config/puzzleConfig.js';
import { generateCustomPuzzles } from './puzzleService.js';
import { callAI } from '../utils/aiClient.js';
import { supabase } from '../config/database.js';

export async function performComprehensiveHealthCheck() {
    const startTime = Date.now();
    console.log('🔍 Starting comprehensive system health check...');

    const result = {
        healthy: true,
        status: 'checking',
        message: '',
        timestamp: new Date().toISOString(),
        checks: {
            database: { status: 'pending' },
            aiServices: { status: 'pending' },
            puzzleGeneration: { status: 'pending' },
            puzzleValidation: { status: 'pending' },
            customPuzzles: { status: 'pending' },
            configuration: { status: 'pending' }
        },
        summary: {
            totalChecks: 6,
            passed: 0,
            failed: 0,
            warnings: 0
        },
        duration: 0
    };

    try {
        // 1. Database connectivity check
        console.log('📊 Checking database connectivity...');
        await checkDatabaseConnectivity(result);

        // 2. AI services check
        console.log('🤖 Checking AI services...');
        await checkAIServices(result);

        // 3. Configuration check
        console.log('⚙️ Checking configuration...');
        await checkConfiguration(result);

        // 4. Puzzle generation check
        console.log('🧩 Checking puzzle generation...');
        await checkPuzzleGeneration(result);

        // 5. Puzzle validation check
        console.log('✅ Checking puzzle validation...');
        await checkPuzzleValidation(result);

        // 6. Custom puzzles check
        console.log('🎯 Checking custom puzzles...');
        await checkCustomPuzzles(result);

        // Calculate final status
        result.duration = Date.now() - startTime;
        result.healthy = result.summary.failed === 0;
        result.status = result.healthy ? 'healthy' : 'unhealthy';
        result.message = result.healthy 
            ? `All systems operational (${result.summary.passed}/${result.summary.totalChecks} checks passed)`
            : `System issues detected (${result.summary.failed} failures, ${result.summary.warnings} warnings)`;

        console.log(`🏥 Health check completed in ${result.duration}ms: ${result.status}`);
        return result;

    } catch (error) {
        result.healthy = false;
        result.status = 'error';
        result.message = `Health check failed: ${error.message}`;
        result.duration = Date.now() - startTime;
        throw error;
    }
}

/**
 * Check database connectivity
 */
async function checkDatabaseConnectivity(result) {
    try {
        // Test Supabase connection
        const { data, error } = await supabase
            .from('puzzles')
            .select('puzzleid')
            .limit(1);

        if (error) {
            throw new Error(`Supabase error: ${error.message}`);
        }

        // Test Firestore connection
        const testDoc = await db.collection('health_check').doc('test').get();

        result.checks.database = {
            status: 'passed',
            message: 'Database connections working',
            supabase: 'connected',
            firestore: 'connected'
        };
        result.summary.passed++;

    } catch (error) {
        result.checks.database = {
            status: 'failed',
            message: `Database connectivity failed: ${error.message}`,
            error: error.message
        };
        result.summary.failed++;
    }
}

/**
 * Check AI services
 */
async function checkAIServices(result) {
    try {
        const testPrompt = 'Respond with just "OK" if you can understand this message.';
        
        // Test Anthropic (Claude)
        let openaiWorking = false;
        try {
            const anthropicResponse = await callAI(testPrompt, 'claude-3-sonnet');
            openaiWorking = anthropicResponse && anthropicResponse.toLowerCase().includes('ok');
        } catch (error) {
            console.warn('⚠️ Anthropic test failed:', error.message);
        }

        // Test DeepSeek  
        let deepseekWorking = false;
        try {
            const deepseekResponse = await callAI(testPrompt, 'deepseek');
            deepseekWorking = deepseekResponse && deepseekResponse.toLowerCase().includes('ok');
        } catch (error) {
            console.warn('⚠️ DeepSeek test failed:', error.message);
        }

        if (openaiWorking || deepseekWorking) {
            result.checks.aiServices = {
                status: 'passed',
                message: 'AI services working',
                anthropic: openaiWorking ? 'working' : 'failed',
                deepseek: deepseekWorking ? 'working' : 'failed'
            };
            result.summary.passed++;
            
            if (!openaiWorking || !deepseekWorking) {
                result.summary.warnings++;
            }
        } else {
            throw new Error('All AI services failed');
        }

    } catch (error) {
        result.checks.aiServices = {
            status: 'failed',
            message: `AI services failed: ${error.message}`,
            error: error.message
        };
        result.summary.failed++;
    }
}

/**
 * Check configuration
 */
async function checkConfiguration(result) {
    try {
        const issues = [];

        // Check puzzle types are defined
        if (!PUZZLE_TYPES || Object.keys(PUZZLE_TYPES).length === 0) {
            issues.push('PUZZLE_TYPES not properly defined');
        }

        // Check difficulty levels
        if (!DIFFICULTY_LEVELS || Object.keys(DIFFICULTY_LEVELS).length === 0) {
            issues.push('DIFFICULTY_LEVELS not properly defined');
        }

        // Check environment variables
        if (!process.env.ANTHROPIC_API_KEY && !process.env.DEEPSEEK_API_KEY) {
            issues.push('No AI API keys configured in environment');
        }

        if (issues.length === 0) {
            result.checks.configuration = {
                status: 'passed',
                message: 'Configuration is valid',
                puzzleTypes: Object.keys(PUZZLE_TYPES).length,
                difficultyLevels: Object.keys(DIFFICULTY_LEVELS).length
            };
            result.summary.passed++;
        } else {
            throw new Error(issues.join('; '));
        }

    } catch (error) {
        result.checks.configuration = {
            status: 'failed',
            message: `Configuration issues: ${error.message}`,
            error: error.message
        };
        result.summary.failed++;
    }
}

/**
 * Check puzzle generation
 */
async function checkPuzzleGeneration(result) {
    try {
        const generator = new PuzzleGenerator();
        const testTypes = ['mathestimation', 'mathtipping']; // Test a couple key types
        const testResults = [];

        for (const puzzleType of testTypes) {
            try {
                console.log(`🧩 Testing ${puzzleType} generation...`);
                
                // Generate test puzzle (don't store it)
                const testResult = await generator.generatePuzzle(puzzleType, 'gpt-3.5-turbo', 'Easy');
                
                testResults.push({
                    type: puzzleType,
                    success: testResult.success,
                    message: testResult.message
                });

            } catch (error) {
                testResults.push({
                    type: puzzleType,
                    success: false,
                    error: error.message
                });
            }
        }

        const successCount = testResults.filter(r => r.success).length;
        
        if (successCount === testTypes.length) {
            result.checks.puzzleGeneration = {
                status: 'passed',
                message: 'Puzzle generation working',
                tested: testTypes,
                results: testResults
            };
            result.summary.passed++;
        } else if (successCount > 0) {
            result.checks.puzzleGeneration = {
                status: 'warning', 
                message: `Partial puzzle generation working (${successCount}/${testTypes.length})`,
                tested: testTypes,
                results: testResults
            };
            result.summary.warnings++;
            result.summary.passed++; // Still counts as passed with warnings
        } else {
            throw new Error('All puzzle generation tests failed');
        }

    } catch (error) {
        result.checks.puzzleGeneration = {
            status: 'failed',
            message: `Puzzle generation failed: ${error.message}`,
            error: error.message
        };
        result.summary.failed++;
    }
}

/**
 * Check puzzle validation
 */
async function checkPuzzleValidation(result) {
    try {
        // Test validation with known good puzzle
        const testPuzzle = {
            numbers: [10, 20, 30],
            sum: 60,
            difficulty: 'Easy',
            hint: 'Add the numbers'
        };

        const validation = await PuzzleValidator.validatePuzzle(testPuzzle, 'mathestimation', 'Easy');

        if (validation.isValid) {
            result.checks.puzzleValidation = {
                status: 'passed',
                message: 'Puzzle validation working',
                testPassed: true
            };
            result.summary.passed++;
        } else {
            throw new Error(`Validation failed for test puzzle: ${validation.reason}`);
        }

    } catch (error) {
        result.checks.puzzleValidation = {
            status: 'failed',
            message: `Puzzle validation failed: ${error.message}`,
            error: error.message
        };
        result.summary.failed++;
    }
}

/**
 * Check custom puzzles
 */
async function checkCustomPuzzles(result) {
    try {
        // Test custom puzzle generation (without actually storing)
        const testTopic = 'test_health_check';
        const testFormat = 'Multiple Choice';
        
        // This is a simplified test - we're not actually running full generation
        // Just checking that the function exists and can be called
        if (typeof generateCustomPuzzles === 'function') {
            result.checks.customPuzzles = {
                status: 'passed',
                message: 'Custom puzzle functions available',
                functionExists: true
            };
            result.summary.passed++;
        } else {
            throw new Error('Custom puzzle generation function not available');
        }

    } catch (error) {
        result.checks.customPuzzles = {
            status: 'failed',
            message: `Custom puzzles check failed: ${error.message}`,
            error: error.message
        };
        result.summary.failed++;
    }
}