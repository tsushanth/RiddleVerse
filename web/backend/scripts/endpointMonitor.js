#!/usr/bin/env node

import https from 'https';
import http from 'http';
import { URL } from 'url';

// Configuration
const BASE_URL = 'https://puzzleverseai.com';
const TIMEOUT = 10000; // 10 seconds
const USER_AGENT = 'PuzzleVerse-Monitor/1.0';

// Test data for POST requests
const TEST_DATA = {
    userId: 'test-user-123',
    email: 'test@example.com',
    puzzleType: 'math',
    difficulty: 'easy',
    token: 'test-token'
};

// Endpoints to test
const ENDPOINTS = [
    // Health checks
    { method: 'GET', path: '/api/health', name: 'Health Check' },
    { method: 'GET', path: '/api/health/detailed', name: 'Detailed Health Check' },
    
    // Public endpoints
    { method: 'GET', path: '/', name: 'Home Page' },
    { method: 'GET', path: '/download', name: 'Download Page' },
    { method: 'GET', path: '/app-ads.txt', name: 'App Ads File' },
    
    // Well-known files
    { method: 'GET', path: '/.well-known/apple-app-site-association', name: 'Apple App Site Association' },
    { method: 'GET', path: '/.well-known/assetlinks.json', name: 'Android Asset Links' },
    
    // Authentication endpoints
    { method: 'GET', path: '/subscription-status', name: 'Subscription Status', params: { userId: TEST_DATA.userId } },
    
    // Puzzle endpoints
    { method: 'GET', path: '/fetch-next-puzzle-ios/math', name: 'Fetch Next Puzzle iOS', params: { userId: TEST_DATA.userId, email: TEST_DATA.email } },
    { method: 'GET', path: '/generation-status', name: 'Generation Status', params: { puzzleType: 'math', difficulty: 'easy' } },
    { method: 'GET', path: '/generation-status/math', name: 'Generation Status by Type', params: { email: TEST_DATA.email } },
    { method: 'GET', path: '/generate-puzzle', name: 'Generate Puzzle', params: { puzzleType: 'math', modelName: 'gpt-3.5-turbo', difficulty: 'easy' } },
    
    // Custom puzzle endpoints
    { method: 'GET', path: '/list-custom-puzzles', name: 'List Custom Puzzles' },
    { method: 'GET', path: '/fetch-custom-puzzle', name: 'Fetch Custom Puzzle', params: { puzzleId: 'test-puzzle-123' } },
    { method: 'GET', path: '/get-featured-puzzles', name: 'Get Featured Puzzles' },
    { method: 'GET', path: '/featured-ai-puzzles', name: 'Featured AI Puzzles' },
    
    // Daily puzzles
    { method: 'GET', path: '/list-user-daily-topics', name: 'List User Daily Topics', params: { email: TEST_DATA.email } },
    
    // Leaderboard endpoints
    { method: 'GET', path: '/leaderboard', name: 'Global Leaderboard' },
    { method: 'GET', path: '/get-puzzle-leaderboard/test-puzzle', name: 'Puzzle Leaderboard' },
    { method: 'GET', path: '/get-score', name: 'Get User Score', params: { userId: TEST_DATA.userId } },
    
    // User endpoints
    { method: 'GET', path: '/get-user-rewards-badges', name: 'User Rewards & Badges', params: { email: TEST_DATA.email } },
    
    // Notification endpoints
    { method: 'GET', path: '/api/notifications/stats', name: 'Notification Stats' },
    { method: 'GET', path: '/api/streak-notifications/status', name: 'Streak Notifications Status' },
    
    
    // POST endpoints with minimal test data
    { 
        method: 'POST', 
        path: '/check-answer', 
        name: 'Check Answer',
        body: { 
            question: 'What is 2+2?', 
            expected_answer: '4', 
            guessed_answer: '4' 
        }
    },
    { 
        method: 'POST', 
        path: '/api/puzzles/check-answer', 
        name: 'Check Answer (Legacy)',
        body: { 
            question: 'What is 2+2?', 
            expected_answer: '4', 
            guessed_answer: '4' 
        }
    },
    { 
        method: 'POST', 
        path: '/call-llm', 
        name: 'Call LLM',
        body: { 
            conversation: [
                { role: 'user', content: 'Hello' }
            ]
        }
    }
];

// Color codes for terminal output
const colors = {
    reset: '\x1b[0m',
    bright: '\x1b[1m',
    red: '\x1b[31m',
    green: '\x1b[32m',
    yellow: '\x1b[33m',
    blue: '\x1b[34m',
    magenta: '\x1b[35m',
    cyan: '\x1b[36m'
};

// Results storage
const results = {
    total: 0,
    passed: 0,
    failed: 0,
    errors: [],
    details: []
};

function makeRequest(endpoint) {
    return new Promise((resolve) => {
        const url = new URL(endpoint.path, BASE_URL);
        
        // Add query parameters for GET requests
        if (endpoint.method === 'GET' && endpoint.params) {
            Object.keys(endpoint.params).forEach(key => {
                url.searchParams.append(key, endpoint.params[key]);
            });
        }
        
        const options = {
            method: endpoint.method,
            headers: {
                'User-Agent': USER_AGENT,
                'Accept': 'application/json',
                'Content-Type': 'application/json'
            },
            timeout: TIMEOUT
        };
        
        let postData = '';
        if (endpoint.method === 'POST' && endpoint.body) {
            postData = JSON.stringify(endpoint.body);
            options.headers['Content-Length'] = Buffer.byteLength(postData);
        }
        
        const startTime = Date.now();
        
        const req = (url.protocol === 'https:' ? https : http).request(url, options, (res) => {
            const duration = Date.now() - startTime;
            let data = '';
            
            res.on('data', (chunk) => {
                data += chunk;
            });
            
            res.on('end', () => {
                const result = {
                    name: endpoint.name,
                    path: endpoint.path,
                    method: endpoint.method,
                    status: res.statusCode,
                    duration,
                    success: res.statusCode < 400,
                    headers: res.headers,
                    bodySize: data.length
                };
                
                // Try to parse JSON response
                try {
                    result.response = JSON.parse(data);
                } catch (e) {
                    result.response = data.substring(0, 200); // First 200 chars for HTML/text
                }
                
                resolve(result);
            });
        });
        
        req.on('error', (error) => {
            resolve({
                name: endpoint.name,
                path: endpoint.path,
                method: endpoint.method,
                success: false,
                error: error.message,
                duration: Date.now() - startTime
            });
        });
        
        req.on('timeout', () => {
            req.destroy();
            resolve({
                name: endpoint.name,
                path: endpoint.path,
                method: endpoint.method,
                success: false,
                error: 'Request timeout',
                duration: TIMEOUT
            });
        });
        
        if (postData) {
            req.write(postData);
        }
        
        req.end();
    });
}

function formatDuration(ms) {
    if (ms < 1000) return `${ms}ms`;
    return `${(ms / 1000).toFixed(2)}s`;
}

function printResult(result) {
    const status = result.success ? 
        `${colors.green}✓ PASS${colors.reset}` : 
        `${colors.red}✗ FAIL${colors.reset}`;
    
    const duration = formatDuration(result.duration);
    const statusCode = result.status ? ` (${result.status})` : '';
    
    console.log(`${status} ${colors.cyan}${result.method}${colors.reset} ${result.path}`);
    console.log(`    ${colors.blue}${result.name}${colors.reset} - ${duration}${statusCode}`);
    
    if (!result.success) {
        const error = result.error || `HTTP ${result.status}`;
        console.log(`    ${colors.red}Error: ${error}${colors.reset}`);
        if (result.response && typeof result.response === 'object' && result.response.error) {
            console.log(`    ${colors.yellow}Details: ${result.response.error}${colors.reset}`);
        }
    }
    
    console.log('');
}

function printSummary() {
    const passRate = ((results.passed / results.total) * 100).toFixed(1);
    
    console.log(`${colors.bright}=== SUMMARY ===${colors.reset}`);
    console.log(`Total endpoints tested: ${results.total}`);
    console.log(`${colors.green}Passed: ${results.passed}${colors.reset}`);
    console.log(`${colors.red}Failed: ${results.failed}${colors.reset}`);
    console.log(`Pass rate: ${passRate}%`);
    
    if (results.failed > 0) {
        console.log(`\n${colors.bright}Failed Endpoints:${colors.reset}`);
        results.details
            .filter(r => !r.success)
            .forEach(r => {
                const error = r.error || `HTTP ${r.status}`;
                console.log(`${colors.red}✗${colors.reset} ${r.method} ${r.path} - ${error}`);
            });
    }
    
    console.log(`\n${colors.bright}Response Time Analysis:${colors.reset}`);
    const durations = results.details.map(r => r.duration).sort((a, b) => a - b);
    const avg = durations.reduce((a, b) => a + b, 0) / durations.length;
    const median = durations[Math.floor(durations.length / 2)];
    const min = Math.min(...durations);
    const max = Math.max(...durations);
    
    console.log(`Average: ${formatDuration(avg)}`);
    console.log(`Median: ${formatDuration(median)}`);
    console.log(`Min: ${formatDuration(min)}`);
    console.log(`Max: ${formatDuration(max)}`);
}

async function runTests() {
    console.log(`${colors.bright}Puzzleverse API Endpoint Monitor${colors.reset}`);
    console.log(`Testing ${ENDPOINTS.length} endpoints on ${BASE_URL}\n`);
    
    for (const endpoint of ENDPOINTS) {
        const result = await makeRequest(endpoint);
        
        results.total++;
        if (result.success) {
            results.passed++;
        } else {
            results.failed++;
            results.errors.push(result);
        }
        results.details.push(result);
        
        printResult(result);
        
        // Small delay between requests to be respectful
        await new Promise(resolve => setTimeout(resolve, 100));
    }
    
    printSummary();
    
    // Exit with error code if tests failed
    process.exit(results.failed > 0 ? 1 : 0);
}

// Handle command line arguments
if (process.argv.includes('--help') || process.argv.includes('-h')) {
    console.log('Puzzleverse API Endpoint Monitor');
    console.log('Usage: node monitor.js [options]');
    console.log('Options:');
    console.log('  --help, -h     Show this help message');
    console.log('  --json         Output results in JSON format');
    console.log('  --verbose, -v  Show detailed response information');
    process.exit(0);
}

if (process.argv.includes('--json')) {
    // JSON output mode for CI/CD integration
    runTests().then(() => {
        console.log(JSON.stringify({
            timestamp: new Date().toISOString(),
            baseUrl: BASE_URL,
            summary: {
                total: results.total,
                passed: results.passed,
                failed: results.failed,
                passRate: ((results.passed / results.total) * 100).toFixed(1)
            },
            results: results.details
        }, null, 2));
    });
} else {
    // Standard colored output
    runTests();
}