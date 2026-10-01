import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { auth } from '../services/firebase'
import { useAuth } from '../context/AuthContext'
import AdminDashboard from '../components/AdminDashboard'

// The gate is /api/dash/overview (404 unless the token is the allowlisted Google account).
export default function AdminPage() {
  const { isAuthenticated, loading } = useAuth()
  const [token, setToken] = useState(null)

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
  return <AdminDashboard token={token} endpoint="/api/dash/overview" />
}
