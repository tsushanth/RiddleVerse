import { useState, useEffect, useRef } from 'react'
import { motion } from 'framer-motion'
import PuzzleTimer from './PuzzleTimer'
import { parseJsonField } from './puzzleUtils'

export default function CryptowordPuzzle({ puzzle, onAnswer }) {
  const parsed = parseJsonField(puzzle.question)

  // The encrypted text has numbers representing letters
  // e.g., "8-5-12-12-15" => "HELLO"
  const encryptedText = parsed?.encrypted || parsed?.cipher || parsed?.text || puzzle.question || ''
  const mapping = parsed?.mapping || parsed?.key || {} // partial hints { "1": "A", ... }

  const answerParsed = parseJsonField(puzzle.answer)
  const correctMapping = answerParsed?.mapping || answerParsed || {}

  // Extract all unique numbers from the text
  const numbers = [...new Set(
    encryptedText.match(/\d+/g)?.map(Number) || []
  )].sort((a, b) => a - b)

  const [userMap, setUserMap] = useState(() => {
    const initial = {}
    // Pre-fill any provided hints
    Object.entries(mapping).forEach(([num, letter]) => {
      initial[num] = letter.toUpperCase()
    })
    return initial
  })
  const [activeNum, setActiveNum] = useState(null)
  const [solved, setSolved] = useState(false)
  const inputRefs = useRef({})

  const handleLetterChange = (num, value) => {
    if (solved) return
    const letter = value.toUpperCase().replace(/[^A-Z]/g, '').slice(-1)
    setUserMap((prev) => ({ ...prev, [num]: letter }))
  }

  const checkSolution = () => {
    if (typeof correctMapping === 'string') {
      // Answer is the decoded text
      const decoded = decodeText()
      const isCorrect = decoded.toLowerCase().replace(/[^a-z]/g, '') ===
        correctMapping.toLowerCase().replace(/[^a-z]/g, '')
      if (isCorrect) {
        setSolved(true)
        setTimeout(() => onAnswer(true, decoded), 800)
      } else {
        onAnswer(false, decoded)
      }
      return
    }

    // Check mapping
    let correct = true
    for (const num of numbers) {
      const expected = (correctMapping[num] || correctMapping[String(num)] || '').toUpperCase()
      const user = (userMap[num] || userMap[String(num)] || '').toUpperCase()
      if (expected && user !== expected) {
        correct = false
        break
      }
    }
    if (correct) {
      setSolved(true)
      setTimeout(() => onAnswer(true, 'solved'), 800)
    } else {
      onAnswer(false, 'incorrect')
    }
  }

  const decodeText = () => {
    return encryptedText.replace(/\d+/g, (match) => {
      return userMap[match] || userMap[parseInt(match)] || `[${match}]`
    })
  }

  const handleTimeUp = () => {
    if (!solved) onAnswer(false, 'timeout')
  }

  // Render encrypted text with interactive number cells
  const renderCipherText = () => {
    const tokens = encryptedText.split(/(\d+|[^0-9]+)/g).filter(Boolean)
    return (
      <div className="flex flex-wrap gap-1 items-end justify-center">
        {tokens.map((token, i) => {
          if (/^\d+$/.test(token)) {
            const num = parseInt(token)
            const letter = userMap[num] || userMap[String(num)] || ''
            const isHint = mapping[num] || mapping[String(num)]
            return (
              <div key={i} className="flex flex-col items-center">
                <input
                  ref={(el) => (inputRefs.current[num] = el)}
                  type="text"
                  maxLength={1}
                  value={letter}
                  onChange={(e) => handleLetterChange(num, e.target.value)}
                  onFocus={() => setActiveNum(num)}
                  disabled={solved || !!isHint}
                  className={`w-8 h-8 text-center font-bold text-sm rounded border transition-colors focus:outline-none ${
                    isHint
                      ? 'bg-green-500/30 border-green-500/50 text-green-300'
                      : activeNum === num
                      ? 'bg-purple-500/40 border-purple-400 text-white'
                      : letter
                      ? 'bg-white/20 border-white/30 text-white'
                      : 'bg-white/5 border-white/20 text-white'
                  }`}
                />
                <span className="text-[10px] text-white/40 mt-0.5">{num}</span>
              </div>
            )
          }
          // Non-number characters (spaces, punctuation)
          return (
            <span key={i} className="text-white/60 text-lg font-mono px-0.5 self-center">
              {token === ' ' ? '\u00A0' : token}
            </span>
          )
        })}
      </div>
    )
  }

  if (!encryptedText) {
    return <p className="text-white/50 text-center py-8">Could not parse cryptoword puzzle.</p>
  }

  return (
    <div className="space-y-5">
      <PuzzleTimer timerStr={puzzle.timer || '3:00'} onTimeUp={handleTimeUp} paused={solved} />

      <p className="text-white/50 text-xs uppercase tracking-widest font-semibold text-center">
        Crack the Code
      </p>
      <p className="text-white/60 text-sm text-center">
        Each number represents a letter. Decode the message.
      </p>

      {puzzle.hint && (
        <p className="text-yellow-300/70 text-sm text-center italic">Hint: {puzzle.hint}</p>
      )}

      {/* Cipher text */}
      <div className="bg-white/5 rounded-xl p-4">
        {renderCipherText()}
      </div>

      {/* Number-to-letter reference */}
      <div className="flex flex-wrap gap-2 justify-center">
        {numbers.map((num) => {
          const letter = userMap[num] || userMap[String(num)] || ''
          return (
            <button
              key={num}
              onClick={() => {
                setActiveNum(num)
                inputRefs.current[num]?.focus()
              }}
              className={`flex flex-col items-center px-2 py-1 rounded-lg text-xs transition-colors ${
                activeNum === num ? 'bg-purple-500/40' : 'bg-white/10'
              }`}
            >
              <span className="text-white font-bold">{letter || '?'}</span>
              <span className="text-white/40">{num}</span>
            </button>
          )
        })}
      </div>

      {!solved && (
        <button
          onClick={checkSolution}
          className="w-full bg-green-500 hover:bg-green-600 text-white font-semibold py-3 rounded-xl transition-colors"
        >
          Check Solution
        </button>
      )}
    </div>
  )
}
