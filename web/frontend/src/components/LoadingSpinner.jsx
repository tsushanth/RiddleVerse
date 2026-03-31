import { motion } from 'framer-motion'

export default function LoadingSpinner({ message = 'Loading...' }) {
  return (
    <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-purple-600 via-purple-700 to-indigo-800">
      <motion.div
        initial={{ opacity: 0, scale: 0.9 }}
        animate={{ opacity: 1, scale: 1 }}
        className="text-center"
      >
        {/* Puzzle icons animation */}
        <div className="flex justify-center gap-4 mb-6">
          {['#ec4899', '#f97316', '#8b5cf6'].map((color, i) => (
            <motion.div
              key={i}
              className="w-12 h-12 rounded-full flex items-center justify-center"
              style={{ backgroundColor: color }}
              animate={{
                scale: [1, 1.2, 1],
                y: [0, -10, 0],
              }}
              transition={{
                duration: 0.8,
                repeat: Infinity,
                delay: i * 0.2,
              }}
            >
              <span className="text-2xl">?</span>
            </motion.div>
          ))}
        </div>

        <h1 className="text-2xl font-bold text-white mb-2">RIDDLEVERSE</h1>

        {/* Loading spinner */}
        <motion.div
          className="w-8 h-8 border-4 border-white/30 border-t-white rounded-full mx-auto mb-4"
          animate={{ rotate: 360 }}
          transition={{ duration: 1, repeat: Infinity, ease: 'linear' }}
        />

        <p className="text-white/80">{message}</p>
      </motion.div>
    </div>
  )
}
