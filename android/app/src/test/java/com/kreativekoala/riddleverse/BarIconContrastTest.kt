package com.kreativekoala.riddleverse

import com.kreativekoala.riddleverse.ui.rvRelativeLuminance
import com.kreativekoala.riddleverse.ui.rvUseDarkBarIcons
import org.junit.Assert.*
import org.junit.Test

class BarIconContrastTest {
    @Test fun whiteAndLightStripsGetDarkIcons() {
        assertTrue(rvUseDarkBarIcons(1f, 1f, 1f))          // RvCanvas white
        assertTrue(rvUseDarkBarIcons(0.9f, 0.9f, 0.95f))   // light grey
        assertTrue(rvUseDarkBarIcons(1f, 0.85f, 0.2f))     // yellow
    }
    @Test fun darkStripsGetLightIcons() {
        assertFalse(rvUseDarkBarIcons(0f, 0f, 0f))
        assertFalse(rvUseDarkBarIcons(0.1f, 0.1f, 0.2f))   // night
        assertFalse(rvUseDarkBarIcons(0.2f, 0.1f, 0.5f))   // deep violet
    }
    @Test fun luminanceEndpoints() {
        assertEquals(0f, rvRelativeLuminance(0f, 0f, 0f), 0.001f)
        assertEquals(1f, rvRelativeLuminance(1f, 1f, 1f), 0.001f)
    }
}
