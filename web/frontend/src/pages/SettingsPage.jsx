import { useState } from 'react'
import { motion } from 'framer-motion'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { api } from '../services/api'
import {
  User, LogOut, Trash2, RefreshCw, Bell, Moon, Sun,
  ChevronRight, AlertTriangle, Loader2, Check
} from 'lucide-react'

export default function SettingsPage() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  const [resetting, setResetting] = useState(false)
  const [resetSuccess, setResetSuccess] = useState(false)
  const [showResetConfirm, setShowResetConfirm] = useState(false)
  const [darkMode, setDarkMode] = useState(false)
  const [notifications, setNotifications] = useState(true)

  const handleLogout = async () => {
    await logout()
    navigate('/welcome')
  }

  const handleResetProgress = async () => {
    if (!user?.uid) return

    setResetting(true)
    try {
      const userId = localStorage.getItem('userId') || user.uid
      const result = await api.resetProgress(userId)

      if (result.success) {
        localStorage.setItem('userScore', '0')
        localStorage.setItem('coins', '0')
        setResetSuccess(true)
        setTimeout(() => {
          setResetSuccess(false)
          setShowResetConfirm(false)
        }, 2000)
      }
    } catch (error) {
      console.error('Reset failed:', error)
    } finally {
      setResetting(false)
    }
  }

  const settingSections = [
    {
      title: 'Account',
      items: [
        {
          icon: User,
          label: 'Profile',
          value: user?.email,
          onClick: null
        }
      ]
    },
    {
      title: 'Preferences',
      items: [
        {
          icon: notifications ? Bell : Bell,
          label: 'Notifications',
          toggle: true,
          value: notifications,
          onChange: () => setNotifications(!notifications)
        },
        {
          icon: darkMode ? Moon : Sun,
          label: 'Dark Mode',
          toggle: true,
          value: darkMode,
          onChange: () => setDarkMode(!darkMode),
          disabled: true,
          hint: 'Coming soon'
        }
      ]
    },
    {
      title: 'Data',
      items: [
        {
          icon: RefreshCw,
          label: 'Reset Progress',
          description: 'Clear all puzzle progress and scores',
          onClick: () => setShowResetConfirm(true),
          danger: true
        }
      ]
    }
  ]

  return (
    <div className="max-w-2xl mx-auto space-y-6">
      {/* Header */}
      <motion.div
        initial={{ opacity: 0, y: -20 }}
        animate={{ opacity: 1, y: 0 }}
      >
        <h1 className="text-2xl font-bold text-white">Settings</h1>
      </motion.div>

      {/* User Card */}
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        className="glass rounded-2xl p-6"
      >
        <div className="flex items-center gap-4">
          <div className="w-16 h-16 bg-purple-500 rounded-full flex items-center justify-center overflow-hidden">
            {user?.photoURL ? (
              <img src={user.photoURL} alt="" className="w-full h-full object-cover" />
            ) : (
              <span className="text-white text-2xl font-bold">
                {user?.email?.[0]?.toUpperCase() || '?'}
              </span>
            )}
          </div>
          <div className="flex-1">
            <h2 className="text-xl font-semibold text-white">
              {user?.displayName || 'Player'}
            </h2>
            <p className="text-white/60">{user?.email}</p>
          </div>
        </div>

        {/* Stats */}
        <div className="grid grid-cols-2 gap-4 mt-6 pt-6 border-t border-white/10">
          <div className="text-center">
            <p className="text-2xl font-bold text-purple-400">
              {localStorage.getItem('userScore') || 0}
            </p>
            <p className="text-white/60 text-sm">Total Score</p>
          </div>
          <div className="text-center">
            <p className="text-2xl font-bold text-yellow-400">
              {localStorage.getItem('coins') || 0}
            </p>
            <p className="text-white/60 text-sm">Coins</p>
          </div>
        </div>
      </motion.div>

      {/* Settings Sections */}
      {settingSections.map((section, sectionIndex) => (
        <motion.div
          key={section.title}
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.1 * sectionIndex }}
          className="space-y-2"
        >
          <h3 className="text-white/50 text-sm font-medium px-1">{section.title}</h3>
          <div className="glass rounded-xl overflow-hidden">
            {section.items.map((item, itemIndex) => {
              const Icon = item.icon
              return (
                <div
                  key={item.label}
                  className={`${itemIndex > 0 ? 'border-t border-white/10' : ''}`}
                >
                  {item.toggle ? (
                    // Toggle item
                    <div
                      className={`flex items-center justify-between p-4 ${
                        item.disabled ? 'opacity-50' : ''
                      }`}
                    >
                      <div className="flex items-center gap-3">
                        <Icon className={item.danger ? 'text-red-400' : 'text-white/70'} size={20} />
                        <div>
                          <p className="text-white font-medium">{item.label}</p>
                          {item.hint && (
                            <p className="text-white/50 text-sm">{item.hint}</p>
                          )}
                        </div>
                      </div>
                      <button
                        onClick={item.onChange}
                        disabled={item.disabled}
                        className={`w-12 h-6 rounded-full transition-colors relative ${
                          item.value ? 'bg-purple-500' : 'bg-white/20'
                        }`}
                      >
                        <div
                          className={`absolute top-1 w-4 h-4 bg-white rounded-full transition-transform ${
                            item.value ? 'translate-x-7' : 'translate-x-1'
                          }`}
                        />
                      </button>
                    </div>
                  ) : item.onClick ? (
                    // Clickable item
                    <button
                      onClick={item.onClick}
                      className="w-full flex items-center justify-between p-4 hover:bg-white/5 transition-colors"
                    >
                      <div className="flex items-center gap-3">
                        <Icon className={item.danger ? 'text-red-400' : 'text-white/70'} size={20} />
                        <div className="text-left">
                          <p className={`font-medium ${item.danger ? 'text-red-400' : 'text-white'}`}>
                            {item.label}
                          </p>
                          {item.description && (
                            <p className="text-white/50 text-sm">{item.description}</p>
                          )}
                        </div>
                      </div>
                      <ChevronRight className="text-white/30" size={20} />
                    </button>
                  ) : (
                    // Display item
                    <div className="flex items-center justify-between p-4">
                      <div className="flex items-center gap-3">
                        <Icon className="text-white/70" size={20} />
                        <p className="text-white font-medium">{item.label}</p>
                      </div>
                      <p className="text-white/50 text-sm truncate max-w-[180px]">{item.value}</p>
                    </div>
                  )}
                </div>
              )
            })}
          </div>
        </motion.div>
      ))}

      {/* Sign Out Button */}
      <motion.button
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.3 }}
        onClick={handleLogout}
        className="w-full flex items-center justify-center gap-2 glass hover:bg-red-500/20 text-red-400 font-medium py-4 rounded-xl transition-colors"
      >
        <LogOut size={20} />
        Sign Out
      </motion.button>

      {/* App Info */}
      <div className="text-center text-white/40 text-sm space-y-1 pb-6">
        <p>RiddleVerse v2.0.0</p>
        <p>Made with passion for puzzle lovers</p>
      </div>

      {/* Reset Confirmation Modal */}
      {showResetConfirm && (
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          className="fixed inset-0 bg-black/50 flex items-center justify-center p-4 z-50"
          onClick={() => !resetting && setShowResetConfirm(false)}
        >
          <motion.div
            initial={{ scale: 0.9, opacity: 0 }}
            animate={{ scale: 1, opacity: 1 }}
            onClick={(e) => e.stopPropagation()}
            className="bg-gray-900 rounded-2xl p-6 max-w-sm w-full space-y-4"
          >
            {resetSuccess ? (
              <div className="text-center py-4">
                <div className="w-16 h-16 bg-green-500/20 rounded-full flex items-center justify-center mx-auto mb-4">
                  <Check className="text-green-400" size={32} />
                </div>
                <h3 className="text-xl font-bold text-white">Progress Reset!</h3>
                <p className="text-white/60">Your progress has been cleared.</p>
              </div>
            ) : (
              <>
                <div className="text-center">
                  <div className="w-16 h-16 bg-red-500/20 rounded-full flex items-center justify-center mx-auto mb-4">
                    <AlertTriangle className="text-red-400" size={32} />
                  </div>
                  <h3 className="text-xl font-bold text-white">Reset Progress?</h3>
                  <p className="text-white/60 mt-2">
                    This will permanently delete all your puzzle progress and scores. This action cannot be undone.
                  </p>
                </div>

                <div className="flex gap-3">
                  <button
                    onClick={() => setShowResetConfirm(false)}
                    disabled={resetting}
                    className="flex-1 bg-white/10 hover:bg-white/20 text-white py-3 rounded-xl transition-colors"
                  >
                    Cancel
                  </button>
                  <button
                    onClick={handleResetProgress}
                    disabled={resetting}
                    className="flex-1 bg-red-500 hover:bg-red-600 text-white py-3 rounded-xl transition-colors flex items-center justify-center gap-2"
                  >
                    {resetting ? (
                      <Loader2 className="animate-spin" size={20} />
                    ) : (
                      <>
                        <Trash2 size={18} />
                        Reset
                      </>
                    )}
                  </button>
                </div>
              </>
            )}
          </motion.div>
        </motion.div>
      )}
    </div>
  )
}
