package com.nekos.android

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeWallpaperValidationTest {
    @Test
    fun acceptsPositiveImageDimensionsWithinPixelLimit() {
        assertTrue(isValidHomeWallpaperDimensions(1920, 1080))
        assertTrue(isValidHomeWallpaperDimensions(10000, 10000))
    }

    @Test
    fun rejectsEmptyOrNegativeImageDimensions() {
        assertFalse(isValidHomeWallpaperDimensions(0, 100))
        assertFalse(isValidHomeWallpaperDimensions(100, 0))
        assertFalse(isValidHomeWallpaperDimensions(-1, 100))
        assertFalse(isValidHomeWallpaperDimensions(100, -1))
    }

    @Test
    fun rejectsImagesAbovePixelLimit() {
        assertFalse(isValidHomeWallpaperDimensions(10001, 10000))
        assertFalse(isValidHomeWallpaperDimensions(50000, 50000))
    }

    @Test
    fun checksPixelCountWithoutIntegerOverflow() {
        assertFalse(isValidHomeWallpaperDimensions(Int.MAX_VALUE, Int.MAX_VALUE))
    }
}
