package com.nekos.android

import java.text.Collator
import java.util.Locale

/**
 * Creates a case-insensitive comparator that follows the supplied device locale.
 * A fresh Collator is created for each sorting operation because Collator is mutable
 * and is not guaranteed to be thread-safe.
 */
internal fun appLabelComparator(locale: Locale = Locale.getDefault()): Comparator<String> {
    val collator = Collator.getInstance(locale).apply {
        strength = Collator.SECONDARY
    }
    return Comparator { first, second -> collator.compare(first, second) }
}
