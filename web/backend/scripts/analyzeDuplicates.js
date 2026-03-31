import { createClient } from '@supabase/supabase-js';
import dotenv from 'dotenv';

dotenv.config();

const supabase = createClient(process.env.SUPABASE_URL, process.env.SUPABASE_KEY);

async function findDuplicates() {
    // Get puzzle counts by type
    const { data: puzzles, error } = await supabase
        .from('puzzles')
        .select('type, question')
        .order('timestamp', { ascending: false })
        .limit(5000);

    if (error) {
        console.error('Error:', error);
        return;
    }

    // Group by type and find duplicates
    const typeStats = {};

    puzzles.forEach(p => {
        const type = (p.type || 'unknown').toLowerCase();
        if (!typeStats[type]) {
            typeStats[type] = { total: 0, questions: new Map() };
        }
        typeStats[type].total++;

        // Hash the question for comparison (first 200 chars)
        const qHash = (p.question || '').substring(0, 200);
        const count = typeStats[type].questions.get(qHash) || 0;
        typeStats[type].questions.set(qHash, count + 1);
    });

    // Calculate duplicate rates
    const results = [];
    for (const [type, stats] of Object.entries(typeStats)) {
        let duplicateCount = 0;
        stats.questions.forEach((count) => {
            if (count > 1) duplicateCount += (count - 1);
        });

        const dupRate = (duplicateCount / stats.total * 100).toFixed(1);
        results.push({
            type,
            total: stats.total,
            unique: stats.questions.size,
            duplicates: duplicateCount,
            dupRate: parseFloat(dupRate)
        });
    }

    // Sort by duplicate rate descending
    results.sort((a, b) => b.dupRate - a.dupRate);

    console.log('\n=== PUZZLE DUPLICATE ANALYSIS ===\n');
    console.log('Type'.padEnd(25) + ' | Total | Unique | Dups | Rate');
    console.log('-'.repeat(60));
    results.forEach(r => {
        if (r.total >= 5) { // Only show types with at least 5 puzzles
            console.log(
                r.type.padEnd(25) + ' | ' +
                String(r.total).padStart(5) + ' | ' +
                String(r.unique).padStart(6) + ' | ' +
                String(r.duplicates).padStart(4) + ' | ' +
                r.dupRate.toFixed(1) + '%'
            );
        }
    });

    // Show worst offenders
    console.log('\n=== TOP 5 WORST DUPLICATE RATES ===\n');
    results.filter(r => r.total >= 10).slice(0, 5).forEach(r => {
        console.log(`${r.type}: ${r.dupRate.toFixed(1)}% duplicates (${r.duplicates}/${r.total})`);
    });
}

findDuplicates();
