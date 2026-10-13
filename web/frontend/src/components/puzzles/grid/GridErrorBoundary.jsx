import { Component } from 'react'
import { trackPuzzleEvent } from '../../../utils/puzzleEvents'

/** Catches render errors inside a grid game, reports them and offers a way out. */
export default class GridErrorBoundary extends Component {
  constructor(props) {
    super(props)
    this.state = { failed: false }
  }

  static getDerivedStateFromError() {
    return { failed: true }
  }

  componentDidCatch(error) {
    trackPuzzleEvent('error', this.props.puzzleType, {
      difficulty: this.props.difficulty,
      errorMessage: (error && error.message) || 'render error',
    })
  }

  render() {
    if (!this.state.failed) return this.props.children
    return (
      <div role="alert" className="text-center space-y-3 py-6">
        <p className="text-white font-semibold">Something went wrong loading this puzzle.</p>
        <button
          type="button"
          onClick={() => this.props.onSkip && this.props.onSkip()}
          className="min-h-[44px] px-5 rounded-xl bg-white/20 hover:bg-white/30 text-white font-semibold"
        >
          Skip this puzzle
        </button>
      </div>
    )
  }
}
