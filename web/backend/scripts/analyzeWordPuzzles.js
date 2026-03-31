// analyzeWordPuzzles.js
// Detailed analysis of word-based puzzle types
// Run: node scripts/analyzeWordPuzzles.js <SUPABASE_KEY>

import { createClient } from '@supabase/supabase-js';

const supabaseUrl = 'https://uujjodxicvifmiwlimob.supabase.co';
const supabaseKey = process.argv[2] || process.env.SUPABASE_ANON_KEY;

if (!supabaseKey) {
    console.error('Usage: node scripts/analyzeWordPuzzles.js <SUPABASE_ANON_KEY>');
    process.exit(1);
}

const supabase = createClient(supabaseUrl, supabaseKey);

const WORD_TYPES = [
    'anagram',
    'antonyms',
    'synonyms',
    'crossword',
    'wordsearch',
    'wordsnake',
    'wordprefix',
    'letterset'
];

async function analyzeType(puzzleType) {
    console.log(`\n${'═'.repeat(70)}`);
    console.log(`  ${puzzleType.toUpperCase()}`);
    console.log('═'.repeat(70));

    // Get total count
    const { count } = await supabase
        .from('puzzles')
        .select('*', { count: 'exact', head: true })
        .eq('type', puzzleType);

    console.log(`\n📊 Total puzzles: ${count}`);

    if (count === 0) {
        console.log('   No puzzles found for this type.');
        return { puzzleType, count: 0, duplicates: 0 };
    }

    // Get sample puzzles to understand structure
    const { data: samples } = await supabase
        .from('puzzles')
        .select('puzzleid, question, answer, difficulty, timestamp')
        .eq('type', puzzleType)
        .order('timestamp', { ascending: false })
        .limit(5);

    console.log('\n📝 Recent puzzles:');
    samples?.forEach((p, i) => {
        const date = new Date(p.timestamp).toISOString().split('T')[0];
        const q = p.question?.slice(0, 50) || 'N/A';
        const a = typeof p.answer === 'string' ? p.answer.slice(0, 30) : JSON.stringify(p.answer).slice(0, 30);
        console.log(`   ${i + 1}. [${date}] ${p.difficulty} - Q: "${q}..." A: "${a}..."`);
    });

    // Get date range
    const { data: oldest } = await supabase
        .from('puzzles')
        .select('timestamp')
        .eq('type', puzzleType)
        .order('timestamp', { ascending: true })
        .limit(1);

    const { data: newest } = await supabase
        .from('puzzles')
        .select('timestamp')
        .eq('type', puzzleType)
        .order('timestamp', { ascending: false })
        .limit(1);

    if (oldest?.[0] && newest?.[0]) {
        const oldDate = new Date(oldest[0].timestamp).toISOString().split('T')[0];
        const newDate = new Date(newest[0].timestamp).toISOString().split('T')[0];
        console.log(`\n📅 Date range: ${oldDate} to ${newDate}`);
    }

    // Count by difficulty
    console.log('\n📈 By difficulty:');
    for (const diff of ['easy', 'medium', 'hard']) {
        const { count: diffCount } = await supabase
            .from('puzzles')
            .select('*', { count: 'exact', head: true })
            .eq('type', puzzleType)
            .eq('difficulty', diff);
        console.log(`   ${diff}: ${diffCount || 0}`);
    }

    // Check for duplicates based on answer
    console.log('\n🔍 Checking for duplicates...');

    const { data: allAnswers } = await supabase
        .from('puzzles')
        .select('answer')
        .eq('type', puzzleType)
        .limit(1000);

    if (allAnswers) {
        const answerStrings = allAnswers.map(p =>
            typeof p.answer === 'string' ? p.answer : JSON.stringify(p.answer)
        );
        const uniqueAnswers = new Set(answerStrings);
        const duplicateCount = answerStrings.length - uniqueAnswers.size;
        const duplicatePercent = ((duplicateCount / answerStrings.length) * 100).toFixed(1);

        console.log(`   Checked: ${answerStrings.length} puzzles`);
        console.log(`   Unique answers: ${uniqueAnswers.size}`);
        console.log(`   Duplicates: ${duplicateCount} (${duplicatePercent}%)`);

        // Find most common duplicates
        if (duplicateCount > 0) {
            const answerCounts = {};
            answerStrings.forEach(a => {
                answerCounts[a] = (answerCounts[a] || 0) + 1;
            });

            const topDupes = Object.entries(answerCounts)
                .filter(([_, count]) => count > 1)
                .sort((a, b) => b[1] - a[1])
                .slice(0, 5);

            if (topDupes.length > 0) {
                console.log('\n   Top duplicated answers:');
                topDupes.forEach(([answer, cnt]) => {
                    const displayAnswer = answer.length > 40 ? answer.slice(0, 40) + '...' : answer;
                    console.log(`      "${displayAnswer}" - ${cnt} times`);
                });
            }
        }

        return { puzzleType, count, duplicates: duplicateCount, duplicatePercent };
    }

    return { puzzleType, count, duplicates: 0 };
}

async function main() {
    console.log('═'.repeat(70));
    console.log('  WORD-BASED PUZZLE ANALYSIS');
    console.log('═'.repeat(70));
    console.log(`  Date: ${new Date().toISOString()}`);

    const results = [];

    for (const type of WORD_TYPES) {
        const result = await analyzeType(type);
        results.push(result);
    }

    // Summary
    console.log('\n\n' + '═'.repeat(70));
    console.log('  SUMMARY');
    console.log('═'.repeat(70));
    console.log('\nType          | Count    | Duplicates | Dup %');
    console.log('─'.repeat(50));

    results.forEach(r => {
        const typeStr = r.puzzleType.padEnd(13);
        const countStr = String(r.count).padEnd(8);
        const dupStr = String(r.duplicates || 0).padEnd(10);
        const pctStr = r.duplicatePercent || '0.0';
        console.log(`${typeStr} | ${countStr} | ${dupStr} | ${pctStr}%`);
    });

    // Recommendations
    const highDupeTypes = results.filter(r => parseFloat(r.duplicatePercent || 0) > 10);
    if (highDupeTypes.length > 0) {
        console.log('\n⚠️  Types with >10% duplicates (may need deduplication):');
        highDupeTypes.forEach(r => console.log(`   - ${r.puzzleType}: ${r.duplicatePercent}%`));
    }

    const lowCountTypes = results.filter(r => r.count < 500 && r.count > 0);
    if (lowCountTypes.length > 0) {
        console.log('\n⚠️  Types with low puzzle count (<500):');
        lowCountTypes.forEach(r => console.log(`   - ${r.puzzleType}: ${r.count} puzzles`));
    }
}

main().catch(err => {
    console.error('Analysis failed:', err);
    process.exit(1);
});
