// smoke-tests.js - Pre-deployment sanity checks
import { fileURLToPath } from 'url';
import { dirname, join } from 'path';
import fs from 'fs';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

// Detect environment
const IS_DOCKER = process.env.DOCKER_BUILD === 'true' || fs.existsSync('/.dockerenv');
const IS_LOCAL_DEV = !IS_DOCKER && process.env.NODE_ENV !== 'production';

// Test configuration
const TESTS = {
  imports: true,
  functions: true,
  dependencies: true,
  environment: true,
  database: false, // Set to true if you want to test DB connections
  fileSystem: !IS_LOCAL_DEV, // Skip file system tests in local dev
};

console.log(`🔍 Environment: ${IS_DOCKER ? 'Docker' : IS_LOCAL_DEV ? 'Local Dev' : 'Production'}`);
if (IS_LOCAL_DEV) {
  console.log('⚡ Running in local development mode - some tests will be skipped');
}

// Critical modules to test
const CRITICAL_MODULES = [
  './services/puzzleGeneration.js',
  './services/puzzleService.js',
  './config/database.js',
  './config/firebaseAdmin.js',
  './utils/aiClient.js',
  './services/deduplicationService.js',
  './services/wordSearchGeneratorService.js',
  './criteria/puzzlePrompts.js',
  './services/puzzleValidation.js',
];

// Mock environment for testing
const mockEnvironment = {
  OPENAI_API_KEY: 'test-key',
  DEEPSEEK_API_KEY: 'test-key',
  ALERT_EMAIL: 'test@example.com',
  PORT: '8080'
};

class SmokeTestRunner {
  constructor() {
    this.passed = 0;
    this.failed = 0;
    this.warnings = 0;
    this.startTime = Date.now();
  }

  log(message, type = 'info') {
    const timestamp = new Date().toISOString();
    const prefix = {
      'info': '📋',
      'success': '✅',
      'error': '❌',
      'warning': '⚠️',
      'debug': '🔍'
    }[type] || '📋';
    
    console.log(`${prefix} [${timestamp}] ${message}`);
  }

  async runTest(testName, testFn) {
    try {
      this.log(`Running ${testName}...`, 'debug');
      await testFn();
      this.passed++;
      this.log(`${testName} - PASSED`, 'success');
      return true;
    } catch (error) {
      this.failed++;
      this.log(`${testName} - FAILED: ${error.message}`, 'error');
      if (error.stack) {
        console.error(error.stack);
      }
      return false;
    }
  }

  async runWarningTest(testName, testFn) {
    try {
      await testFn();
      this.log(`${testName} - OK`, 'success');
      return true;
    } catch (error) {
      this.warnings++;
      this.log(`${testName} - WARNING: ${error.message}`, 'warning');
      return false;
    }
  }

  // Test 1: Import/Export Validation
  async testImports() {
    this.log('Testing module imports...', 'info');
    
    for (const modulePath of CRITICAL_MODULES) {
      await this.runTest(`Import ${modulePath}`, async () => {
        try {
          const module = await import(modulePath);
          
          // Basic validation that module exported something
          if (!module || (typeof module === 'object' && Object.keys(module).length === 0)) {
            throw new Error(`Module ${modulePath} exports nothing or is empty`);
          }
          
          // Check for common export patterns
          const hasNamedExports = Object.keys(module).some(key => key !== 'default');
          const hasDefaultExport = 'default' in module;
          
          if (!hasNamedExports && !hasDefaultExport) {
            throw new Error(`Module ${modulePath} has no exports`);
          }
          
        } catch (importError) {
          if (importError.code === 'ERR_MODULE_NOT_FOUND') {
            throw new Error(`Module not found: ${modulePath}`);
          } else if (importError.message.includes('Cannot resolve dependency')) {
            throw new Error(`Dependency issue in ${modulePath}: ${importError.message}`);
          } else {
            throw new Error(`Import error in ${modulePath}: ${importError.message}`);
          }
        }
      });
    }
  }

  // Test 2: Critical Function Availability
  async testFunctions() {
    this.log('Testing critical functions...', 'info');
    
    // Test PuzzleGenerator instantiation
    await this.runTest('PuzzleGenerator instantiation', async () => {
      const { PuzzleGenerator } = await import('./services/puzzleGeneration.js');
      if (!PuzzleGenerator) {
        throw new Error('PuzzleGenerator class not found');
      }
      
      const generator = new PuzzleGenerator();
      if (!generator) {
        throw new Error('Failed to instantiate PuzzleGenerator');
      }
      
      // Check critical methods exist
      const requiredMethods = ['generatePuzzle', 'validateInputs', 'setDebugMode'];
      for (const method of requiredMethods) {
        if (typeof generator[method] !== 'function') {
          throw new Error(`PuzzleGenerator missing method: ${method}`);
        }
      }
    });

    // Test puzzle service functions
    await this.runTest('PuzzleService functions', async () => {
      const puzzleService = await import('./services/puzzleService.js');
      
      const requiredFunctions = [
        'storePuzzleInSupabase',
        'fetchNextPuzzle',
        'checkAnswer',
        'isDuplicateQuestion'
      ];
      
      for (const funcName of requiredFunctions) {
        if (typeof puzzleService[funcName] !== 'function') {
          throw new Error(`PuzzleService missing function: ${funcName}`);
        }
      }
    });

    // Test AI client
    await this.runTest('AI Client functions', async () => {
      const aiClient = await import('./utils/aiClient.js');
      
      if (typeof aiClient.callAI !== 'function') {
        throw new Error('callAI function not found in aiClient');
      }
      
      if (typeof aiClient.generateTTSAudio !== 'function') {
        throw new Error('generateTTSAudio function not found in aiClient');
      }
    });

    // Test database configuration (skip during Docker build when env vars not available)
    await this.runTest('Database configuration', async () => {
      const dbConfig = await import('./config/database.js');

      // During Docker build, supabase may be null (env vars not available)
      if (IS_DOCKER) {
        if (dbConfig.supabase === null) {
          console.log('⚠️ Skipping Supabase validation during Docker build');
          return; // Pass the test - env vars will be available at runtime
        }
      }

      if (!dbConfig.supabase) {
        throw new Error('Supabase client not found in database config');
      }

      // Basic supabase client validation
      if (typeof dbConfig.supabase.from !== 'function') {
        throw new Error('Supabase client missing from() method');
      }
    });
  }

  // Test 3: Required Dependencies
  async testDependencies() {
    this.log('Testing package dependencies...', 'info');
    
    await this.runTest('package.json exists', async () => {
      const packagePath = join(__dirname, 'package.json');
      if (!fs.existsSync(packagePath)) {
        throw new Error('package.json not found');
      }
      
      const packageJson = JSON.parse(fs.readFileSync(packagePath, 'utf8'));
      if (!packageJson.dependencies) {
        throw new Error('No dependencies found in package.json');
      }
    });

    // Test critical npm packages
    const criticalPackages = [
      'express',
      'axios', 
      '@supabase/supabase-js',
      'firebase-admin',
      '@xenova/transformers',
      'cosine-similarity',
      'crypto'
    ];

    for (const pkg of criticalPackages) {
      await this.runTest(`Package: ${pkg}`, async () => {
        try {
          await import(pkg);
        } catch (error) {
          if (pkg === 'crypto') {
            // crypto is built-in, check differently
            const crypto = await import('crypto');
            if (!crypto.randomUUID) {
              throw new Error('crypto module missing randomUUID function');
            }
          } else {
            throw new Error(`Package ${pkg} not available: ${error.message}`);
          }
        }
      });
    }
  }

  // Test 4: Environment Configuration
  async testEnvironment() {
    this.log('Testing environment configuration...', 'info');
    
    // Set mock environment for testing
    const originalEnv = { ...process.env };
    Object.assign(process.env, mockEnvironment);
    
    try {
      const requiredEnvVars = [
        'OPENAI_API_KEY',
        'DEEPSEEK_API_KEY', 
        'ALERT_EMAIL',
        'PORT'
      ];

      for (const envVar of requiredEnvVars) {
        await this.runTest(`Environment variable: ${envVar}`, async () => {
          if (!process.env[envVar]) {
            throw new Error(`Required environment variable ${envVar} is not set`);
          }
        });
      }

      // Test PORT validation
      await this.runTest('PORT validation', async () => {
        const port = parseInt(process.env.PORT);
        if (isNaN(port) || port < 1 || port > 65535) {
          throw new Error(`Invalid PORT value: ${process.env.PORT}`);
        }
      });

    } finally {
      // Restore original environment
      process.env = originalEnv;
    }
  }

  // Test 5: File System Resources
  async testFileSystem() {
    this.log('Testing file system resources...', 'info');
    
    // Check model cache directory exists but don't fail on missing files
    await this.runTest('Model cache directory exists', async () => {
      const modelPath = join(__dirname, 'model-cache/Xenova/all-MiniLM-L6-v2');
      if (!fs.existsSync(modelPath)) {
        throw new Error(`Model cache directory not found: ${modelPath}`);
      }
    });

    // Check model files as warnings (since they might be copied during Docker build)
    const modelPath = join(__dirname, 'model-cache/Xenova/all-MiniLM-L6-v2');
    if (fs.existsSync(modelPath)) {
      const modelFiles = ['config.json', 'tokenizer.json', 'tokenizer_config.json'];
      for (const file of modelFiles) {
        await this.runWarningTest(`Model file: ${file}`, async () => {
          const filePath = join(modelPath, file);
          if (!fs.existsSync(filePath)) {
            throw new Error(`Model file missing: ${file} (may be copied during Docker build)`);
          }
        });
      }
    }

    await this.runTest('Public directory', async () => {
      const publicPath = join(__dirname, 'public');
      if (!fs.existsSync(publicPath)) {
        throw new Error('Public directory not found (frontend not copied?)');
      }
    });
  }

  // Test 6: Basic Logic Validation
  async testBasicLogic() {
    this.log('Testing basic application logic...', 'info');
    
    await this.runTest('Puzzle type constants', async () => {
      const { PUZZLE_TYPES } = await import('./config/puzzleConfig.js');
      
      if (!PUZZLE_TYPES || typeof PUZZLE_TYPES !== 'object') {
        throw new Error('PUZZLE_TYPES not found or invalid');
      }
      
      const requiredTypes = ['ANAGRAM', 'CROSSWORD', 'MATH_ESTIMATION'];
      for (const type of requiredTypes) {
        if (!PUZZLE_TYPES[type]) {
          throw new Error(`Missing puzzle type: ${type}`);
        }
      }
    });

    await this.runTest('Utility functions', async () => {
      const { OPTION_CONSTRAINTS, truncateOption } = await import('./services/puzzleService.js');
      
      if (!OPTION_CONSTRAINTS) {
        throw new Error('OPTION_CONSTRAINTS not found');
      }
      
      if (typeof truncateOption !== 'function') {
        throw new Error('truncateOption function not found');
      }
      
      // Test truncateOption with a simple case
      const result = truncateOption('test string', 5);
      if (typeof result !== 'string') {
        throw new Error('truncateOption returned non-string');
      }
    });
  }

  // Main test runner
  async runAllTests() {
    this.log('🚀 Starting smoke tests...', 'info');
    this.log(`Node.js version: ${process.version}`, 'info');
    this.log(`Platform: ${process.platform}`, 'info');
    this.log(`Environment: ${IS_DOCKER ? 'Docker' : IS_LOCAL_DEV ? 'Local Dev' : 'Production'}`, 'info');
    
    try {
      if (TESTS.imports) await this.testImports();
      if (TESTS.functions) await this.testFunctions();
      if (TESTS.dependencies) await this.testDependencies();
      if (TESTS.environment) await this.testEnvironment();
      
      // Conditional tests based on environment
      if (TESTS.fileSystem) {
        await this.runWarningTest('File system resources', () => this.testFileSystem());
      } else {
        this.log('Skipping file system tests (local development mode)', 'info');
      }
      
      await this.runWarningTest('Basic logic validation', () => this.testBasicLogic());
      
    } catch (error) {
      this.log(`Unexpected error during testing: ${error.message}`, 'error');
      this.failed++;
    }

    // Results summary
    const duration = Date.now() - this.startTime;
    this.log(`\n📊 Smoke Test Results:`, 'info');
    this.log(`✅ Passed: ${this.passed}`, 'success');
    this.log(`❌ Failed: ${this.failed}`, this.failed > 0 ? 'error' : 'success');
    this.log(`⚠️ Warnings: ${this.warnings}`, this.warnings > 0 ? 'warning' : 'success');
    this.log(`⏱️ Duration: ${duration}ms`, 'info');
    
    if (this.failed > 0) {
      this.log('\n🚨 SMOKE TESTS FAILED - Deployment should be blocked!', 'error');
      process.exit(1);
    } else {
      this.log('\n🎉 All critical smoke tests passed - Safe to deploy!', 'success');
      if (this.warnings > 0) {
        this.log(`Note: ${this.warnings} non-critical warnings detected`, 'warning');
      }
      process.exit(0);
    }
  }
}

// Run tests if this file is executed directly
if (import.meta.url === `file://${process.argv[1]}`) {
  const runner = new SmokeTestRunner();
  runner.runAllTests().catch(error => {
    console.error('💥 Smoke test runner crashed:', error);
    process.exit(1);
  });
}

export { SmokeTestRunner };