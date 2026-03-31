import { Outlet, NavLink, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { Home, Trophy, Settings, LogOut, Compass, PlusCircle, Award } from 'lucide-react'
import { motion } from 'framer-motion'
import CoinDisplay from './CoinDisplay'

export default function Layout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  const handleLogout = async () => {
    await logout()
    navigate('/welcome')
  }

  const navItems = [
    { path: '/', icon: Home, label: 'Home' },
    { path: '/browse', icon: Compass, label: 'Browse' },
    { path: '/create', icon: PlusCircle, label: 'Create' },
    { path: '/leaderboard', icon: Trophy, label: 'Leaderboard' },
    { path: '/badges', icon: Award, label: 'Badges' },
    { path: '/settings', icon: Settings, label: 'Settings' },
  ]

  return (
    <div className="min-h-screen bg-gradient-to-br from-purple-600 via-purple-700 to-indigo-800">
      {/* Desktop Header */}
      <header className="glass sticky top-0 z-50 hidden md:block">
        <div className="max-w-7xl mx-auto px-4 py-3 flex items-center justify-between">
          {/* Logo */}
          <NavLink to="/" className="flex items-center gap-2">
            <div className="flex gap-1">
              <span className="w-6 h-6 bg-pink-500 rounded-full flex items-center justify-center text-white text-sm">?</span>
              <span className="w-7 h-7 bg-orange-500 rounded-full flex items-center justify-center text-white text-base">?</span>
              <span className="w-6 h-6 bg-purple-400 rounded-full flex items-center justify-center text-white text-sm">?</span>
            </div>
            <span className="font-bold text-white text-lg">RIDDLEVERSE</span>
          </NavLink>

          {/* Desktop Navigation */}
          <nav className="flex items-center gap-6">
            {navItems.map(({ path, icon: Icon, label }) => (
              <NavLink
                key={path}
                to={path}
                end={path === '/'}
                className={({ isActive }) =>
                  `flex items-center gap-2 px-3 py-2 rounded-lg transition-all ${
                    isActive
                      ? 'bg-white/20 text-white'
                      : 'text-white/70 hover:text-white hover:bg-white/10'
                  }`
                }
              >
                <Icon size={18} />
                <span>{label}</span>
              </NavLink>
            ))}
          </nav>

          {/* User info & Logout */}
          <div className="flex items-center gap-4">
            <CoinDisplay />
            <div className="flex items-center gap-2 text-white">
              {user?.photoURL ? (
                <img src={user.photoURL} alt="" className="w-8 h-8 rounded-full" />
              ) : (
                <div className="w-8 h-8 bg-white/20 rounded-full flex items-center justify-center">
                  {user?.email?.[0]?.toUpperCase() || '?'}
                </div>
              )}
              <span className="text-sm max-w-[120px] truncate">{user?.displayName || user?.email}</span>
            </div>

            <button
              onClick={handleLogout}
              className="flex items-center gap-2 text-white/70 hover:text-white transition-colors"
            >
              <LogOut size={18} />
            </button>
          </div>
        </div>
      </header>

      {/* Main Content */}
      <main className="max-w-7xl mx-auto px-4 py-6 pb-24 md:pb-6">
        <Outlet />
      </main>

      {/* Mobile Bottom Tab Bar */}
      <nav className="md:hidden fixed bottom-0 left-0 right-0 z-50 bg-gray-900/95 backdrop-blur-lg border-t border-white/10">
        <div className="flex items-center justify-around px-2 py-2">
          {navItems.map(({ path, icon: Icon, label }) => (
            <NavLink
              key={path}
              to={path}
              end={path === '/'}
              className={({ isActive }) =>
                `flex flex-col items-center gap-0.5 px-3 py-1.5 rounded-lg transition-all min-w-0 ${
                  isActive
                    ? 'text-purple-400'
                    : 'text-white/40'
                }`
              }
            >
              {({ isActive }) => (
                <>
                  <div className={`p-1.5 rounded-lg transition-all ${isActive ? 'bg-purple-500/20' : ''}`}>
                    <Icon size={22} />
                  </div>
                  <span className="text-[10px] font-medium leading-tight">{label}</span>
                </>
              )}
            </NavLink>
          ))}
        </div>
        {/* Safe area padding for iOS */}
        <div className="h-[env(safe-area-inset-bottom)]" />
      </nav>
    </div>
  )
}
