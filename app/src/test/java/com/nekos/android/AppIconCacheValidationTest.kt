package com.nekos.android

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppIconCacheValidationTest {
    @Test
    fun acceptsExpectedLauncherIconDimensions() {
        assertTrue(isValidCachedIconDimensions(96, 96))
        assertTrue(isValidCachedIconDimensions(48, 72))
        assertTrue(isValidCachedIconDimensions(1, 1))
    }

    @Test
    fun rejectsEmptyOrInvalidDimensions() {
        assertFalse(isValidCachedIconDimensions(0, 96))
        assertFalse(isValidCachedIconDimensions(96, 0))
        assertFalse(isValidCachedIconDimensions(-1, 96))
    }

    @Test
    fun rejectsOversizedDiskCacheImages() {
        assertFalse(isValidCachedIconDimensions(97, 96))
        assertFalse(isValidCachedIconDimensions(96, 512))
        assertFalse(isValidCachedIconDimensions(512, 512))
    }

    @Test
    fun rejectsInvalidMaximumDimension() {
        assertFalse(isValidCachedIconDimensions(1, 1, 0))
    }
}
