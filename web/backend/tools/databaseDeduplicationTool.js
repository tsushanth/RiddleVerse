#!/usr/bin/env node

// tools/databaseDeduplicationTool.js - Standalone database analysis and cleanup tool

import { deduplicationService } from '../services/deduplicationService.js';
import { supabase } from '../config/database.js';
import fs from 'fs/promises';
import path from 'path';

/**
 * Comprehensive database deduplication analysis and cleanup tool
 * Uses the centralized deduplication service for analysis
 */
class DatabaseDeduplicationTool {
    constructor() {
        this.service = deduplicationService;
        this.service.setDebugMode(false); // Reduce noise for batch operations
        this.reportDir = './reports';
        this.backupDir = './backups';
    }

    /**
     * Analyze all puzzles in the database for duplicates
     */
    async analyzeAllPuzzles(options = {}) {
        const {
            puzzleTypes = null, // null = all types
            generateReport = true,
            batchSize = 100,
            maxPuzzlesPerType = null
        } = options;

        console.log('🔍 Starting comprehensive duplicate analysis...');
        console.log(`📊 Options: batchSize=${batchSize}, generateReport=${generateReport}`);

        const startTime = Date.now();
        const results = {
            totalPuzzlesAnalyzed: 0,
            totalDuplicatesFound: 0,
            typeAnalysis: {},
            errors: [],
            analysisTime: 0
        };

        try {
            // Get all puzzle types if not specified
            const typesToAnalyze = puzzleTypes || await this.getAllPuzzleTypes();
            console.log(`📋 Analyzing ${typesToAnalyze.length} puzzle types: ${typesToAnalyze.join(', ')}`);

            for (const puzzleType of typesToAnalyze) {
                console.log(`\n🔍 Analyzing ${puzzleType}...`);
                
                try {
                    const typeResult = await this.analyzePuzzleType(puzzleType, {
                        batchSize,
                        maxPuzzles: maxPuzzlesPerType
                    });
                    
                    results.typeAnalysis[puzzleType] = typeResult;
                    results.totalPuzzlesAnalyzed += typeResult.totalPuzzles;
                    results.totalDuplicatesFound += typeResult.duplicateCount;
                    
                    console.log(`✅ ${puzzleType}: ${typeResult.duplicateCount}/${typeResult.totalPuzzles} duplicates (${typeResult.duplicatePercentage}%)`);
                    
                } catch (error) {
                    console.error(`❌ Error analyzing ${puzzleType}: ${error.message}`);
                    results.errors.push({
                        puzzleType,
                        error: error.message,
                        timestamp: new Date().toISOString()
                    });
                }
            }

            results.analysisTime = Date.now() - startTime;
            
            // Generate report if requested
            if (generateReport) {
                const reportPath = await this.generateAnalysisReport(results);
                console.log(`\n📄 Analysis report saved: ${reportPath}`);
            }

            this.printAnalysisSummary(results);
            return results;

        } catch (error) {
            console.error(`💥 Analysis failed: ${error.message}`);
            throw error;
        }
    }

    /**
     * Analyze a specific puzzle type for duplicates
     */
    async analyzePuzzleType(puzzleType, options = {}) {
        const { batchSize = 100, maxPuzzles = null } = options;
        
        // Get all puzzles for this type
        let query = supabase
            .from('puzzles')
            .select('puzzleid, question, answer, timestamp, difficulty')
            .ilike('type', puzzleType)
            .not('question', 'is', null)
            .order('timestamp', { ascending: true });

        if (maxPuzzles) {
            query = query.limit(maxPuzzles);
        }

        const { data: puzzles, error } = await query;
        
        if (error) {
            throw new Error(`Failed to fetch ${puzzleType} puzzles: ${error.message}`);
        }

        if (!puzzles || puzzles.length === 0) {
            return {
                puzzleType,
                totalPuzzles: 0,
                duplicateCount: 0,
                duplicatePercentage: 0,
                duplicates: [],
                strategy: this.service.getDeduplicationStrategy(puzzleType)
            };
        }

        const duplicates = [];
        const seenQuestions = new Map(); // question -> first occurrence info
        let processedCount = 0;

        // Process in batches to avoid memory issues
        for (let i = 0; i < puzzles.length; i += batchSize) {
            const batch = puzzles.slice(i, i + batchSize);
            console.log(`  📦 Processing batch ${Math.floor(i/batchSize) + 1}/${Math.ceil(puzzles.length/batchSize)} (${batch.length} puzzles)`);

            for (const puzzle of batch) {
                try {
                    const question = puzzle.question;
                    
                    // Check against all previously seen questions
                    let isDuplicate = false;
                    let duplicateOf = null;

                    for (const [seenQuestion, seenInfo] of seenQuestions.entries()) {
                        if (await this.service.isDuplicate(puzzleType, question, [seenQuestion])) {
                            isDuplicate = true;
                            duplicateOf = seenInfo;
                            break;
                        }
                    }

                    if (isDuplicate) {
                        duplicates.push({
                            puzzleId: puzzle.puzzleid,
                            question: question.substring(0, 100) + (question.length > 100 ? '...' : ''),
                            timestamp: puzzle.timestamp,
                            difficulty: puzzle.difficulty,
                            duplicateOf: {
                                puzzleId: duplicateOf.puzzleId,
                                timestamp: duplicateOf.timestamp
                            }
                        });
                    } else {
                        // Add to seen questions
                        seenQuestions.set(question, {
                            puzzleId: puzzle.puzzleid,
                            timestamp: puzzle.timestamp,
                            difficulty: puzzle.difficulty
                        });
                    }

                    processedCount++;
                } catch (error) {
                    console.warn(`⚠️ Error processing puzzle ${puzzle.puzzleid}: ${error.message}`);
                }
            }
        }

        return {
            puzzleType,
            totalPuzzles: puzzles.length,
            duplicateCount: duplicates.length,
            duplicatePercentage: puzzles.length > 0 ? Math.round((duplicates.length / puzzles.length) * 100 * 10) / 10 : 0,
            duplicates,
            strategy: this.service.getDeduplicationStrategy(puzzleType),
            processedCount
        };
    }

    /**
     * Remove duplicates from the database
     */
    async removeDuplicates(puzzleType, options = {}) {
        const {
            dryRun = true,
            backupFirst = true,
            batchSize = 50,
            maxDeletions = null
        } = options;

        console.log(`${dryRun ? '🔍 ANALYZING' : '🗑️ REMOVING'} duplicates for ${puzzleType}`);
        console.log(`📋 Options: dryRun=${dryRun}, backup=${backupFirst}, batchSize=${batchSize}`);

        const startTime = Date.now();
        const results = {
            puzzleType,
            totalPuzzles: 0,
            duplicatesFound: 0,
            duplicatesRemoved: 0,
            backupPath: null,
            errors: [],
            executionTime: 0
        };

        try {
            // First, analyze to find duplicates
            console.log('🔍 Analyzing for duplicates...');
            const analysis = await this.analyzePuzzleType(puzzleType);
            
            results.totalPuzzles = analysis.totalPuzzles;
            results.duplicatesFound = analysis.duplicateCount;

            if (analysis.duplicateCount === 0) {
                console.log('✅ No duplicates found!');
                return results;
            }

            console.log(`📊 Found ${analysis.duplicateCount} duplicates out of ${analysis.totalPuzzles} puzzles`);

            // Create backup if requested and not dry run
            if (backupFirst && !dryRun) {
                console.log('💾 Creating backup...');
                results.backupPath = await this.createBackup(puzzleType);
                console.log(`✅ Backup created: ${results.backupPath}`);
            }

            // Remove duplicates if not dry run
            if (!dryRun) {
                console.log('🗑️ Removing duplicates...');
                
                const duplicatesToRemove = analysis.duplicates.slice(0, maxDeletions || analysis.duplicates.length);
                const batches = this.chunkArray(duplicatesToRemove, batchSize);

                for (let i = 0; i < batches.length; i++) {
                    const batch = batches[i];
                    console.log(`  🗑️ Deleting batch ${i + 1}/${batches.length} (${batch.length} puzzles)`);

                    try {
                        const puzzleIds = batch.map(d => d.puzzleId);
                        
                        const { error } = await supabase
                            .from('puzzles')
                            .delete()
                            .in('puzzleid', puzzleIds);

                        if (error) {
                            throw new Error(`Batch deletion failed: ${error.message}`);
                        }

                        results.duplicatesRemoved += batch.length;
                        console.log(`    ✅ Deleted ${batch.length} duplicates`);

                    } catch (error) {
                        console.error(`    ❌ Batch ${i + 1} failed: ${error.message}`);
                        results.errors.push({
                            batch: i + 1,
                            error: error.message,
                            puzzleIds: batch.map(d => d.puzzleId)
                        });
                    }

                    // Small delay between batches
                    if (i < batches.length - 1) {
                        await new Promise(resolve => setTimeout(resolve, 1000));
                    }
                }
            } else {
                console.log('🔍 DRY RUN - No puzzles were actually deleted');
                results.duplicatesRemoved = 0;
            }

            results.executionTime = Date.now() - startTime;

            // Generate removal report
            const reportPath = await this.generateRemovalReport(results, analysis.duplicates);
            console.log(`📄 Removal report saved: ${reportPath}`);

            this.printRemovalSummary(results);
            return results;

        } catch (error) {
            console.error(`💥 Duplicate removal failed: ${error.message}`);
            results.errors.push({
                operation: 'general',
                error: error.message,
                timestamp: new Date().toISOString()
            });
            throw error;
        }
    }

    /**
     * Batch remove duplicates across all puzzle types
     */
    async removeDuplicatesForAllTypes(options = {}) {
        const {
            dryRun = true,
            backupFirst = true,
            puzzleTypes = null
        } = options;

        console.log(`🗑️ ${dryRun ? 'ANALYZING' : 'REMOVING'} duplicates across all puzzle types`);

        const typesToProcess = puzzleTypes || await this.getAllPuzzleTypes();
        const results = {
            totalTypes: typesToProcess.length,
            results: {},
            summary: {
                totalPuzzles: 0,
                totalDuplicatesFound: 0,
                totalDuplicatesRemoved: 0,
                errorsCount: 0
            }
        };

        for (const puzzleType of typesToProcess) {
            console.log(`\n🔄 Processing ${puzzleType}...`);
            
            try {
                const typeResult = await this.removeDuplicates(puzzleType, {
                    ...options,
                    backupFirst: false // Don't backup each type individually
                });
                
                results.results[puzzleType] = typeResult;
                results.summary.totalPuzzles += typeResult.totalPuzzles;
                results.summary.totalDuplicatesFound += typeResult.duplicatesFound;
                results.summary.totalDuplicatesRemoved += typeResult.duplicatesRemoved;
                results.summary.errorsCount += typeResult.errors.length;

            } catch (error) {
                console.error(`❌ Failed to process ${puzzleType}: ${error.message}`);
                results.results[puzzleType] = {
                    puzzleType,
                    error: error.message
                };
                results.summary.errorsCount++;
            }
        }

        // Create comprehensive backup if requested and not dry run
        if (backupFirst && !dryRun) {
            console.log('\n💾 Creating comprehensive backup...');
            const backupPath = await this.createFullDatabaseBackup();
            results.backupPath = backupPath;
            console.log(`✅ Full backup created: ${backupPath}`);
        }

        // Generate comprehensive report
        const reportPath = await this.generateComprehensiveReport(results);
        console.log(`\n📄 Comprehensive report saved: ${reportPath}`);

        this.printComprehensiveSummary(results);
        return results;
    }

    /**
     * Create backup of puzzles before deletion
     */
    async createBackup(puzzleType) {
        await this.ensureDirectoryExists(this.backupDir);
        
        const timestamp = new Date().toISOString().replace(/[:.]/g, '-');
        const backupPath = path.join(this.backupDir, `${puzzleType}_backup_${timestamp}.json`);

        // Get all puzzles for this type
        const { data: puzzles, error } = await supabase
            .from('puzzles')
            .select('*')
            .ilike('type', puzzleType);

        if (error) {
            throw new Error(`Failed to fetch puzzles for backup: ${error.message}`);
        }

        const backupData = {
            puzzleType,
            backupTimestamp: new Date().toISOString(),
            totalPuzzles: puzzles.length,
            puzzles
        };

        await fs.writeFile(backupPath, JSON.stringify(backupData, null, 2));
        return backupPath;
    }

    /**
     * Create full database backup
     */
    async createFullDatabaseBackup() {
        await this.ensureDirectoryExists(this.backupDir);
        
        const timestamp = new Date().toISOString().replace(/[:.]/g, '-');
        const backupPath = path.join(this.backupDir, `full_database_backup_${timestamp}.json`);

        console.log('📥 Downloading full database...');
        const { data: puzzles, error } = await supabase
            .from('puzzles')
            .select('*')
            .order('timestamp', { ascending: true });

        if (error) {
            throw new Error(`Failed to fetch all puzzles: ${error.message}`);
        }

        const backupData = {
            backupType: 'full_database',
            backupTimestamp: new Date().toISOString(),
            totalPuzzles: puzzles.length,
            puzzles
        };

        await fs.writeFile(backupPath, JSON.stringify(backupData, null, 2));
        console.log(`💾 Full backup saved: ${puzzles.length} puzzles`);
        return backupPath;
    }

    /**
     * Restore from backup
     */
    async restoreFromBackup(backupPath, options = {}) {
        const { dryRun = true, clearFirst = false } = options;

        console.log(`🔄 ${dryRun ? 'SIMULATING' : 'PERFORMING'} restore from: ${backupPath}`);

        try {
            const backupData = JSON.parse(await fs.readFile(backupPath, 'utf8'));
            const { puzzles, puzzleType, backupTimestamp } = backupData;

            console.log(`📋 Backup info: ${puzzles.length} puzzles, created: ${backupTimestamp}`);

            if (clearFirst && !dryRun) {
                console.log('🗑️ Clearing existing puzzles...');
                
                let deleteQuery = supabase.from('puzzles').delete();
                if (puzzleType) {
                    deleteQuery = deleteQuery.ilike('type', puzzleType);
                }

                const { error } = await deleteQuery;
                if (error) {
                    throw new Error(`Failed to clear existing puzzles: ${error.message}`);
                }
            }

            if (!dryRun) {
                console.log('📥 Restoring puzzles...');
                
                const batchSize = 100;
                const batches = this.chunkArray(puzzles, batchSize);

                for (let i = 0; i < batches.length; i++) {
                    const batch = batches[i];
                    console.log(`  📦 Restoring batch ${i + 1}/${batches.length} (${batch.length} puzzles)`);

                    const { error } = await supabase
                        .from('puzzles')
                        .insert(batch);

                    if (error) {
                        console.error(`❌ Batch ${i + 1} failed: ${error.message}`);
                    } else {
                        console.log(`✅ Batch ${i + 1} restored successfully`);
                    }
                }
            }

            console.log(`${dryRun ? '🔍 SIMULATION' : '✅ RESTORE'} complete`);
            return {
                success: true,
                puzzlesRestored: puzzles.length,
                dryRun
            };

        } catch (error) {
            console.error(`💥 Restore failed: ${error.message}`);
            throw error;
        }
    }

    /**
     * Generate analysis report
     */
    async generateAnalysisReport(results) {
        await this.ensureDirectoryExists(this.reportDir);
        
        const timestamp = new Date().toISOString().replace(/[:.]/g, '-');
        const reportPath = path.join(this.reportDir, `duplicate_analysis_${timestamp}.json`);

        const report = {
            reportType: 'duplicate_analysis',
            generatedAt: new Date().toISOString(),
            summary: {
                totalPuzzlesAnalyzed: results.totalPuzzlesAnalyzed,
                totalDuplicatesFound: results.totalDuplicatesFound,
                duplicatePercentage: results.totalPuzzlesAnalyzed > 0 ? 
                    Math.round((results.totalDuplicatesFound / results.totalPuzzlesAnalyzed) * 100 * 10) / 10 : 0,
                analysisTimeMs: results.analysisTime,
                typesAnalyzed: Object.keys(results.typeAnalysis).length
            },
            typeAnalysis: results.typeAnalysis,
            errors: results.errors,
            recommendations: this.generateRecommendations(results)
        };

        await fs.writeFile(reportPath, JSON.stringify(report, null, 2));
        return reportPath;
    }

    /**
     * Generate removal report
     */
    async generateRemovalReport(results, duplicateDetails) {
        await this.ensureDirectoryExists(this.reportDir);
        
        const timestamp = new Date().toISOString().replace(/[:.]/g, '-');
        const reportPath = path.join(this.reportDir, `duplicate_removal_${results.puzzleType}_${timestamp}.json`);

        const report = {
            reportType: 'duplicate_removal',
            generatedAt: new Date().toISOString(),
            puzzleType: results.puzzleType,
            summary: results,
            duplicateDetails: duplicateDetails,
            backupPath: results.backupPath
        };

        await fs.writeFile(reportPath, JSON.stringify(report, null, 2));
        return reportPath;
    }

    /**
     * Generate comprehensive report for all types
     */
    async generateComprehensiveReport(results) {
        await this.ensureDirectoryExists(this.reportDir);
        
        const timestamp = new Date().toISOString().replace(/[:.]/g, '-');
        const reportPath = path.join(this.reportDir, `comprehensive_deduplication_${timestamp}.json`);

        const report = {
            reportType: 'comprehensive_deduplication',
            generatedAt: new Date().toISOString(),
            summary: results.summary,
            typeResults: results.results,
            backupPath: results.backupPath,
            recommendations: this.generateComprehensiveRecommendations(results)
        };

        await fs.writeFile(reportPath, JSON.stringify(report, null, 2));
        return reportPath;
    }

    /**
     * Get all puzzle types from the database
     */
    async getAllPuzzleTypes() {
        const { data, error } = await supabase
            .from('puzzles')
            .select('type')
            .not('type', 'is', null);

        if (error) {
            throw new Error(`Failed to get puzzle types: ${error.message}`);
        }

        const types = [...new Set(data.map(row => row.type))].filter(Boolean);
        return types.sort();
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

    async ensureDirectoryExists(dir) {
        try {
            await fs.access(dir);
        } catch {
            await fs.mkdir(dir, { recursive: true });
        }
    }

    generateRecommendations(results) {
        const recommendations = [];
        
        for (const [puzzleType, analysis] of Object.entries(results.typeAnalysis)) {
            if (analysis.duplicatePercentage > 20) {
                recommendations.push({
                    priority: 'high',
                    puzzleType,
                    issue: `High duplicate rate: ${analysis.duplicatePercentage}%`,
                    action: 'Immediate cleanup recommended'
                });
            } else if (analysis.duplicatePercentage > 10) {
                recommendations.push({
                    priority: 'medium',
                    puzzleType,
                    issue: `Moderate duplicate rate: ${analysis.duplicatePercentage}%`,
                    action: 'Consider cleanup'
                });
            }
        }

        return recommendations;
    }

    generateComprehensiveRecommendations(results) {
        const recommendations = [];
        
        if (results.summary.totalDuplicatesFound > 100) {
            recommendations.push({
                priority: 'high',
                scope: 'database',
                issue: `${results.summary.totalDuplicatesFound} total duplicates found`,
                action: 'Comprehensive cleanup recommended'
            });
        }

        if (results.summary.errorsCount > 0) {
            recommendations.push({
                priority: 'medium',
                scope: 'system',
                issue: `${results.summary.errorsCount} errors occurred during processing`,
                action: 'Review error logs and fix underlying issues'
            });
        }

        return recommendations;
    }

    /**
     * Print summaries
     */
    printAnalysisSummary(results) {
        console.log('\n📊 ANALYSIS SUMMARY');
        console.log('='.repeat(50));
        console.log(`Total Puzzles Analyzed: ${results.totalPuzzlesAnalyzed}`);
        console.log(`Total Duplicates Found: ${results.totalDuplicatesFound}`);
        console.log(`Overall Duplicate Rate: ${results.totalPuzzlesAnalyzed > 0 ? Math.round((results.totalDuplicatesFound / results.totalPuzzlesAnalyzed) * 100 * 10) / 10 : 0}%`);
        console.log(`Analysis Time: ${Math.round(results.analysisTime / 1000)}s`);
        console.log(`Types Analyzed: ${Object.keys(results.typeAnalysis).length}`);
        
        if (results.errors.length > 0) {
            console.log(`\n❌ Errors: ${results.errors.length}`);
        }
    }

    printRemovalSummary(results) {
        console.log('\n🗑️ REMOVAL SUMMARY');
        console.log('='.repeat(50));
        console.log(`Puzzle Type: ${results.puzzleType}`);
        console.log(`Total Puzzles: ${results.totalPuzzles}`);
        console.log(`Duplicates Found: ${results.duplicatesFound}`);
        console.log(`Duplicates Removed: ${results.duplicatesRemoved}`);
        console.log(`Execution Time: ${Math.round(results.executionTime / 1000)}s`);
        
        if (results.backupPath) {
            console.log(`Backup Created: ${results.backupPath}`);
        }
        
        if (results.errors.length > 0) {
            console.log(`\n❌ Errors: ${results.errors.length}`);
        }
    }

    printComprehensiveSummary(results) {
        console.log('\n🏁 COMPREHENSIVE SUMMARY');
        console.log('='.repeat(50));
        console.log(`Types Processed: ${results.totalTypes}`);
        console.log(`Total Puzzles: ${results.summary.totalPuzzles}`);
        console.log(`Total Duplicates Found: ${results.summary.totalDuplicatesFound}`);
        console.log(`Total Duplicates Removed: ${results.summary.totalDuplicatesRemoved}`);
        
        if (results.summary.errorsCount > 0) {
            console.log(`\n❌ Total Errors: ${results.summary.errorsCount}`);
        }
        
        if (results.backupPath) {
            console.log(`\nFull Backup: ${results.backupPath}`);
        }
    }
}

// Command Line Interface
async function main() {
    const args = process.argv.slice(2);
    const tool = new DatabaseDeduplicationTool();

    if (args.includes('--help') || args.includes('-h') || args.length === 0) {
        console.log(`
🗄️ Database Deduplication Tool

Usage:
  node tools/databaseDeduplicationTool.js <command> [options]

Commands:
  analyze [type]              Analyze puzzles for duplicates
  remove <type>               Remove duplicates for specific puzzle type
  remove-all                  Remove duplicates for all puzzle types
  backup <type>               Create backup of puzzle type
  backup-all                  Create full database backup
  restore <backup-path>       Restore from backup file

Options:
  --dry-run                   Don't actually delete anything (default: true)
  --wet-run                   Actually perform deletions
  --no-backup                 Skip backup creation
  --batch-size <n>            Batch size for operations (default: 100)
  --max-puzzles <n>           Limit analysis to N puzzles per type
  --max-deletions <n>         Limit deletions to N duplicates

Examples:
  # Analyze all puzzle types
  node tools/databaseDeduplicationTool.js analyze

  # Analyze specific type
  node tools/databaseDeduplicationTool.js analyze mathTipping

  # Remove duplicates (dry run)
  node tools/databaseDeduplicationTool.js remove mathTipping

  # Actually remove duplicates
  node tools/databaseDeduplicationTool.js remove mathTipping --wet-run

  # Remove all duplicates across all types
  node tools/databaseDeduplicationTool.js remove-all --wet-run

  # Create backup
  node tools/databaseDeduplicationTool.js backup mathTipping

  # Restore from backup
  node tools/databaseDeduplicationTool.js restore ./backups/mathTipping_backup.json
        `);
        process.exit(0);
    }

    const command = args[0];
    const isDryRun = !args.includes('--wet-run');
    const noBackup = args.includes('--no-backup');
    const batchSize = parseInt(args.find(arg => arg.startsWith('--batch-size='))?.split('=')[1]) || 100;
    const maxPuzzles = parseInt(args.find(arg => arg.startsWith('--max-puzzles='))?.split('=')[1]) || null;
    const maxDeletions = parseInt(args.find(arg => arg.startsWith('--max-deletions='))?.split('=')[1]) || null;

    try {
        switch (command) {
            case 'analyze':
                const puzzleType = args[1];
                if (puzzleType) {
                    console.log(`🔍 Analyzing ${puzzleType} for duplicates...`);
                    const result = await tool.analyzePuzzleType(puzzleType, { batchSize, maxPuzzles });
                    console.log(`\n📊 Results: ${result.duplicateCount}/${result.totalPuzzles} duplicates (${result.duplicatePercentage}%)`);
                } else {
                    await tool.analyzeAllPuzzles({ batchSize, maxPuzzlesPerType: maxPuzzles });
                }
                break;

            case 'remove':
                const typeToRemove = args[1];
                if (!typeToRemove) {
                    console.error('❌ Puzzle type required for remove command');
                    process.exit(1);
                }
                await tool.removeDuplicates(typeToRemove, {
                    dryRun: isDryRun,
                    backupFirst: !noBackup,
                    batchSize,
                    maxDeletions
                });
                break;

            case 'remove-all':
                await tool.removeDuplicatesForAllTypes({
                    dryRun: isDryRun,
                    backupFirst: !noBackup,
                    batchSize
                });
                break;

            case 'backup':
                const typeToBackup = args[1];
                if (!typeToBackup) {
                    console.error('❌ Puzzle type required for backup command');
                    process.exit(1);
                }
                const backupPath = await tool.createBackup(typeToBackup);
                console.log(`✅ Backup created: ${backupPath}`);
                break;

            case 'backup-all':
                const fullBackupPath = await tool.createFullDatabaseBackup();
                console.log(`✅ Full backup created: ${fullBackupPath}`);
                break;

            case 'restore':
                const restorePath = args[1];
                if (!restorePath) {
                    console.error('❌ Backup file path required for restore command');
                    process.exit(1);
                }
                await tool.restoreFromBackup(restorePath, {
                    dryRun: isDryRun,
                    clearFirst: args.includes('--clear-first')
                });
                break;

            default:
                console.error(`❌ Unknown command: ${command}`);
                console.log('Use --help for usage information');
                process.exit(1);
        }

        console.log('\n✅ Operation completed successfully!');

    } catch (error) {
        console.error(`💥 Operation failed: ${error.message}`);
        console.error(error);
        process.exit(1);
    }
}

// Run if called directly
if (import.meta.url === `file://${process.argv[1]}`) {
    main();
}

export { DatabaseDeduplicationTool };