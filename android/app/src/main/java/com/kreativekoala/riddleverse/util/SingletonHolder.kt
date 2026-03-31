// SingletonHolder.kt - Reusable singleton pattern utility
package com.kreativekoala.riddleverse.util

/**
 * Generic singleton holder that provides thread-safe lazy initialization.
 * Eliminates boilerplate for companion object singleton patterns.
 *
 * Usage:
 * ```
 * class MyManager private constructor(context: Context) {
 *     companion object : SingletonHolder<MyManager, Context>(::MyManager)
 * }
 *
 * // Then use:
 * MyManager.getInstance(context)
 * ```
 */
open class SingletonHolder<out T : Any, in A>(creator: (A) -> T) {
    private var creator: ((A) -> T)? = creator
    @Volatile private var instance: T? = null

    fun getInstance(arg: A): T {
        val currentInstance = instance
        if (currentInstance != null) {
            return currentInstance
        }

        return synchronized(this) {
            val syncedInstance = instance
            if (syncedInstance != null) {
                syncedInstance
            } else {
                val created = creator!!(arg)
                instance = created
                creator = null
                created
            }
        }
    }

    fun isInitialized(): Boolean = instance != null

    /**
     * Clears the singleton instance. Use with caution.
     */
    fun clearInstance() {
        synchronized(this) {
            instance = null
        }
    }
}

/**
 * Singleton holder for classes that don't require initialization arguments.
 *
 * Usage:
 * ```
 * class MyManager private constructor() {
 *     companion object : NoArgSingletonHolder<MyManager>(::MyManager)
 * }
 *
 * // Then use:
 * MyManager.getInstance()
 * ```
 */
open class NoArgSingletonHolder<out T : Any>(creator: () -> T) {
    private var creator: (() -> T)? = creator
    @Volatile private var instance: T? = null

    fun getInstance(): T {
        val currentInstance = instance
        if (currentInstance != null) {
            return currentInstance
        }

        return synchronized(this) {
            val syncedInstance = instance
            if (syncedInstance != null) {
                syncedInstance
            } else {
                val created = creator!!()
                instance = created
                creator = null
                created
            }
        }
    }

    fun isInitialized(): Boolean = instance != null

    /**
     * Clears the singleton instance. Use with caution.
     */
    fun clearInstance() {
        synchronized(this) {
            instance = null
        }
    }
}
