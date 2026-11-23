package com.kreativekoala.riddleverse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** The startup contract: an SDK init runs once, and a caller that needs it before the background thread got there is safe. */
class AppStartupTest {

    @Test fun ensureRunsTheBlockOnceNoMatterHowManyCallers() {
        val runs = AtomicInteger()
        val init = OnceInit("x") { runs.incrementAndGet() }
        assertFalse(init.isFinished)
        repeat(5) { assertTrue(init.ensure()) }
        assertEquals(1, runs.get())
        assertTrue(init.isFinished)
        assertTrue(init.succeeded)
    }

    @Test fun concurrentCallersWaitForTheRunningInitAndSeeItFinished() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val runs = AtomicInteger()
        val ready = java.util.concurrent.atomic.AtomicBoolean(false)
        val init = OnceInit("slow") {
            runs.incrementAndGet()
            entered.countDown()
            release.await(5, TimeUnit.SECONDS)
            ready.set(true)
        }
        val bg = Executors.newSingleThreadExecutor()
        bg.execute { init.ensure() }               // the startup thread is mid-init...
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        val seenReady = AtomicInteger(-1)
        val caller = Thread { init.ensure(); seenReady.set(if (ready.get()) 1 else 0) }  // ...when the "main thread" needs it
        caller.start()
        Thread.sleep(100)
        assertEquals("caller must still be waiting", -1, seenReady.get())
        release.countDown()
        caller.join(5000)
        assertEquals("caller returned only after init completed", 1, seenReady.get())
        assertEquals(1, runs.get())
        bg.shutdownNow()
    }

    @Test fun aFailingInitIsReportedNotThrownAndNotRetried() {
        val runs = AtomicInteger()
        val init = OnceInit("boom") { runs.incrementAndGet(); throw IllegalStateException("nope") }
        assertFalse(init.ensure())
        assertFalse(init.ensure())
        assertEquals(1, runs.get())
        assertTrue(init.isFinished)
        assertFalse(init.succeeded)
    }

    @Test fun usingTheSdkGatesBeforeStartIsSafe() {
        // AppStartup.start was never called here: every gate must still return without throwing.
        assertTrue(AppStartup.ensureExperimentManager())
        assertTrue(AppStartup.ensureRatingKit())
        assertTrue(AppStartup.ensureTikTok())
    }

    @Test fun postedWorkRunsInOrderAndATaskThatThrowsDoesNotKillTheQueue() {
        val order = java.util.Collections.synchronizedList(mutableListOf<Int>())
        val done = CountDownLatch(1)
        AppStartup.post { order += 1 }
        AppStartup.post { throw RuntimeException("bad task") }
        AppStartup.post { order += 2 }
        AppStartup.post { done.countDown() }
        assertTrue(done.await(5, TimeUnit.SECONDS))
        assertEquals(listOf(1, 2), order)
    }
}
