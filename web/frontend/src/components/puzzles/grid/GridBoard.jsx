import { symbolText } from './engine/factory.js'
import { cageLabel } from './engine/cagemath.js'

const OP_WORD = { '+': 'sum', '-': 'difference', '*': 'product', '/': 'quotient', '=': 'value' }

function describeCell(spec, state, i, isSel) {
  const n = spec.n
  const r = Math.floor(i / n)
  const c = i % n
  const v = state.values[i]
  const given = state.isGiven(i)
  const wrong = state.isWrong(i)
  let s = `Row ${r + 1}, column ${c + 1}, `
  if (v !== 0) s += `${symbolText(spec, v)}${given ? ', given' : ''}${wrong ? ', incorrect' : ''}`
  else if (state.notes[i] !== 0) {
    const ds = []
    for (let d = 1; d <= spec.n; d++) if (state.notes[i] & (1 << d)) ds.push(d)
    s += `empty, notes ${ds.join(' ')}`
  } else s += 'empty'
  if (spec.game === 'cagemath') {
    const cg = spec.cages[spec.cageOf[i]]
    s += `, cage ${cg.target} ${OP_WORD[cg.op]} of ${cg.cells.length} cells`
  }
  if (spec.game === 'signgrid') {
    const h = spec.hRel
    const vr = spec.vRel
    if (c < n - 1 && h[r * (n - 1) + c]) s += h[r * (n - 1) + c] === 1 ? ', less than the cell to the right' : ', greater than the cell to the right'
    if (c > 0 && h[r * (n - 1) + c - 1]) s += h[r * (n - 1) + c - 1] === 1 ? ', greater than the cell to the left' : ', less than the cell to the left'
    if (r < n - 1 && vr[r * n + c]) s += vr[r * n + c] === 1 ? ', less than the cell below' : ', greater than the cell below'
    if (r > 0 && vr[(r - 1) * n + c]) s += vr[(r - 1) * n + c] === 1 ? ', greater than the cell above' : ', less than the cell above'
  }
  if (isSel) s += ', selected'
  return s
}

const THICK = '2.5px'
const THIN = '1px'

function sideWidths(spec, i) {
  const n = spec.n
  const r = Math.floor(i / n)
  const c = i % n
  if (spec.game === 'sudoku') {
    const br = spec.boxRows
    const bc = spec.boxCols
    return {
      top: r % br === 0 ? THICK : THIN,
      left: c % bc === 0 ? THICK : THIN,
      bottom: r === n - 1 ? THICK : (r + 1) % br === 0 ? THICK : THIN,
      right: c === n - 1 ? THICK : (c + 1) % bc === 0 ? THICK : THIN,
    }
  }
  if (spec.game === 'cagemath') {
    const co = spec.cageOf
    const me = co[i]
    return {
      top: r === 0 || co[i - n] !== me ? THICK : THIN,
      bottom: r === n - 1 || co[i + n] !== me ? THICK : THIN,
      left: c === 0 || co[i - 1] !== me ? THICK : THIN,
      right: c === n - 1 || co[i + 1] !== me ? THICK : THIN,
    }
  }
  return null
}

function peers(spec, a, b) {
  const n = spec.n
  const ra = Math.floor(a / n), ca = a % n, rb = Math.floor(b / n), cb = b % n
  if (ra === rb || ca === cb) return true
  if (spec.game === 'sudoku') {
    return Math.floor(ra / spec.boxRows) === Math.floor(rb / spec.boxRows) && Math.floor(ca / spec.boxCols) === Math.floor(cb / spec.boxCols)
  }
  return false
}

function Notes({ mask, n }) {
  const cols = n <= 4 ? 2 : 3
  return (
    <span
      aria-hidden="true"
      className="absolute inset-[6%] grid text-white/70 leading-none"
      style={{
        gridTemplateColumns: `repeat(${cols}, 1fr)`,
        fontSize: `max(7px, calc(100cqw / ${n} * 0.2))`,
      }}
    >
      {Array.from({ length: n }, (_, k) => k + 1).map((d) => (
        <span key={d} className="flex items-center justify-center">
          {mask & (1 << d) ? d : ''}
        </span>
      ))}
    </span>
  )
}

/**
 * Renders the board for all four games. `state` is a mutable GridPlayState; `rev` forces re-render.
 */
export default function GridBoard({ spec, state, rev, onCell, cellRefs, revealed }) {
  const n = spec.n
  const isSign = spec.game === 'signgrid'
  const isBinary = spec.game === 'binarygrid'
  const sel = state.selected
  const selVal = sel >= 0 ? state.values[sel] : 0
  const gapCalc = isSign ? `calc(100cqw / ${n} * 0.3)` : isBinary ? '3px' : '0px'
  const labelFor = (i) => {
    if (spec.game !== 'cagemath') return null
    const cg = spec.cages[spec.cageOf[i]]
    return cg.cells[0] === i ? cageLabel(cg) : null
  }

  return (
    <div
      role="group"
      aria-label={`${spec.game} board, ${n} by ${n}`}
      data-rev={rev}
      className="w-full mx-auto select-none"
      style={{ containerType: 'inline-size', maxWidth: 460 }}
    >
      <div
        className={`grid w-full ${spec.game === 'sudoku' || spec.game === 'cagemath' ? 'rounded-md overflow-hidden' : ''}`}
        style={{
          gridTemplateColumns: `repeat(${n}, minmax(0, 1fr))`,
          gap: gapCalc,
          '--gap': gapCalc,
          ...(spec.game === 'sudoku' || spec.game === 'cagemath' ? { background: 'rgba(255,255,255,0.08)' } : {}),
        }}
      >
        {Array.from({ length: n * n }, (_, i) => {
          const v = state.values[i]
          const given = state.isGiven(i)
          const wrong = state.isWrong(i)
          const isSel = i === sel
          const sameVal = v !== 0 && v === selVal && !isSel
          const peer = sel >= 0 && !isSel && peers(spec, sel, i)
          const sw = sideWidths(spec, i)
          const r = Math.floor(i / n)
          const c = i % n
          const label = labelFor(i)
          const hRel = isSign && c < n - 1 ? spec.hRel[r * (n - 1) + c] : 0
          const vRel = isSign && r < n - 1 ? spec.vRel[r * n + c] : 0
          let bg = 'bg-white/10'
          if (given) bg = 'bg-white/25'
          if (peer) bg = given ? 'bg-white/35' : 'bg-white/20'
          if (sameVal) bg = 'bg-sky-300/35'
          if (isSel) bg = 'bg-amber-200/50'
          if (wrong) bg = 'bg-red-500/60'
          const color = wrong ? 'text-white' : given ? 'text-white font-extrabold' : revealed && !given ? 'text-emerald-200 font-bold' : 'text-yellow-100 font-bold'
          return (
            <button
              key={i}
              type="button"
              ref={(el) => {
                if (cellRefs) cellRefs.current[i] = el
              }}
              tabIndex={i === (sel >= 0 ? sel : 0) ? 0 : -1}
              aria-label={describeCell(spec, state, i, isSel)}
              aria-pressed={isSel}
              onClick={() => onCell(i)}
              className={`relative aspect-square flex items-center justify-center p-0 ${bg} ${color} ${
                isBinary || isSign ? 'rounded-md' : ''
              } border-solid border-white/60 focus:outline-none focus-visible:ring-4 focus-visible:ring-white focus-visible:z-20 ${
                isSel ? 'ring-2 ring-yellow-200 z-10' : ''
              }`}
              style={{
                fontSize: `calc(100cqw / ${n} * 0.52)`,
                minWidth: 0,
                ...(sw
                  ? {
                      borderTopWidth: sw.top,
                      borderBottomWidth: sw.bottom,
                      borderLeftWidth: sw.left,
                      borderRightWidth: sw.right,
                      borderStyle: 'solid',
                      borderTopColor: sw.top === THICK ? 'rgba(255,255,255,0.85)' : 'rgba(255,255,255,0.15)',
                      borderBottomColor: sw.bottom === THICK ? 'rgba(255,255,255,0.85)' : 'rgba(255,255,255,0.15)',
                      borderLeftColor: sw.left === THICK ? 'rgba(255,255,255,0.85)' : 'rgba(255,255,255,0.15)',
                      borderRightColor: sw.right === THICK ? 'rgba(255,255,255,0.85)' : 'rgba(255,255,255,0.15)',
                    }
                  : { border: '1px solid rgba(255,255,255,0.25)' }),
              }}
            >
              {label && (
                <span
                  aria-hidden="true"
                  className="absolute top-[3%] left-[6%] text-white/90 font-semibold leading-none"
                  style={{ fontSize: `max(9px, calc(100cqw / ${n} * 0.2))` }}
                >
                  {label}
                </span>
              )}
              {v !== 0 ? (
                <span aria-hidden="true" className="leading-none">
                  {symbolText(spec, v)}
                </span>
              ) : state.notes[i] ? (
                <Notes mask={state.notes[i]} n={n} />
              ) : null}
              {wrong && (
                <span aria-hidden="true" className="absolute top-0 right-[6%] text-white font-black leading-none" style={{ fontSize: `max(9px, calc(100cqw / ${n} * 0.22))` }}>
                  {'✕'}
                </span>
              )}
              {hRel !== 0 && (
                <span
                  aria-hidden="true"
                  className="absolute z-10 flex items-center justify-center text-white font-black leading-none pointer-events-none"
                  style={{ left: '100%', top: '50%', width: 'var(--gap)', transform: 'translateY(-50%)', fontSize: `max(12px, calc(100cqw / ${n} * 0.28))` }}
                >
                  {hRel === 1 ? '<' : '>'}
                </span>
              )}
              {vRel !== 0 && (
                <span
                  aria-hidden="true"
                  className="absolute z-10 flex items-center justify-center text-white font-black leading-none pointer-events-none"
                  style={{ top: '100%', left: '50%', height: 'var(--gap)', transform: 'translateX(-50%)', fontSize: `max(12px, calc(100cqw / ${n} * 0.28))` }}
                >
                  {vRel === 1 ? '∧' : '∨'}
                </span>
              )}
            </button>
          )
        })}
      </div>
    </div>
  )
}
