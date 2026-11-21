package com.nekos.android

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLabelSortingTest {
    @Test
    fun sortsLabelsAlphabeticallyForEnglish() {
        val labels = listOf("Zebra", "banana", "Apple")

        val sorted = labels.sortedWith(appLabelComparator(Locale.US))

        assertEquals(listOf("Apple", "banana", "Zebra"), sorted)
    }

    @Test
    fun followsSwedishAlphabeticalOrder() {
        val labels = listOf("öga", "älg", "zebra", "åska")

        val sorted = labels.sortedWith(appLabelComparator(Locale("sv", "SE")))

        assertEquals(listOf("zebra", "åska", "älg", "öga"), sorted)
    }

    @Test
    fun comparesLabelsWithoutTreatingLetterCaseAsDifferent() {
        val comparator = appLabelComparator(Locale.US)

        assertEquals(0, comparator.compare("NekOS", "nekos"))
    }

    @Test
    fun keepsAccentedLettersDistinctAccordingToLocale() {
        val comparator = appLabelComparator(Locale("sv", "SE"))

        assertTrue(comparator.compare("z", "å") < 0)
        assertTrue(comparator.compare("å", "ä") < 0)
        assertTrue(comparator.compare("ä", "ö") < 0)
    }
}
