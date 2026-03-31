// flowPuzzleService.js - Flow puzzle generator service (using Simple Flow algorithm)
import { randomUUID, randomBytes } from 'node:crypto';

export class FlowPuzzleGenerator {
    constructor() {
        this.debugMode = false;
        this.sessionPuzzles = new Set();
        this.sessionSignatures = new Set();
        this.puzzleCache = new Map();
        
        // Difficulty configs adapted for Simple Flow approach
        // Pair counts tuned for reliable generation with greedy pathfinding
        // Difficulty comes from grid size and path complexity, not just pair count
        this.difficultyConfigs = {
            easy: {
                basePairs: 4,
                minDist: (size) => Math.max(2, Math.min(4, Math.floor(size / 3))),
                maxPuzzleTries: 150
            },
            medium: {
                basePairs: 5,
                minDist: (size) => Math.max(2, Math.min(3, Math.floor(size / 4))),
                maxPuzzleTries: 300
            },
            hard: {
                basePairs: 5,  // Same pairs as medium, difficulty from larger grid
                minDist: (size) => 2,  // Minimal distance constraint
                maxPuzzleTries: 400
            }
        };
        
        this.colors = [
            '#E53E3E', '#38A169', '#3182CE', '#D69E2E',
            '#805AD5', '#DD6B20', '#319795', '#EC4899',
            '#4A5568', '#2B6CB0', '#9F7AEA', '#C53030',
            '#2F855A', '#2C5282', '#B7791F', '#6B46C1'
        ];
        
        this.labels = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'.split('');
    }

    /**
     * Main generation method - creates Flow puzzles
     */
    async generateFlowPuzzle(size = 8, difficulty = 'medium', count = 1, seed = null) {
        this.log(`Generating ${count} Flow puzzle(s) of size ${size}x${size} with ${difficulty} difficulty`);
        
        try {
            const puzzles = [];
            
            for (let i = 0; i < count; i++) {
                const puzzleSeed = seed ? `${seed}_${i}` : this.generateSeed();
                const rng = this.makeRng(puzzleSeed);
                
                const puzzle = this.generateSinglePuzzle(size, difficulty, rng, puzzleSeed);
                
                if (puzzle.success) {
                    puzzles.push(puzzle.puzzle);
                    this.sessionPuzzles.add(puzzle.puzzle.id);
                } else {
                    this.log(`Failed to generate puzzle ${i + 1}: ${puzzle.error}`, 'warn');
                }
            }
            
            this.log(`Successfully generated ${puzzles.length} puzzles`);
            
            return {
                success: true,
                puzzles: puzzles,
                stats: {
                    requested: count,
                    generated: puzzles.length,
                    failed: count - puzzles.length,
                    difficulty: difficulty,
                    gridSize: size
                }
            };
            
        } catch (error) {
            this.log(`Flow puzzle generation failed: ${error.message}`, 'error');
            return {
                success: false,
                error: error.message,
                puzzles: []
            };
        }
    }

    /**
     * Generate a single Flow puzzle using Simple Flow algorithm
     */
    generateSinglePuzzle(size, difficulty, rng, seed) {
        try {
            const config = this.getConfigForDifficulty(size, difficulty);
            const result = this.generateSimpleFlow(
                size, 
                config.pairCount, 
                config.minDist, 
                config.maxPuzzleTries,
                rng
            );
            
            if (!result) {
                return { 
                    success: false, 
                    error: `Failed to generate puzzle after ${config.maxPuzzleTries} attempts` 
                };
            }

            // Check for duplicate signatures
            const sig = this.signatureFromPairs(result.pairs);
            if (this.sessionSignatures.has(sig)) {
                this.log(`Duplicate signature detected, regenerating...`, 'warn');
                // Try one more time with perturbed seed
                const newSeed = `${seed}_retry_${Date.now()}`;
                const newRng = this.makeRng(newSeed);
                const retryResult = this.generateSimpleFlow(
                    size, 
                    config.pairCount, 
                    config.minDist, 
                    config.maxPuzzleTries,
                    newRng
                );
                if (retryResult) {
                    const retrySig = this.signatureFromPairs(retryResult.pairs);
                    if (!this.sessionSignatures.has(retrySig)) {
                        this.sessionSignatures.add(retrySig);
                        return this.buildPuzzleFromResult(retryResult, size, difficulty, newSeed);
                    }
                }
            }

            this.sessionSignatures.add(sig);
            return this.buildPuzzleFromResult(result, size, difficulty, seed);
            
        } catch (error) {
            return { success: false, error: error.message };
        }
    }

    /**
     * Build puzzle object from Simple Flow result
     */
    buildPuzzleFromResult(result, size, difficulty, seed) {
        // Convert Simple Flow format to service format
        const pairs = result.pairs.map((pair, index) => ({
            id: `pair_${index}`,
            color: this.colors[index % this.colors.length],
            label: pair.label,
            start: pair.start,
            end: pair.end,
            length: pair.path.length
        }));

        const solution = {};
        result.pairs.forEach((pair, index) => {
            solution[`pair_${index}`] = pair.path;
        });

        const puzzle = {
            id: `flow_${seed}`,
            gridSize: size,
            difficulty,
            seed,
            cells: size * size,
            pairs,
            solution,
            metadata: {
                pathLength: result.pairs.reduce((sum, p) => sum + p.path.length, 0),
                totalBends: result.pairs.reduce((sum, p) => sum + this.bendsCount(p.path), 0),
                segmentCount: result.pairs.length,
                minDist: result.minDist,
                attempt: result.attempt,
                generatedAt: new Date().toISOString(),
                algorithm: 'simple_flow_farthest_first',
                pathMethod: 'greedy_best_first_with_separation'
            }
        };

        return { success: true, puzzle };
    }

    /**
     * Get configuration for difficulty level
     */
    getConfigForDifficulty(size, difficulty) {
        const base = this.difficultyConfigs[difficulty] ?? this.difficultyConfigs.medium;
        const maxPairs = Math.min(26, Math.floor((size * size) / 4));
        const pairCount = Math.min(base.basePairs, maxPairs);
        const minDist = typeof base.minDist === 'function' ? base.minDist(size) : base.minDist;
        
        return { 
            pairCount, 
            minDist,
            maxPuzzleTries: base.maxPuzzleTries 
        };
    }

    /**
     * Create signature from pairs for duplicate detection
     */
    signatureFromPairs(pairs) {
        const canon = pairs.map(pair => {
            const s = pair.start;
            const e = pair.end;
            const s1 = `${s.x},${s.y}`;
            const s2 = `${e.x},${e.y}`;
            return s1 < s2 ? `${s1}|${s2}` : `${s2}|${s1}`;
        }).sort().join('||');
        return canon;
    }

    // ==================== SIMPLE FLOW ALGORITHM ====================

    /**
     * Main Simple Flow generation function
     */
    generateSimpleFlow(size, pairCount, minDist, maxPuzzleTries = 250, rng) {
      for (let attempt = 1; attempt <= maxPuzzleTries; attempt++) {
          const endpoints = this.pickSeparatedEndpoints(size, pairCount, minDist, rng);
          if (!endpoints) continue;

          const pairs = this.pairByFarthestFirst(endpoints);
          const occupied = new Set(endpoints.map(p => this.key(p)));

          // Shuffle pairs to vary solving order
          this.shuffleInPlace(pairs, rng);
          const paths = [];
          let ok = true;

          for (const [a, b] of pairs) {
              // Temporarily free endpoints for this pair
              occupied.delete(this.key(a));
              occupied.delete(this.key(b));
              
              const path = this.findPathGreedy(a, b, size, occupied, rng);
              
              if (!path) {
                  ok = false;
                  break;
              }

              // VALIDATION: Check if path overlaps with already occupied cells
              let hasOverlap = false;
              for (const p of path) {
                  if (occupied.has(this.key(p))) {
                      hasOverlap = true;
                      break;
                  }
              }
              
              // If overlap detected, reject this entire puzzle attempt
              if (hasOverlap) {
                  ok = false;
                  break;
              }

              // Mark path as occupied (including endpoints)
              for (const p of path) {
                  occupied.add(this.key(p));
              }
              paths.push(path);
          }

          if (!ok) continue;

          // SUCCESS: All pairs have valid non-overlapping paths
          // Build labeled result
          const labeled = [];
          for (let i = 0; i < paths.length; i++) {
              const L = this.labels[i % 26];
              const s = paths[i][0];
              const e = paths[i][paths[i].length - 1];
              labeled.push({ 
                  label: L, 
                  start: s, 
                  end: e, 
                  path: paths[i] 
              });
          }

          return { 
              size, 
              pairs: labeled, 
              attempt, 
              minDist 
          };
      }
      return null;
    }

    /**
     * Pick separated endpoints using poisson-disk-like approach
     * With randomized pool selection for variety
     */
    pickSeparatedEndpoints(n, pairCount, minDist, rng, maxTries = 300) {
        const all = [];
        for (let y = 0; y < n; y++) {
            for (let x = 0; x < n; x++) {
                all.push({ x, y });
            }
        }

        // Separate edge and interior pools
        const edges = all.filter(p => p.x === 0 || p.y === 0 || p.x === n - 1 || p.y === n - 1);
        const interior = all.filter(p => !(p.x === 0 || p.y === 0 || p.x === n - 1 || p.y === n - 1));

        let currentMinDist = minDist;

        for (let attempt = 1; attempt <= maxTries; attempt++) {
            const endpoints = [];
            const needed = pairCount * 2;

            // Randomize starting pool each attempt for variety
            const startWithEdge = rng() < 0.5;

            // Shuffle both pools fresh each attempt
            const edgePool = this.shuffleArray(edges.slice(), rng);
            const interiorPool = this.shuffleArray(interior.slice(), rng);

            while (endpoints.length < needed) {
                // Alternate pools but with randomized starting pool
                const useEdge = startWithEdge ? (endpoints.length % 2 === 0) : (endpoints.length % 2 === 1);
                const primaryPool = useEdge ? edgePool : interiorPool;
                const fallbackPool = useEdge ? interiorPool : edgePool;

                let placed = false;

                // Try primary pool first
                for (const c of primaryPool) {
                    if (endpoints.every(e => this.manhattan(e, c) >= currentMinDist)) {
                        endpoints.push(c);
                        placed = true;
                        break;
                    }
                }

                // If primary pool failed, try fallback pool
                if (!placed) {
                    for (const c of fallbackPool) {
                        if (endpoints.every(e => this.manhattan(e, c) >= currentMinDist)) {
                            endpoints.push(c);
                            placed = true;
                            break;
                        }
                    }
                }

                if (!placed) break;
            }

            if (endpoints.length === needed) return endpoints;

            // Relax minDist slightly every ~50 attempts
            if (attempt % 50 === 0) {
                currentMinDist = Math.max(2, currentMinDist - 1);
            }
        }
        return null;
    }

    /**
     * Pair endpoints by farthest-first heuristic
     */
    pairByFarthestFirst(points) {
        const pts = points.slice();
        const pairs = [];
        
        while (pts.length >= 2) {
            // Find farthest two
            let bi = 0, bj = 1, bd = -1;
            for (let i = 0; i < pts.length; i++) {
                for (let j = i + 1; j < pts.length; j++) {
                    const d = this.manhattan(pts[i], pts[j]);
                    if (d > bd) {
                        bd = d;
                        bi = i;
                        bj = j;
                    }
                }
            }
            
            const hi = Math.max(bi, bj);
            const lo = Math.min(bi, bj);
            const b = pts.splice(hi, 1)[0];
            const a = pts.splice(lo, 1)[0];
            pairs.push([a, b]);
        }
        
        return pairs;
    }

    /**
     * Greedy best-first pathfinding
     */
    findPathGreedy(start, goal, size, occupied, rng) {
        const open = [];
        const gScore = new Map();
        const came = new Map();
        const closed = new Set();

        const startKey = this.key(start);
        gScore.set(startKey, 0);
        open.push({ 
            p: start, 
            g: 0, 
            f: this.manhattan(start, goal), 
            pk: null 
        });

        const dirs = this.shuffleArray([
            { dx: 1, dy: 0 }, 
            { dx: -1, dy: 0 }, 
            { dx: 0, dy: 1 }, 
            { dx: 0, dy: -1 }
        ], rng);

        const popBest = () => {
            let bi = 0;
            for (let i = 1; i < open.length; i++) {
                if (open[i].f < open[bi].f || 
                    (open[i].f === open[bi].f && rng() < 0.5)) {
                    bi = i;
                }
            }
            return open.splice(bi, 1)[0];
        };

        while (open.length) {
            const cur = popBest();
            const ck = this.key(cur.p);
            
            if (closed.has(ck)) continue;
            closed.add(ck);

            if (cur.p.x === goal.x && cur.p.y === goal.y) {
                // Reconstruct path
                const path = [];
                let k = ck;
                let node = cur;
                
                while (node) {
                    path.push(node.p);
                    const pk = node.pk;
                    node = pk ? { 
                        p: this.parseKey(pk), 
                        pk: came.get(pk) 
                    } : null;
                }
                
                return path.reverse();
            }

            for (const d of dirs) {
                const np = { 
                    x: cur.p.x + d.dx, 
                    y: cur.p.y + d.dy 
                };
                
                if (!this.inBounds(np, size)) continue;
                
                const nk = this.key(np);
                if (occupied.has(nk) && nk !== this.key(goal)) continue;
                if (closed.has(nk)) continue;

                const ng = cur.g + 1;
                if (!gScore.has(nk) || ng < gScore.get(nk)) {
                    gScore.set(nk, ng);
                    came.set(nk, ck);
                    const f = ng + this.manhattan(np, goal) + rng() * 0.1;
                    open.push({ p: np, g: ng, f, pk: ck });
                }
            }
        }
        
        return null;
    }

    // ==================== UTILITIES ====================

    key(p) {
        return `${p.x},${p.y}`;
    }

    parseKey(s) {
        const [x, y] = s.split(',').map(n => parseInt(n, 10));
        return { x, y };
    }

    manhattan(a, b) {
        return Math.abs(a.x - b.x) + Math.abs(a.y - b.y);
    }

    inBounds(p, n) {
        return p.x >= 0 && p.y >= 0 && p.x < n && p.y < n;
    }

    /**
     * Get direction between two points
     */
    getDirection(pointA, pointB) {
        if (pointB.x < pointA.x) return 0; // Left
        if (pointB.x > pointA.x) return 1; // Right
        if (pointB.y < pointA.y) return 2; // Up
        return 3; // Down
    }

    /**
     * Count bends in a path sequence
     */
    bendsCount(sequence) {
        if (sequence.length < 3) return 0;
        
        let bends = 0;
        let previousDirection = this.getDirection(sequence[0], sequence[1]);
        
        for (let i = 1; i < sequence.length - 1; i++) {
            const currentDirection = this.getDirection(sequence[i], sequence[i + 1]);
            if (currentDirection !== previousDirection) {
                bends++;
                previousDirection = currentDirection;
            }
        }
        
        return bends;
    }

    /**
     * Shuffle array (returns new array)
     */
    shuffleArray(array, rng) {
        const arr = array.slice();
        for (let i = arr.length - 1; i > 0; i--) {
            const j = Math.floor(rng() * (i + 1));
            [arr[i], arr[j]] = [arr[j], arr[i]];
        }
        return arr;
    }

    /**
     * Shuffle array in place
     */
    shuffleInPlace(array, rng) {
        for (let i = array.length - 1; i > 0; i--) {
            const j = Math.floor(rng() * (i + 1));
            [array[i], array[j]] = [array[j], array[i]];
        }
    }

    // ==================== RNG ====================

    /**
     * Generate random seed
     */
    generateSeed() {
        this.__seedCounter = (this.__seedCounter ?? 0) + 1;
        const pid = typeof process !== 'undefined' && process.pid ? process.pid : 0;
        return `flow_${Date.now()}_${pid}_${this.__seedCounter}_${randomUUID?.() ?? ''}_${(typeof randomBytes === 'function' ? randomBytes(6).toString('hex') : '')}`;
    }

    /**
     * Create seeded RNG from string
     */
    makeRng(seedString) {
        if (!seedString) return Math.random;
        const seed = Math.floor(this.xfnv1a(seedString)() * 0xffffffff);
        const r = this.mulberry32(seed);
        const bump = 11 + (seed & 7);
        for (let i = 0; i < bump; i++) r();
        return r;
    }

    /**
     * FNV-1a hash function
     */
    xfnv1a(str) {
        let hash = 2166136261 >>> 0;
        for (let i = 0; i < str.length; i++) {
            hash ^= str.charCodeAt(i);
            hash = Math.imul(hash, 16777619);
        }
        return () => {
            hash += hash << 13;
            hash ^= hash >>> 7;
            hash += hash << 3;
            hash ^= hash >>> 17;
            hash += hash << 5;
            return (hash >>> 0) / 4294967296;
        };
    }

    /**
     * Mulberry32 PRNG
     */
    mulberry32(seed) {
        let state = seed >>> 0;
        return () => {
            state += 0x6D2B79F5;
            let result = Math.imul(state ^ (state >>> 15), 1 | state);
            result ^= result + Math.imul(result ^ (result >>> 7), 61 | result);
            return ((result ^ (result >>> 14)) >>> 0) / 4294967296;
        };
    }

    // ==================== LOGGING ====================

    log(message, type = 'info') {
        if (!this.debugMode && type === 'info') return;
        const timestamp = new Date().toISOString().split('T')[1].split('.')[0];
        const prefix = type === 'error' ? '❌' : type === 'warn' ? '⚠️' : type === 'success' ? '✅' : 'ℹ️';
        console.log(`[${timestamp}] ${prefix} FlowPuzzle: ${message}`);
    }

    setDebugMode(enabled) {
        this.debugMode = enabled;
    }

    // ==================== ANALYSIS METHODS ====================

    /**
     * Validate a puzzle solution
     */
    validateSolution(puzzle, solution) {
        const errors = [];
        const gridSize = puzzle.gridSize;
        const occupied = new Map();

        // Check each pair's solution
        for (const pair of puzzle.pairs) {
            const path = solution[pair.id];
            if (!path || !Array.isArray(path)) {
                errors.push(`Missing or invalid path for ${pair.label}`);
                continue;
            }

            // Verify path connects endpoints
            const start = path[0];
            const end = path[path.length - 1];
            
            if (start.x !== pair.start.x || start.y !== pair.start.y) {
                errors.push(`${pair.label} path doesn't start at correct position`);
            }
            if (end.x !== pair.end.x || end.y !== pair.end.y) {
                errors.push(`${pair.label} path doesn't end at correct position`);
            }

            // Verify path is continuous
            for (let i = 1; i < path.length; i++) {
                if (this.manhattan(path[i - 1], path[i]) !== 1) {
                    errors.push(`${pair.label} path is not continuous`);
                    break;
                }
            }

            // Check for overlaps
            for (const cell of path) {
                const key = this.key(cell);
                if (occupied.has(key) && occupied.get(key) !== pair.id) {
                    errors.push(`Overlap at (${cell.x},${cell.y}) between ${pair.label} and ${occupied.get(key)}`);
                }
                occupied.set(key, pair.id);
            }
        }

        // Verify full coverage
        if (occupied.size !== gridSize * gridSize) {
            errors.push(`Incomplete coverage: ${occupied.size}/${gridSize * gridSize} cells filled`);
        }

        return { 
            valid: errors.length === 0, 
            errors 
        };
    }

    /**
     * Get puzzle difficulty statistics
     */
    analyzePuzzle(puzzle) {
        const totalPathCells = Object.values(puzzle.solution)
            .reduce((acc, seg) => acc + seg.length, 0);

        const stats = {
            gridSize: puzzle.gridSize,
            pairCount: puzzle.pairs.length,
            totalCells: puzzle.cells,
            coverage: totalPathCells / puzzle.cells,
            avgPairDistance: 0,
            maxBends: 0,
            avgBends: 0,
            minPathLength: Infinity,
            maxPathLength: 0,
            avgPathLength: 0
        };

        let totalDistance = 0;
        let totalBends = 0;
        let totalLength = 0;

        for (const pair of puzzle.pairs) {
            const d = this.manhattan(pair.start, pair.end);
            totalDistance += d;
            
            const seg = puzzle.solution[pair.id];
            if (seg) {
                const bends = this.bendsCount(seg);
                totalBends += bends;
                stats.maxBends = Math.max(stats.maxBends, bends);
                
                const len = seg.length;
                totalLength += len;
                stats.minPathLength = Math.min(stats.minPathLength, len);
                stats.maxPathLength = Math.max(stats.maxPathLength, len);
            }
        }

        stats.avgPairDistance = totalDistance / Math.max(1, puzzle.pairs.length);
        stats.avgBends = totalBends / Math.max(1, puzzle.pairs.length);
        stats.avgPathLength = totalLength / Math.max(1, puzzle.pairs.length);

        return stats;
    }
}