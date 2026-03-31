export class BeamSearch5x5CrosswordGenerator {
  constructor() {
    this.GRID_SIZE = 5;
    this.maxTimeMs = 30000;
    this.maxBeamWidth = 3;
    this.startTime = null;
    this.validWordSet = new Set(['FAD', 'BOLO', 'CRIME', 'GALS', 'INS', 'CGI', 'BRAN', 'FOILS', 'ALMS', 'DOE']);
  }

  debugLog(msg) {
    const time = new Date().toISOString().split('T')[1].split('.')[0];
    console.log(`🧠 [${time}] Beam5x5: ${msg}`);
  }

  isTimedOut() {
    return Date.now() - this.startTime > this.maxTimeMs;
  }

  async generate5x5Crossword(wordList) {
    this.startTime = Date.now();
    this.debugLog(`Starting beam search with ${wordList.length} words`);

    const initialBeams = [];

    for (const word of wordList) {
      const placements = this.generateStartPositions(word.word);
      for (const pos of placements) {
        const grid = this.createEmptyGrid();
        if (this.placeWordOnGrid(grid, word.word, pos.x, pos.y, pos.direction)) {
          initialBeams.push({
            grid,
            words: [{
              word: word.word,
              hint: word.hint,
              startX: pos.x,
              startY: pos.y,
              direction: pos.direction,
              length: word.word.length
            }],
            used: new Set([word.word]),
            emptySpaces: this.countEmptySpaces(grid)
          });
        }
      }
    }

    let beams = initialBeams;
    let bestSolution = null;

    while (beams.length > 0 && !this.isTimedOut()) {
      const nextBeams = [];

      for (const beam of beams) {
        const remaining = wordList.filter(w => !beam.used.has(w.word));
        for (const word of remaining) {
          const placements = this.findValidPlacements(beam.grid, word.word);
          for (const pos of placements) {
            const newGrid = this.copyGrid(beam.grid);
            if (this.placeWordOnGrid(newGrid, word.word, pos.x, pos.y, pos.direction)) {
              const newWords = beam.words.concat([{
                word: word.word,
                hint: word.hint,
                startX: pos.x,
                startY: pos.y,
                direction: pos.direction,
                length: word.word.length
              }]);

              const used = new Set(beam.used);
              used.add(word.word);

              const emptySpaces = this.countEmptySpaces(newGrid);
              const candidate = {
                grid: newGrid,
                words: newWords,
                used,
                emptySpaces
              };

              if (!bestSolution || this.calculateScore(candidate) > this.calculateScore(bestSolution)) {
                bestSolution = candidate;
              }

              nextBeams.push(candidate);
            }
          }
        }
      }

      beams = nextBeams
        .sort((a, b) => this.calculateScore(b) - this.calculateScore(a))
        .slice(0, this.maxBeamWidth);
    }

    if (bestSolution) {
      return {
        matrix: bestSolution.grid,
        words: bestSolution.words,
        width: this.GRID_SIZE,
        height: this.GRID_SIZE,
        emptySpaces: bestSolution.emptySpaces
      };
    }

    this.debugLog("No solution found.");
    return null;
  }

  formatForStorage(crossword, difficulty = 'medium') {
    if (!crossword) throw new Error('No crossword to format');
    return {
      question: JSON.stringify({
        matrix: crossword.matrix,
        words: crossword.words,
        width: crossword.width,
        height: crossword.height
      }),
      answer: JSON.stringify(crossword.words.map(w => w.word)),
      hint: `Crossword puzzle with ${crossword.words.length} words`,
      difficulty,
      options: [],
      metadata: {
        wordCount: crossword.words.length,
        gridSize: `${crossword.width}x${crossword.height}`,
        emptySpaces: crossword.emptySpaces
      }
    };
  }

  createEmptyGrid() {
    return Array(this.GRID_SIZE).fill(null).map(() => Array(this.GRID_SIZE).fill('_'));
  }

  generateStartPositions(word) {
    const positions = [];
    const len = word.length;
    for (let y = 0; y < this.GRID_SIZE; y++) {
      for (let x = 0; x <= this.GRID_SIZE - len; x++) {
        positions.push({ x, y, direction: 'horizontal' });
      }
    }
    for (let x = 0; x < this.GRID_SIZE; x++) {
      for (let y = 0; y <= this.GRID_SIZE - len; y++) {
        positions.push({ x, y, direction: 'vertical' });
      }
    }
    return positions;
  }

  findValidPlacements(grid, word) {
    const positions = [];
    const len = word.length;
    for (let y = 0; y < this.GRID_SIZE; y++) {
      for (let x = 0; x <= this.GRID_SIZE - len; x++) {
        if (this.canPlaceWord(grid, word, x, y, 'horizontal')) {
          positions.push({ x, y, direction: 'horizontal' });
        }
      }
    }
    for (let x = 0; x < this.GRID_SIZE; x++) {
      for (let y = 0; y <= this.GRID_SIZE - len; y++) {
        if (this.canPlaceWord(grid, word, x, y, 'vertical')) {
          positions.push({ x, y, direction: 'vertical' });
        }
      }
    }
    return positions;
  }

  placeWordOnGrid(grid, word, x, y, dir) {
    if (!this.canPlaceWord(grid, word, x, y, dir)) return false;
    for (let i = 0; i < word.length; i++) {
      const cx = dir === 'horizontal' ? x + i : x;
      const cy = dir === 'horizontal' ? y : y + i;
      grid[cy][cx] = word[i];
    }
    return true;
  }

  canPlaceWord(grid, word, x, y, dir) {
    let hasIntersection = false;
    for (let i = 0; i < word.length; i++) {
      const cx = dir === 'horizontal' ? x + i : x;
      const cy = dir === 'horizontal' ? y : y + i;
      const cell = grid[cy][cx];
      if (cell === '_') continue;
      if (cell === word[i]) hasIntersection = true;
      else return false;
    }

    if (this.gridHasLetters(grid) && !hasIntersection) return false;
    return true;
  }

  gridHasLetters(grid) {
    for (const row of grid) {
      if (row.some(c => c !== '_')) return true;
    }
    return false;
  }

  countEmptySpaces(grid) {
    return grid.flat().filter(c => c === '_').length;
  }

  copyGrid(grid) {
    return grid.map(row => [...row]);
  }

  calculateScore({ words, emptySpaces }) {
    return words.length * 1000 - emptySpaces * 25;
  }
}

