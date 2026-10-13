import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { auth } from '../services/firebase'
import { useAuth } from '../context/AuthContext'
import AdminDashboard from '../components/AdminDashboard'
import PuzzlesPanel from '../components/PuzzlesPanel'

// The gate is /api/dash/overview (404 unless the token is the allowlisted Google account).
export default function AdminPage() {
  const { isAuthenticated, loading } = useAuth()
  const [token, setToken] = useState(null)
  const [tab, setTab] = useState('health')

  useEffect(() => {
    if (!isAuthenticated) return
    let cancelled = false
    auth.currentUser?.getIdToken().then((t) => { if (!cancelled) setToken(t) })
    return () => { cancelled = true }
  }, [isAuthenticated])

  if (loading) return null
  if (!isAuthenticated) {
    return <p style={{ padding: 24 }}>Sign in with Google on <Link to="/welcome" style={{ textDecoration: 'underline' }}>/welcome</Link>, then open /admin again.</p>
  }
  if (!token) return null
  const tabBtn = (id, label) => (
    <button key={id} onClick={() => setTab(id)} style={{ padding: '6px 14px', borderRadius: 6, border: '1px solid #8886', fontWeight: tab === id ? 700 : 400, background: tab === id ? '#8882' : 'transparent' }}>{label}</button>
  )
  return (
    <>
      <div style={{ maxWidth: 1100, margin: '0 auto', padding: '16px 16px 0', display: 'flex', gap: 8 }}>
        {tabBtn('health', 'Health')}{tabBtn('puzzles', 'Puzzles')}
      </div>
      {tab === 'health'
        ? <AdminDashboard token={token} endpoint="/api/dash/overview" />
        : <div style={{ maxWidth: 1100, margin: '0 auto', padding: 16, fontSize: 14, lineHeight: 1.45 }}><PuzzlesPanel token={token} /></div>}
    </>
  )
}
