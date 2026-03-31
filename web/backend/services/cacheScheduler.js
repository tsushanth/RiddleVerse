// cacheScheduler.js - LIGHTWEIGHT VERSION
import cron from 'node-cron';
import { dailyPuzzleCache } from './dailyPuzzleCache.js';
import { db } from '../config/firebaseAdmin.js';

class CacheScheduler {
  constructor() {
    this.scheduledTasks = new Map();
    this.isInitialized = false;
    this.tasksStarted = false;
    
    // Default schedule configuration
    this.scheduleConfig = {
      dailyRefresh: '0 2 * * *',
      healthCheck: '*/30 * * * *',
      cleanupStale: '0 3 * * 0',
      configRefresh: '0 1 * * *'
    };
    
    // Monitoring
    this.stats = {
      lastRefresh: null,
      refreshCount: 0,
      errorCount: 0,
      lastError: null
    };
  }

  /**
   * LIGHTWEIGHT initialization - just setup, no heavy operations
   */
  async initializeLightweight() {
    if (this.isInitialized) return;

    try {
      this.isInitialized = true;
    } catch (error) {
      console.error('Failed to initialize cache scheduler:', error.message);
      throw error;
    }
  }

  /**
   * Start scheduled tasks - called later in background
   */
  async startScheduledTasks() {
    if (this.tasksStarted) return;

    try {
      await this.loadScheduleConfiguration();

      this.setupDailyRefresh();
      this.setupHealthCheck();
      this.setupStaleCleanup();
      this.setupConfigRefresh();

      this.tasksStarted = true;
      console.log('[SCHEDULER] Tasks started - Daily refresh:', this.scheduleConfig.dailyRefresh);
    } catch (error) {
      console.error('[SCHEDULER] Failed to start tasks:', error.message);
    }
  }

  /**
   * Load schedule configuration from Firebase
   */
  async loadScheduleConfiguration() {
    try {
      const configDoc = await db.collection('system_config').doc('cache_scheduler').get();

      if (configDoc.exists) {
        const config = configDoc.data();
        if (config.schedules) {
          this.scheduleConfig = { ...this.scheduleConfig, ...config.schedules };
        }
      }
    } catch (error) {
      // Use defaults on error
    }
  }

  /**
   * Set up daily cache refresh task
   */
  setupDailyRefresh() {
    const task = cron.schedule(this.scheduleConfig.dailyRefresh, async () => {
      await this.performDailyRefresh();
    }, { scheduled: true, timezone: "UTC" });
    this.scheduledTasks.set('dailyRefresh', task);
  }

  setupHealthCheck() {
    const task = cron.schedule(this.scheduleConfig.healthCheck, async () => {
      await this.performHealthCheck();
    }, { scheduled: true, timezone: "UTC" });
    this.scheduledTasks.set('healthCheck', task);
  }

  setupStaleCleanup() {
    const task = cron.schedule(this.scheduleConfig.cleanupStale, async () => {
      await this.performStaleCleanup();
    }, { scheduled: true, timezone: "UTC" });
    this.scheduledTasks.set('cleanupStale', task);
  }

  setupConfigRefresh() {
    const task = cron.schedule(this.scheduleConfig.configRefresh, async () => {
      await this.refreshCacheConfigurations();
    }, { scheduled: true, timezone: "UTC" });
    this.scheduledTasks.set('configRefresh', task);
  }

  /**
   * Perform daily cache refresh - OPTIMIZED VERSION
   */
  async performDailyRefresh() {
    const startTime = Date.now();
    console.log('[SCHEDULER] Daily refresh starting...');

    try {
      const puzzleTypes = [
        // Core puzzle types
        'math', 'anagram', 'imagepuzzle', 'wordsearch', 'crossword', 'trivia', 'memorystory',
        // Visual puzzle types
        'realorai', 'uniqueobject', 'find_object', 'imagequestion', 'waldopuzzle', 'progressiverevelation'
      ];
      const difficulties = ['easy', 'medium', 'hard'];
      const batchSize = 3;
      const results = [];

      for (let i = 0; i < puzzleTypes.length; i++) {
        const puzzleType = puzzleTypes[i];

        for (let j = 0; j < difficulties.length; j += batchSize) {
          const batch = difficulties.slice(j, j + batchSize);

          const batchPromises = batch.map(difficulty =>
            this.refreshSpecificCache(puzzleType, difficulty)
              .then(result => ({ puzzleType, difficulty, success: result.success, error: result.error }))
              .catch(error => ({ puzzleType, difficulty, success: false, error: error.message }))
          );

          const batchResults = await Promise.all(batchPromises);
          results.push(...batchResults);

          if (j + batchSize < difficulties.length || i < puzzleTypes.length - 1) {
            await new Promise(resolve => setTimeout(resolve, 2000));
          }
        }
      }

      const successful = results.filter(r => r.success);
      const failed = results.filter(r => !r.success);
      const duration = Date.now() - startTime;

      this.stats.lastRefresh = new Date().toISOString();
      this.stats.refreshCount++;
      if (failed.length > 0) {
        this.stats.errorCount++;
        this.stats.lastError = failed.slice(0, 3).map(f => `${f.puzzleType}/${f.difficulty}: ${f.error}`).join('; ');
      }

      console.log(`[SCHEDULER] Daily refresh completed: ${successful.length}/${results.length} success in ${Math.round(duration / 1000)}s`);

      setImmediate(() => {
        this.logRefreshResult({
          timestamp: new Date().toISOString(),
          duration,
          totalCaches: results.length,
          successful: successful.length,
          failed: failed.length,
          failures: failed.slice(0, 10)
        });
      });

    } catch (error) {
      console.error('[SCHEDULER] Daily refresh failed:', error.message);
      this.stats.errorCount++;
      this.stats.lastError = error.message;
    }
  }

  /**
   * Refresh a specific cache with error handling
   */
  async refreshSpecificCache(puzzleType, difficulty) {
    try {
      return await dailyPuzzleCache.buildDailyCache(puzzleType, difficulty);
    } catch (error) {
      return { success: false, error: error.message };
    }
  }

  /**
   * Perform health check - LIGHTWEIGHT VERSION
   */
  async performHealthCheck() {
    try {
      await dailyPuzzleCache.redis.ping();
    } catch (error) {
      console.error('[SCHEDULER] Health check failed:', error.message);
      setImmediate(() => {
        this.logHealthCheckResult({
          timestamp: new Date().toISOString(),
          success: false,
          error: error.message
        });
      });
    }
  }

  /**
   * Clean up stale data from Redis - OPTIMIZED VERSION
   */
  async performStaleCleanup() {
    try {
      const cacheKeys = await dailyPuzzleCache.redis.keys('daily_puzzles:*');
      let deletedKeys = 0;
      const maxAge = 7 * 24 * 60 * 60;
      const batchSize = 10;

      for (let i = 0; i < cacheKeys.length; i += batchSize) {
        const batch = cacheKeys.slice(i, i + batchSize);

        const deletePromises = batch.map(async (key) => {
          try {
            const ttl = await dailyPuzzleCache.redis.ttl(key);
            if (ttl === -1 || (ttl > 0 && ttl < maxAge)) {
              await dailyPuzzleCache.redis.del(key);
              return 1;
            }
            return 0;
          } catch {
            return 0;
          }
        });

        const batchDeleted = await Promise.all(deletePromises);
        deletedKeys += batchDeleted.reduce((sum, count) => sum + count, 0);

        if (i + batchSize < cacheKeys.length) {
          await new Promise(resolve => setTimeout(resolve, 100));
        }
      }

      setImmediate(() => {
        this.logCleanupResult({
          timestamp: new Date().toISOString(),
          deletedKeys,
          totalChecked: cacheKeys.length
        });
      });

    } catch (error) {
      console.error('[SCHEDULER] Stale cleanup failed:', error.message);
    }
  }

  /**
   * Refresh cache configurations
   */
  async refreshCacheConfigurations() {
    try {
      const configKeys = await dailyPuzzleCache.redis.keys('puzzle_config:*');
      if (configKeys.length > 0) {
        await dailyPuzzleCache.redis.del(...configKeys);
      }
      await this.loadScheduleConfiguration();
    } catch (error) {
      console.error('[SCHEDULER] Config refresh failed:', error.message);
    }
  }

  /**
   * Log refresh results - ASYNC ONLY
   */
  async logRefreshResult(result) {
    try {
      await db.collection('cache_refresh_logs').add(result);
    } catch {
      // Silent fail for logging
    }
  }

  async logHealthCheckResult(result) {
    try {
      await db.collection('cache_health_logs').add(result);
    } catch {
      // Silent fail for logging
    }
  }

  async logCleanupResult(result) {
    try {
      await db.collection('cache_cleanup_logs').add(result);
    } catch {
      // Silent fail for logging
    }
  }

  /**
   * Stop all scheduled tasks
   */
  stopAllTasks() {
    this.scheduledTasks.forEach((task) => task.stop());
    this.scheduledTasks.clear();
    this.tasksStarted = false;
  }

  /**
   * Get scheduler statistics
   */
  getStats() {
    return {
      ...this.stats,
      activeTasks: this.scheduledTasks.size,
      isInitialized: this.isInitialized,
      tasksStarted: this.tasksStarted,
      scheduleConfig: this.scheduleConfig
    };
  }

  /**
   * Manually trigger daily refresh
   */
  async manualRefresh() {
    await this.performDailyRefresh();
  }
}

// Create singleton instance
const cacheScheduler = new CacheScheduler();

export { cacheScheduler, CacheScheduler };