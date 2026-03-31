#!/usr/bin/env node

// scripts/health-check.js - Manual health check script

import axios from 'axios';

const SERVER_URL = process.env.SERVER_URL || 'http://localhost:8080';

async function runHealthCheck() {
    console.log('🏥 Running comprehensive health check...');
    console.log(`📍 Server: ${SERVER_URL}`);
    
    try {
        const startTime = Date.now();
        
        // Run comprehensive health check
        const response = await axios.get(`${SERVER_URL}/api/health/comprehensive?force=true`, {
            timeout: 120000 // 2 minute timeout
        });
        
        const duration = Date.now() - startTime;
        const result = response.data;
        
        console.log('\n' + '='.repeat(60));
        console.log(`🏥 HEALTH CHECK RESULTS (${duration}ms)`);
        console.log('='.repeat(60));
        
        // Overall status
        const statusIcon = result.healthy ? '✅' : '❌';
        console.log(`${statusIcon} Overall Status: ${result.status.toUpperCase()}`);
        console.log(`📝 Message: ${result.message}`);
        console.log(`⏱️ Duration: ${result.duration}ms`);
        
        // Summary
        console.log('\n📊 SUMMARY:');
        console.log(`   ✅ Passed: ${result.summary.passed}/${result.summary.totalChecks}`);
        console.log(`   ❌ Failed: ${result.summary.failed}`);
        console.log(`   ⚠️ Warnings: ${result.summary.warnings}`);
        
        // Individual checks
        console.log('\n🔍 DETAILED RESULTS:');
        Object.entries(result.checks).forEach(([checkName, check]) => {
            const icon = check.status === 'passed' ? '✅' : 
                        check.status === 'warning' ? '⚠️' : '❌';
            console.log(`   ${icon} ${checkName}: ${check.message}`);
            
            if (check.error) {
                console.log(`      🔥 Error: ${check.error}`);
            }
        });
        
        console.log('\n' + '='.repeat(60));
        
        if (result.healthy) {
            console.log('🎉 System is healthy and ready for production!');
            process.exit(0);
        } else {
            console.log('💥 System has issues that need attention!');
            process.exit(1);
        }
        
    } catch (error) {
        console.error('\n❌ Health check failed to run:');
        
        if (error.response) {
            console.error(`   Status: ${error.response.status}`);
            console.error(`   Message: ${error.response.data?.message || 'Unknown error'}`);
        } else if (error.code === 'ECONNREFUSED') {
            console.error('   Server is not running or not accessible');
        } else {
            console.error(`   Error: ${error.message}`);
        }
        
        console.log('\n💡 Troubleshooting:');
        console.log('   1. Make sure the server is running');
        console.log('   2. Check if the port is correct');
        console.log('   3. Verify network connectivity');
        console.log('   4. Check server logs for errors');
        
        process.exit(1);
    }
}

// Run if called directly
if (import.meta.url === `file://${process.argv[1]}`) {
    runHealthCheck();
}

export { runHealthCheck };