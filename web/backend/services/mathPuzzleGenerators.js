/**
 * 🎯 Comprehensive Randomized Math Puzzle Generators
 * Eliminates AI dependency for all math puzzle types
 */

import { DIFFICULTY_CRITERIA, PUZZLE_TYPES, DIFFICULTY_LEVELS } from '../config/puzzleConfig.js';
import { supabase } from '../config/database.js';

export class MathPuzzleGenerators {
    constructor() {
        // Track generated combinations to prevent duplicates within session
        this.usedCombinations = new Map(); // puzzleType -> Set of combination keys
        
        // Initialize used combinations for each puzzle type
        Object.values(PUZZLE_TYPES).forEach(type => {
            if (this.isMathPuzzle(type)) {
                this.usedCombinations.set(type, new Set());
            }
        });
    }

    /**
     * Check if a puzzle type is math-based and can be randomized
     */
    isMathPuzzle(puzzleType) {
        const mathTypes = [
            PUZZLE_TYPES.MATH_ESTIMATION,
            PUZZLE_TYPES.MATH_TIPPING,
            PUZZLE_TYPES.PERCENTAGES,
            PUZZLE_TYPES.DIVISION,
            PUZZLE_TYPES.AVERAGE,
            PUZZLE_TYPES.SUBTRACTION,
            PUZZLE_TYPES.PURCHASING,
            PUZZLE_TYPES.DISCOUNTS,
            PUZZLE_TYPES.CONVERSION
        ];
        return mathTypes.includes(puzzleType);
    }

    /**
     * Main generation function - routes to appropriate generator
     */
    async generatePuzzle(puzzleType, difficulty = 'Medium') {
        if (!this.isMathPuzzle(puzzleType)) {
            throw new Error(`${puzzleType} is not supported by randomized generation`);
        }

        const criteria = DIFFICULTY_CRITERIA[puzzleType]?.[difficulty];
        if (!criteria) {
            throw new Error(`No criteria found for ${puzzleType}/${difficulty}`);
        }

        let attempts = 0;
        const maxAttempts = 50;

        while (attempts < maxAttempts) {
            attempts++;

            let puzzle;
            try {
                // Generate puzzle based on type
                switch (puzzleType) {
                    case PUZZLE_TYPES.MATH_ESTIMATION:
                        puzzle = this.generateMathEstimation(difficulty, criteria);
                        break;
                    case PUZZLE_TYPES.MATH_TIPPING:
                        puzzle = this.generateMathTipping(difficulty, criteria);
                        break;
                    case PUZZLE_TYPES.PERCENTAGES:
                        puzzle = this.generatePercentages(difficulty, criteria);
                        break;
                    case PUZZLE_TYPES.DIVISION:
                        puzzle = this.generateDivision(difficulty, criteria);
                        break;
                    case PUZZLE_TYPES.AVERAGE:
                        puzzle = this.generateAverage(difficulty, criteria);
                        break;
                    case PUZZLE_TYPES.SUBTRACTION:
                        puzzle = this.generateSubtraction(difficulty, criteria);
                        break;
                    case PUZZLE_TYPES.PURCHASING:
                        puzzle = this.generatePurchasing(difficulty, criteria);
                        break;
                    case PUZZLE_TYPES.DISCOUNTS:
                        puzzle = this.generateDiscounts(difficulty, criteria);
                        break;
                    case PUZZLE_TYPES.CONVERSION:
                        puzzle = this.generateConversion(difficulty, criteria);
                        break;
                    default:
                        throw new Error(`Generator not implemented for ${puzzleType}`);
                }

                // Check for duplicates
                const combinationKey = this.getCombinationKey(puzzleType, puzzle);
                if (await this.isRecentDuplicate(puzzleType, combinationKey)) {
                    console.log(`⚠️ Attempt ${attempts}: Skipping duplicate ${puzzleType}`);
                    continue;
                }

                // Validate puzzle meets criteria
                if (!criteria.validateFn(puzzle)) {
                    console.log(`⚠️ Attempt ${attempts}: Puzzle failed validation for ${puzzleType}/${difficulty}`);
                    continue;
                }

                // Mark as used
                this.usedCombinations.get(puzzleType).add(combinationKey);

                console.log(`✅ Generated ${puzzleType} puzzle (attempt ${attempts})`);

                return {
                    success: true,
                    puzzle: puzzle,
                    attempts: attempts,
                    generationMethod: 'randomized'
                };

            } catch (error) {
                console.error(`❌ Error generating ${puzzleType} on attempt ${attempts}:`, error);
                continue;
            }
        }

        console.error(`❌ Failed to generate ${puzzleType} after ${maxAttempts} attempts`);
        return {
            success: false,
            error: `Could not generate unique ${puzzleType} puzzle after ${maxAttempts} attempts`,
            attempts: maxAttempts
        };
    }

    /**
     * Math Estimation Generator
     */
    generateMathEstimation(difficulty, criteria) {
        const { numberCount, numberRange, decimalPlaces } = criteria;
        
        // Generate count of numbers
        const count = this.randomInt(numberCount.min, numberCount.max);
        const numbers = [];
        
        for (let i = 0; i < count; i++) {
            let number;
            if (decimalPlaces && decimalPlaces.max > 0) {
                // Generate decimal number
                const whole = this.randomInt(numberRange.min, numberRange.max);
                const decimal = Math.random();
                const places = Math.min(decimalPlaces.max, 2);
                number = parseFloat((whole + decimal).toFixed(places));
            } else {
                // Generate whole number
                number = this.randomInt(numberRange.min, numberRange.max);
            }
            numbers.push(number);
        }

        const sum = Math.round(numbers.reduce((a, b) => a + b, 0) * 100) / 100;

        return {
            numbers: numbers,
            sum: sum,
            difficulty: difficulty,
            hint: "Round each number to make estimation easier"
        };
    }

    // Update this method in mathPuzzleGenerators.js - generateMathTipping()

    /**
     * Math Tipping Generator (Enhanced - Always Correct Tips)
     */
    generateMathTipping(difficulty, criteria) {
        const { billAmount, tipPercentages } = criteria;
        
        // Generate bill amount
        const bill = this.randomFloat(billAmount.min, billAmount.max, 2);
        
        // Select tip percentage
        const tipPercent = this.randomChoice(tipPercentages);
        
        // Calculate correct tip - ALWAYS use the correct amount
        const correctTip = Math.round(bill * (tipPercent / 100) * 100) / 100;
        
        // ✅ ALWAYS use correct tip amount and set isCorrect to true
        const tipAmount = correctTip;
        const isCorrect = true;

        return {
            billAmount: bill,
            tipPercentage: tipPercent,
            tipAmount: tipAmount,
            isCorrect: isCorrect,
            difficulty: difficulty,
            hint: `Calculate ${tipPercent}% tip on $${bill}`
        };
    }

    generateConversion(difficulty, criteria) {
        // Comprehensive conversion database
        const conversionTypes = {
            distance: {
                conversions: [
                    { from: 'miles', to: 'kilometers', factor: 1.60934, precision: 1 },
                    { from: 'kilometers', to: 'miles', factor: 0.621371, precision: 1 },
                    { from: 'feet', to: 'meters', factor: 0.3048, precision: 1 },
                    { from: 'meters', to: 'feet', factor: 3.28084, precision: 0 },
                    { from: 'inches', to: 'centimeters', factor: 2.54, precision: 1 },
                    { from: 'centimeters', to: 'inches', factor: 0.393701, precision: 1 },
                    { from: 'yards', to: 'meters', factor: 0.9144, precision: 1 },
                    { from: 'meters', to: 'yards', factor: 1.09361, precision: 1 }
                ],
                ranges: {
                    [DIFFICULTY_LEVELS.EASY]: { min: 1, max: 100 },
                    [DIFFICULTY_LEVELS.MEDIUM]: { min: 50, max: 500 },
                    [DIFFICULTY_LEVELS.HARD]: { min: 100, max: 1000 },
                    [DIFFICULTY_LEVELS.EXPERT]: { min: 500, max: 5000 }
                }
            },
            weight: {
                conversions: [
                    { from: 'pounds', to: 'kilograms', factor: 0.453592, precision: 1 },
                    { from: 'kilograms', to: 'pounds', factor: 2.20462, precision: 1 },
                    { from: 'ounces', to: 'grams', factor: 28.3495, precision: 0 },
                    { from: 'grams', to: 'ounces', factor: 0.035274, precision: 2 },
                    { from: 'tons', to: 'kilograms', factor: 1000, precision: 0 },
                    { from: 'kilograms', to: 'tons', factor: 0.001, precision: 3 }
                ],
                ranges: {
                    [DIFFICULTY_LEVELS.EASY]: { min: 1, max: 50 },
                    [DIFFICULTY_LEVELS.MEDIUM]: { min: 25, max: 200 },
                    [DIFFICULTY_LEVELS.HARD]: { min: 100, max: 1000 },
                    [DIFFICULTY_LEVELS.EXPERT]: { min: 500, max: 5000 }
                }
            },
            volume: {
                conversions: [
                    { from: 'gallons', to: 'liters', factor: 3.78541, precision: 1 },
                    { from: 'liters', to: 'gallons', factor: 0.264172, precision: 2 },
                    { from: 'cups', to: 'milliliters', factor: 236.588, precision: 0 },
                    { from: 'milliliters', to: 'cups', factor: 0.00422675, precision: 2 },
                    { from: 'quarts', to: 'liters', factor: 0.946353, precision: 2 },
                    { from: 'liters', to: 'quarts', factor: 1.05669, precision: 2 }
                ],
                ranges: {
                    [DIFFICULTY_LEVELS.EASY]: { min: 1, max: 20 },
                    [DIFFICULTY_LEVELS.MEDIUM]: { min: 10, max: 100 },
                    [DIFFICULTY_LEVELS.HARD]: { min: 50, max: 500 },
                    [DIFFICULTY_LEVELS.EXPERT]: { min: 100, max: 1000 }
                }
            },
            temperature: {
                conversions: [
                    { 
                        from: 'Fahrenheit', 
                        to: 'Celsius', 
                        convertFn: (f) => (f - 32) * 5/9,
                        precision: 1
                    },
                    { 
                        from: 'Celsius', 
                        to: 'Fahrenheit', 
                        convertFn: (c) => c * 9/5 + 32,
                        precision: 1
                    }
                ],
                ranges: {
                    [DIFFICULTY_LEVELS.EASY]: { min: 0, max: 100 },
                    [DIFFICULTY_LEVELS.MEDIUM]: { min: -20, max: 120 },
                    [DIFFICULTY_LEVELS.HARD]: { min: -50, max: 200 },
                    [DIFFICULTY_LEVELS.EXPERT]: { min: -100, max: 500 }
                }
            }
        };

        // Select conversion type
        const conversionTypeNames = Object.keys(conversionTypes);
        const selectedTypeName = this.randomChoice(conversionTypeNames);
        const selectedType = conversionTypes[selectedTypeName];
        
        // Select specific conversion
        const conversion = this.randomChoice(selectedType.conversions);
        const range = selectedType.ranges[difficulty];
        
        // Generate value1 within appropriate range
        let value1;
        if (selectedTypeName === 'temperature') {
            value1 = this.randomInt(range.min, range.max);
        } else {
            // For non-temperature, use decimals sometimes
            const useDecimals = Math.random() < 0.3; // 30% chance of decimals
            if (useDecimals && difficulty !== DIFFICULTY_LEVELS.EASY) {
                value1 = this.randomFloat(range.min, range.max, 1);
            } else {
                value1 = this.randomInt(range.min, range.max);
            }
        }
        
        // Calculate correct conversion
        let correctValue2;
        if (conversion.convertFn) {
            // Special function (temperature)
            correctValue2 = conversion.convertFn(value1);
        } else {
            // Simple multiplication
            correctValue2 = value1 * conversion.factor;
        }
        
        // Round to appropriate precision
        correctValue2 = this.roundToPrecision(correctValue2, conversion.precision);
        
        // Decide if this should be equal or not equal
        const isEqual = Math.random() < 0.7; // 70% chance of being equal
        
        let value2, comparison;
        if (isEqual) {
            value2 = correctValue2;
            comparison = 'equal';
        } else {
            // Generate incorrect value2 that's clearly different
            let incorrectValue2;
            const errorPercent = this.randomFloat(10, 40, 0); // 10-40% error
            const direction = Math.random() < 0.5 ? 1 : -1; // Higher or lower
            
            incorrectValue2 = correctValue2 * (1 + (direction * errorPercent / 100));
            incorrectValue2 = this.roundToPrecision(incorrectValue2, conversion.precision);
            
            // Ensure it's actually different and not too close
            if (Math.abs(incorrectValue2 - correctValue2) < 0.1) {
                incorrectValue2 = correctValue2 + (direction * Math.max(1, correctValue2 * 0.2));
                incorrectValue2 = this.roundToPrecision(incorrectValue2, conversion.precision);
            }
            
            value2 = incorrectValue2;
            comparison = 'not equal';
        }

        return {
            value1: value1,
            unit1: conversion.from,
            value2: value2,
            unit2: conversion.to,
            comparison: comparison,
            difficulty: difficulty,
            hint: isEqual ? 
                `Convert ${value1} ${conversion.from} to ${conversion.to}` :
                `Check if ${value1} ${conversion.from} equals ${value2} ${conversion.to}`,
            metadata: {
                conversionType: selectedTypeName,
                correctValue: correctValue2,
                isCorrect: isEqual
            }
        };
    }

    

   

    /**
     * Percentages Generator
     */
    generatePercentages(difficulty, criteria) {
        const { total, percentages } = criteria;
        
        // Generate total amount
        const totalAmount = this.randomInt(total.min, total.max);
        
        // Select percentage
        const percentage = this.randomChoice(percentages);
        
        // Calculate answer
        const answer = Math.round((totalAmount * percentage / 100) * 100) / 100;

        return {
            total: totalAmount,
            percentage: percentage,
            answer: answer,
            difficulty: difficulty,
            hint: `What is ${percentage}% of ${totalAmount}?`
        };
    }

    /**
     * Division Generator
     */
    generateDivision(difficulty, criteria) {
        const { dividend, divisor, problemCount } = criteria;
        
        const count = this.randomInt(problemCount.min, problemCount.max);
        const problems = [];

        for (let i = 0; i < count; i++) {
            let div = this.randomInt(divisor.min, divisor.max);
            
            // Generate dividend that divides evenly
            let quotient = this.randomInt(
                Math.ceil(dividend.min / div), 
                Math.floor(dividend.max / div)
            );
            
            let divd = quotient * div;
            
            // Ensure dividend is in range
            if (divd < dividend.min || divd > dividend.max) {
                divd = this.randomInt(dividend.min, dividend.max);
                divd = divd - (divd % div); // Make it divide evenly
                quotient = divd / div;
            }

            problems.push([divd, div, quotient]);
        }

        return {
            problems: problems,
            difficulty: difficulty,
            hint: "Think about multiplication tables to help with division"
        };
    }

    /**
     * Average Generator
     */
    generateAverage(difficulty, criteria) {
        const { numberCount, numberRange } = criteria;
        
        const count = this.randomInt(numberCount.min, numberCount.max);
        
        // Generate target average (whole number)
        const targetAverage = this.randomInt(
            Math.ceil(numberRange.min * 1.2), 
            Math.floor(numberRange.max * 0.8)
        );
        
        // Generate numbers that average to the target
        const numbers = [];
        let sum = 0;
        
        // Generate first n-1 numbers
        for (let i = 0; i < count - 1; i++) {
            const num = this.randomInt(numberRange.min, numberRange.max);
            numbers.push(num);
            sum += num;
        }
        
        // Calculate last number to reach target average
        const lastNumber = (targetAverage * count) - sum;
        
        // Ensure last number is in range, adjust if needed
        if (lastNumber >= numberRange.min && lastNumber <= numberRange.max) {
            numbers.push(lastNumber);
        } else {
            // Regenerate with different approach
            numbers.length = 0;
            sum = 0;
            
            for (let i = 0; i < count; i++) {
                const baseNum = targetAverage + this.randomInt(-10, 10);
                const clampedNum = Math.max(numberRange.min, Math.min(numberRange.max, baseNum));
                numbers.push(clampedNum);
                sum += clampedNum;
            }
            
            // Adjust last number to make average close to whole number
            const currentAverage = sum / count;
            const adjustment = Math.round(currentAverage) * count - sum;
            numbers[numbers.length - 1] += adjustment;
        }

        const finalSum = numbers.reduce((a, b) => a + b, 0);
        const finalAverage = finalSum / count;

        return {
            numbers: numbers,
            average: finalAverage,
            difficulty: difficulty,
            hint: "Add all numbers together and divide by how many numbers there are"
        };
    }

    /**
     * Subtraction Generator
     */
    generateSubtraction(difficulty, criteria) {
        const problemCount = this.randomInt(2, 5); // 2-5 problems
        const problems = [];

        for (let i = 0; i < problemCount; i++) {
            let minuend, subtrahend;
            
            if (difficulty === DIFFICULTY_LEVELS.EASY) {
                minuend = this.randomInt(50, 200);
                subtrahend = this.randomInt(10, minuend - 10);
            } else if (difficulty === DIFFICULTY_LEVELS.MEDIUM) {
                minuend = this.randomInt(200, 500);
                subtrahend = this.randomInt(50, minuend - 50);
            } else if (difficulty === DIFFICULTY_LEVELS.HARD) {
                minuend = this.randomInt(500, 1500);
                subtrahend = this.randomInt(100, minuend - 100);
            } else { // Expert
                minuend = this.randomInt(1500, 5000);
                subtrahend = this.randomInt(500, minuend - 500);
            }

            const difference = minuend - subtrahend;
            problems.push([minuend, subtrahend, difference]);
        }

        return {
            problems: problems,
            difficulty: difficulty,
            hint: "Work from right to left, borrowing when necessary"
        };
    }

    /**
     * Purchasing Generator
     */
    generatePurchasing(difficulty, criteria) {
        const frequencies = ['weekly', 'biweekly', 'monthly', 'quarterly', 'yearly'];
        const frequency = this.randomChoice(frequencies);
        
        let payment;
        if (difficulty === DIFFICULTY_LEVELS.EASY) {
            payment = this.randomFloat(10, 100, 2);
        } else if (difficulty === DIFFICULTY_LEVELS.MEDIUM) {
            payment = this.randomFloat(50, 300, 2);
        } else if (difficulty === DIFFICULTY_LEVELS.HARD) {
            payment = this.randomFloat(200, 800, 2);
        } else { // Expert
            payment = this.randomFloat(500, 2000, 2);
        }

        const frequencyMultipliers = {
            'weekly': 52,
            'biweekly': 26,
            'monthly': 12,
            'quarterly': 4,
            'yearly': 1
        };

        const yearlyTotal = Math.round(payment * frequencyMultipliers[frequency] * 100) / 100;

        return {
            payment: payment,
            frequency: frequency,
            yearlyTotal: yearlyTotal,
            difficulty: difficulty,
            hint: `Multiply the ${frequency} payment by the number of periods in a year`
        };
    }

    /**
     * Discounts Generator
     */
    generateDiscounts(difficulty, criteria) {
        const itemCount = this.randomInt(3, 5);
        const items = [];
        
        const itemNames = [
            'Laptop', 'Phone', 'Tablet', 'Watch', 'Headphones', 
            'Camera', 'Speaker', 'Monitor', 'Keyboard', 'Mouse',
            'Jacket', 'Shoes', 'Backpack', 'Sunglasses', 'Book'
        ];

        for (let i = 0; i < itemCount; i++) {
            const name = this.randomChoice(itemNames);
            let originalPrice, discountPercentage;

            if (difficulty === DIFFICULTY_LEVELS.EASY) {
                originalPrice = this.randomFloat(20, 200, 2);
                discountPercentage = this.randomChoice([10, 15, 20, 25]);
            } else if (difficulty === DIFFICULTY_LEVELS.MEDIUM) {
                originalPrice = this.randomFloat(100, 500, 2);
                discountPercentage = this.randomChoice([15, 20, 25, 30, 35]);
            } else if (difficulty === DIFFICULTY_LEVELS.HARD) {
                originalPrice = this.randomFloat(300, 1000, 2);
                discountPercentage = this.randomChoice([20, 25, 30, 35, 40, 45]);
            } else { // Expert
                originalPrice = this.randomFloat(500, 2000, 2);
                discountPercentage = this.randomChoice([25, 30, 35, 40, 45, 50]);
            }

            const finalPrice = Math.round(originalPrice * (1 - discountPercentage / 100) * 100) / 100;

            items.push({
                name: name,
                originalPrice: originalPrice,
                discountPercentage: discountPercentage,
                finalPrice: finalPrice,
                originalIndex: i + 1
            });
        }

        // Sort by final price to get correct order
        const sortedItems = [...items].sort((a, b) => a.finalPrice - b.finalPrice);
        const correctOrder = sortedItems.map(item => item.originalIndex);

        return {
            items: items,
            correctOrder: correctOrder,
            difficulty: difficulty,
            hint: "Calculate the final price after applying discounts, then order from least to most expensive"
        };
    }

    /**
     * Generate combination key for duplicate detection
     */
    getCombinationKey(puzzleType, puzzle) {
        switch (puzzleType) {
            case PUZZLE_TYPES.MATH_ESTIMATION:
                return puzzle.numbers.sort((a, b) => a - b).join('_');
            
            case PUZZLE_TYPES.MATH_TIPPING:
                return `${Math.round(puzzle.billAmount * 2) / 2}_${puzzle.tipPercentage}`;
            
            case PUZZLE_TYPES.PERCENTAGES:
                return `${puzzle.total}_${puzzle.percentage}`;
            
            case PUZZLE_TYPES.DIVISION:
                return puzzle.problems.map(p => `${p[0]}÷${p[1]}`).sort().join('|');
            
            case PUZZLE_TYPES.AVERAGE:
                return puzzle.numbers.sort((a, b) => a - b).join('_');
            
            case PUZZLE_TYPES.SUBTRACTION:
                return puzzle.problems.map(p => `${p[0]}-${p[1]}`).sort().join('|');
            
            case PUZZLE_TYPES.PURCHASING:
                return `${puzzle.payment}_${puzzle.frequency}`;
            
            case PUZZLE_TYPES.DISCOUNTS:
                return puzzle.items.map(i => `${i.originalPrice}_${i.discountPercentage}`).sort().join('|');
            
            case PUZZLE_TYPES.CONVERSION:
                return `${puzzle.value1}_${puzzle.unit1}_${puzzle.value2}_${puzzle.unit2}_${puzzle.comparison}`;
                            
            default:
                return JSON.stringify(puzzle);
        }
    }

    /**
     * Check for recent duplicates against database
     */
    async isRecentDuplicate(puzzleType, combinationKey) {
        // Check session cache first
        const usedKeys = this.usedCombinations.get(puzzleType);
        if (usedKeys.has(combinationKey)) {
            return true;
        }
        
        // Check recent database entries (last 100 puzzles)
        try {
            const { data: recentPuzzles } = await supabase
                .from('puzzles')
                .select('question')
                .ilike('type', puzzleType)
                .order('timestamp', { ascending: false })
                .limit(100);
            
            if (recentPuzzles) {
                for (const puzzle of recentPuzzles) {
                    try {
                        const data = JSON.parse(puzzle.question);
                        const existingKey = this.getCombinationKey(puzzleType, data);
                        
                        if (existingKey === combinationKey) {
                            return true;
                        }
                    } catch (e) {
                        continue;
                    }
                }
            }
        } catch (error) {
            console.warn('⚠️ Could not check recent duplicates:', error.message);
        }
        
        return false;
    }

    /**
     * Generate batch of puzzles
     */
    async generateBatch(puzzleType, difficulty = 'Medium', count = 5) {
        console.log(`🎯 Generating batch of ${count} ${difficulty} ${puzzleType} puzzles`);
        
        const results = {
            puzzles: [],
            successful: 0,
            failed: 0,
            totalAttempts: 0
        };
        
        for (let i = 0; i < count; i++) {
            const result = await this.generatePuzzle(puzzleType, difficulty);
            results.totalAttempts += result.attempts;
            
            if (result.success) {
                results.puzzles.push(result.puzzle);
                results.successful++;
            } else {
                results.failed++;
                console.error(`❌ Failed to generate puzzle ${i + 1}/${count}`);
            }
            
            // Small delay between generations
            await new Promise(resolve => setTimeout(resolve, 10));
        }
        
        console.log(`🎯 Batch complete: ${results.successful}/${count} puzzles generated (${results.totalAttempts} total attempts)`);
        
        return results;
    }

    /**
     * Clear session cache
     */
    clearCache() {
        Object.values(PUZZLE_TYPES).forEach(type => {
            if (this.isMathPuzzle(type)) {
                this.usedCombinations.get(type).clear();
            }
        });
        console.log('🧹 Cleared all math puzzle generation caches');
    }

    /**
     * Get statistics
     */
    getStats() {
        const stats = {};
        Object.values(PUZZLE_TYPES).forEach(type => {
            if (this.isMathPuzzle(type)) {
                stats[type] = this.usedCombinations.get(type).size;
            }
        });
        
        return {
            cachedCombinations: stats,
            supportedTypes: Object.values(PUZZLE_TYPES).filter(type => this.isMathPuzzle(type)),
            totalCachedCombinations: Object.values(stats).reduce((a, b) => a + b, 0)
        };
    }

    // Utility functions
    randomInt(min, max) {
        return Math.floor(Math.random() * (max - min + 1)) + min;
    }

    randomFloat(min, max, decimals = 2) {
        const value = Math.random() * (max - min) + min;
        return Math.round(value * Math.pow(10, decimals)) / Math.pow(10, decimals);
    }

    randomChoice(array) {
        return array[Math.floor(Math.random() * array.length)];
    }

    roundToPrecision(value, precision) {
        const factor = Math.pow(10, precision);
        return Math.round(value * factor) / factor;
    }
}



export default MathPuzzleGenerators;