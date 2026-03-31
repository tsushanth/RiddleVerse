// testFlowPuzzleVariety.js
// Tests flow puzzle generator for pattern variety
// Run: node scripts/testFlowPuzzleVariety.js

import { FlowPuzzleGenerator } from '../services/flowPuzzleService.js';

async function testFlowPuzzleVariety() {
    console.log('═'.repeat(60));
    console.log('  FLOW PUZZLE VARIETY TEST');
    console.log('═'.repeat(60));
    console.log(`  Time: ${new Date().toISOString()}`);
    console.log('═'.repeat(60));

    const generator = new FlowPuzzleGenerator();
    generator.setDebugMode(false);

    const testCases = [
        { size: 6, difficulty: 'easy', count: 10 },
        { size: 8, difficulty: 'medium', count: 10 },
        { size: 10, difficulty: 'hard', count: 10 }
    ];

    for (const test of testCases) {
        console.log(`\n📊 Testing ${test.difficulty.toUpperCase()} (${test.size}x${test.size}, ${test.count} puzzles)...`);

        const startPositions = [];
        const signatures = new Set();
        const firstPairPositions = [];

        for (let i = 0; i < test.count; i++) {
            const result = await generator.generateFlowPuzzle(test.size, test.difficulty, 1);

            if (result.success && result.puzzles.length > 0) {
                const puzzle = result.puzzles[0];

                // Track first pair's start position
                if (puzzle.pairs.length > 0) {
                    const firstPair = puzzle.pairs[0];
                    const startKey = `${firstPair.start.x},${firstPair.start.y}`;
                    startPositions.push(startKey);
                    firstPairPositions.push({
                        start: firstPair.start,
                        end: firstPair.end
                    });
                }

                // Track signature for uniqueness
                const sig = puzzle.pairs.map(p =>
                    `${p.start.x},${p.start.y}-${p.end.x},${p.end.y}`
                ).sort().join('|');
                signatures.add(sig);
            }
        }

        // Analyze variety
        const uniqueStarts = new Set(startPositions);
        const startCounts = {};
        startPositions.forEach(pos => {
            startCounts[pos] = (startCounts[pos] || 0) + 1;
        });

        console.log(`   Generated: ${startPositions.length}/${test.count} puzzles`);
        console.log(`   Unique signatures: ${signatures.size}/${startPositions.length}`);
        console.log(`   Unique first-pair starts: ${uniqueStarts.size}/${startPositions.length}`);

        // Show distribution of first pair start positions
        console.log('\n   First pair start position distribution:');
        const sortedCounts = Object.entries(startCounts)
            .sort((a, b) => b[1] - a[1])
            .slice(0, 5);

        sortedCounts.forEach(([pos, count]) => {
            const pct = ((count / startPositions.length) * 100).toFixed(1);
            const bar = '█'.repeat(Math.ceil(count / 2));
            console.log(`      (${pos}): ${count} times (${pct}%) ${bar}`);
        });

        // Check for repetitive patterns
        const maxRepeat = Math.max(...Object.values(startCounts));
        const repeatPct = (maxRepeat / startPositions.length) * 100;

        if (repeatPct > 40) {
            console.log(`   ⚠️  WARNING: High repetition - same start position ${repeatPct.toFixed(1)}% of time`);
        } else if (repeatPct > 25) {
            console.log(`   🟡 MODERATE: Some repetition - same start position ${repeatPct.toFixed(1)}% of time`);
        } else {
            console.log(`   ✅ GOOD: Low repetition - max ${repeatPct.toFixed(1)}% same start position`);
        }

        // Show sample of first pair positions
        console.log('\n   Sample first pairs (first 5):');
        firstPairPositions.slice(0, 5).forEach((fp, i) => {
            console.log(`      ${i+1}. Start: (${fp.start.x},${fp.start.y}) → End: (${fp.end.x},${fp.end.y})`);
        });
    }

    console.log('\n' + '═'.repeat(60));
    console.log('  TEST COMPLETE');
    console.log('═'.repeat(60));
}

testFlowPuzzleVariety().catch(err => {
    console.error('Test failed:', err);
    process.exit(1);
});
