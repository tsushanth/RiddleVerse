import { useState } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import { Share2 } from 'lucide-react'

export default function ShareButton({ title, text, url }) {
  const [showToast, setShowToast] = useState(false)

  const handleShare = async () => {
    if (navigator.share) {
      try {
        await navigator.share({ title, text, url })
      } catch (err) {
        // User cancelled or share failed — ignore
        if (err.name !== 'AbortError') {
          fallbackCopy()
        }
      }
    } else {
      fallbackCopy()
    }
  }

  const fallbackCopy = async () => {
    try {
      await navigator.clipboard.writeText(url || text)
      setShowToast(true)
      setTimeout(() => setShowToast(false), 2000)
    } catch {
      // Clipboard API not available
    }
  }

  return (
    <div className="relative">
      <button
        onClick={handleShare}
        className="flex items-center gap-2 glass hover:bg-white/20 text-purple-300 hover:text-white font-medium px-4 py-2.5 rounded-xl transition-all"
      >
        <Share2 size={18} />
        <span>Share</span>
      </button>

      {/* Toast */}
      <AnimatePresence>
        {showToast && (
          <motion.div
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -10 }}
            className="absolute -top-10 left-1/2 -translate-x-1/2 bg-green-500/90 text-white text-sm font-medium px-3 py-1.5 rounded-lg whitespace-nowrap"
          >
            Link copied!
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  )
}
