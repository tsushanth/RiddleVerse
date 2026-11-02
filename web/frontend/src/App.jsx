import { lazy, Suspense } from 'react'
import { Routes, Route, Navigate, useSearchParams } from 'react-router-dom'
import { useAuth } from './context/AuthContext'
import { GameProvider } from './context/GameContext'
import Layout from './components/Layout'
import WelcomePage from './pages/WelcomePage'
import HomePage from './pages/HomePage'
import PuzzlePage from './pages/PuzzlePage'
import LeaderboardPage from './pages/LeaderboardPage'
import SettingsPage from './pages/SettingsPage'
import BrowseGamesPage from './pages/BrowseGamesPage'
import CreateGamePage from './pages/CreateGamePage'
import DailyChallengePage from './pages/DailyChallengePage'
import BadgesPage from './pages/BadgesPage'
import GameLeaderboardPage from './pages/GameLeaderboardPage'
import CoinShopPage from './pages/CoinShopPage'
import GameSessionPage from './pages/GameSessionPage'
import LoadingSpinner from './components/LoadingSpinner'
import AdminPage from './pages/AdminPage'
// The public /play pages (shell, catalog, analytics, daily) load on demand: they are only needed on /play*, and keeping them
// out of the shared bundle holds the main chunk within its size budget.
const PlayIndexPage = lazy(() => import('./pages/PlayIndexPage'))
const PlayGamePage = lazy(() => import('./pages/PlayGamePage'))
const DailyPage = lazy(() => import('./pages/DailyPage'))

// Protected route wrapper
function ProtectedRoute({ children }) {
  const { isAuthenticated, loading } = useAuth()

  if (loading) {
    return <LoadingSpinner />
  }

  if (!isAuthenticated) {
    return <Navigate to="/welcome" replace />
  }

  return children
}

// Public route (redirects to home if already logged in)
function PublicRoute({ children }) {
  const { isAuthenticated, loading } = useAuth()

  if (loading) {
    return <LoadingSpinner />
  }

  if (isAuthenticated) {
    return <Navigate to="/" replace />
  }

  return children
}

// /play?session_id=<uuid> is the host-controlled AI game session wrapper; plain /play is the public game list.
function PlayEntry() {
  const [params] = useSearchParams()
  return params.get('session_id') !== null ? <GameSessionPage /> : <Suspense fallback={<LoadingSpinner />}><PlayIndexPage /></Suspense>
}

export default function App() {
  return (
    <GameProvider>
    <Routes>
      <Route path="/admin" element={<AdminPage />} />

      <Route path="/welcome" element={
        <PublicRoute>
          <WelcomePage />
        </PublicRoute>
      } />

      <Route element={<Layout />}>
        <Route path="/" element={
          <ProtectedRoute>
            <HomePage />
          </ProtectedRoute>
        } />

        <Route path="/puzzle/:type" element={
          <ProtectedRoute>
            <PuzzlePage />
          </ProtectedRoute>
        } />

        <Route path="/leaderboard" element={
          <ProtectedRoute>
            <LeaderboardPage />
          </ProtectedRoute>
        } />

        <Route path="/settings" element={
          <ProtectedRoute>
            <SettingsPage />
          </ProtectedRoute>
        } />

        <Route path="/browse" element={
          <ProtectedRoute>
            <BrowseGamesPage />
          </ProtectedRoute>
        } />

        <Route path="/create" element={
          <ProtectedRoute>
            <CreateGamePage />
          </ProtectedRoute>
        } />

        <Route path="/daily" element={
          <ProtectedRoute>
            <DailyChallengePage />
          </ProtectedRoute>
        } />

        <Route path="/badges" element={
          <ProtectedRoute>
            <BadgesPage />
          </ProtectedRoute>
        } />

        <Route path="/game/:gameId/leaderboard" element={
          <ProtectedRoute>
            <GameLeaderboardPage />
          </ProtectedRoute>
        } />

        <Route path="/coins" element={
          <ProtectedRoute>
            <CoinShopPage />
          </ProtectedRoute>
        } />
      </Route>

      {/* Public, indexable, no-login play pages (meta for crawlers is injected by the backend, see services/playPages.js) */}
      <Route path="/play" element={<PlayEntry />} />
      <Route path="/play/daily" element={<Suspense fallback={<LoadingSpinner />}><DailyPage /></Suspense>} /> {/* web/daily-page */}
      <Route path="/play/:type" element={<Suspense fallback={<LoadingSpinner />}><PlayGamePage /></Suspense>} />

      {/* Catch all - redirect to home or welcome */}
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
    </GameProvider>
  )
}
