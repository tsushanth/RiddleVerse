#!/usr/bin/env node

// Academic vocabulary antonym generator - works with specialized words

import { supabase } from '../config/database.js';
import { callAI } from '../utils/aiClient.js';
import fs from 'fs/promises';
import path from 'path';

class AcademicAntonymGenerator {
    constructor() {
        this.reportDir = './reports';
        
        // Lower thresholds for academic vocabulary
        this.thresholds = {
            minCrosswordScore: 15.0,    // Your words have high scores (20+)
            batchSize: 8,               // Smaller batches for AI efficiency
            targetPairs: 25,            // Reasonable target
            maxRetries: 3
        };

        // Academic word categories that often have antonyms
        this.academicCategories = {
            emotions: ['personality', 'character', 'temperament'],
            relationships: ['relationship', 'relations', 'interaction'],
            processes: ['formation', 'creation', 'development', 'termination'],
            qualities: ['excellence', 'superiority', 'inferiority'],
            states: ['completion', 'initiation', 'continuation'],
            concepts: ['clarity', 'complexity', 'simplicity']
        };
    }

    async generateAcademicAntonyms(options = {}) {
        const {
            maxWords = 200,
            targetPairs = 25,
            validateOnly = false,
            useContextual = true
        } = options;

        console.log('🎓 ACADEMIC VOCABULARY ANTONYM GENERATOR');
        console.log('='.repeat(60));
        console.log(`📊 Max words: ${maxWords}`);
        console.log(`🎯 Target pairs: ${targetPairs}`);
        console.log(`🔍 Mode: ${validateOnly ? 'validate only' : 'generate and store'}`);
        console.log(`🧠 Contextual antonyms: ${useContextual ? 'enabled' : 'disabled'}`);
        console.log('');

        const startTime = Date.now();
        const results = {
            totalWordsProcessed: 0,
            antonymPairsGenerated: 0,
            strategies: {
                ai_conceptual: 0,
                ai_contextual: 0,
                academic_patterns: 0
            },
            generatedPairs: [],
            errors: [],
            executionTime: 0
        };

        try {
            // Step 1: Get your high-quality academic words
            console.log('📚 Fetching academic vocabulary...');
            const academicWords = await this.getAcademicWords(maxWords);
            console.log(`✅ Found ${academicWords.length} academic words`);
            
            if (academicWords.length === 0) {
                throw new Error('No academic words found');
            }

            results.totalWordsProcessed = academicWords.length;

            // Show sample of academic words
            console.log('\n📝 Sample academic words:');
            academicWords.slice(0, 8).forEach((word, index) => {
                console.log(`   ${index + 1}. "${word.word}" (${word.part_of_speech}, score: ${word.crossword_score})`);
            });

            // Step 2: AI-powered conceptual antonym generation
            console.log('\n🤖 Strategy 1: AI Conceptual Antonym Generation...');
            const conceptualPairs = await this.generateConceptualAntonyms(academicWords, targetPairs / 2);
            results.strategies.ai_conceptual = conceptualPairs.length;
            results.generatedPairs.push(...conceptualPairs);
            console.log(`✅ Generated ${conceptualPairs.length} conceptual antonym pairs`);

            // Step 3: Contextual antonym generation (if enabled)
            if (useContextual && results.generatedPairs.length < targetPairs) {
                console.log('\n🧠 Strategy 2: AI Contextual Antonym Generation...');
                const remaining = targetPairs - results.generatedPairs.length;
                const contextualPairs = await this.generateContextualAntonyms(academicWords, remaining);
                results.strategies.ai_contextual = contextualPairs.length;
                results.generatedPairs.push(...contextualPairs);
                console.log(`✅ Generated ${contextualPairs.length} contextual antonym pairs`);
            }
            results.generatedPairs = this.deduplicatePairs(results.generatedPairs).slice(0, targetPairs);

            // Step 4: Store the pairs
            if (!validateOnly && results.generatedPairs.length > 0) {
                console.log('\n💾 Storing generated antonym pairs...');
                const stored = await this.storeGeneratedPairs(results.generatedPairs);
                results.antonymPairsGenerated = stored;
                console.log(`✅ Stored ${stored} antonym pairs`);
            } else if (validateOnly) {
                results.antonymPairsGenerated = results.generatedPairs.length;
            }

            // Step 5: Generate report
            results.executionTime = Date.now() - startTime;
            const reportPath = await this.generateAcademicReport(results, academicWords);
            console.log(`\n📄 Report saved: ${reportPath}`);

            this.printAcademicSummary(results);
            return results;

        } catch (error) {
            console.error(`💥 Fatal error:`, error);
            results.executionTime = Date.now() - startTime;
            return results;
        }
    }

    /**
     * Get academic words with high crossword scores
     */
    async getAcademicWords(limit) {
        // Your words have high crossword scores, so target those
        const { data: words, error } = await supabase
            .from('common_words')
            .select('word, part_of_speech, difficulty, crossword_score, has_definition, definition')
            .eq('has_definition', true)
            .gte('crossword_score', this.thresholds.minCrosswordScore)
            .in('part_of_speech', ['n', 'adj', 'v']) // Focus on nouns, adjectives, verbs
            .order('crossword_score', { ascending: false })
            .limit(limit);

        if (error) {
            throw new Error(`Failed to fetch academic words: ${error.message}`);
        }

        return words || [];
    }

    deduplicatePairs(pairs) {
        const seen = new Set();
        return pairs.filter(p => {
            const key = [p.word1, p.word2].sort().join('|');
            if (seen.has(key)) return false;
            seen.add(key);
            return true;
        });
    }

    /**
     * Generate conceptual antonyms using AI
     */
    async generateConceptualAntonyms(academicWords, targetCount) {
        console.log(`   🤖 Generating conceptual antonyms for ${academicWords.length} academic words...`);
        
        const generatedPairs = [];
        const batches = this.chunkArray(academicWords, this.thresholds.batchSize);
        
        for (let i = 0; i < batches.length && generatedPairs.length < targetCount; i++) {
            const batch = batches[i];
            console.log(`   📦 Processing conceptual batch ${i + 1}/${Math.min(batches.length, Math.ceil(targetCount / this.thresholds.batchSize))}`);
            
            try {
                const batchPairs = await this.aiGenerateConceptualAntonyms(batch);
                
                for (const pair of batchPairs) {
                    if (generatedPairs.length >= targetCount) break;
                    
                    // Validate that both words are meaningful
                    if (this.validateAcademicPair(pair)) {
                        generatedPairs.push({
                            ...pair,
                            source: 'ai_conceptual',
                            confidence: pair.confidence || 0.8,
                            category: 'conceptual'
                        });
                        console.log(`      ✅ "${pair.word1}" ↔ "${pair.word2}" (confidence: ${(pair.confidence || 0.8).toFixed(2)})`);
                    } else {
                        console.log(`      ❌ Rejected: "${pair.word1}" ↔ "${pair.word2}" (validation failed)`);
                    }
                }
            } catch (error) {
                console.warn(`   ⚠️ Batch ${i + 1} error: ${error.message}`);
            }
            
            // Pause between batches
            await this.sleep(2000);
        }

        return generatedPairs;
    }

    /**
     * AI conceptual antonym generation
     */
    async aiGenerateConceptualAntonyms(wordBatch) {
        const wordsWithDefs = wordBatch.map(w => {
            const def = w.definition?.trim().replace(/\.$/, '') || 'academic term';
            return `- "${w.word}" (${def})`;
        }).join('\n');
        
        const prompt = `For each academic word below, create a conceptual antonym based on the provided definition. Focus on meaningful, academic opposites—not just morphological variations.
        
        ${wordsWithDefs}
        
        Respond with JSON array only:
        [
          { "word1": "clarity", "word2": "obscurity", "confidence": 0.9, "reasoning": "Clear conceptual opposites" },
          ...
        ]`;
        
        try {
            const response = await callAI(prompt);
            const results = JSON.parse(response);
            
            console.log(`      🧠 AI generated ${results.length} conceptual antonym pairs`);
            return results;
            
        } catch (error) {
            console.warn(`   ⚠️ AI conceptual generation failed: ${error.message}`);
            return [];
        }
    }

    /**
     * Generate contextual antonyms
     */
    async generateContextualAntonyms(academicWords, targetCount) {
        console.log(`   🧠 Generating contextual antonyms for academic vocabulary...`);
        
        const generatedPairs = [];
        const batches = this.chunkArray(academicWords, this.thresholds.batchSize);
        
        for (let i = 0; i < batches.length && generatedPairs.length < targetCount; i++) {
            const batch = batches[i];
            console.log(`   📦 Processing contextual batch ${i + 1}/${Math.min(batches.length, Math.ceil(targetCount / this.thresholds.batchSize))}`);
            
            try {
                const batchPairs = await this.aiGenerateContextualAntonyms(batch);
                
                for (const pair of batchPairs) {
                    if (generatedPairs.length >= targetCount) break;
                    
                    if (this.validateAcademicPair(pair)) {
                        generatedPairs.push({
                            ...pair,
                            source: 'ai_contextual',
                            confidence: pair.confidence || 0.7,
                            category: 'contextual'
                        });
                        console.log(`      ✅ "${pair.word1}" ↔ "${pair.word2}" (${pair.context})`);
                    }
                }
            } catch (error) {
                console.warn(`   ⚠️ Contextual batch ${i + 1} error: ${error.message}`);
            }
            
            await this.sleep(2000);
        }

        return generatedPairs;
    }

    /**
     * AI contextual antonym generation
     */
    async aiGenerateContextualAntonyms(wordBatch) {
        const words = wordBatch.map(w => `- "${w.word}" (${w.definition || 'academic term'})`).join('\n');

const prompt = `For each academic word below, generate a contextual antonym using its meaning. Focus on opposites in professional, scientific, or educational settings.

${words}

Respond as JSON:
[
  { "word1": "formation", "word2": "dissolution", "confidence": 0.85, "context": "process", "reasoning": "Formation creates; dissolution ends" }
]`;

        try {
            const response = await callAI(prompt, 'GPT-3.5-turbo');
            const results = JSON.parse(response);
            
            console.log(`      🧠 AI generated ${results.length} contextual antonym pairs`);
            return results;
            
        } catch (error) {
            console.warn(`   ⚠️ AI contextual generation failed: ${error.message}`);
            return [];
        }
    }

    /**
     * Validate academic word pairs
     */
    validateAcademicPair(pair) {
        // Basic validation
        if (!pair.word1 || !pair.word2 || pair.word1 === pair.word2) {
            return false;
        }

        // Check word length (academic words are usually longer)
        if (pair.word1.length < 3 || pair.word2.length < 3) {
            return false;
        }

        // Check confidence
        if ((pair.confidence || 0) < 0.6) {
            return false;
        }
        const weakAntonyms = ['thing', 'stuff', 'subject', 'object', 'ocean', 'area', 'matter'];
        if (weakAntonyms.includes(pair.word2.toLowerCase())) return false;

        if (pair.word1.length < 4 || pair.word2.length < 4) return false;
        if (pair.word1.toLowerCase() === pair.word2.toLowerCase()) return false;

        return true;
    }

    /**
     * Store generated pairs
     */
    async storeGeneratedPairs(pairs) {
        let storedCount = 0;
        pairs.sort((a, b) => (b.confidence || 0) - (a.confidence || 0));

        
        for (const pair of pairs) {
            try {
                const pairData = {
                    word_1: pair.word1,
                    word_2: pair.word2,
                    confidence_score: pair.confidence,
                    source: pair.source,
                    semantic_distance: 0.6, // Default for AI-generated
                    frequency_balance: 0.7,
                    difficulty_match: 0.8,  // Academic words often same difficulty
                    context_compatibility: 0.9, // AI ensures compatibility
                    puzzle_quality_score: pair.confidence,
                    validation_status: 'ai_validated',
                    manual_verified: false,
                    usage_examples: JSON.stringify({
                        context: pair.context || pair.reasoning,
                        category: pair.category
                    }),
                    created_at: new Date().toISOString(),
                    updated_at: new Date().toISOString()
                };

                const { error } = await supabase
                    .from('word_antonyms')
                    .upsert(pairData, { onConflict: 'word_1,word_2' });

                if (error) {
                    console.warn(`   ⚠️ Storage error for [${pair.word1}, ${pair.word2}]: ${error.message}`);
                } else {
                    storedCount++;
                }

            } catch (error) {
                console.warn(`   ⚠️ Storage exception for [${pair.word1}, ${pair.word2}]: ${error.message}`);
            }
        }

        return storedCount;
    }

    /**
     * Utility methods
     */
    chunkArray(array, size) {
        const chunks = [];
        for (let i = 0; i < array.length; i += size) {
            chunks.push(array.slice(i, i + size));
        }
        return chunks;
    }

    async sleep(ms) {
        return new Promise(resolve => setTimeout(resolve, ms));
    }

    async ensureDirectoryExists(dir) {
        try {
            await fs.access(dir);
        } catch {
            await fs.mkdir(dir, { recursive: true });
        }
    }

    /**
     * Generate academic report
     */
    async generateAcademicReport(results, academicWords) {
        await this.ensureDirectoryExists(this.reportDir);
        
        const timestamp = new Date().toISOString().replace(/[:.]/g, '-');
        const reportPath = path.join(this.reportDir, `academic_antonym_generation_${timestamp}.json`);

        const report = {
            reportType: 'academic_antonym_generation',
            generatedAt: new Date().toISOString(),
            summary: results,
            academicWordsSample: academicWords.slice(0, 20).map(w => ({
                word: w.word,
                part_of_speech: w.part_of_speech,
                crossword_score: w.crossword_score
            })),
            generatedPairsSample: results.generatedPairs.slice(0, 10),
            thresholds: this.thresholds,
            recommendations: this.generateAcademicRecommendations(results)
        };

        await fs.writeFile(reportPath, JSON.stringify(report, null, 2));
        return reportPath;
    }

    /**
     * Generate recommendations
     */
    generateAcademicRecommendations(results) {
        const recommendations = [];

        if (results.antonymPairsGenerated === 0) {
            recommendations.push({
                priority: 'high',
                message: 'No academic antonym pairs generated - check AI service and word selection'
            });
        } else if (results.antonymPairsGenerated < 10) {
            recommendations.push({
                priority: 'medium',
                message: 'Limited academic antonym pairs - consider expanding target or improving prompts'
            });
        } else {
            recommendations.push({
                priority: 'info',
                message: `Successfully generated ${results.antonymPairsGenerated} academic antonym pairs`
            });
        }

        if (results.strategies.ai_conceptual > results.strategies.ai_contextual) {
            recommendations.push({
                priority: 'info',
                message: 'Conceptual antonyms more successful - academic vocabulary works well with concept-based opposites'
            });
        }

        return recommendations;
    }

    /**
     * Print summary
     */
    printAcademicSummary(results) {
        console.log('\n🏁 ACADEMIC ANTONYM GENERATION COMPLETE');
        console.log('='.repeat(50));
        console.log(`📚 Academic words processed: ${results.totalWordsProcessed}`);
        console.log(`✅ Antonym pairs generated: ${results.antonymPairsGenerated}`);
        console.log(`⏱️ Execution time: ${Math.round(results.executionTime / 1000)}s`);

        console.log('\n📊 STRATEGY BREAKDOWN:');
        Object.entries(results.strategies).forEach(([strategy, count]) => {
            console.log(`   ${strategy}: ${count} pairs`);
        });

        if (results.generatedPairs.length > 0) {
            console.log('\n📝 SAMPLE GENERATED PAIRS:');
            results.generatedPairs.slice(0, 5).forEach((pair, index) => {
                console.log(`   ${index + 1}. "${pair.word1}" ↔ "${pair.word2}" (${pair.confidence.toFixed(2)}, ${pair.category})`);
            });
        }

        if (results.antonymPairsGenerated > 0) {
            console.log(`\n🎉 SUCCESS! Generated ${results.antonymPairsGenerated} academic antonym pairs`);
            console.log(`📝 Ready for puzzle generation: node freshAntonymGenerator.js`);
        } else {
            console.log(`\n⚠️ No academic antonym pairs generated`);
        }
    }
}

// Command Line Interface
async function main() {
    const args = process.argv.slice(2);
    const generator = new AcademicAntonymGenerator();

    if (args.includes('--help') || args.includes('-h')) {
        console.log(`
🎓 Academic Vocabulary Antonym Generator

Specialized for academic/professional vocabulary with high crossword scores.
Uses AI to create conceptual and contextual antonyms.

Usage:
  node academicAntonymGenerator.js [options]

Options:
  --max-words <n>        Maximum words to process (default: 200)
  --target-pairs <n>     Target number of antonym pairs (default: 25)
  --validate-only        Only generate, don't store (default: false)
  --no-contextual        Disable contextual antonym generation

Examples:
  # Generate academic antonyms
  node academicAntonymGenerator.js

  # Generate more pairs
  node academicAntonymGenerator.js --target-pairs 50

  # Test mode without storing
  node academicAntonymGenerator.js --validate-only --target-pairs 10
        `);
        process.exit(0);
    }

    const maxWords = parseInt(args.find(arg => arg.startsWith('--max-words='))?.split('=')[1]) || 
                     parseInt(args[args.indexOf('--max-words') + 1]) || 200;
    const targetPairs = parseInt(args.find(arg => arg.startsWith('--target-pairs='))?.split('=')[1]) || 
                       parseInt(args[args.indexOf('--target-pairs') + 1]) || 25;
    const validateOnly = args.includes('--validate-only');
    const useContextual = !args.includes('--no-contextual');

    try {
        const results = await generator.generateAcademicAntonyms({
            maxWords,
            targetPairs,
            validateOnly,
            useContextual
        });

        if (results.antonymPairsGenerated > 0) {
            console.log('\n✅ Academic antonym generation completed successfully!');
            process.exit(0);
        } else {
            console.log('\n⚠️ No academic antonym pairs generated');
            process.exit(1);
        }

    } catch (error) {
        console.error(`💥 Academic generation failed: ${error.message}`);
        console.error(error);
        process.exit(1);
    }
}

// Run if called directly
if (import.meta.url === `file://${process.argv[1]}`) {
    main();
}

export { AcademicAntonymGenerator };