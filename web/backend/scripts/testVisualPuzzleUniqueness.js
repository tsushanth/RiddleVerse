// testVisualPuzzleUniqueness.js
// Tests that visual puzzle generators are producing unique puzzles
// Run: node scripts/testVisualPuzzleUniqueness.js <SUPABASE_KEY>

import { createClient } from '@supabase/supabase-js';

// Supabase credentials
const supabaseUrl = 'https://uujjodxicvifmiwlimob.supabase.co';
const supabaseKey = process.argv[2] || process.env.SUPABASE_ANON_KEY;

if (!supabaseKey) {
    console.error('Usage: node testVisualPuzzleUniqueness.js <SUPABASE_ANON_KEY>');
    process.exit(1);
}

const supabase = createClient(supabaseUrl, supabaseKey);
const SERVER_URL = process.env.SERVER_URL || 'https://puzzleverseai.com';

const VISUAL_PUZZLE_TYPES = [
    'realorai',
    'uniqueobject',
    'find_object',
    'imagequestion',
    'waldopuzzle',
    'progressiverevelation'
];

async function fetchExistingPuzzles(puzzleType, limit = 50) {
    // Use select('*') to get all columns since schema varies by puzzle type
    const { data, error } = await supabase
        .from('puzzles')
        .select('*')
        .eq('type', puzzleType)
        .order('timestamp', { ascending: false })
        .limit(limit);

    if (error) {
        console.error(`   DB Error for ${puzzleType}:`, error.message);
        return [];
    }
    return data || [];
}

function extractUniqueIdentifier(puzzle) {
    if (!puzzle) return null;

    // The puzzle record itself contains the data (not nested)
    switch (puzzle.type) {
        case 'realorai':
            // Check real image URL, photographer, or subject
            return puzzle.realimageurl || puzzle.realImageUrl ||
                   puzzle.photographer || puzzle.subject || puzzle.question;

        case 'uniqueobject':
        case 'find_object':
        case 'waldopuzzle':
            return puzzle.imageurl || puzzle.imageUrl ||
                   puzzle.targetobject || puzzle.answer;

        case 'imagequestion':
            return `${puzzle.imageurl || puzzle.imageUrl}_${puzzle.question}`;

        case 'progressiverevelation':
            return puzzle.answer || puzzle.imageurl || puzzle.imageUrl;

        default:
            return puzzle.answer || puzzle.question || puzzle.puzzleid;
    }
}

async function triggerGeneration(puzzleType, difficulty = 'easy', retries = 3) {
    for (let attempt = 1; attempt <= retries; attempt++) {
        try {
            const url = `${SERVER_URL}/api/daily-cache/todays-puzzles/${puzzleType}?difficulty=${difficulty}&count=1`;
            console.log(`   Requesting: ${url}`);

            const response = await fetch(url, {
                method: 'GET',
                headers: {
                    'Content-Type': 'application/json',
                    'Accept': 'application/json'
                }
            });

            const text = await response.text();

            // Check if response is HTML (error page)
            if (text.startsWith('<!DOCTYPE') || text.startsWith('<html')) {
                console.log(`   ⚠️  Got HTML response (attempt ${attempt}/${retries})`);
                if (attempt < retries) {
                    await new Promise(r => setTimeout(r, 5000));
                    continue;
                }
                return { success: false, error: 'Server returned HTML instead of JSON' };
            }

            const data = JSON.parse(text);

            // Check for rate limiting
            if (data.error && data.error.includes('Too many')) {
                console.log(`   ⚠️  Rate limited (attempt ${attempt}/${retries})`);
                if (attempt < retries) {
                    await new Promise(r => setTimeout(r, 10000));
                    continue;
                }
                return { success: false, error: 'Rate limited' };
            }

            if (data.success && (data.puzzleData || (data.puzzles && data.puzzles.length > 0))) {
                return {
                    success: true,
                    puzzle: data.puzzleData || data.puzzles[0],
                    wasGenerated: data.wasGenerated || false,
                    source: data.metadata?.source || 'unknown'
                };
            }

            return {
                success: false,
                error: data.message || 'No puzzle returned',
                generationInProgress: data.generationInProgress
            };
        } catch (error) {
            console.log(`   ⚠️  Error (attempt ${attempt}/${retries}): ${error.message}`);
            if (attempt < retries) {
                await new Promise(r => setTimeout(r, 5000));
                continue;
            }
            return { success: false, error: error.message };
        }
    }
}

async function checkUniqueness(puzzleType, newPuzzle, existingPuzzles) {
    // Construct a pseudo-puzzle object for the new puzzle
    const newPuzzleObj = { type: puzzleType, ...newPuzzle };
    const newIdentifier = extractUniqueIdentifier(newPuzzleObj);

    if (!newIdentifier) {
        return {
            isUnique: null,
            reason: 'Could not extract identifier from new puzzle'
        };
    }

    const duplicates = existingPuzzles.filter(existing => {
        const existingIdentifier = extractUniqueIdentifier(existing);
        return existingIdentifier === newIdentifier;
    });

    return {
        isUnique: duplicates.length === 0,
        newIdentifier: String(newIdentifier).slice(0, 100),
        duplicateCount: duplicates.length,
        duplicateIds: duplicates.slice(0, 3).map(d => d.puzzleid)
    };
}

async function testPuzzleType(puzzleType) {
    console.log(`\n${'='.repeat(60)}`);
    console.log(`Testing: ${puzzleType.toUpperCase()}`);
    console.log('='.repeat(60));

    // Step 1: Fetch existing puzzles
    console.log('\n1. Fetching existing puzzles from database...');
    const existingPuzzles = await fetchExistingPuzzles(puzzleType);
    console.log(`   Found ${existingPuzzles.length} existing puzzles`);

    if (existingPuzzles.length > 0) {
        const latestDate = new Date(existingPuzzles[0].timestamp);
        console.log(`   Latest puzzle: ${latestDate.toISOString()}`);

        // Show sample identifier from existing
        const sampleId = extractUniqueIdentifier(existingPuzzles[0]);
        if (sampleId) {
            console.log(`   Sample existing identifier: ${String(sampleId).slice(0, 60)}...`);
        }
    }

    // Step 2: Trigger new puzzle generation
    console.log('\n2. Triggering puzzle generation via API...');
    const result = await triggerGeneration(puzzleType);

    if (!result.success) {
        console.log(`   ❌ Generation failed: ${result.error}`);
        if (result.generationInProgress) {
            console.log('   ⏳ Generation in progress - retry in 30 seconds');
        }
        return {
            puzzleType,
            success: false,
            error: result.error,
            existingCount: existingPuzzles.length
        };
    }

    console.log(`   ✅ Puzzle received (source: ${result.source})`);
    console.log(`   Was generated: ${result.wasGenerated}`);

    // Step 3: Check uniqueness
    console.log('\n3. Checking uniqueness against existing puzzles...');
    const uniquenessResult = await checkUniqueness(puzzleType, result.puzzle, existingPuzzles);

    if (uniquenessResult.isUnique === null) {
        console.log(`   ⚠️  ${uniquenessResult.reason}`);
    } else if (uniquenessResult.isUnique) {
        console.log(`   ✅ UNIQUE - No duplicates found`);
        console.log(`   New identifier: ${uniquenessResult.newIdentifier}...`);
    } else {
        console.log(`   ❌ DUPLICATE FOUND!`);
        console.log(`   Identifier: ${uniquenessResult.newIdentifier}...`);
        console.log(`   Matches ${uniquenessResult.duplicateCount} existing puzzle(s)`);
        console.log(`   Duplicate IDs: ${uniquenessResult.duplicateIds.join(', ')}`);
    }

    // Step 4: Show sample data for verification
    console.log('\n4. Sample data from new puzzle:');
    const puzzle = result.puzzle;
    if (puzzle) {
        const keys = Object.keys(puzzle).filter(k =>
            ['realImageUrl', 'realimageurl', 'aiImageUrl', 'aiimageurl',
             'imageUrl', 'imageurl', 'answer', 'question', 'subject',
             'category', 'photographer'].includes(k)
        );
        keys.forEach(k => {
            const val = puzzle[k];
            if (val) {
                const display = typeof val === 'string' && val.length > 70
                    ? val.slice(0, 70) + '...'
                    : val;
                console.log(`   ${k}: ${display}`);
            }
        });
    }

    return {
        puzzleType,
        success: true,
        isUnique: uniquenessResult.isUnique,
        existingCount: existingPuzzles.length,
        source: result.source,
        wasGenerated: result.wasGenerated,
        newIdentifier: uniquenessResult.newIdentifier,
        duplicateCount: uniquenessResult.duplicateCount || 0
    };
}

async function main() {
    console.log('╔════════════════════════════════════════════════════════════╗');
    console.log('║     VISUAL PUZZLE UNIQUENESS TEST                          ║');
    console.log('╠════════════════════════════════════════════════════════════╣');
    console.log(`║ Server: ${SERVER_URL.padEnd(49)}║`);
    console.log(`║ Time: ${new Date().toISOString().padEnd(51)}║`);
    console.log('╚════════════════════════════════════════════════════════════╝');

    const results = [];

    for (const puzzleType of VISUAL_PUZZLE_TYPES) {
        const result = await testPuzzleType(puzzleType);
        results.push(result);

        // Delay between tests to avoid rate limiting
        await new Promise(resolve => setTimeout(resolve, 3000));
    }

    // Summary
    console.log('\n\n');
    console.log('╔════════════════════════════════════════════════════════════╗');
    console.log('║                        SUMMARY                             ║');
    console.log('╚════════════════════════════════════════════════════════════╝');
    console.log('\n');
    console.log('Puzzle Type           | Existing | Result    | Unique?');
    console.log('─'.repeat(60));

    for (const r of results) {
        const status = r.success ? (r.isUnique ? '✅ PASS' : '❌ FAIL') : '⚠️  ERROR';
        const unique = r.isUnique === true ? 'Yes' : r.isUnique === false ? 'NO!' : 'N/A';
        console.log(
            `${r.puzzleType.padEnd(21)} | ${String(r.existingCount).padEnd(8)} | ${status.padEnd(9)} | ${unique}`
        );
    }

    const passed = results.filter(r => r.success && r.isUnique).length;
    const failed = results.filter(r => r.success && r.isUnique === false).length;
    const errors = results.filter(r => !r.success).length;

    console.log('─'.repeat(60));
    console.log(`\nTotal: ${results.length} | Passed: ${passed} | Failed: ${failed} | Errors: ${errors}`);

    if (failed > 0) {
        console.log('\n⚠️  Some puzzle types are generating duplicates!');
        process.exit(1);
    } else if (errors > 0) {
        console.log('\n⚠️  Some tests had errors. Check server logs.');
        process.exit(2);
    } else {
        console.log('\n✅ All visual puzzle types generating unique puzzles!');
        process.exit(0);
    }
}

main().catch(err => {
    console.error('Test script failed:', err);
    process.exit(1);
});
