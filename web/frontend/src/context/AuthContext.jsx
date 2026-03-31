import { createContext, useContext, useState, useEffect } from 'react'
import { auth, onAuthChange, signInWithGoogle, signInWithEmail, signUpWithEmail, signOut, trackEvent } from '../services/firebase'
import { api } from '../services/api'

const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  useEffect(() => {
    const unsubscribe = onAuthChange(async (firebaseUser) => {
      if (firebaseUser) {
        const token = localStorage.getItem('token')
        setUser({
          uid: firebaseUser.uid,
          email: firebaseUser.email,
          displayName: firebaseUser.displayName,
          photoURL: firebaseUser.photoURL,
          token
        })
      } else {
        setUser(null)
      }
      setLoading(false)
    })

    return () => unsubscribe()
  }, [])

  const loginWithGoogle = async () => {
    try {
      setError(null)
      const { user: firebaseUser, idToken } = await signInWithGoogle()

      // Authenticate with backend
      const response = await api.authenticateWithGoogle(idToken)

      if (response.success) {
        localStorage.setItem('token', response.token)
        localStorage.setItem('email', response.email)
        localStorage.setItem('userId', response.userId)

        setUser({
          uid: firebaseUser.uid,
          email: firebaseUser.email,
          displayName: firebaseUser.displayName,
          photoURL: firebaseUser.photoURL,
          token: response.token
        })

        trackEvent('login', { method: 'google' })
        return { success: true }
      } else {
        throw new Error(response.message || 'Authentication failed')
      }
    } catch (err) {
      setError(err.message)
      return { success: false, error: err.message }
    }
  }

  const loginWithEmail = async (email, password) => {
    try {
      setError(null)
      const { user: firebaseUser, idToken } = await signInWithEmail(email, password)

      // Authenticate with backend
      const response = await api.authenticateWithGoogle(idToken)

      if (response.success) {
        localStorage.setItem('token', response.token)
        localStorage.setItem('email', response.email)
        localStorage.setItem('userId', response.userId)

        setUser({
          uid: firebaseUser.uid,
          email: firebaseUser.email,
          displayName: firebaseUser.displayName,
          token: response.token
        })

        trackEvent('login', { method: 'email' })
        return { success: true }
      } else {
        throw new Error(response.message || 'Authentication failed')
      }
    } catch (err) {
      setError(err.message)
      return { success: false, error: err.message }
    }
  }

  const registerWithEmail = async (email, password) => {
    try {
      setError(null)
      const { user: firebaseUser, idToken } = await signUpWithEmail(email, password)

      // Authenticate with backend
      const response = await api.authenticateWithGoogle(idToken)

      if (response.success) {
        localStorage.setItem('token', response.token)
        localStorage.setItem('email', response.email)
        localStorage.setItem('userId', response.userId)

        setUser({
          uid: firebaseUser.uid,
          email: firebaseUser.email,
          displayName: firebaseUser.displayName,
          token: response.token
        })

        trackEvent('sign_up', { method: 'email' })
        return { success: true }
      } else {
        throw new Error(response.message || 'Registration failed')
      }
    } catch (err) {
      setError(err.message)
      return { success: false, error: err.message }
    }
  }

  const logout = async () => {
    try {
      await signOut()
      setUser(null)
      trackEvent('logout')
    } catch (err) {
      setError(err.message)
    }
  }

  return (
    <AuthContext.Provider value={{
      user,
      loading,
      error,
      isAuthenticated: !!user,
      loginWithGoogle,
      loginWithEmail,
      registerWithEmail,
      logout
    }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider')
  }
  return context
}

export default AuthContext
