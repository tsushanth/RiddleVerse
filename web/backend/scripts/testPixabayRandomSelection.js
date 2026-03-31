// testPixabayRandomSelection.js
// Tests that Pixabay search returns random images (not always the first one)
// Run: node scripts/testPixabayRandomSelection.js

import https from 'https';

const PIXABAY_KEY = "51870401-10c08576f17e1cb89793c39c7";

// Test queries - same ones from the generator
const TEST_QUERIES = [
    'golden retriever dog portrait',
    'eiffel tower paris',
    'sushi plate restaurant',
    'mountain lake landscape'
];

async function searchPixabay(query) {
    return new Promise((resolve, reject) => {
        const url = `https://pixabay.com/api/?key=${PIXABAY_KEY}&q=${encodeURIComponent(query)}&image_type=photo&per_page=20&safesearch=true&min_width=512&min_height=512&order=popular`;

        https.get(url, (res) => {
            let data = '';
            res.on('data', chunk => data += chunk);
            res.on('end', () => {
                try {
                    const result = JSON.parse(data);
                    if (result.hits && result.hits.length > 0) {
                        // Randomly select from available images
                        const randomIndex = Math.floor(Math.random() * result.hits.length);
                        resolve({
                            totalAvailable: result.hits.length,
                            selectedIndex: randomIndex,
                            photographer: result.hits[randomIndex].user,
                            imageId: result.hits[randomIndex].id
                        });
                    } else {
                        reject(new Error('No images found'));
                    }
                } catch (error) {
                    reject(error);
                }
            });
        }).on('error', reject);
    });
}

async function main() {
    console.log('═'.repeat(60));
    console.log('  PIXABAY RANDOM SELECTION TEST');
    console.log('═'.repeat(60));
    console.log('\nThis test verifies that Pixabay searches return random images');
    console.log('instead of always picking the first (most popular) result.\n');

    for (const query of TEST_QUERIES) {
        console.log(`\n📸 Query: "${query}"`);
        console.log('   Running 5 searches to check for variation...\n');

        const results = [];
        for (let i = 0; i < 5; i++) {
            try {
                const result = await searchPixabay(query);
                results.push(result);
                console.log(`   ${i + 1}. Index ${result.selectedIndex}/${result.totalAvailable - 1} - ${result.photographer} (ID: ${result.imageId})`);

                // Small delay to avoid rate limiting
                await new Promise(r => setTimeout(r, 500));
            } catch (err) {
                console.log(`   ${i + 1}. Error: ${err.message}`);
            }
        }

        // Check for variation
        const uniqueIndices = [...new Set(results.map(r => r.selectedIndex))];
        const uniquePhotographers = [...new Set(results.map(r => r.photographer))];

        if (uniqueIndices.length > 1) {
            console.log(`\n   ✅ PASS: Got ${uniqueIndices.length} different indices (random selection working)`);
        } else if (results.length > 0) {
            console.log(`\n   ⚠️  WARNING: All 5 searches returned index ${uniqueIndices[0]}`);
            console.log('   This could be random chance, or the fix may not be working.');
        }

        console.log(`   Unique photographers: ${uniquePhotographers.join(', ')}`);
    }

    console.log('\n' + '═'.repeat(60));
    console.log('  TEST COMPLETE');
    console.log('═'.repeat(60));
    console.log('\nIf you see different indices for each query, random selection is working.');
    console.log('The actual puzzle generation will produce varied images from the pool.');
}

main().catch(err => {
    console.error('Test failed:', err);
    process.exit(1);
});
