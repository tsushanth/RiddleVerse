import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, join } from 'node:path'
import { buildLists } from '../../src/components/puzzles/word/lib/wordLists.js'

const dir = join(dirname(fileURLToPath(import.meta.url)), '../../public/word_puzzles')
const read = (n) => readFileSync(join(dir, n), 'utf8').split(/\r?\n/)
export const lists = buildLists(read('common_words.txt'), read('valid5.txt'))
