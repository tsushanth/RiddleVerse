import { useEffect, useMemo, useState, useCallback } from 'react'
import { Link, Navigate, useParams } from 'react-router-dom'
import { RefreshCw } from 'lucide-react'
import PlayShell, { PlayStoreButton } from '../components/play/PlayShell'
import PuzzleRenderer from '../components/puzzles/PuzzleRenderer'
import { PLAY_GAMES, getPlayGame, playGameDescription, playGameTitle } from '../utils/playCatalog'
import { applyPageMeta } from '../utils/pageMeta'

const LEVELS = [
  { level: 1, label: 'Easy' },
  { level: 2, label: 'Medium' },
  { level: 3, label: 'Hard' },
]

// Public, no-login page. The game is the same web port the signed-in app uses; it runs entirely in the
// browser from a local seed. Nothing here calls the backend or needs a token.
export default function PlayGamePage() {
  const { type } = useParams()
  const game = getPlayGame(type)
  if (!game) return <Navigate to="/play" replace />
  return <PlayGame key={game.slug} game={game} />
}

function PlayGame({ game }) {
  const [difficulty, setDifficulty] = useState(1)
  const [round, setRound] = useState(0)
  const [finishedOnce, setFinishedOnce] = useState(false)

  useEffect(() => {
    applyPageMeta({ title: playGameTitle(game), description: playGameDescription(game), path: `/play/${game.slug}` })
  }, [game])

  // A fresh puzzleId per round gives every game a fresh seed.
  const puzzle = useMemo(
    () => ({
      puzzleId: `web-guest-${game.slug}-${round}-${difficulty}-${Math.random().toString(36).slice(2, 10)}`,
      puzzleType: game.slug,
      question: '',
      answer: '',
      hint: '',
      options: [],
      timer: '1:30',
      difficulty,
    }),
    [game.slug, round, difficulty]
  )

  const onAnswer = useCallback(() => setFinishedOnce(true), [])
  // The four grid games ship their own difficulty chips and New puzzle control; do not double them up.
  const selfManaged = game.category === 'grid'
  const others = PLAY_GAMES.filter((g) => g.slug !== game.slug && g.category === game.category)
  const more = others.length ? others : PLAY_GAMES.filter((g) => g.slug !== game.slug).slice(0, 4)

  return (
    <PlayShell>
      <nav aria-label="Breadcrumb" className="text-sm text-white/70">
        <Link to="/play" className="hover:underline">Play</Link> <span aria-hidden="true">/</span> {game.name}
      </nav>

      <h1 className="text-3xl font-extrabold">Play {game.name} online, free</h1>

      {!selfManaged && (
      <div className="glass rounded-2xl p-3 flex items-center gap-2" role="group" aria-label="Difficulty">
        <span className="text-sm text-white/70 pr-1">Difficulty</span>
        {LEVELS.map(({ level, label }) => (
          <button
            key={level}
            type="button"
            aria-pressed={difficulty === level}
            onClick={() => { setDifficulty(level); setFinishedOnce(false) }}
            className={`flex-1 min-h-[44px] rounded-xl text-sm font-semibold transition-colors focus:outline-none focus-visible:ring-4 focus-visible:ring-white ${
              difficulty === level ? 'bg-white text-purple-800' : 'bg-white/10 hover:bg-white/20'
            }`}
          >
            {label}
          </button>
        ))}
      </div>
      )}

      <div className="glass rounded-2xl p-4 sm:p-6">
        <PuzzleRenderer key={puzzle.puzzleId} puzzleType={game.slug} puzzle={puzzle} onAnswer={onAnswer} />
      </div>

      <div className="flex flex-wrap gap-3 items-center">
        {!selfManaged && (
          <button
            type="button"
            onClick={() => { setRound((r) => r + 1); setFinishedOnce(false) }}
            className="inline-flex items-center gap-2 bg-white/15 hover:bg-white/25 px-4 min-h-[44px] rounded-xl font-semibold focus:outline-none focus-visible:ring-4 focus-visible:ring-white"
          >
            <RefreshCw size={18} aria-hidden="true" /> New puzzle
          </button>
        )}
        <PlayStoreButton campaign={game.slug} />
      </div>

      {finishedOnce && (
        <p role="status" className="glass rounded-xl p-4 text-sm">
          Nice. Want more? Start another round, or take {game.name} and the other RiddleVerse puzzles with you on Android.
        </p>
      )}

      <section className="glass rounded-2xl p-5 space-y-3">
        <h2 className="text-xl font-bold">About {game.name}</h2>
        <p className="text-white/90">{game.description}</p>
        <h2 className="text-xl font-bold pt-2">How to play {game.name}</h2>
        <ol className="list-decimal pl-6 space-y-1 text-white/90">
          {game.howToPlay.map((s) => <li key={s}>{s}</li>)}
        </ol>
        <p className="text-white/90"><strong>Tip:</strong> {game.tip}</p>
      </section>

      <section aria-labelledby="more-games" className="space-y-3">
        <h2 id="more-games" className="text-xl font-bold">More free puzzle games</h2>
        <ul className="flex flex-wrap gap-2">
          {more.map((g) => (
            <li key={g.slug}>
              <Link to={`/play/${g.slug}`} className="glass inline-block rounded-xl px-4 py-2 hover:bg-white/25">{g.name}</Link>
            </li>
          ))}
          <li><Link to="/play" className="glass inline-block rounded-xl px-4 py-2 hover:bg-white/25">All games</Link></li>
        </ul>
      </section>
    </PlayShell>
  )
}
