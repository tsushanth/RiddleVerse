// Which game is "today's daily puzzle". A port of Android DailyPuzzleSchedule.kt (game and number only, no seeds),
// so the bot names the same game the app shows. The rotation is the ANDROID one (11 games; "never edit after release").
// Pinned against the Kotlin golden vectors in test/telegramDaily.test.js.

export const DAY_MS = 86_400_000;
export const FIRST_DAY = Math.floor(Date.UTC(2026, 10, 1) / DAY_MS); // Daily #1 = 2026-11-01
export const ROTATION = Object.freeze([
    'sudoku', 'binarygrid', 'signgrid', 'pixellogic', 'tubetint', 'conduit', 'crateshift', 'wordladder', 'wordhive', 'crowns', 'trail',
]);

const MASK = (1n << 64n) - 1n;
const u64 = (x) => BigInt.asUintN(64, x);
const GOLDEN = 0x9E3779B97F4A7C15n;
const MIX1 = 0xBF58476D1CE4E5B9n;
const MIX2 = 0x94D049BB133111EBn;
const SHUFFLE_ADD = 0x5851F42D4C957F2Dn;

class SplitMix64 {
    constructor(state) { this.state = u64(BigInt(state)); }
    next() {
        this.state = u64(this.state + GOLDEN);
        let z = this.state;
        z = u64((z ^ (z >> 30n)) * MIX1);
        z = u64((z ^ (z >> 27n)) * MIX2);
        return (z ^ (z >> 31n)) & MASK;
    }
}

export const dayOf = (epochMillis) => Math.floor(epochMillis / DAY_MS);
export const numberOf = (day) => day - FIRST_DAY + 1;
const floorMod = (a, n) => ((a % n) + n) % n;

function shuffled(cycle) {
    const list = [...ROTATION];
    const rng = new SplitMix64(u64(BigInt(cycle) * GOLDEN + SHUFFLE_ADD));
    for (let i = list.length - 1; i >= 0; i--) {
        const j = Number((rng.next() >> 1n) % BigInt(i + 1));
        const t = list[i]; list[i] = list[j]; list[j] = t;
    }
    return list;
}

export function order(cycle) {
    const current = shuffled(cycle);
    if (current[0] === shuffled(cycle - 1)[ROTATION.length - 1]) {
        const t = current[0]; current[0] = current[1]; current[1] = t;
    }
    return current;
}

/** { day, number, type } for a UTC epoch day. */
export function specFor(day) {
    const index = day - FIRST_DAY;
    const cycle = Math.floor(index / ROTATION.length);
    return { day, number: numberOf(day), type: order(cycle)[floorMod(index, ROTATION.length)] };
}

export const todayFor = (nowMillis) => specFor(dayOf(nowMillis));
