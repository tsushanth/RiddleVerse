// testWordGenerators.js
// Tests anagram and letterset generators for variety
// Run: node scripts/testWordGenerators.js <SUPABASE_KEY>

import { createClient } from '@supabase/supabase-js';
import { AnagramGenerator } from '../services/anagramGeneratorService.js';
import { LetterSetGenerator } from '../services/letterSetGenerator.js';

const supabaseUrl = 'https://uujjodxicvifmiwlimob.supabase.co';
const supabaseKey = process.argv[2] || process.env.SUPABASE_ANON_KEY;

if (!supabaseKey) {
    console.error('Usage: node scripts/testWordGenerators.js <SUPABASE_KEY>');
    process.exit(1);
}

// Mock supabase for the generators
global.supabase = createClient(supabaseUrl, supabaseKey);

async function testAnagramGenerator() {
    console.log('\n' + '═'.repeat(60));
    console.log('  ANAGRAM GENERATOR TEST');
    console.log('═'.repeat(60));

    const generator = new AnagramGenerator();
    generator.setDebugMode(true);

    const results = [];

    for (const difficulty of ['easy', 'medium', 'hard']) {
        console.log(`\n📝 Testing ${difficulty.toUpperCase()} difficulty...`);

        try {
            const result = await generator.generateAnagramPuzzle(difficulty, null, 5);

            if (result.success && result.anagrams.length > 0) {
                console.log(`   ✅ Generated ${result.anagrams.length} anagrams`);
                result.anagrams.forEach((a, i) => {
                    console.log(`      ${i + 1}. ${a.scrambledWord} → ${a.originalWord} (${a.originalWord.length} letters)`);
                });

                // Check for uniqueness among generated
                const words = result.anagrams.map(a => a.originalWord);
                const uniqueWords = new Set(words);
                if (uniqueWords.size < words.length) {
                    console.log(`   ⚠️  Duplicates in batch!`);
                } else {
                    console.log(`   ✅ All ${words.length} words unique in batch`);
                }

                results.push({
                    difficulty,
                    success: true,
                    count: result.anagrams.length,
                    words: words
                });
            } else {
                console.log(`   ❌ Failed: ${result.error || 'No anagrams generated'}`);
                results.push({ difficulty, success: false, error: result.error });
            }
        } catch (err) {
            console.log(`   ❌ Error: ${err.message}`);
            results.push({ difficulty, success: false, error: err.message });
        }
    }

    return results;
}

async function testLetterSetGenerator() {
    console.log('\n' + '═'.repeat(60));
    console.log('  LETTERSET GENERATOR TEST');
    console.log('═'.repeat(60));

    const generator = new LetterSetGenerator();
    generator.setDebugMode(true);

    const results = [];

    for (const difficulty of ['Easy', 'Medium', 'Hard']) {
        console.log(`\n📝 Testing ${difficulty.toUpperCase()} difficulty...`);

        try {
            const result = await generator.generateLetterSetPuzzle(difficulty);

            if (result.success && result.puzzleData) {
                const data = result.puzzleData;
                console.log(`   ✅ Generated puzzle with letter set: ${data.letterSet}`);
                console.log(`      Words found: ${data.allWords?.length || 0}`);
                console.log(`      Key word: ${data.keyWord || 'none'}`);
                console.log(`      Source: ${data.metadata?.letterSetSource || 'unknown'}`);

                // Show sample words
                const sampleWords = (data.allWords || []).slice(0, 8).map(w => w.word);
                console.log(`      Sample: ${sampleWords.join(', ')}`);

                results.push({
                    difficulty,
                    success: true,
                    letterSet: data.letterSet,
                    wordCount: data.allWords?.length || 0,
                    source: data.metadata?.letterSetSource
                });
            } else {
                console.log(`   ❌ Failed: ${result.message || 'No puzzle generated'}`);
                results.push({ difficulty, success: false, error: result.message });
            }
        } catch (err) {
            console.log(`   ❌ Error: ${err.message}`);
            results.push({ difficulty, success: false, error: err.message });
        }
    }

    return results;
}

async function main() {
    console.log('═'.repeat(60));
    console.log('  WORD GENERATOR TEST SUITE');
    console.log('═'.repeat(60));
    console.log(`  Time: ${new Date().toISOString()}`);

    const anagramResults = await testAnagramGenerator();
    const letterSetResults = await testLetterSetGenerator();

    // Summary
    console.log('\n\n' + '═'.repeat(60));
    console.log('  SUMMARY');
    console.log('═'.repeat(60));

    console.log('\nAnagram Generator:');
    anagramResults.forEach(r => {
        const status = r.success ? '✅' : '❌';
        console.log(`  ${status} ${r.difficulty}: ${r.success ? `${r.count} words` : r.error}`);
    });

    console.log('\nLetterSet Generator:');
    letterSetResults.forEach(r => {
        const status = r.success ? '✅' : '❌';
        console.log(`  ${status} ${r.difficulty}: ${r.success ? `${r.letterSet} (${r.wordCount} words)` : r.error}`);
    });

    // Check expanded pool size
    console.log('\nLetterSet knownGoodSets pool sizes:');
    const letterGen = new LetterSetGenerator();
    console.log(`  Easy: ${letterGen.knownGoodSets['Easy']?.length || 0} sets`);
    console.log(`  Medium: ${letterGen.knownGoodSets['Medium']?.length || 0} sets`);
    console.log(`  Hard: ${letterGen.knownGoodSets['Hard']?.length || 0} sets`);

    const allSuccess = [...anagramResults, ...letterSetResults].every(r => r.success);
    if (allSuccess) {
        console.log('\n✅ All generators working correctly!');
    } else {
        console.log('\n⚠️  Some generators had issues.');
    }
}

main().catch(err => {
    console.error('Test failed:', err);
    process.exit(1);
});
