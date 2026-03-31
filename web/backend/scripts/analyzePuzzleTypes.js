// analyzePuzzleTypes.js
// Analyzes all puzzle types in the database
// Run: node scripts/analyzePuzzleTypes.js <SUPABASE_KEY>

import { createClient } from '@supabase/supabase-js';

const supabaseUrl = 'https://uujjodxicvifmiwlimob.supabase.co';
const supabaseKey = process.argv[2] || process.env.SUPABASE_ANON_KEY;

if (!supabaseKey) {
    console.error('Usage: node scripts/analyzePuzzleTypes.js <SUPABASE_ANON_KEY>');
    process.exit(1);
}

const supabase = createClient(supabaseUrl, supabaseKey);

// Categorized puzzle types
const CATEGORIES = {
    'Visual': ['realorai', 'uniqueobject', 'find_object', 'imagequestion', 'waldopuzzle', 'progressiverevelation', 'imagepuzzle', 'imagematch', 'find_differences'],
    'Word': ['wordsearch', 'crossword', 'anagram', 'synonyms', 'antonyms', 'wordsnake', 'wordprefix', 'letterset'],
    'Math': ['math', 'mathestimation', 'subtraction', 'division', 'percentages', 'mathtipping', 'mathcomparison', 'average', 'purchasing', 'discounts', 'conversion'],
    'Memory': ['memorystory', 'memorysquares', 'memorypreviouspair', 'memoryprevioussingle', 'memorymatrixpath', 'memorysequencing', 'memoryretention'],
    'Trivia/Other': ['trivia', 'storypuzzle', 'crypto', 'sentencetransitions', 'connotationwords', 'flowpuzzle', 'pinballdeflector', 'musicidentification']
};

// In scheduler (from cacheScheduler.js)
const IN_SCHEDULER = [
    'math', 'anagram', 'imagepuzzle', 'wordsearch', 'crossword', 'trivia', 'memorystory',
    'realorai', 'uniqueobject', 'find_object', 'imagequestion', 'waldopuzzle', 'progressiverevelation'
];

async function analyzePuzzleType(puzzleType) {
    // Get count
    const { count, error: countError } = await supabase
        .from('puzzles')
        .select('*', { count: 'exact', head: true })
        .eq('type', puzzleType);

    if (countError) {
        return { puzzleType, error: countError.message };
    }

    if (count === 0) {
        return { puzzleType, count: 0, lastGenerated: null, inScheduler: IN_SCHEDULER.includes(puzzleType) };
    }

    // Get latest puzzle
    const { data: latest, error: latestError } = await supabase
        .from('puzzles')
        .select('timestamp')
        .eq('type', puzzleType)
        .order('timestamp', { ascending: false })
        .limit(1);

    // Get oldest puzzle
    const { data: oldest, error: oldestError } = await supabase
        .from('puzzles')
        .select('timestamp')
        .eq('type', puzzleType)
        .order('timestamp', { ascending: true })
        .limit(1);

    const lastGenerated = latest?.[0]?.timestamp ? new Date(latest[0].timestamp) : null;
    const firstGenerated = oldest?.[0]?.timestamp ? new Date(oldest[0].timestamp) : null;

    // Calculate days since last generation
    const daysSinceLastGen = lastGenerated
        ? Math.floor((Date.now() - lastGenerated.getTime()) / (1000 * 60 * 60 * 24))
        : null;

    return {
        puzzleType,
        count,
        lastGenerated: lastGenerated?.toISOString().split('T')[0],
        firstGenerated: firstGenerated?.toISOString().split('T')[0],
        daysSinceLastGen,
        inScheduler: IN_SCHEDULER.includes(puzzleType),
        status: daysSinceLastGen === null ? 'empty' :
                daysSinceLastGen > 30 ? 'stale' :
                daysSinceLastGen > 7 ? 'aging' : 'active'
    };
}

async function main() {
    console.log('═'.repeat(80));
    console.log('  PUZZLE TYPE ANALYSIS');
    console.log('═'.repeat(80));
    console.log(`  Date: ${new Date().toISOString().split('T')[0]}`);
    console.log('═'.repeat(80));

    const allResults = [];

    for (const [category, types] of Object.entries(CATEGORIES)) {
        console.log(`\n\n📁 ${category.toUpperCase()}`);
        console.log('─'.repeat(80));
        console.log('Type                  | Count  | Last Gen   | Days Ago | Scheduler | Status');
        console.log('─'.repeat(80));

        for (const puzzleType of types) {
            const result = await analyzePuzzleType(puzzleType);
            allResults.push({ ...result, category });

            const countStr = String(result.count || 0).padEnd(6);
            const lastGenStr = (result.lastGenerated || 'never').padEnd(10);
            const daysStr = result.daysSinceLastGen !== null
                ? String(result.daysSinceLastGen).padEnd(8)
                : 'N/A'.padEnd(8);
            const schedulerStr = result.inScheduler ? '✅ Yes' : '❌ No';
            const statusIcon = result.status === 'active' ? '🟢' :
                              result.status === 'aging' ? '🟡' :
                              result.status === 'stale' ? '🔴' : '⚫';

            console.log(
                `${puzzleType.padEnd(21)} | ${countStr} | ${lastGenStr} | ${daysStr} | ${schedulerStr.padEnd(9)} | ${statusIcon} ${result.status}`
            );
        }
    }

    // Summary
    console.log('\n\n' + '═'.repeat(80));
    console.log('  SUMMARY');
    console.log('═'.repeat(80));

    const active = allResults.filter(r => r.status === 'active');
    const aging = allResults.filter(r => r.status === 'aging');
    const stale = allResults.filter(r => r.status === 'stale');
    const empty = allResults.filter(r => r.status === 'empty');
    const notScheduled = allResults.filter(r => !r.inScheduler && r.count > 0);

    console.log(`\n🟢 Active (< 7 days): ${active.length}`);
    console.log(`🟡 Aging (7-30 days): ${aging.length}`);
    console.log(`🔴 Stale (> 30 days): ${stale.length}`);
    console.log(`⚫ Empty (no puzzles): ${empty.length}`);

    if (stale.length > 0) {
        console.log('\n⚠️  STALE PUZZLE TYPES (need attention):');
        stale.forEach(r => {
            console.log(`   - ${r.puzzleType}: ${r.count} puzzles, last gen ${r.daysSinceLastGen} days ago ${r.inScheduler ? '(in scheduler)' : '(NOT scheduled)'}`);
        });
    }

    if (notScheduled.length > 0) {
        console.log('\n⚠️  PUZZLE TYPES NOT IN SCHEDULER:');
        notScheduled.forEach(r => {
            console.log(`   - ${r.puzzleType}: ${r.count} puzzles, last gen ${r.lastGenerated}`);
        });
    }

    // Recommendations
    console.log('\n📋 RECOMMENDATIONS:');
    const toAddToScheduler = stale.filter(r => !r.inScheduler && r.count > 0);
    if (toAddToScheduler.length > 0) {
        console.log('   Add to scheduler: ' + toAddToScheduler.map(r => r.puzzleType).join(', '));
    }

    const inSchedulerButStale = stale.filter(r => r.inScheduler);
    if (inSchedulerButStale.length > 0) {
        console.log('   In scheduler but stale (check generator): ' + inSchedulerButStale.map(r => r.puzzleType).join(', '));
    }
}

main().catch(err => {
    console.error('Analysis failed:', err);
    process.exit(1);
});
