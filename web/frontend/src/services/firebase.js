import { initializeApp } from 'firebase/app'
import {
  getAuth,
  GoogleAuthProvider,
  signInWithPopup,
  signInWithEmailAndPassword,
  createUserWithEmailAndPassword,
  signOut as firebaseSignOut,
  onAuthStateChanged
} from 'firebase/auth'
import { getAnalytics, logEvent } from 'firebase/analytics'

// Firebase configuration - matching iOS/Android
const firebaseConfig = {
  apiKey: "AIzaSyCUdOy2BUynLmg3eSJQwRz2sNpIWOSbFq0",
  authDomain: "aipuzzle-3122c.firebaseapp.com",
  projectId: "aipuzzle-3122c",
  storageBucket: "aipuzzle-3122c.firebasestorage.app",
  messagingSenderId: "719851629523",
  appId: "1:719851629523:web:d1f7f9e20e3ef6df129d26",
  measurementId: "G-BJ4LKWX858"
}

// Initialize Firebase
const app = initializeApp(firebaseConfig)
export const auth = getAuth(app)
export const analytics = typeof window !== 'undefined' ? getAnalytics(app) : null

const googleProvider = new GoogleAuthProvider()

// Authentication functions
export const signInWithGoogle = async () => {
  try {
    const result = await signInWithPopup(auth, googleProvider)
    const idToken = await result.user.getIdToken()
    return { user: result.user, idToken }
  } catch (error) {
    console.error('Google sign-in error:', error)
    throw error
  }
}

export const signInWithEmail = async (email, password) => {
  try {
    const result = await signInWithEmailAndPassword(auth, email, password)
    const idToken = await result.user.getIdToken()
    return { user: result.user, idToken }
  } catch (error) {
    console.error('Email sign-in error:', error)
    throw error
  }
}

export const signUpWithEmail = async (email, password) => {
  try {
    const result = await createUserWithEmailAndPassword(auth, email, password)
    const idToken = await result.user.getIdToken()
    return { user: result.user, idToken }
  } catch (error) {
    console.error('Email sign-up error:', error)
    throw error
  }
}

export const signOut = async () => {
  try {
    await firebaseSignOut(auth)
    localStorage.removeItem('token')
    localStorage.removeItem('email')
    localStorage.removeItem('userId')
  } catch (error) {
    console.error('Sign-out error:', error)
    throw error
  }
}

export const onAuthChange = (callback) => {
  return onAuthStateChanged(auth, callback)
}

// Analytics tracking
export const trackEvent = (eventName, params = {}) => {
  if (analytics) {
    logEvent(analytics, eventName, params)
  }
}

export default app
