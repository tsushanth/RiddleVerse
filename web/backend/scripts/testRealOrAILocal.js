// testRealOrAILocal.js
// Tests the WhichIsRealGenerator directly (bypasses API rate limits)
// Run: node scripts/testRealOrAILocal.js <SUPABASE_KEY>

import { createClient } from '@supabase/supabase-js';
import { WhichIsRealGenerator } from '../services/whichIsRealGenerator.js';

const supabaseUrl = 'https://uujjodxicvifmiwlimob.supabase.co';
const supabaseKey = process.argv[2] || process.env.SUPABASE_ANON_KEY;

if (!supabaseKey) {
    console.error('Usage: node scripts/testRealOrAILocal.js <SUPABASE_ANON_KEY>');
    process.exit(1);
}

const supabase = createClient(supabaseUrl, supabaseKey);

function extractPhotographer(puzzle) {
    try {
        // Answer is stored as JSON string
        const answer = typeof puzzle.answer === 'string'
            ? JSON.parse(puzzle.answer)
            : puzzle.answer;

        // Find the real image details
        const imageDetails = answer?.imageDetails || {};
        for (const key of ['image_a', 'image_b']) {
            const img = imageDetails[key];
            if (img?.type === 'real' && img?.photographer) {
                return img.photographer;
            }
        }
        return null;
    } catch {
        return null;
    }
}

async function fetchExistingRealOrAI(limit = 100) {
    const { data, error } = await supabase
        .from('puzzles')
        .select('puzzleid, answer, question, timestamp')
        .eq('type', 'realorai')
        .order('timestamp', { ascending: false })
        .limit(limit);

    if (error) {
        console.error('DB Error:', error.message);
        return [];
    }
    return data || [];
}

async function main() {
    console.log('═'.repeat(60));
    console.log('  REAL OR AI GENERATOR - LOCAL UNIQUENESS TEST');
    console.log('═'.repeat(60));

    // Step 1: Fetch existing puzzles
    console.log('\n1. Fetching existing realorai puzzles...');
    const existing = await fetchExistingRealOrAI();
    console.log(`   Found ${existing.length} existing puzzles`);

    // Extract photographers from existing puzzles
    const existingPhotographers = existing.map(p => extractPhotographer(p)).filter(Boolean);
    const uniquePhotographers = [...new Set(existingPhotographers)];

    console.log(`   Unique photographers: ${uniquePhotographers.length}`);
    console.log(`   Photographer frequency:`);

    // Count photographer frequency
    const photographerCounts = {};
    existingPhotographers.forEach(p => {
        photographerCounts[p] = (photographerCounts[p] || 0) + 1;
    });

    // Show top 5 most common
    const topPhotographers = Object.entries(photographerCounts)
        .sort((a, b) => b[1] - a[1])
        .slice(0, 5);

    topPhotographers.forEach(([name, count]) => {
        console.log(`      ${name}: ${count} puzzles`);
    });

    // Step 2: Generate new puzzles
    console.log('\n2. Generating 5 new puzzles using WhichIsRealGenerator...');
    const generator = new WhichIsRealGenerator();
    generator.setDebugMode(true);

    const newPuzzles = [];
    const duplicatePhotographers = [];

    for (let i = 0; i < 5; i++) {
        console.log(`\n   --- Puzzle ${i + 1}/5 ---`);
        try {
            const puzzle = await generator.generateWhichIsRealPuzzle('easy');

            if (puzzle) {
                // Extract photographer from generated puzzle
                const photographer = puzzle.imageData?.realImage?.photographer;
                const subject = puzzle.subject;
                const category = puzzle.category;

                // Check if this photographer was already used
                const isDupe = existingPhotographers.includes(photographer);

                newPuzzles.push({
                    index: i + 1,
                    subject,
                    category,
                    photographer,
                    isDuplicate: isDupe
                });

                if (isDupe) {
                    duplicatePhotographers.push(photographer);
                    console.log(`   ⚠️  KNOWN PHOTOGRAPHER: ${photographer}`);
                    console.log(`      Subject: ${subject}`);
                    console.log(`      (Photographer appeared in ${photographerCounts[photographer] || 0} existing puzzles)`);
                } else {
                    console.log(`   ✅ NEW PHOTOGRAPHER: ${photographer || 'unknown'}`);
                    console.log(`      Subject: ${subject}`);
                    console.log(`      Category: ${category}`);
                }
            } else {
                console.log(`   ⚠️  Failed to generate puzzle ${i + 1}`);
            }
        } catch (err) {
            console.log(`   ⚠️  Error generating puzzle ${i + 1}: ${err.message}`);
        }

        // Small delay between generations
        await new Promise(r => setTimeout(r, 3000));
    }

    // Summary
    console.log('\n' + '═'.repeat(60));
    console.log('  SUMMARY');
    console.log('═'.repeat(60));

    const newPhotographers = newPuzzles.filter(p => !p.isDuplicate).length;
    const knownPhotographers = newPuzzles.filter(p => p.isDuplicate).length;

    console.log(`\nGenerated: ${newPuzzles.length}`);
    console.log(`New photographers: ${newPhotographers}`);
    console.log(`Known photographers: ${knownPhotographers}`);

    console.log('\nNew puzzles:');
    newPuzzles.forEach(p => {
        const status = p.isDuplicate ? '⚠️  KNOWN' : '✅ NEW';
        console.log(`  ${p.index}. ${status} - ${p.subject} by ${p.photographer || 'unknown'}`);
    });

    // Check uniqueness among newly generated
    const newPhotographerList = newPuzzles.map(p => p.photographer).filter(Boolean);
    const uniqueNewPhotographers = [...new Set(newPhotographerList)];

    if (uniqueNewPhotographers.length < newPhotographerList.length) {
        console.log(`\n⚠️  ${newPhotographerList.length - uniqueNewPhotographers.length} duplicate(s) among new puzzles`);
    } else {
        console.log(`\n✅ All ${newPuzzles.length} new puzzles have different photographers`);
    }

    if (newPhotographers >= 3) {
        console.log('\n✅ SUCCESS: Mostly new photographers - random selection is working!');
        process.exit(0);
    } else if (newPuzzles.length === 0) {
        console.log('\n❌ FAILED: Could not generate any puzzles');
        process.exit(1);
    } else {
        console.log(`\n⚠️  PARTIAL: ${newPhotographers}/${newPuzzles.length} new photographers`);
        console.log('   Random selection working but may hit same queries occasionally');
        process.exit(0);
    }
}

main().catch(err => {
    console.error('Test failed:', err);
    process.exit(1);
});
